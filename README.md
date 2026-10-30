# Sprint Framework (Mini Spring MVC)

Framework MVC léger développé en Java (Jakarta EE 10 / Tomcat 10+) inspiré de Spring MVC.

---

## 📁 Structure du Projet

```text
SprintFramework/
├── .gitignore              # Exclusion des classes compilées, jar et fichiers temporaires
├── deploy.sh               # Script de compilation Linux/macOS (avec support déploiement auto)
├── deploy.bat              # Script de compilation Windows
├── lib/
│   └── jakarta.servlet-api-6.0.0.jar
└── src/
    ├── BindingException.java        # Exception de binding & validation
    ├── FrontControllerListener.java # Scanner de packages & enregistrement des routes
    ├── FrontControllerServlet.java  # Dispatcher principal & exécution des méthodes
    ├── JsonUtil.java                # Sérialiseur JSON par réflexion (sans dépendance externe)
    ├── Mapping.java                 # Clé de mapping (URL normalisée + méthode HTTP)
    ├── ModelAndView.java            # Modèle & nom de vue pour le forward JSP
    ├── ParamBinder.java             # Injection des arguments (Servlet, Session, Param, Part, Objet)
    ├── Sessions.java                # Gestionnaire de session synchronisé avec HttpSession/JSP
    ├── UploadController.java        # Contrôleur d'upload de fichiers via Part
    └── annotation/
        ├── Controller.java          # @Controller(urlBase = "...")
        ├── DateFormat.java          # @DateFormat(pattern = "...")
        ├── Get.java                 # @Get (marqueur HTTP)
        ├── GetMapping.java          # @GetMapping("/...")
        ├── Numeric.java             # @Numeric validation
        ├── Object.java              # @Object réponse JSON
        ├── Param.java               # @Param("nom") injection de paramètre HTTP
        ├── ParamObject.java         # @ParamObject(name = "prefix") binding d'objet
        ├── Post.java                # @Post (marqueur HTTP)
        ├── PostMapping.java         # @PostMapping("/...")
        ├── Range.java               # @Range(min = ..., max = ...) validation
        ├── RequestMapping.java      # @RequestMapping("/...")
        ├── Required.java            # @Required validation
        ├── SessionAttribute.java    # @SessionAttribute("nom") injection depuis session
        ├── Url.java                 # @Url("/...") mapping d'URL
        └── UrlApi.java              # @UrlApi réponse JSON
```

---

## 🚀 Compilation et Déploiement

### Sur Linux / macOS :
```bash
# 1. Compiler et générer Framework.jar
./deploy.sh

# 2. (Optionnel) Compiler et copier directement dans votre projet webapp
./deploy.sh /chemin/vers/votre-webapp/WEB-INF/lib
```

### Sur Windows :
```cmd
rem Compiler et générer Framework.jar
deploy.bat

rem Ou avec déploiement automatique dans votre projet
deploy.bat C:\chemin\vers\votre-webapp\WEB-INF\lib
```

> **Note :** Le flag `-parameters` de `javac` est activé par défaut dans les scripts afin de conserver les noms de paramètres dans le bytecode.

---

## ⚙️ Configuration `web.xml`

Pour utiliser le framework dans n'importe quelle application Jakarta EE (Tomcat 10+) :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee
                             https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
         version="6.0">

    <!-- Paramètres globaux -->
    <context-param>
        <param-name>base-package</param-name>
        <param-value>tsiresy.main</param-value>
    </context-param>
    <context-param>
        <param-name>view-prefix</param-name>
        <param-value>/WEB-INF/jsp/</param-value>
    </context-param>
    <context-param>
        <param-name>view-suffix</param-name>
        <param-value>.jsp</param-value>
    </context-param>

    <!-- Listener de découverte des contrôleurs -->
    <listener>
        <listener-class>src.FrontControllerListener</listener-class>
    </listener>

    <!-- Servlet Front Controller -->
    <servlet>
        <servlet-name>FrontControllerServlet</servlet-name>
        <servlet-class>src.FrontControllerServlet</servlet-class>
        <!-- Support multipart pour l'upload de fichiers -->
        <multipart-config>
            <max-file-size>20971520</max-file-size>
            <max-request-size>26214400</max-request-size>
            <file-size-threshold>1048576</file-size-threshold>
        </multipart-config>
    </servlet>

    <servlet-mapping>
        <servlet-name>FrontControllerServlet</servlet-name>
        <url-pattern>/</url-pattern>
    </servlet-mapping>
</web-app>
```

---

## 📚 Guide des Fonctionnalités

### 1. Contrôleurs & Routage
```java
@Controller(urlBase = "/client")
public class ClientController {

    @GetMapping("/liste")
    public ModelAndView liste() {
        ModelAndView mv = new ModelAndView("clients/liste");
        mv.addObject("titre", "Liste des clients");
        return mv;
    }

    @PostMapping("/enregistrer")
    public String save(@Param(name = "nom") String nom) {
        return "Client " + nom + " enregistré";
    }

    @Url(url = "/detail", method = "GET")
    public String detail(@Param(name = "id") int id) {
        return "Détail client #" + id;
    }
}
```

### 2. Retour JSON (`@UrlApi` ou `@Object`)
```java
@Controller
public class ApiController {

    @GetMapping("/api/users")
    @UrlApi
    public List<User> getUsers() {
        return List.of(new User("Alice", 25), new User("Bob", 30));
    }
}
```

### 3. Upload de Fichier avec `Part` (UploadController)
```java
@Controller
public class UploadController {

    @PostMapping("/upload")
    public String upload(@Param(name = "file") Part file) throws IOException {
        String safeName = Paths.get(file.getSubmittedFileName()).getFileName().toString();
        Path dest = Paths.get("uploads").resolve(safeName);
        Files.createDirectories(dest.getParent());
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return "Fichier uploadé : " + safeName;
    }
}
```

### 4. Sessions (`Sessions` & `@SessionAttribute`)
```java
@Controller
public class AuthController {

    @PostMapping("/login")
    public ModelAndView login(@Param(name = "pseudo") String pseudo, Sessions session) {
        session.set("user", pseudo); // Synchronisé avec HttpSession et JSP ${sessionScope.user}
        return new ModelAndView("accueil").addObject("msg", "Connecté !");
    }

    @GetMapping("/profil")
    public ModelAndView profil(@SessionAttribute(name = "user") String user) {
        return new ModelAndView("profil").addObject("user", user);
    }
}
```

### 5. Binding Objet & Validation (`@ParamObject`)
```java
public class Etudiant {
    @Required(message = "Le nom est obligatoire")
    private String nom;

    @Range(min = 18, max = 65, message = "L'âge doit être compris entre 18 et 65 ans")
    private int age;

    @DateFormat(pattern = "yyyy-MM-dd", message = "Format attendu : yyyy-MM-dd")
    private LocalDate dateNaissance;
    // Getters et Setters...
}

@Controller
public class EtudiantController {
    @PostMapping("/etudiants")
    public String creer(@ParamObject(name = "etudiant") Etudiant e) {
        return "Étudiant créé : " + e.getNom();
    }
}
```

---

## 🔍 Visualisation des Routes

L'accès à la racine `/` du serveur affiche automatiquement la liste de toutes les routes détectées :
```text
===== LISTE DES ROUTES DISPONIBLES =====

URL         : /upload
HTTP Method : POST
Type        : VUE
Classe      : UploadController
Méthode     : upload
----------------------------------------
```