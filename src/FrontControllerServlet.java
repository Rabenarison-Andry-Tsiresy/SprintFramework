package src;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import src.annotation.Controller;
import src.annotation.GetMapping;
import src.annotation.PostMapping;
import src.annotation.RequestMapping;

public class FrontControllerServlet extends HttpServlet {
    private final Map<Mapping, List<MethodInfo>> urlMappings = new HashMap<>();
    private String packageName;

    private static class MethodInfo {
        Class<?> controllerClass;
        Method method;
        Object controllerInstance;

        MethodInfo(Class<?> controllerClass, Method method,
             Object controllerInstance) {
            this.controllerClass = controllerClass;
            this.method = method;
            this.controllerInstance = controllerInstance;
        }
    }

    @Override
    public void init() throws ServletException {
        ServletConfig config = getServletConfig();
        String configuredPackage = config != null ? config.getInitParameter("base-package") : null;
        packageName = configuredPackage != null && !configuredPackage.isBlank()
                ? configuredPackage
                : "Tsiresy.main";

        try {
            scanPackage(packageName);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private void scanPackage(String packageName) throws IOException, ClassNotFoundException, URISyntaxException,
            InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        String path = packageName.replace('.', '/');
        URL url = Thread.currentThread().getContextClassLoader().getResource(path);

        if (url == null) {
            throw new IOException("Package introuvable : " + packageName);
        }

        if ("jar".equals(url.getProtocol())) {
            scanJar(path, url);
        } else if ("file".equals(url.getProtocol())) {
            scanDirectory(packageName, new File(url.toURI()));
        }
    }

    private void scanJar(String path, URL url) throws IOException, ClassNotFoundException, InstantiationException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        JarURLConnection connection = (JarURLConnection) url.openConnection();
        JarFile jarFile = connection.getJarFile();

        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();

            if (name.startsWith(path) && name.endsWith(".class") && !entry.isDirectory()) {
                String className = name.replace('/', '.').substring(0, name.length() - 6);
                registerIfController(className);
            }
        }
    }

    private void scanDirectory(String packageName, File folder) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(packageName + "." + file.getName(), file);
                continue;
            }

            if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                registerIfController(className);
            }
        }
    }

    private void registerIfController(String className) throws ClassNotFoundException, InstantiationException,
            IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Class<?> clazz = Class.forName(className);

        if (clazz.isAnnotationPresent(Controller.class)) {
            Object controllerInstance = clazz.getDeclaredConstructor().newInstance();
            Method[] methods = clazz.getDeclaredMethods();

            for (Method method : methods) {
                if (method.isAnnotationPresent(RequestMapping.class)) {
                    RequestMapping rm = method.getAnnotation(RequestMapping.class);
                    Mapping mapping = new Mapping(rm.url(), rm.method());
                    checkDuplicate(mapping, clazz, method);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new MethodInfo(clazz, method, controllerInstance));
                }

                if (method.isAnnotationPresent(GetMapping.class)) {

                    GetMapping gm = method.getAnnotation(GetMapping.class);

                    Mapping mapping = new Mapping(gm.value(), "GET");

                    checkDuplicate(mapping, clazz, method);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new MethodInfo(clazz, method, controllerInstance));
                }

                if (method.isAnnotationPresent(PostMapping.class)) {

                    PostMapping pm = method.getAnnotation(PostMapping.class);

                    Mapping mapping = new Mapping(pm.value(), "POST");

                    checkDuplicate(mapping, clazz, method);
                    urlMappings.computeIfAbsent(mapping, k -> new ArrayList<>())
                            .add(new MethodInfo(clazz, method, controllerInstance));
                }
            }
        }
    }

    private void checkDuplicate(Mapping mapping, Class<?> clazz, Method method) {
        List<MethodInfo> existing = urlMappings.get(mapping);
        if (existing != null && !existing.isEmpty()) {
            throw new IllegalStateException(
                "Duplicate mapping detected: [" + mapping.getHttpMethod() + "] " + mapping.getUrl()
                + " already mapped in " + existing.get(0).controllerClass.getSimpleName()
                + "." + existing.get(0).method.getName()
                + " and cannot be mapped again in " + clazz.getSimpleName() + "." + method.getName()
            );
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }
public void processRequest(HttpServletRequest req, HttpServletResponse res)
        throws ServletException, IOException {

    String requestUri = req.getRequestURI();
    String contextPath = req.getContextPath();
    String url = requestUri.substring(contextPath.length());

    // ===============================
    // Laisser Tomcat servir les ressources statiques
    // ===============================
    if (url.matches(".*\\.(html|htm|css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf)$")) {
        req.getServletContext()
                .getNamedDispatcher("default")
                .forward(req, res);
        return;
    }

    res.setContentType("text/plain");
    PrintWriter out = res.getWriter();

    // Afficher les routes disponibles à la racine
    if (url.equals("") || url.equals("/")) {

        out.println("===== LISTE DES ROUTES DISPONIBLES =====");
        out.println();

        for (Map.Entry<Mapping, List<MethodInfo>> entry : urlMappings.entrySet()) {

            Mapping mapping = entry.getKey();
            List<MethodInfo> infos = entry.getValue();

            for (MethodInfo info : infos) {
                out.println("URL         : " + mapping.getUrl());
                out.println("HTTP Method : " + mapping.getHttpMethod());
                out.println("Classe      : " + info.controllerClass.getSimpleName());
                out.println("Méthode     : " + info.method.getName());
                out.println("----------------------------------------");
            }
        }

        return;
    }

    String httpMethod = req.getMethod();

    Mapping mapping = new Mapping(url, httpMethod);

    List<MethodInfo> methodInfos = urlMappings.get(mapping);

    if (methodInfos != null && !methodInfos.isEmpty()) {

        try {

            for (MethodInfo methodInfo : methodInfos) {
                Object result = methodInfo.method.invoke(methodInfo.controllerInstance);

                out.println("===== ROUTE TROUVÉE =====");
                out.println("URL         : " + url);
                out.println("HTTP Method : " + httpMethod);
                out.println("Classe      : " + methodInfo.controllerClass.getSimpleName());
                out.println("Méthode     : " + methodInfo.method.getName());

                if (result != null) {
                    out.println("Retour : " + result);
                }
                out.println("----------------------------------------");
            }

        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new ServletException("Erreur lors de l'appel de la méthode.", e);
        }

    } else {

        res.setStatus(HttpServletResponse.SC_NOT_FOUND);

        out.println("===== ERREUR 404 =====");
        out.println("Aucune méthode trouvée pour :");
        out.println("URL         : " + url);
        out.println("HTTP Method : " + httpMethod);
        out.println();

        out.println("Routes disponibles :");

        for (Map.Entry<Mapping, List<MethodInfo>> entry : urlMappings.entrySet()) {

            Mapping m = entry.getKey();
            List<MethodInfo> infos = entry.getValue();

            for (MethodInfo info : infos) {
                out.println(
                        "[" + m.getHttpMethod() + "] "
                        + m.getUrl()
                        + " -> "
                        + info.controllerClass.getSimpleName()
                        + "."
                        + info.method.getName()
                );
            }
        }
    }
}
    
}