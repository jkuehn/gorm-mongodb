package grails.plugins.mongodb;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Reference;
import com.google.code.morphia.annotations.Transient;
import com.google.code.morphia.mapping.MappedClass;
import grails.util.GrailsNameUtils;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.validation.ConstrainedProperty;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Author: Juri Kuehn
 * Date: 02.06.2010
 */
public class MongoDomainClassProperty implements GrailsDomainClassProperty {
  private Class ownerClass;
  private Field field;
  private String name;
  private Class type;
  private MongoDomainClass domainClass;
  private Method getter;
  private boolean persistent = true;
  private boolean identity = false;

  public MongoDomainClassProperty(MongoDomainClass domain, Field field, PropertyDescriptor descriptor) {
    this.ownerClass = domain.getClazz();
    this.domainClass = domain;
    this.field = field;
    this.name = descriptor.getName();
    this.type = descriptor.getPropertyType();
    this.getter = descriptor.getReadMethod();
    this.persistent = checkPersistence(descriptor);

    checkIfTransient();
  }

  private boolean checkPersistence(PropertyDescriptor descriptor) {
    // check if type is supported
    // !MappedClass.isSupportedType(field.getType()) &&
    if (field.getAnnotation(Embedded.class) == null
        && field.getAnnotation(Reference.class) == null
        ) {
      return false;
    }
    // check if groovy/java property
    if (descriptor.getName().equals("class")
        || descriptor.getName().equals("metaClass")) {
      return false;
    }

    return true;
  }

  // Checks whether this property is transient... copied from DefaultGrailsDomainClassProperty.
  private void checkIfTransient() {
    if (isAnnotatedWith(Transient.class)) {
      this.persistent = false;

    } else {
      List transientProps = getTransients(domainClass);
      if (transientProps != null) {
        for (Object currentObj : transientProps) {
          // make sure its a string otherwise ignore. Note: Again maybe a warning?
          if (currentObj instanceof String) {
            String propertyName = (String) currentObj;

            // if the property name is on the not persistant list
            // then set persistant to false
            if (propertyName.equals(this.name)) {
              this.persistent = false;
              if (!isAnnotatedWith(Transient.class))
                domainClass.log(ownerClass.getName() + "." + this.name + " should be annotated with morphias Transient!");
              break;
            }
          }
        }
      }
    }
  }

  // Retrieves the transient properties... copied from DefaultGrailsDomainClassProperty.
  private List getTransients(GrailsDomainClass domainClass) {
    List transientProps;
    transientProps = domainClass.getPropertyValue(TRANSIENT, List.class);

    // Undocumented feature alert! Steve insisted on this :-)
    List evanescent = domainClass.getPropertyValue(EVANESCENT, List.class);
    if (evanescent != null) {
      if (transientProps == null) {
        transientProps = new ArrayList();
      }

      transientProps.addAll(evanescent);
    }
    return transientProps;
  }

  public int getFetchMode() {
    return FETCH_EAGER;
  }

  public String getName() {
    return this.name;
  }

  public Class getType() {
    return this.type;
  }

  public Class getReferencedPropertyType() {
    if (Collection.class.isAssignableFrom(getType())) {
      final Type genericType = field.getGenericType();
      if (genericType instanceof ParameterizedType) {
        final Type[] arguments = ((ParameterizedType) genericType).getActualTypeArguments();
        if (arguments.length > 0) {
          return (Class) arguments[0];
        }
      }
    }
    return getType();
  }

  public GrailsDomainClassProperty getOtherSide() {
    return null;
  }

  public String getTypePropertyName() {
    return GrailsNameUtils.getPropertyName(getType());
  }

  public GrailsDomainClass getDomainClass() {
    return domainClass;
  }

  public boolean isPersistent() {
    return persistent;
  }

  public boolean isOptional() {
    ConstrainedProperty constrainedProperty = (ConstrainedProperty) domainClass.getConstrainedProperties().get(name);
    return (constrainedProperty != null) && constrainedProperty.isNullable();
  }

  public boolean isIdentity() {
    return identity;
  }

  public void setIdentity(boolean identity) {
    this.identity = identity;
  }

  public boolean isOneToMany() {
    return false;
  }

  public boolean isManyToOne() {
    return false;
  }

  public boolean isManyToMany() {
    return false;
  }

  public boolean isBidirectional() {
    return false;
  }

  public String getFieldName() {
    return getName().toUpperCase();
  }

  public boolean isOneToOne() {
    return false;
  }

  public GrailsDomainClass getReferencedDomainClass() {
    return null;
  }

  public boolean isAssociation() {
    return false;
  }

  public boolean isEnum() {
    return false;
  }

  public String getNaturalName() {
    return GrailsNameUtils.getNaturalName(name);
  }

  public void setReferencedDomainClass(GrailsDomainClass referencedGrailsDomainClass) {

  }

  public void setOtherSide(GrailsDomainClassProperty referencedProperty) {

  }

  public boolean isInherited() {
    return false;
  }

  public boolean isOwningSide() {
    return false;
  }

  public boolean isCircular() {
    return getType().equals(ownerClass);
  }

  public String getReferencedPropertyName() {
    return null;
  }

  public boolean isEmbedded() {
    return false;
  }

  public GrailsDomainClass getComponent() {
    return null;
  }

  public void setOwningSide(boolean b) {

  }

  public boolean isBasicCollectionType() {
    return false;
  }

  public boolean isAnnotatedWith(Class annotation) {
    return (field != null && field.getAnnotation(annotation) != null) || (getter != null && getter.getAnnotation(annotation) != null);
  }

  // grails 1.2 GrailsDomainClass
  public boolean isHasOne() {
    return false;
  }

  public String toString() {
    String assType = null;
    if (isManyToMany()) {
      assType = "many-to-many";
    } else if (isOneToMany()) {
      assType = "one-to-many";
    } else if (isOneToOne()) {
      assType = "one-to-one";
    } else if (isManyToOne()) {
      assType = "many-to-one";
    } else if (isEmbedded()) {
      assType = "embedded";
    }
    return new ToStringBuilder(this).append("name", this.name).append("type", this.type).append("persistent", isPersistent()).append("optional", isOptional()).append("association", isAssociation()).append("bidirectional", isBidirectional()).append("association-type", assType).toString();
  }
}
