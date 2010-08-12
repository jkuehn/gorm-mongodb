package grails.plugins.mongodb;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @deprecated
 * As global AST transformations are used, no annotation is needed anymore
 * All classes in grails-app/mongo are mapped to morphia automatically.
 * Classes in other directories need to be annotated with com.google.code.morphia.annotations.Entity
 */
@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@Deprecated
public @interface MongoEntity {
}