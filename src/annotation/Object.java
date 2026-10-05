package src.annotation;


import java.lang.annotation.ElementType;
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Object {
    /**
     * Marque une méthode qui retourne toujours un Object.
     * Le framework sérialise la valeur de retour en JSON
     * (comme @UrlApi). Exemple :
     * <pre>
     * &#64;GetMapping("/emp")
     * &#64;Object
     * public Employe getEmp() { return new Employe("Rakoto", 30); }
     * </pre>
     * Attention : utiliser le nom qualifié
     * {@code src.annotation.Object} dans le framework car
     * {@code Object} seul désigne {@code java.lang.Object}.
     */
}

