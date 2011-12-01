package grails.plugins.mongodb;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.mapping.MappingException;
import com.google.code.morphia.utils.ReflectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.grails.commons.AbstractGrailsClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClass;
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty;
import org.codehaus.groovy.grails.commons.GrailsDomainConfigurationUtil;
import org.codehaus.groovy.grails.exceptions.GrailsDomainException;
import org.codehaus.groovy.grails.validation.ConstrainedProperty;
import org.springframework.validation.Validator;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Author: Juri Kuehn
 * Date: 30.05.2010
 */
public class MongoDomainClass extends AbstractGrailsClass implements GrailsDomainClass {
    private static final Log log = LogFactory.getLog(MongoDomainClass.class);

    Map<String, GrailsDomainClassProperty> propertyMap = new HashMap<String, GrailsDomainClassProperty>();
    private GrailsDomainClassProperty[] propertiesArray;
    private GrailsDomainClassProperty[] persistentPropertyArray;

    private Map<String, ConstrainedProperty> constraints = new HashMap<String, ConstrainedProperty>();
    private Validator validator;

    private MongoDomainClassProperty identifier;
    private MongoDomainClassProperty version = null; // no versioning

    public MongoDomainClass(Class artefactClass) {
        super(artefactClass, "");

        Entity entityAnnotation = (Entity) artefactClass.getAnnotation(Entity.class);
        if (entityAnnotation == null) {
            throw new GrailsDomainException("Class [" + artefactClass.getName() + "] is not annotated with com.google.code.morphia.annotations.Entity!");
        }

        evaluateClassProperties(artefactClass);

        // process the constraints
        try {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), this.persistentPropertyArray);
        } catch (Exception e) {
            log.error("Error reading class [" + getClazz() + "] constraints: " + e.getMessage(), e);
        }
    }

    private void evaluateClassProperties(Class artefactClass) {
        Map<String, GrailsDomainClassProperty> persistentProperties = new HashMap<String, GrailsDomainClassProperty>();

        Field[] classFields = ReflectionUtils.getDeclaredAndInheritedFields(artefactClass, true);
        for (Field field : classFields) {
            PropertyDescriptor descriptor = null;
            try {
                descriptor = new PropertyDescriptor(field.getName(), artefactClass);
            } catch (IntrospectionException e) {
                log.warn("Could not create PropertyDescriptor for class " + artefactClass.getName() + " field " + field.getName(), e);
                continue;
            }
            if (GrailsDomainConfigurationUtil.isNotConfigurational(descriptor)) {
                final MongoDomainClassProperty property = new MongoDomainClassProperty(this, field, descriptor);

                // property.isAnnotatedWith(Id.class) does not recognize inherited field and their annotations
                if (field.getAnnotation(Id.class) != null) {
                    this.identifier = property;
                } else {
                    propertyMap.put(descriptor.getName(), property);
                    if (property.isPersistent()) {
                        persistentProperties.put(descriptor.getName(), property);
                    }
                }
            }
        }

        //only check if there is an identifier when the class is not embedded
        if(artefactClass.getAnnotation(Embedded.class) == null) {
            // if we don't have an annotated identifier
            if (this.identifier == null) {
                throw new MappingException("You need to set the morphia Id annotation upon your id field on class " + getClazz().getName());
            }

            this.identifier.setIdentity(true);
        }

        // convert to arrays for optimization - as used by grails
        propertiesArray = propertyMap.values().toArray(new GrailsDomainClassProperty[propertyMap.size()]);
        persistentPropertyArray = persistentProperties.values().toArray(new GrailsDomainClassProperty[persistentProperties.size()]);

    }

    public boolean isOwningClass(Class domainClass) {
        return false;
    }

    public GrailsDomainClassProperty[] getProperties() {
        return propertiesArray;  //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * Returns all of the persistant properties of the domain class
     * @return The domain class' persistant properties
     * @deprecated Use #getPersistentProperties instead
     */
    public GrailsDomainClassProperty[] getPersistantProperties() {
        return getPersistentProperties();
    }

    public GrailsDomainClassProperty[] getPersistentProperties() {
        return persistentPropertyArray;
    }

    public GrailsDomainClassProperty getIdentifier() {
        return this.identifier;
    }

    public GrailsDomainClassProperty getVersion() {
        return this.version;
    }

    public Map getAssociationMap() {
        return Collections.EMPTY_MAP;
    }

    public GrailsDomainClassProperty getPropertyByName(String name) {
        return propertyMap.get(name);
    }

    public GrailsDomainClassProperty getPersistentProperty(String name) {
        for (GrailsDomainClassProperty prop : persistentPropertyArray) {
            if (prop.getName().equals(name)) return prop;
        }

        return null;
    }

    public String getFieldName(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getFieldName() : null;
    }

    public boolean isOneToMany(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isOneToMany();
    }

    public boolean isManyToOne(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isManyToOne();
    }

    public boolean isBidirectional(String propertyName) {
        return false;
    }

    public Class getRelatedClassType(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null ? prop.getType() : null;
    }

    public Map getConstrainedProperties() {
        return Collections.unmodifiableMap(this.constraints);
    }

    public Validator getValidator() {
        return this.validator;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public String getMappingStrategy() {
        return "MongoDB";
    }

    public boolean isRoot() {
        return true;
    }

    public Set<GrailsDomainClass> getSubClasses() {
        return Collections.emptySet();
    }

    public void refreshConstraints() {
        try {
            this.constraints = GrailsDomainConfigurationUtil.evaluateConstraints(getClazz(), this.persistentPropertyArray);

            // Embedded components have their own ComponentDomainClass
            // instance which won't be refreshed by the application.
            // So, we have to do it here.
            for (GrailsDomainClassProperty property : this.persistentPropertyArray) {
                if (property.isEmbedded()) {
                    property.getComponent().refreshConstraints();
                }
            }
        } catch (Exception e) {
            log.error("Error reading class [" + getClazz() + "] constraints: " + e.getMessage(), e);
        }
    }

    public boolean hasSubClasses() {
        return false;
    }

    public Map getMappedBy() {
        return Collections.EMPTY_MAP;
    }

    public boolean hasPersistentProperty(String propertyName) {
        GrailsDomainClassProperty prop = getPropertyByName(propertyName);
        return prop != null && prop.isPersistent();
    }

    public void setMappingStrategy(String strategy) {
        // do nothing
    }

    public void log(String message) {
        log.info(message);
    }
}