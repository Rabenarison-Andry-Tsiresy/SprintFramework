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
import java.net.URL;
import java.net.URISyntaxException;
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

@WebListener
public class FrontControllerListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        String configuredPackage = context.getInitParameter("base-package");
        String packageName = configuredPackage != null && !configuredPackage.isBlank()
                ? configuredPackage
                : "Tsiresy.main";

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
            scanPackage(packageName, urlMappings);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'initialisation du FrontController", e);
        }

        context.setAttribute("urlMappings", urlMappings);
        context.setAttribute("viewPrefix", viewPrefix);
        context.setAttribute("viewSuffix", viewSuffix);
    }

    private void scanPackage(String packageName, Map<Mapping, List<FrontControllerServlet.MethodInfo>> urlMappings)
            throws IOException, ClassNotFoundException, URISyntaxException,
            InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String path = packageName.replace('.', '/');
        URL url = Thread.currentThread().getContextClassLoader().getResource(path);

        if (url == null) {
            throw new IOException("Package introuvable : " + packageName);
        }

        if ("jar".equals(url.getProtocol())) {
            scanJar(path, url, urlMappings);
        } else if ("file".equals(url.getProtocol())) {
            scanDirectory(packageName, new File(url.toURI()), urlMappings);
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
            Object controllerInstance = clazz.getDeclaredConstructor().newInstance();
            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping rm = method.getAnnotation(RequestMapping.class);
                    Mapping mapping = new Mapping(rm.url(), rm.method());
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }

                if (method.isAnnotationPresent(GetMapping.class)) {
                    GetMapping gm = method.getAnnotation(GetMapping.class);
                    Mapping mapping = new Mapping(gm.value(), "GET");
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }

                if (method.isAnnotationPresent(PostMapping.class)) {
                    PostMapping pm = method.getAnnotation(PostMapping.class);
                    Mapping mapping = new Mapping(pm.value(), "POST");
                    checkDuplicate(mapping, clazz, method, urlMappings);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new FrontControllerServlet.MethodInfo(clazz, method, controllerInstance));
                }
            }
        }
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
