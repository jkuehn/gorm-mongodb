package grails.plugins.mongodb;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.DatastoreImpl;
import com.google.code.morphia.Morphia;
import com.mongodb.*;
import groovy.util.ConfigObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.springframework.context.ApplicationContext;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
    Mongo mongo;

    Map flatConfig = application.getConfig().flatten();

    String database = getConfigVar(flatConfig, "mongodb.databaseName", null);
    if (database == null) database = getConfigVar(flatConfig, "mongodb.database", "test");

    List<String> replicaSets = null;
    try {
      replicaSets = (List<String>)((ConfigObject)application.getConfig().get("mongodb")).get("replicaSet");
    } catch (Exception ignore) {}

    MongoOptions mongoOptions = null;
    try {
      mongoOptions = (MongoOptions)((ConfigObject)application.getConfig().get("mongodb")).get("options");
    } catch (Exception ignore) {
        mongoOptions = new MongoOptions();
    }

    if (replicaSets != null) { // user replica sets
      log.info("Creating MongoDB connection with replica sets " + replicaSets + " and database " + database);
      List<ServerAddress> addressList = new ArrayList<ServerAddress>();
      for (String addr : replicaSets) {
        addressList.add(new ServerAddress(addr));
      }
      mongo = new Mongo(addressList, mongoOptions);
    } else { // use host port
      String host = getConfigVar(flatConfig, "mongodb.host", "localhost");
      int port = parsePortFromConfig(getConfigVar(flatConfig, "mongodb.port", "27017"), 27017);
      log.info("Creating MongoDB connection to host " + host + ":" + port + " and database " + database);

      mongo = new Mongo(new ServerAddress(host, port), mongoOptions);
    }

    morphia = new Morphia();
    String username = getConfigVar(flatConfig,"mongodb.username",null);
    char[] password = getCharArray(getConfigVar(flatConfig, "mongodb.password", null));
    datastore = (DatastoreImpl)morphia.createDatastore(mongo, database,username,password);

    // init ObjectFactory
    morphia.getMapper().getOptions().objectFactory = new MongoDomainObjectFactory(application);
  }

  private char[] getCharArray(String configVar) {
      return configVar == null ? null : configVar.toCharArray();
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
