package grails.plugins.mongodb

import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.ApplicationContext
import org.codehaus.groovy.grails.support.SoftThreadLocalMap
import com.mongodb.Mongo
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.plugins.DomainClassPluginSupport
import org.springframework.beans.BeanUtils
import org.codehaus.groovy.grails.web.binding.DataBindingUtils
import org.codehaus.groovy.grails.web.binding.DataBindingLazyMetaPropertyMap
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.Errors
import org.codehaus.groovy.grails.web.context.ServletContextHolder
import com.google.code.morphia.Morphia
import com.google.code.morphia.Datastore
import com.google.code.morphia.query.Query
import java.beans.Introspector

/**
 * Author: Juri Kuehn
 * Date: 30.05.2010
 */
class MongoPluginSupport {

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
          "NotBetween",
          "Between" ])
  static final COMPARATORS_RE = COMPARATORS.join("|")
  static final DYNAMIC_FINDER_RE = /(\w+?)(${COMPARATORS_RE})?((And)(\w+?)(${COMPARATORS_RE})?)?/
//  static final DYNAMIC_FINDER_RE = /(\w+?)(${COMPARATORS_RE})?((And|Or)(\w+?)(${COMPARATORS_RE})?)?/

  static enhanceDomainClass(MongoDomainClass domainClass, GrailsApplication application, ApplicationContext ctx) {
    addStaticMethods(application, domainClass, ctx)
    addInstanceMethods(application, domainClass, ctx)
    addDynamicFinderSupport(application, domainClass, ctx)

    addValidationMethods(application, domainClass, ctx)
  }

  private static addInstanceMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc

    metaClass.save = {->
      save(null)
    }

    metaClass.save = {Map args = [:] ->
      // todo: add support for failOnError:true in grails 1.2 (GRAILS-4343)
      if (validate()) {
        // only process if beforeSave didnt return false
        if (!triggerEvent(EVENT_BEFORE_SAVE, delegate)) {
          autoTimeStamp(application, delegate)
          if (getDatastoreForOperation(application).save(delegate)) {
            triggerEvent(EVENT_AFTER_SAVE, delegate) // call only on successful save
          }
        }

        return delegate
      }

      return null
    }

    metaClass.delete = { ->
      triggerEvent(EVENT_BEFORE_DELETE, delegate)
      getDatastoreForOperation(application).delete(delegate)
      triggerEvent(EVENT_AFTER_DELETE, delegate)
    }

    metaClass.delete = { Map dontcare -> // in case flush:true is passed in
      delete()
    }
  }

  private static addStaticMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def final metaClass = dc.metaClass
    def final domainClass = dc

    metaClass.static.get = { Serializable docId ->
      try {
        return getDatastoreForOperation(application).get(domainClass.clazz, docId.toString())
      } catch (Exception e) {
        // fall through to return null
      }
      return null
    }

    // Foo.exists(1)
    metaClass.static.exists = {Serializable docId ->
      get(docId) != null
    }

    // cannot use Serializeable here, because Map implements it too
    metaClass.static.delete = { String docId ->
      getDatastoreForOperation(application).delete(domainClass.clazz, docId.toString())
    }

    metaClass.static.delete = { Map filter ->
      Datastore ds = getDatastoreForOperation(application)
      Query query = ds.find(domainClass.clazz)

      filter.each { k, v ->
        query.filter(k.toString(), v)
      }

      ds.delete(query)
    }

    metaClass.static.count = {
      return (getDatastoreForOperation(application).getCount(domainClass.clazz) as Long)
    }

    /**
     * return only the first object, if any
     */
    metaClass.static.find = { Map filter = [:], Map queryParams = [:] ->
      queryParams['max'] = 1

      def res = findAll(filter, queryParams).toList()
      return res?res[0]:null
    }

    metaClass.static.findAll = { Map filter = [:], Map queryParams = [:] ->
      Query query = getDatastoreForOperation(application).find(domainClass.clazz)
      configureQuery query, queryParams

      filter.each { k, v ->
        query.filter(k.toString(), v)
      }

      return query.fetch()
    }

    metaClass.static.list = { Map queryParams = [:] ->
      findAll([:], queryParams)
    }

    metaClass.static.getDatastore = {
      getDatastoreForOperation(application)
    }

    metaClass.static.getDatastore = {
      getDatastoreForOperation(application)
    }
  }

  public static void configureQuery(Query query, Map queryParams) {
    // @todo be more graceful
    def sort = queryParams.remove('sort')?.toString()
    def limit = (int)(queryParams.remove('max') ?: 25)
    def offset = (int)(queryParams.remove('offset') ?: 0)

    if (sort) query.order(sort)
    query.limit(limit)
    query.offset(offset)
  }

  private static addDynamicFinderSupport(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc

    // This adds basic dynamic finder support.
    metaClass.static.methodMissing = { method, args ->
      def m = method =~ /^find(All)?By${DYNAMIC_FINDER_RE}$/
      if (m) {
        def fields = []
        def comparator = m[0][3]
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

  private static addValidationMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
    def metaClass = dc.metaClass
    def domainClass = dc

    metaClass.static.getConstraints = {->
      domainClass.constrainedProperties
    }

    metaClass.getConstraints = {->
      domainClass.constrainedProperties
    }

    metaClass.constructor = {Map map ->
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

    metaClass.hasErrors = {-> delegate.errors?.hasErrors() }

    def get
    def put
    try {
      def rch = application.classLoader.loadClass("org.springframework.web.context.request.RequestContextHolder")
      get = {
        def attributes = rch.getRequestAttributes()
        if (attributes) {
          return attributes.request.getAttribute(it)
        } else {
          return PROPERTY_INSTANCE_MAP.get().get(it)
        }
      }
      put = {key, val ->
        def attributes = rch.getRequestAttributes()
        if (attributes) {
          attributes.request.setAttribute(key, val)
        } else {
          PROPERTY_INSTANCE_MAP.get().put(key, val)
        }
      }
    } catch (Throwable e) {
      get = { PROPERTY_INSTANCE_MAP.get().get(it) }
      put = {key, val -> PROPERTY_INSTANCE_MAP.get().put(key, val) }
    }

    metaClass.getErrors = {->
      def errors
      def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
      errors = get(key)
      if (!errors) {
        errors = new BeanPropertyBindingResult(delegate, delegate.getClass().getName())
        put key, errors
      }
      errors
    }

    metaClass.setErrors = {Errors errors ->
      def key = "org.codehaus.groovy.grails.ERRORS_${delegate.class.name}_${System.identityHashCode(delegate)}"
      put key, errors
    }

    metaClass.clearErrors = {->
      delegate.setErrors(new BeanPropertyBindingResult(delegate, delegate.getClass().getName()))
    }

    if (!metaClass.respondsTo(dc.getReference(), "validate")) {
      metaClass.validate = {->
        DomainClassPluginSupport.validateInstance(delegate, ctx)
      }
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

  private static String getDocumentId(MongoDomainClass dc, Object domain) {
    def id = dc.getIdentifier()
    if (id) {
      domain[id.name]
    }
  }

  private static Datastore getDatastoreForOperation(GrailsApplication application) {
    Datastore morphiaDS = getDatastore(application)
    if (!morphiaDS) throw new Exception("Morphia instance could not be set up. Plugin did not initialize correctly")
    morphiaDS
  }

  public static Datastore getDatastore(GrailsApplication application, forceReload = false) {
    def servletCtx = ServletContextHolder.getServletContext()
    if (servletCtx == null) {
      println "Grails has not finished loading, cannot register MongoDB Morphia Datastore"
      return
    }
    Datastore morphiaDS = null
    if (!forceReload) morphiaDS = (Datastore)servletCtx.getAttribute(MORPHIA_ATTRIBUTE)
    if (morphiaDS) return morphiaDS

    // no datastore initialized yet or reload is requested, do this now

    // get configureation
    def ds = application.config.mongodb
    String host = ds?.host ?: "localhost"
    Integer port = ds?.port ?: 27017
    String database = ds?.database ?: application.metadata["app.name"]
    String username = ds?.username ?: ""  // not used yet
    String password = ds?.password ?: ""  // not used yet

    // create mongo instance and datastore
    Mongo db = new Mongo(host, port)
    def morphia = new Morphia()

    application.MongoDomainClasses.each { dc ->
      println "adding domain " + dc.getClazz() + " to morphia"
      morphia.map(dc.getClazz())
    }

    morphiaDS = morphia.createDatastore(db, database)
    servletCtx.setAttribute(MORPHIA_ATTRIBUTE, morphiaDS)
    return morphiaDS
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
}
