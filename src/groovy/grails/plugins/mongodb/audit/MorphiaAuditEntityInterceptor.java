package grails.plugins.mongodb.audit;

import com.google.code.morphia.AbstractEntityInterceptor;
import com.google.code.morphia.mapping.MappedClass;
import com.google.code.morphia.mapping.Mapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBObject;
import grails.plugins.mongodb.MongoDomainClassArtefactHandler;
import grails.plugins.mongodb.MongoHolderBean;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.codehaus.groovy.grails.commons.GrailsClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * After each save, also save the entry to a history collection
 */
public class MorphiaAuditEntityInterceptor extends AbstractEntityInterceptor implements InitializingBean {

    @Autowired private MongoHolderBean mongo;
    @Autowired private GrailsApplication grailsApplication;

    private DB db;
    private MongodbAuditProcessor auditProcessor = null;
    private Map<Class, String> watchedClasses = new HashMap<Class, String>();

    @Override
    public void postPersist(Object ent, DBObject dbObj, Mapper mapr) {
        if (ent == null || dbObj == null) return; // nothing to do for incomplete requests
        String historyCollectionName = watchedClasses.get(ent.getClass());
        if (historyCollectionName != null) {
            // persist this entity to the history collection
            BasicDBObject historyEntity = new BasicDBObject("date", new Date()).append("entity", dbObj);
            boolean abort = false;
            if (auditProcessor != null) abort = !auditProcessor.processHistoryEntry(historyEntity, ent, dbObj, mapr);
            if (!abort) db.getCollection(historyCollectionName).save(historyEntity);
        }
    }

    /**
     * Add class to watched classes for auditing
     * Not thread safe. Call this method once during app initialization
     * @param auditClass
     */
    public void auditClass(Class auditClass) {
        MappedClass mappedClass = mongo.getMorphia().getMapper().getMappedClass(auditClass);
        if (mappedClass != null) {
            watchedClasses.put(auditClass, mappedClass.getCollectionName() + "History");
        }
    }


    @Override
    public void afterPropertiesSet() throws Exception {
        db = mongo.getDb();

        /**
         * add auditing features to given entities. after save of given entities, put the entity into according history collection too
         *
         * entities that should have history entires should be annotated with {@link Audited}
         */
        for (GrailsClass domainClass : grailsApplication.getArtefacts(MongoDomainClassArtefactHandler.TYPE)) {
            if (domainClass instanceof GrailsDomainClass && domainClass.getClazz().getAnnotation(Audited.class) != null) auditClass(domainClass.getClazz());
        }

        // init audit processor
        try {
            Object mongodbAuditProcessor = grailsApplication.getMainContext().getBean("mongodbAuditProcessor");
            if (mongodbAuditProcessor instanceof MongodbAuditProcessor) this.auditProcessor = (MongodbAuditProcessor)mongodbAuditProcessor;
        } catch (BeansException ignore) {
            // do not care if not defined
        }
    }
}
