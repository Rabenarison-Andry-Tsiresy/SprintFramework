package src.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Sprint 7 : binding simple + conversion String -> int/double/boolean/Date
// - sur paramètre : public String simple(@Param(name = "nom") String nom, ...)
//   @Param("nom") et @Param(value = "nom") aussi acceptés
// - sur champ (compat ancien style Employe) : @Param("nom") private String nom;
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface Param {
    String name() default "";
    String value() default "";
}
