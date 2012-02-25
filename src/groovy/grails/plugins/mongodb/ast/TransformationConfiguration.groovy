package grails.plugins.mongodb.ast;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author: Juri Kuehn
 */
@Target([ElementType.TYPE])
@Retention(RetentionPolicy.CLASS)
public @interface TransformationConfiguration {

  /**
   * Whether an id property should be injected, if not defined manually
   */
  boolean injectId() default true;

  /**
   * Whether the class should be annotated with the morphia entity type, if not annotated manually
   */
  boolean injectEntityType() default true;

  /**
   * Whether an version property should be injected, if not defined manually
   */
  boolean injectVersion() default true;

  /**
   * Whether transient properties should be annotated with morphias Transient annotation to be excluded from persistence
   */
  boolean annotateTransients() default true;

  /**
   * Whether closures should be annotated with morphias Transient annotation to be excluded from persistence
   */
  boolean annotateClosuresAsTransients() default true;

}
