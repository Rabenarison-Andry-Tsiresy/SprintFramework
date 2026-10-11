package src.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Sprint 7b : validation / parsing - format de date attendu
// Ex : @DateFormat(pattern = "yyyy-MM-dd")
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DateFormat {
    String pattern() default "yyyy-MM-dd";
    String message() default "Format de date invalide";
}
