package grails.plugins.mongodb.audit;

import com.google.code.morphia.mapping.Mapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * Processor for history entries from auditing.
 */
public interface MongodbAuditProcessor {

    /**
     * Manipulate the historyEntry (like adding add editing user information) before it gets saved to the history collection
     * @param historyEntry
     * @param entity
     * @param serializedEntity
     * @param mapper
     * @return false if this entry should not be saved to history collection
     */
    public boolean processHistoryEntry(BasicDBObject historyEntry, Object entity, DBObject serializedEntity, Mapper mapper);

}
