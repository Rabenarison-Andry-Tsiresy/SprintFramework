package src;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import src.annotation.UrlApi;

public class FrontControllerServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    private Map<Mapping, List<MethodInfo>> getUrlMappings() {
        ServletContext context = getServletContext();
        if (context == null) {
            throw new IllegalStateException("ServletContext non disponible. Le listener n'a pas été initialisé.");
        }
        return (Map<Mapping, List<MethodInfo>>) context.getAttribute("urlMappings");
    }

    static class MethodInfo {
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

    // Afficher les routes disponibles à la racine
    if (url.equals("") || url.equals("/")) {

        res.setContentType("text/plain");
        java.io.PrintWriter out = res.getWriter();

        out.println("===== LISTE DES ROUTES DISPONIBLES =====");
        out.println();

        for (Map.Entry<Mapping, List<MethodInfo>> entry : getUrlMappings().entrySet()) {

            Mapping mapping = entry.getKey();
            List<MethodInfo> infos = entry.getValue();

            for (MethodInfo info : infos) {
                boolean isApi = info.method.isAnnotationPresent(UrlApi.class);
                boolean isObject = info.method.isAnnotationPresent(src.annotation.Object.class);
                String type = isApi ? "API (JSON)" : (isObject ? "OBJET (JSON)" : "VUE");
                out.println("URL         : " + mapping.getUrl());
                out.println("HTTP Method : " + mapping.getHttpMethod());
                out.println("Type        : " + type);
                out.println("Classe      : " + info.controllerClass.getSimpleName());
                out.println("Méthode     : " + info.method.getName());
                out.println("----------------------------------------");
            }
        }

        return;
    }

    String httpMethod = req.getMethod();

    Mapping mapping = new Mapping(url, httpMethod);

    List<MethodInfo> methodInfos = getUrlMappings().get(mapping);

    if (methodInfos != null && !methodInfos.isEmpty()) {

        try {

            for (MethodInfo methodInfo : methodInfos) {
                Object[] args;
                try {
                    args = ParamBinder.resolveMethodArgs(methodInfo.method, req, res);
                } catch (Exception e) {
                    res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    res.setContentType("text/plain;charset=UTF-8");
                    res.getWriter().println("===== ERREUR 400 - BINDING =====");
                    res.getWriter().println("Impossible de remplir l'objet depuis la requête : " + e.getMessage());
                    return;
                }
                Object result = methodInfo.method.invoke(methodInfo.controllerInstance, args);

                boolean isApi = methodInfo.method.isAnnotationPresent(UrlApi.class);
                boolean isObject = methodInfo.method.isAnnotationPresent(src.annotation.Object.class);
                if (isApi || isObject) {
                    Object payload = result;

                    if (payload instanceof ModelAndView mav) {
                        payload = mav.getData();
                    }
                    res.setContentType("application/json;charset=UTF-8");
                    res.setCharacterEncoding("UTF-8");
                    res.getWriter().print(JsonUtil.toJson(payload));
                    return;
                }

                if (result instanceof ModelAndView modelAndView) {
                    ServletContext context = getServletContext();
                    String viewPrefix = (String) context.getAttribute("viewPrefix");
                    String viewSuffix = (String) context.getAttribute("viewSuffix");

                    String viewPath = viewPrefix + modelAndView.getViewName() + viewSuffix;

                    // Placer les données du modèle dans les attributs de la requête
                    for (Map.Entry<String, Object> entry : modelAndView.getData().entrySet()) {
                        req.setAttribute(entry.getKey(), entry.getValue());
                    }

                    RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
                    dispatcher.forward(req, res);
                    return;
                }

                // Sinon, affichage texte classique
                res.setContentType("text/plain");
                java.io.PrintWriter out = res.getWriter();

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
        res.setContentType("text/plain");
        java.io.PrintWriter out = res.getWriter();

        out.println("===== ERREUR 404 =====");
        out.println("Aucune méthode trouvée pour :");
        out.println("URL         : " + url);
        out.println("HTTP Method : " + httpMethod);
        out.println();

        out.println("Routes disponibles :");

        for (Map.Entry<Mapping, List<MethodInfo>> entry : getUrlMappings().entrySet()) {

            Mapping m = entry.getKey();
            List<MethodInfo> infos = entry.getValue();

            for (MethodInfo info : infos) {
                boolean isApi = info.method.isAnnotationPresent(UrlApi.class);
                boolean isObject = info.method.isAnnotationPresent(src.annotation.Object.class);
                out.println(
                        "[" + m.getHttpMethod() + "] "
                        + m.getUrl()
                        + (isApi ? " [API]" : "")
                        + (isObject ? " [OBJECT]" : "")
                        + " -> "
                        + info.controllerClass.getSimpleName()
                        + "."
                        + info.method.getName()
                );
            }
        }
    }
}
//misy miova
    
}