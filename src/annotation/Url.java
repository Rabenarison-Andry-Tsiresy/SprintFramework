package src.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Sprint 7 : mapping URL. Compatible avec @Get/@Post marqueurs.
// Ex : @Url(url = "/bind-simple")  -> GET par défaut
//      @Post @Url(url = "/bind-objet") -> POST
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Url {
    String url() default "";
    String value() default "";
    String method() default "GET";
}
