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
import java.util.Map;

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
   * Parses config options, creates database connection and morphia & datastore instances
   *
   * @param application
   * @throws UnknownHostException
   */
  public MongoHolderBean(GrailsApplication application) throws UnknownHostException {
    Map flatConfig = application.getConfig().flatten();

    String host = getConfigVar(flatConfig, "mongodb.host", "localhost");
    int port = parsePortFromConfig(getConfigVar(flatConfig, "mongodb.port", "27017"), 27017);
    String database = getConfigVar(flatConfig, "mongodb.database", "test");

    log.info("Creating MongoDB connection to host " + host + ":" + port + " and database " + database);

    morphia = new Morphia();
    datastore = (DatastoreImpl)morphia.createDatastore(new Mongo(host, port), database);
  }

  private String getConfigVar(Map config, String key, String defaultValue) {
    if (!config.containsKey(key)) {
      log.info("MongoDB configuration option missing: '" + key + "'. Using default value '" + defaultValue + "'.");
      return defaultValue;
    }
    return config.get(key).toString();
  }

  private int parsePortFromConfig(String configVal, int defaultValue) {
    int port;
    try { // in case port is not a valid number
      port = Integer.parseInt(configVal);
    } catch (Exception e) {
      log.info("MongoDB port invalid: '" + configVal + "' is not a number. Using default value " + defaultValue);
      return defaultValue;
    }

    return port;
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
