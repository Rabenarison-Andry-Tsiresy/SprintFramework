package src;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import src.annotation.Controller;
import src.annotation.GetMapping;
import src.annotation.PostMapping;
import src.annotation.RequestMapping;
import src.annotation.Url;

@WebListener
public class FrontControllerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        String configuredPackage = context.getInitParameter("base-package");
        String packageConfig = configuredPackage != null && !configuredPackage.isBlank()
                ? configuredPackage
                : "tsiresy.main";

        String viewPrefix = context.getInitParameter("view-prefix");
        if (viewPrefix == null) {
            viewPrefix = "/WEB-INF/jsp/";
        }

        String viewSuffix = context.getInitParameter("view-suffix");
        if (viewSuffix == null) {
            viewSuffix = ".jsp";
        }

        Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings = new HashMap<>();

        try {
            String[] packages = packageConfig.split("[,;\\s]+");
            for (String pkg : packages) {
                if (!pkg.isBlank()) {
                    scanPackage(pkg.trim(), urlMappings);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'initialisation du FrontController", e);
        }

        context.setAttribute("urlMappings", urlMappings);
        context.setAttribute("viewPrefix", viewPrefix);
        context.setAttribute("viewSuffix", viewSuffix);
        System.out.println("[SprintFramework] FrontController initialisé avec " + urlMappings.size() + " route(s).");
    }

    private void scanPackage(String packageName, Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings)
            throws IOException, ClassNotFoundException, URISyntaxException,
            InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String path = packageName.replace('.', '/');
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = FrontControllerListener.class.getClassLoader();
        }

        Enumeration<URL> resources = cl.getResources(path);
        boolean found = false;

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();
            found = true;
            if ("jar".equals(url.getProtocol())) {
                scanJar(path, url, urlMappings);
            } else if ("file".equals(url.getProtocol())) {
                scanDirectory(packageName, new File(url.toURI()), urlMappings);
            }
        }

        if (!found) {
            System.err.println("[SprintFramework] Avertissement : Le package \"" + packageName + "\" est introuvable sur le classpath.");
        }
    }

    private void scanJar(String path, URL url, Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings)
            throws IOException, ClassNotFoundException, InstantiationException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        JarFile jarFile = connection.getJarFile();

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.startsWith(path) && name.endsWith(".class") && !entry.isDirectory()) {
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                registerIfController(className, urlMappings);
            }
        }
    }

    private void scanDirectory(String packageName, File folder, Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings)
            throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(packageName + "." + file.getName(), file, urlMappings);
                continue;
            }

            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                registerIfController(className, urlMappings);
            }
        }
    }

    private void registerIfController(String className, Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings)
            throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Class<?> clazz = Class.forName(className);

        if (clazz.isAnnotationPresent(Controller.class)) {
            Controller ctrlAnn = clazz.getAnnotation(Controller.class);
            String urlBase = getControllerBaseUrl(ctrlAnn);
            Object controllerInstance = clazz.getDeclaredConstructor().newInstance();
            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping rm = method.getAnnotation(RequestMapping.class);
                    String rawUrl = !rm.value().isBlank() ? rm.value() : rm.url();
                    Mapping mapping = new Mapping(combineUrl(urlBase, rawUrl), rm.method());
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }

                if (method.isAnnotationPresent(GetMapping.class)) {
                    GetMapping gm = method.getAnnotation(GetMapping.class);
                    Mapping mapping = new Mapping(combineUrl(urlBase, gm.value()), "GET");
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }

                if (method.isAnnotationPresent(PostMapping.class)) {
                    PostMapping pm = method.getAnnotation(PostMapping.class);
                    Mapping mapping = new Mapping(combineUrl(urlBase, pm.value()), "POST");
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }

                // Sprint 7 : @Url(url = "/bind-simple") + @Get/@Post marqueurs
                // Ex : @Url(url = "/bind-simple") -> GET
                //      @Post @Url(url = "/bind-objet") -> POST
                if (method.isAnnotationPresent(Url.class)) {
                    Url u = method.getAnnotation(Url.class);
                    String rawUrl = !u.url().isBlank() ? u.url() : u.value();
                    String httpMethod = u.method();
                    if (method.isAnnotationPresent(src.annotation.Post.class)) {
                        httpMethod = "POST";
                    } else if (method.isAnnotationPresent(src.annotation.Get.class)) {
                        httpMethod = "GET";
                    }
                    Mapping mapping = new Mapping(combineUrl(urlBase, rawUrl), httpMethod);
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }
            }
        }
    }

    private static String getControllerBaseUrl(Controller ann) {
        if (ann == null) return "/";
        String val = ann.value();
        if (val != null && !val.isBlank()) return val.trim();
        String base = ann.urlBase();
        return (base != null && !base.isBlank()) ? base.trim() : "/";
    }

    private static String combineUrl(String baseUrl, String methodUrl) {
        String m = (methodUrl == null || methodUrl.isBlank()) ? "/" : methodUrl.trim();
        if (!m.startsWith("/")) {
            m = "/" + m;
        }
        if (baseUrl == null || baseUrl.isBlank() || baseUrl.equals("/")) {
            return m;
        }
        String b = baseUrl.trim();
        if (!b.startsWith("/")) {
            b = "/" + b;
        }
        if (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (m.equals("/")) {
            return b;
        }
        return b + m;
    }

    private void checkDuplicate(Mapping mapping, Class<?> clazz, Method method,
                                Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings) {
        List<FrontControllerServlet.MethodInfo> existing = urlMappings.get(mapping);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalStateException(
                "Duplicate mapping detected: [" + mapping.getHttpMethod() + "] " + mapping.getUrl()
                + " already mapped in " + existing.get(0).controllerClass.getSimpleName()
                + "." + existing.get(0).method.getName()
                + " and cannot be mapped again in " + clazz.getSimpleName() + "." + method.getName()
            );
        }
    }


}
