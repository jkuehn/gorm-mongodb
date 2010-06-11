package grails.plugins.mongodb;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author: Juri Kuehn
 */
@Target ({ElementType.TYPE})
@Retention (RetentionPolicy.RUNTIME)
@Documented
@GroovyASTTransformationClass ({"grails.plugins.mongodb.ast.MongoDomainASTTransformation"})
public @interface MongoEntity {
}