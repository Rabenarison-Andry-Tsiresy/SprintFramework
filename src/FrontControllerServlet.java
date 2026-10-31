package src;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import src.annotation.Authenticated;
import src.annotation.RolesAllowed;
import src.annotation.UrlApi;

// Sprint 8 : active le parsing multipart de Tomcat (request.getPart(...)).
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024,
    maxFileSize = 1024 * 1024 * 20,
    maxRequestSize = 1024 * 1024 * 25
)
public class FrontControllerServlet extends HttpServlet {

    @SuppressWarnings("unchecked")
    private Map<Mapping, List<MethodInfo>> getUrlMappings() {
        ServletContext context = getServletContext();
        if (context == null) {
            return null;
        }
        return (Map<Mapping, List<MethodInfo>>) context.getAttribute("urlMappings");
    }

    static class MethodInfo {
        Class<?> controllerClass;
        Method method;
        Object controllerInstance; // sprint 11 : instance singleton

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

        req.setCharacterEncoding("UTF-8");

        String requestUri = req.getRequestURI();
        String contextPath = req.getContextPath();
        String url = requestUri.substring(contextPath.length());

        // Nettoyer les paramètres de session / matrice (;jsessionid=...)
        if (url.contains(";")) {
            url = url.split(";")[0];
        }
        if (url.isEmpty()) {
            url = "/";
        }

        // ===============================
        // Laisser Tomcat servir les ressources statiques
        // ===============================
        if (url.matches(".*\\.(html|htm|css|js|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|map)$")) {
            req.getServletContext()
                    .getNamedDispatcher("default")
                    .forward(req, res);
            return;
        }

        Map<Mapping, List<MethodInfo>> urlMappings = getUrlMappings();
        if (urlMappings == null) {
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("Erreur 500 : urlMappings non initialisé dans le ServletContext. " +
                    "Vérifiez que FrontControllerListener est bien actif.");
            return;
        }

        String httpMethod = req.getMethod();
        Mapping mapping = new Mapping(url, httpMethod);
        List<MethodInfo> methodInfos = urlMappings.get(mapping);

        // Afficher les routes disponibles à la racine uniquement si aucune méthode n'est mappée sur "/"
        if ((methodInfos == null || methodInfos.isEmpty()) && (url.equals("") || url.equals("/"))) {

            res.setContentType("text/plain;charset=UTF-8");
            java.io.PrintWriter out = res.getWriter();

            out.println("===== LISTE DES ROUTES DISPONIBLES =====");
            out.println();

            for (Map.Entry<Mapping, List<MethodInfo>> entry : urlMappings.entrySet()) {

                Mapping m = entry.getKey();
                List<MethodInfo> infos = entry.getValue();

                for (MethodInfo info : infos) {
                    boolean isApi = info.method.isAnnotationPresent(UrlApi.class);
                    boolean isObject = info.method.isAnnotationPresent(src.annotation.Object.class);
                    String type = isApi ? "API (JSON)" : (isObject ? "OBJET (JSON)" : "VUE");
                    out.println("URL         : " + m.getUrl());
                    out.println("HTTP Method : " + m.getHttpMethod());
                    out.println("Type        : " + type);
                    out.println("Classe      : " + info.controllerClass.getSimpleName());
                    out.println("Méthode     : " + info.method.getName());
                    out.println("----------------------------------------");
                }
            }

            return;
        }

        if (methodInfos != null && !methodInfos.isEmpty()) {

            try {

                for (MethodInfo methodInfo : methodInfos) {

                    // ===============================
                    // Contrôle d'accès (Authenticated / RolesAllowed)
                    // ===============================
                    int accessStatus = accessStatus(methodInfo, req);
                    if (accessStatus != HttpServletResponse.SC_OK) {
                        res.setStatus(accessStatus);
                        res.setContentType("text/plain;charset=UTF-8");
                        res.getWriter().print(accessStatus == HttpServletResponse.SC_UNAUTHORIZED
                                ? "Authentification requise."
                                : "Acces refuse : role insuffisant.");
                        return;
                    }

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
                        if (viewPrefix == null) {
                            viewPrefix = "/WEB-INF/jsp/";
                        }
                        String viewSuffix = (String) context.getAttribute("viewSuffix");
                        if (viewSuffix == null) {
                            viewSuffix = ".jsp";
                        }

                        String viewPath = viewPrefix + modelAndView.getViewName() + viewSuffix;

                        // Placer les données du modèle dans les attributs de la requête
                        if (modelAndView.getData() != null) {
                            for (Map.Entry<String, Object> entry : modelAndView.getData().entrySet()) {
                                req.setAttribute(entry.getKey(), entry.getValue());
                            }
                        }

                        RequestDispatcher dispatcher = req.getRequestDispatcher(viewPath);
                        if (dispatcher == null) {
                            throw new ServletException("Vue introuvable : " + viewPath);
                        }
                        dispatcher.forward(req, res);
                        return;
                    }

                    // Sinon, affichage texte classique
                    res.setContentType("text/plain;charset=UTF-8");
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
                    return;
                }

            } catch (InvocationTargetException ite) {
                Throwable target = ite.getTargetException() != null ? ite.getTargetException() : ite;
                throw new ServletException("Erreur lors de l'appel de la méthode : " + target.getMessage(), target);
            } catch (IllegalAccessException e) {
                throw new ServletException("Erreur d'accès lors de l'appel de la méthode.", e);
            }

        } else {

            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("text/plain;charset=UTF-8");
            java.io.PrintWriter out = res.getWriter();

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

    /**
     * Uses the servlet container's authenticated principal and role mapping.
     * - @Authenticated (méthode ou classe) -> 401 si pas d'utilisateur authentifié
     * - @RolesAllowed  (méthode ou classe) -> 401 si pas authentifié, 403 si aucun rôle ne matche
     *   (l'annotation de méthode l'emporte sur celle de classe)
     */
    private int accessStatus(MethodInfo info, HttpServletRequest req) {
        RolesAllowed methodRoles = info.method.getAnnotation(RolesAllowed.class);
        RolesAllowed classRoles = info.controllerClass.getAnnotation(RolesAllowed.class);
        RolesAllowed roles = methodRoles != null ? methodRoles : classRoles;

        boolean requiresAuthentication = info.method.isAnnotationPresent(Authenticated.class)
                || info.controllerClass.isAnnotationPresent(Authenticated.class)
                || roles != null;

        if (requiresAuthentication && req.getUserPrincipal() == null) {
            return HttpServletResponse.SC_UNAUTHORIZED;
        }
        if (roles != null) {
            for (String role : roles.value()) {
                if (role != null && !role.isBlank() && req.isUserInRole(role)) {
                    return HttpServletResponse.SC_OK;
                }
            }
            return HttpServletResponse.SC_FORBIDDEN;
        }
        return HttpServletResponse.SC_OK;
    }
}