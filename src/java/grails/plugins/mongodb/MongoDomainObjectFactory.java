package grails.plugins.mongodb;

import com.google.code.morphia.mapping.DefaultCreator;
import com.google.code.morphia.mapping.MappedField;
import com.google.code.morphia.mapping.Mapper;
import com.mongodb.DBObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.GrailsApplication;
import org.springframework.context.ApplicationContext;

/**
 * @author: Juri Kuehn
 */
public class MongoDomainObjectFactory extends DefaultCreator {

  private static final Log log = LogFactory.getLog(MongoDomainClass.class);

  private GrailsApplication application;

  public MongoDomainObjectFactory(GrailsApplication application) {
    this.application = application;
  }

  @Override
  public Object createInstance(Class clazz) {
    return getBean(clazz.getName());
  }

  @Override
  public Object createInstance(Class clazz, DBObject dbObj) {
    String className = getClassName(dbObj);
		if (className != null) {
      return getBean(className);
    }

    return createInstance(clazz);
  }

  @Override
  public Object createInstance(Mapper mapr, MappedField mf, DBObject dbObj) {
    String className = getClassName(dbObj);
		if (className != null) {
      return getBean(className);
    }

    Class c = mf.isSingleValue() ? mf.getConcreteType() : mf.getSubClass();
		return createInstance(c);
  }

  private String getClassName(DBObject dbObj) {
    // see if there is a className value
    String className = (String) dbObj.get(Mapper.CLASS_NAME_FIELDNAME);
    if (className != null) {
      return className;
    }
    return null;
  }

  private Object getBean(String clazz) {
    return application.getMainContext().getBean(clazz);
  }


}
