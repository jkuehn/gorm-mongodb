package grails.plugins.mongodb

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.support.SoftThreadLocalMap

import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.springframework.beans.BeanUtils
import org.codehaus.groovy.grails.web.binding.DataBindingUtils
import org.codehaus.groovy.grails.web.binding.DataBindingLazyMetaPropertyMap

import com.google.code.morphia.Datastore
import com.google.code.morphia.query.Query
import java.beans.Introspector
import com.mongodb.BasicDBObject
import com.google.code.morphia.mapping.Mapper
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import com.google.code.morphia.mapping.MappingException
import com.mongodb.DBCollection
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.codehaus.groovy.grails.plugins.DomainClassPluginSupport
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainConfigurationUtil
import grails.plugins.mongodb.dsl.IndexInfoBuilder
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import com.google.code.morphia.utils.IndexFieldDef
import com.mongodb.WriteConcern
import org.bson.types.ObjectId

/**
 * Author: Juri Kuehn
 * Date: 30.05.2010
 */
class MongoPluginSupport {
  private static final Log log = LogFactory.getLog(MongoDomainClass.class);

  static final PROPERTY_INSTANCE_MAP = new SoftThreadLocalMap()
  public static final String MORPHIA_ATTRIBUTE = "morphiaDS"

  public static final String EVENT_BEFORE_SAVE = "beforeSave"
  public static final String EVENT_AFTER_SAVE = "afterSave"
  public static final String EVENT_BEFORE_DELETE = "beforeDelete"
  public static final String EVENT_AFTER_DELETE = "afterDelete"

  // for dynamic finders
  static final COMPARATORS = Collections.unmodifiableList([
          "IsNull",
          "IsNotNull",
          "LessThan",
          "LessThanEquals",
          "GreaterThan",
          "GreaterThanEquals",
          "NotEqual",
          "Size",
          "All",
          "InList",
          "NotInList",
//          "NotBetween", // OR operator not supported by mongodb yet
          "Between" ])
  static final COMPARATORS_RE = COMPARATORS.join("|")
  static final DYNAMIC_FINDER_RE = /(\w+?)(${COMPARATORS_RE})?((And)(\w+?)(${COMPARATORS_RE})?)?/
//  static final DYNAMIC_FINDER_RE = /(\w+?)(${COMPARATORS_RE})?((And|Or)(\w+?)(${COMPARATORS_RE})?)?/

  static enhanceDomainClass(MongoDomainClass domainClass, GrailsApplication application, ApplicationContext ctx) {
    addGrailsDomainPluginMethods(application, domainClass, ctx)

    addStaticMethods(application, domainClass, ctx)
    addInstanceMethods(application, domainClass, ctx)
    addDynamicFinderSupport(application, domainClass, ctx)
    addInitMethods(application, domainClass, ctx)

    ensureIndices(application, domainClass, ctx)
    ensureIndicesDeprecated(application, domainClass, ctx)
  }

  static void ensureIndices(application, domainClass, ctx) {
    def domain = domainClass.clazz
    final Datastore datastore = getMongoBean(application).datastore

    try {
      def f = domain.getDeclaredField("indexes")
      f.accessible = true
      def mappingClosure = f.get()
      def builder = new IndexInfoBuilder()
      mappingClosure.delegate = builder
      mappingClosure()

      builder.errors.each {
        log.error("Error in index definition for class ${domain.class.name}: $it")
      }

      for (i in builder.indexes) {
        // add index to db
        datastore.ensureIndex(domain, i.name, i.fields as IndexFieldDef[], i.unique, i.dropDups)
      }
    } catch (NoSuchFieldException nsfe) {
      // no problem
    } catch (com.mongodb.MongoException mongoEx) {
      // usually communications problems, cannot ensure index
      throw mongoEx
    } catch (e) {
      throw new MappingException("Could not evaluate mapping for mongo domain " + domain.name)
    }  }

  // @todo REMOVE ME from 0.6 on
  static void ensureIndicesDeprecated(application, domainClass, ctx) {
    def domain = domainClass.clazz
    final DBCollection collection = getMongoBean(application).datastore.getCollection(domain)

    try {
      def f = domain.getDeclaredField(domainClass.ORM_MAPPING)
      f.accessible = true
      def mappingClosure = f.get()
      def evaluator = new DomainClassMappingEvaluator()
      mappingClosure.delegate = evaluator
      mappingClosure()

      def idx = evaluator.indices
      if (idx) println("\n\n*** " + domain.name + " uses deprecated index definitions. See user guide for new syntax.\n\n\n")
      for (i in idx) {
        def iName = i.key
        def fields = [:]
        i.value.each {
          fields[it] = 1
        }
        collection.ensureIndex(fields as BasicDBObject, iName)
      }
    } catch (NoSuchFieldException nsfe) {
      // no problem
    } catch (com.mongodb.MongoException mongoEx) {
      // usually communications problems, cannot ensure index
      throw mongoEx
    } catch (e) {
      throw new MappingException("Could not evaluate mapping for mongo domain " + domain.name)
    }
  }

  private static addInstanceMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc
    final Datastore datastore = getMongoBean(application).datastore

    metaClass.save = {->
      save(null)
    }

    metaClass.save = {Map args = [:] ->
      // todo: add support for failOnError:true in grails 1.2 (GRAILS-4343)

      // only process if beforeSave didnt return false
      if (!triggerEvent(EVENT_BEFORE_SAVE, delegate) && validate()) {
        autoTimeStamp(application, delegate)
        if (datastore.save(delegate)) {
          triggerEvent(EVENT_AFTER_SAVE, delegate) // call only on successful save
          return delegate
        }
      }

      return null
    }

    /**
     * creates a key object that can be used for referencing
     */
    metaClass.makeKey = {
      return datastore.getKey(delegate)
    }

    /**
     * call mongodb update function on this entity
     * http://code.google.com/p/morphia/wiki/Updating
     */
    metaClass.update = { Closure data, boolean createIfMissing = false, WriteConcern wc = null ->
      if (!delegate.ident()) {
        throw new IllegalStateException("Cannot update instances without an id")
      }
      def query = datastore.createQuery(delegate.class)
      def updateOp = datastore.createUpdateOperations(delegate.class)

      query.filter(Mapper.ID_KEY, delegate.ident());

      data.delegate = updateOp
      data()

      println "query: " + query.toString()
      println "updateop: " + updateOp

      def updateResult = datastore.update(query, updateOp, createIfMissing, wc)
      println updateResult
    }

    metaClass.delete = { ->
      triggerEvent(EVENT_BEFORE_DELETE, delegate)
      datastore.delete(delegate)
      triggerEvent(EVENT_AFTER_DELETE, delegate)
    }

    metaClass.delete = { Map dontcare -> // in case flush:true is passed in
      delete()
    }

    // allow autowiring. @todo: make this method deprecated and move to factory http://code.google.com/p/morphia/issues/detail?id=65
    metaClass.autowire = {
      ctx.beanFactory.autowireBeanProperties(delegate, ctx.beanFactory.AUTOWIRE_BY_NAME, false)
    }
  }

  private static addStaticMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def final metaClass = dc.metaClass
    def final domainClass = dc
    final Datastore datastore = getMongoBean(application).datastore

    metaClass.static.get = { Serializable docId ->
      try {
        // fetch from db
        def obj = datastore.get(domainClass.clazz, _checkedId(domainClass, docId))

        // dependency injection
        if (obj) ctx.beanFactory.autowireBeanProperties(obj, ctx.beanFactory.AUTOWIRE_BY_NAME, false)
        return obj
      } catch (Exception e) {
        // fall through to return null
        e.printStackTrace()
      }
      return null
    }

    // Foo.exists(1)
    metaClass.static.exists = {Serializable docId ->
      get(docId) != null
    }

    metaClass.static.deleteOne = { Serializable docId ->
      datastore.delete(domainClass.clazz, _checkedId(domainClass, docId))
    }

    // delete all documents with given ids
    metaClass.static.deleteAll = { List docIds ->
      datastore.delete(domainClass.clazz, docIds?.collect { _checkedId(domainClass, it) })
    }

    metaClass.static.deleteAll = { Map filter = [:] ->
      Query query = datastore.find(domainClass.clazz)

      filter.each { k, v ->
        query.filter(k.toString(), v)
      }

      datastore.delete(query)
    }

    metaClass.static.count = {
      return (datastore.getCount(domainClass.clazz) as Long)
    }

    /**
     * return only the first object, if any
     */
    metaClass.static.find = { Map filter = [:], Map queryParams = [:] ->
      queryParams['max'] = 1

      def res = findAll(filter, queryParams).toList()
      if (res) ctx.beanFactory.autowireBeanProperties(res[0], ctx.beanFactory.AUTOWIRE_BY_NAME, false)
      return res?res[0]:null
    }

    metaClass.static.findAll = { Map filter = [:], Map queryParams = [:] ->
      Query query = datastore.find(domainClass.clazz)
      configureQuery query, queryParams

      filter.each { k, v ->
        query.filter(k.toString(), v)
      }

      return query.fetch()
    }

    metaClass.static.list = { Map queryParams = [:] ->
      findAll([:], queryParams)
    }
  }

  public static void configureQuery(Query query, Map queryParams) {
    // @todo be more graceful
    def sort = queryParams.get('sort')?.toString()
    def limit = (int)(queryParams.get('max') ?: 25).toInteger()
    def offset = (int)(queryParams.get('offset') ?: 0).toInteger()

    if (sort) query.order(sort)
    query.limit(limit)
    query.offset(offset)
  }

  private static addDynamicFinderSupport(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc

    // This adds basic dynamic finder support.
    metaClass.static.methodMissing = { method, args ->
      def m = method =~ /^find(All|One)?By${DYNAMIC_FINDER_RE}$/
      if (m) {
        def fields = []
        def comparator = m[0][3]
        boolean returnOne = (m[0][1] == "One")
        // How many arguments do we need to pass for the given
        // comparator?
        def numArgs = getArgCountForComparator(comparator)

        fields << [field:Introspector.decapitalize(m[0][2]),
            args:args[0..<numArgs], // @todo move args out of here, it'll stay for newMethod in memory unused
            argCount:numArgs,
            comparator:comparator]

        // Strip out that number of arguments from the ones
        // we've been passed.
        args = args[numArgs..<args.size()]

        // If we have a second clause, evaluate it now.
        def join = m[0][5]

        if (join) {
          comparator = m[0][7]
          numArgs = getArgCountForComparator(comparator)
          fields << [field: Introspector.decapitalize(m[0][6]),
              args:args[0..<numArgs],
              argCount:numArgs,
              comparator:comparator]

          // remove args for second parameter
          args = args[numArgs..<args.size()]
        }

        final int expectedMinArgsCount = (int)fields.inject(0) { acc, val -> acc+val.argCount }
        // cache new behavior
        def newMethod = { Object[] varArgs ->
          def localArgs = varArgs ? varArgs[0] : []
          if (localArgs.size() < expectedMinArgsCount) throw new MissingMethodException(method, delegate, varArgs)

          def filter = [:]
          def field1ArgCount = fields[0].argCount
          updateFilter(filter, fields[0].field, fields[0].comparator, localArgs[0..<field1ArgCount])
          if (fields.size()>1) {
            updateFilter(filter, fields[1].field, fields[1].comparator, localArgs[field1ArgCount..<(field1ArgCount+fields[1].argCount)])
          }

          // put options to the map
          Map queryParams
          if (localArgs.size() > expectedMinArgsCount && localArgs[expectedMinArgsCount] instanceof Map)
            queryParams = localArgs[expectedMinArgsCount]
          else
            queryParams = [:]

          // return only the first result
          if (returnOne) return find(filter, queryParams)

          // return the iterator for this collection
          return findAll(filter, queryParams)
        }

        // register new cached behavior on metaclass to speed up next invokation
        domainClass.metaClass.static."$method" = newMethod

        // Check whether we have any options, such as "sort".
        def queryParams = [:]
        if (args) {
          if(args[0] instanceof Map) {
            queryParams = args[0]
          }
        }


        def finalArgs = fields.collect { it.args }.flatten()
        finalArgs << queryParams

        // invoke new behavior
        return newMethod(finalArgs)


      } else {
        throw new MissingMethodException(method, delegate, args)
      }
    }
  }

  private static addInitMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc

    metaClass.constructor = { Map map = [:] ->
      def instance = ctx.containsBean(domainClass.fullName) ? ctx.getBean(domainClass.fullName) : BeanUtils.instantiateClass(domainClass.clazz)
      DataBindingUtils.bindObjectToDomainInstance(domainClass, instance, map)
      DataBindingUtils.assignBidirectionalAssociations(instance, map, domainClass)
      return instance
    }
    metaClass.setProperties = {Object o ->
      DataBindingUtils.bindObjectToDomainInstance(domainClass, delegate, o)
    }
    metaClass.getProperties = {->
      new DataBindingLazyMetaPropertyMap(delegate)
    }
  }

  private static Object autoTimeStamp(GrailsApplication application, Object domain) {

    MongoDomainClass dc = (MongoDomainClass) application.getArtefact(MongoDomainClassArtefactHandler.TYPE, domain.getClass().getName())
    if (dc) {
      def metaClass = dc.metaClass

      MetaProperty property = metaClass.hasProperty(dc, GrailsDomainClassProperty.DATE_CREATED)
      def time = System.currentTimeMillis()
      if (property && domain[property.name] == null) {
        def now = property.getType().newInstance([time] as Object[])
        domain[property.name] = now
      }

      property = metaClass.hasProperty(dc, GrailsDomainClassProperty.LAST_UPDATED)
      if (property) {
        def now = property.getType().newInstance([time] as Object[])
        domain[property.name] = now
      }
    }

    return domain
  }

  /**
   * autoconvert to ObjectId if neccessary
   * @param id
   * @return
   */
  protected static Serializable  _checkedId(domainClass, id) {
    if (domainClass.identifier.type == ObjectId.class && !(id instanceof ObjectId))
      return new ObjectId(id.toString())

    return id
  }

  private static MongoHolderBean getMongoBean(GrailsApplication application) {
    (MongoHolderBean)application.mainContext.getBean("mongo")
  }

  /**
   * call event on domain class
   * if event closure returns a boolean false this method
   * returns true, false otherwise
   *
   * @param event
   * @param entity
   * @return
   */
  private static boolean triggerEvent(String event, entity) {
    def result = false
    if (entity?.metaClass) {
      if(entity.metaClass.hasProperty(entity, event)) {
        def callable = entity."$event"
        if(callable instanceof Closure) {
          callable.resolveStrategy = Closure.DELEGATE_FIRST
          callable.delegate = entity
          result = callable.call()
          if (result instanceof Boolean) result = !result
          else {
            result = false
          }
        }
      }
    }
    return result
  }

  public static updateFilter(Map filter, String field, String comparator, values) {
    // default to equals
    switch(comparator) {
      case "IsNull": filter["$field exists"] =  0; break
      case "IsNotNull": filter["$field exists"] =  1; break
      case "Size": filter["$field size"] = values[0]; break
      case "All": filter["$field all"] = values[0]; break
      case "Between":
        filter["$field >="] = values[0];
        filter["$field <="] = values[1]; break
      case "NotBetween":
        filter["$field <"] = values[0];
        filter["$field >"] = values[1]; break
      case "InList": filter["$field in"] = values[0]; break
      case "NotInList": filter["$field nin"] = values[0]; break
      case "LessThan": filter["$field <"] = values[0]; break
      case "LessThanEquals": filter["$field <="] = values[0]; break
      case "GreaterThan": filter["$field >"] = values[0]; break
      case "GreaterThanEquals": filter["$field >="] = values[0]; break
      case "NotEqual": filter["$field !="] = values[0]; break
      default: filter["$field ="] = values[0]; break // equal
    }
  }

  private static int getArgCountForComparator(String comparator) {
    if (comparator == "Between" || comparator == "NotBetween") {
      return 2
    }
    else if (["IsNull", "IsNotNull"].contains(comparator)) {
      return 0
    }
    else {
      return 1
    }
  }

  /**
   * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   * COPY & PASTE FROM org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin START
   * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   */
  static void addGrailsDomainPluginMethods(application, domainClass, ctx) {
    MetaClass metaClass = domainClass.metaClass

    metaClass.ident = {-> delegate[domainClass.identifier.name] }
    metaClass.constructor = { ->
      if (ctx.containsBean(domainClass.fullName)) {
        ctx.getBean(domainClass.fullName)
      }
      else {
        BeanUtils.instantiateClass(domainClass.clazz)
      }
    }

    metaClass.static.create = { ->
      if (ctx.containsBean(domainClass.fullName)) {
        ctx.getBean(domainClass.fullName)
      }
      else {
        BeanUtils.instantiateClass(domainClass.clazz)
      }
    }

    addValidationMethods(application, domainClass, ctx)
    addRelationshipManagementMethods(domainClass)
  }

  private static addValidationMethods(GrailsApplication application, GrailsDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc

    registerConstraintsProperty(metaClass, domainClass)

    metaClass.hasErrors = {-> delegate.errors?.hasErrors() }

    def get
    def put
    try {
      def rch = application.classLoader.loadClass("org.springframework.web.context.request.RequestContextHolder")
      get = {
        def attributes = rch.getRequestAttributes()
        if (attributes) {
          return attributes.request.getAttribute(it)
        }
        return PROPERTY_INSTANCE_MAP.get().get(it)
      }
      put = { key, val ->
        def attributes = rch.getRequestAttributes()
        if (attributes) {
          attributes.request.setAttribute(key,val)
        }
        else {
          PROPERTY_INSTANCE_MAP.get().put(key,val)
        }
      }
    }
    catch (Throwable e) {
      get = { PROPERTY_INSTANCE_MAP.get().get(it) }
      put = { key, val -> PROPERTY_INSTANCE_MAP.get().put(key,val) }
    }

    metaClass.getErrors = { ->
      def errors
      def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
      errors = get(key)
      if (!errors) {
        errors =  new BeanPropertyBindingResult( delegate, delegate.getClass().getName())
        put key, errors
      }
      errors
    }
    metaClass.setErrors = { Errors errors ->
      def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
      put key, errors
    }
    metaClass.clearErrors = { ->
      delegate.setErrors (new BeanPropertyBindingResult(delegate, delegate.getClass().getName()))
    }
    if (!domainClass.hasMetaMethod("validate")) {
      metaClass.validate = { ->
        DomainClassPluginSupport.validateInstance(delegate, ctx)
      }
    }
  }

  /**
   * Registers the constraints property for the given MetaClass and domainClass instance
   */
  static void registerConstraintsProperty(MetaClass metaClass, GrailsDomainClass domainClass) {
    metaClass.'static'.getConstraints = { -> domainClass.constrainedProperties }

    metaClass.getConstraints = {-> domainClass.constrainedProperties }
  }

  private static addRelationshipManagementMethods(GrailsDomainClass dc) {
    def metaClass = dc.metaClass
    for (p in dc.persistantProperties) {
      def prop = p
      if (prop.basicCollectionType) { // @todo check on these - never true right now
        def collectionName = GrailsClassUtils.getClassNameRepresentation(prop.name)
        metaClass."addTo$collectionName" = { obj ->
          if (obj instanceof CharSequence && !(obj instanceof String)) {
            obj = obj.toString()
          }
          if (prop.referencedPropertyType.isInstance(obj)) {
            if (delegate[prop.name] == null) {
              delegate[prop.name] = GrailsClassUtils.createConcreteCollection(prop.type)
            }
            delegate[prop.name] << obj
            return delegate
          }
          else {
            throw new MissingMethodException("addTo${collectionName}", dc.clazz, [obj] as Object[])
          }
        }
        metaClass."removeFrom$collectionName" = { obj ->
          if (delegate[prop.name]) {
            if (obj instanceof CharSequence && !(obj instanceof String)) {
              obj = obj.toString()
            }
            delegate[prop.name].remove(obj)
          }
          return delegate
        }
      }
      else if (prop.oneToOne || prop.manyToOne) { // @todo check on these - never true right now
        def identifierPropertyName = "${prop.name}Id"
        if (!dc.hasMetaProperty(identifierPropertyName)) {
          def getterName = GrailsClassUtils.getGetterName(identifierPropertyName)
          metaClass."$getterName" = {-> GrailsDomainConfigurationUtil.getAssociationIdentifier(
              delegate, prop.name, prop.referencedDomainClass) }
        }
      }
      else if (prop.oneToMany || prop.manyToMany) { // @todo check on these - never true right now
        if (metaClass instanceof ExpandoMetaClass) {
          def propertyName = prop.name
          def collectionName = GrailsClassUtils.getClassNameRepresentation(propertyName)
          def otherDomainClass = prop.referencedDomainClass

          metaClass."addTo${collectionName}" = { Object arg ->
            Object obj
            if (delegate[prop.name] == null) {
              delegate[prop.name] = GrailsClassUtils.createConcreteCollection(prop.type)
            }
            if (arg instanceof Map) {
              obj = otherDomainClass.newInstance()
              obj.properties = arg
              delegate[prop.name].add(obj)
            }
            else if (otherDomainClass.clazz.isInstance(arg)) {
              obj = arg
              delegate[prop.name].add(obj)
            }
            else {
              throw new MissingMethodException("addTo${collectionName}", dc.clazz, [arg] as Object[])
            }
            if (prop.bidirectional && prop.otherSide) {
              def otherSide = prop.otherSide
              if (otherSide.oneToMany || otherSide.manyToMany) {
                String name = prop.otherSide.name
                if (!obj[name]) {
                  obj[name] = GrailsClassUtils.createConcreteCollection(prop.otherSide.type)
                }
                obj[prop.otherSide.name].add(delegate)
              }
              else {
                obj[prop.otherSide.name] = delegate
              }
            }
            delegate
          }
          metaClass."removeFrom${collectionName}" = {Object arg ->
            if (otherDomainClass.clazz.isInstance(arg)) {
              delegate[prop.name]?.remove(arg)
              if (prop.bidirectional) {
                if (prop.manyToMany) {
                  String name = prop.otherSide.name
                  arg[name]?.remove(delegate)
                }
                else {
                  arg[prop.otherSide.name] = null
                }
              }
            }
            else {
              throw new MissingMethodException("removeFrom${collectionName}", dc.clazz, [arg] as Object[])
            }
            delegate
          }
        }
      }
    }
  }
  /**
   * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   * COPY & PASTE FROM org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin END
   * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
   */

}
