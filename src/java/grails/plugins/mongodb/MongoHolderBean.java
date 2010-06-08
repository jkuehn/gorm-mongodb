package grails.plugins.mongodb;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.DatastoreImpl;
import com.google.code.morphia.Morphia;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.GrailsApplication;

import java.net.UnknownHostException;
import java.util.Properties;

/**
 * should be registered with spring. provides access to
 * all relevant instances for working with mongodb
 *
 * @author: Juri Kuehn
 */
public class MongoHolderBean {
  private static final Log log = LogFactory.getLog(MongoDomainClass.class);

  private Morphia morphia;
  private DatastoreImpl datastore;

  /**
   * create database connection and morphia and datastore instances
   * @param application
   * @throws UnknownHostException
   */
  public MongoHolderBean(GrailsApplication application) throws UnknownHostException {
    Properties flatConfig = application.getConfig().toProperties();
    String database = flatConfig.get("mongodb.database").toString();
    String host = flatConfig.get("mongodb.host").toString();
    int port = Integer.parseInt(flatConfig.get("mongodb.port").toString());

    log.info("Creating MongoDB connection to host " + host + ":" + port + " and database " + database);

    morphia = new Morphia();
    datastore = (DatastoreImpl)morphia.createDatastore(new Mongo(host, port), database);
  }

  public Morphia getMorphia() {
    return morphia;
  }

  public Datastore getDatastore() {
    return datastore;
  }

  public Mongo getMongo() {
    return datastore.getMongo();
  }

  public DB getDb() {
    return datastore.getDB();
  }

  /**
   * returns the collection that stores instances of the given class
   * @param clazz
   * @return
   */
  public DBCollection getCollection(Class clazz) {
    return datastore.getCollection(clazz);
  }

  public DBCollection getCollection(Object obj) {
    return datastore.getCollection(obj);
  }
}
