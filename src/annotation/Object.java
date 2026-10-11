package src.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Sprint 6 : marque une méthode comme réponse JSON objet (comme @UrlApi)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Object {
}
