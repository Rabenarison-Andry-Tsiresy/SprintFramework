package src.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Sprint 7b : binding objet via préfixe. Champs attendus : prefix.nom, prefix.age, ...
// Ex : public String objet(@ParamObject(name = "etudiant") Etudiant etudiant)
// Formulaire : <input name="etudiant.nom" />, <input name="etudiant.age" />, ...
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParamObject {
    String name() default "";
    String value() default "";
}
