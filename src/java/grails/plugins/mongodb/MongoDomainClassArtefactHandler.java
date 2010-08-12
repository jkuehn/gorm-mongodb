package grails.plugins.mongodb;

import com.google.code.morphia.annotations.Entity;
import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.GrailsClass;

/**
 * Author: Juri Kuehn
 * Date: 30.05.2010
 */
public class MongoDomainClassArtefactHandler extends ArtefactHandlerAdapter {

    public static String TYPE = "MongoDomain"; // dont change, plugin relies on this artefact name

    public MongoDomainClassArtefactHandler() {
      super(TYPE, MongoDomainClass.class, MongoDomainClass.class, null);
    }

    public boolean isArtefactClass(Class clazz) {
        return isMongoDomainClass(clazz);
    }

    @SuppressWarnings("unchecked")
    public static boolean isMongoDomainClass(Class clazz) {
        return clazz != null && clazz.getAnnotation(Entity.class) != null;
    }

    public GrailsClass newArtefactClass(Class artefactClass) {
        return new MongoDomainClass(artefactClass);
    }
}
