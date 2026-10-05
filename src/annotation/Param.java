package src.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Associe un paramètre HTTP à un champ d'objet ou à un argument de méthode.
 *
 * Côté développeur, chaque champ du POJO porte son annotation :
 * <pre>
 * public class Employe {
 *     &#64;Param("nom")
 *     private String nom;
 *
 *     &#64;Param("age")
 *     private int age;
 * }
 * </pre>
 *
 * Et la méthode du contrôleur reçoit l'objet déjà rempli :
 * <pre>
 * &#64;PostMapping("/save")
 * public ModelAndView save(Employe emp) { ... }
 *
 * &#64;PostMapping("/save2")
 * public ModelAndView save2(&#64;Param("nom") String nom, &#64;Param("age") int age) { ... }
 * </pre>
 */
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    /**
     * Nom du paramètre HTTP. Si vide, le nom du champ / du paramètre est utilisé.
     */
    String value() default "";
}
