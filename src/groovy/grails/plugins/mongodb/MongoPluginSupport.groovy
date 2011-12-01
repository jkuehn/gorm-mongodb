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
import com.google.code.morphia.mapping.Mapper
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import com.google.code.morphia.mapping.MappingException
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
import com.google.code.morphia.DatastoreImpl
import com.mongodb.DBObject
import com.google.code.morphia.Key
import com.mongodb.DBRef
import com.google.code.morphia.AdvancedDatastore
import org.apache.commons.lang.math.NumberUtils
import grails.validation.ValidationException

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
    public static final String EVENT_BEFORE_VALIDATE = "beforeValidate"

    public static final int DEFAULT_MAX_RESULTS = 25

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
                // @todo implement sparse indexes as soon as morphia provides the api - boolean:i.sparse
                datastore.ensureIndex(domain, i.name, i.fields as IndexFieldDef[], i.unique, i.dropDups)
            }
        } catch (NoSuchFieldException nsfe) {
            // no problem
        } catch (com.mongodb.MongoException mongoEx) {
            // usually communications problems, cannot ensure index
            throw mongoEx
        } catch (e) {
            throw new MappingException("Could not evaluate mapping for mongo domain " + domain.name + " - " + e.message)
        }
    }

    private static addInstanceMethods(GrailsApplication application, MongoDomainClass dc, ApplicationContext ctx) {
        def metaClass = dc.metaClass
        def domainClass = dc
        final MongoHolderBean mongoHolderBean = getMongoBean(application)
        final DatastoreImpl datastore = (DatastoreImpl)mongoHolderBean.datastore

        metaClass.save = {->
            save(null)
        }

        metaClass.save = {Map args = [:] ->
            boolean doValidate = args.containsKey('validate') ? (boolean) args.get('validate')  : true
            boolean doFailOnError = args.containsKey('failOnError') ? (boolean) args.get('failOnError')  : false

            // only process if beforeSave didnt return false to permit
            if (!triggerEvent(EVENT_BEFORE_SAVE, delegate)) {

                // do validation if requested
                if (doValidate && !validate()) {
                    // validation has errors
                    if (doFailOnError) throw new ValidationException(dc.getFullName() + ' has validation errors', errors)
                } else {
                    // no errors, save
                    autoTimeStamp(application, delegate)
                    if (datastore.save(delegate)) {
                        triggerEvent(EVENT_AFTER_SAVE, delegate) // call only on successful save
                        return delegate
                    }
                }

            }

            return null
        }

        /**
         * creates a key object that can be used for referencing
         */
        metaClass.createKey = {
            return datastore.getKey(delegate)
        }

        /**
         * @deprecated use createKey instead
         */
        metaClass.makeKey = {
            log.error "makeKey is deprecated, please use createKey instead"
            return datastore.getKey(delegate)
        }

        /**
         * creates a DBRef object that can be used for referencing
         */
        metaClass.createDBRef = {
            return datastore.createRef(delegate)
        }

        /**
         * merge instance values into database instance
         */
        metaClass.merge = {
            return datastore.merge(delegate)
        }

        /**
         * creates a DBObject based on the values of this instance
         */
        metaClass.toDBObject = {
            return mongoHolderBean.morphia.toDBObject(delegate)
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

            datastore.update(query, updateOp, createIfMissing, wc)
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
        final MongoHolderBean mongoHolderBean = getMongoBean(application)
        final Datastore datastore = mongoHolderBean.datastore

        metaClass.static.get = { Object docId ->
            // fetch from db
            try {
                // handle dbrefs and keys too
                if (docId instanceof Key) {
                    return datastore.getByKey(domainClass.clazz, docId)
                }
                if (docId instanceof DBRef && datastore instanceof AdvancedDatastore) {
                    return datastore.get(domainClass.clazz, docId)
                }

                // else: its a document id
                return datastore.get(domainClass.clazz, _checkedId(domainClass, docId))
            } catch (Exception e) {
                // fall through to return null
                log.error("Could not get instance from DB", e)
            }
            return null
        }

        metaClass.static.getAll = { Collection docIds ->
            findAll('id in': docIds)
        }

        // Foo.exists(1)
        metaClass.static.exists = { Object docId ->
            get(docId) != null
        }

        metaClass.static.getCollection = {
            datastore.getCollection(domainClass.clazz)
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

        /**
         * @deprecated use count instead, will be removed soon
         */
        metaClass.static.countAll = { Map filter = [:] ->
            throw new DeprecationException("countAll is deprecated, please use count instead")
        }

        metaClass.static.count = { Map filter = [:] ->
            Query query = datastore.find(domainClass.clazz)

            filter.each { k, v ->
                query.filter(k.toString(), v)
            }

            datastore.getCount(query)
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
            if (!queryParams.containsKey('max')) queryParams.max = 0 // list all by default

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

        /**
         * update closure used by update and updateFirst methods
         */
        def updateFunction = { boolean multi, filter, Closure data, boolean createIfMissing = false, WriteConcern wc = null ->
            if (!(filter instanceof Map)) filter = [(Mapper.ID_KEY): _checkedId(domainClass, filter)]

            def query = datastore.createQuery(domainClass.clazz)
            def updateOp = datastore.createUpdateOperations(domainClass.clazz)

            filter.each { k, v ->
                query.filter(k.toString(), v)
            }

            data.delegate = updateOp
            data()

            if (multi) {
                datastore.update(query, updateOp, createIfMissing, wc)
            } else {
                datastore.updateFirst(query, updateOp, createIfMissing, wc)
            }
        }

        metaClass.static.update = { filter, Closure data, boolean createIfMissing = false, WriteConcern wc = null ->
            updateFunction(true, filter, data, createIfMissing, wc)
        }
        metaClass.static.updateFirst = { filter, Closure data, boolean createIfMissing = false, WriteConcern wc = null ->
            updateFunction(false, filter, data, createIfMissing, wc)
        }

        /**
         * creates a new instance of this class and populates fields from DBObject
         */
        metaClass.static.fromDBObject = { DBObject dbObject ->
            return mongoHolderBean.morphia.fromDBObject(domainClass.clazz, dbObject)
        }

        /**
         * do a morphia query
         */
        metaClass.static.query = { Map params, Closure config = null ->
            Query query = datastore.createQuery(domainClass.clazz)

            if (params) query.setParams(params)

            if (config) {
                // execute closure in query context
                config.delegate = query
                config.resolveStrategy = Closure.DELEGATE_FIRST
                config.call()
            }

            return query
        }

        metaClass.static.query = { Closure config = null ->
            query(null, config)
        }


    }

    public static void configureQuery(Query query, Map queryParams) {
        // @todo be more graceful
        def sort = queryParams.get('sort')?.toString()
        def limit = (int)(queryParams.containsKey('max') ? queryParams.get('max') : DEFAULT_MAX_RESULTS).toInteger()
        def offset = (int)(queryParams.get('offset') ?: 0).toInteger()
        // handle the morphia query validation - default validation is set to true
        def validation = queryParams.containsKey('validate') ? (boolean) queryParams.get('validate')  : true
        if (queryParams.containsKey('validation')) {
            // @todo #jk 01.12.11 14:16 (jk): remove deprecated
            log.error("'validation' parameter is deprecated for queries. Use the new name 'validate'")
            validation = queryParams.containsKey('validation') ? (boolean) queryParams.get('validation')  : true
        }

        if(!validation){
            // disables query validation - this enables querying of embedded object properties like 'parent.embedded.property'
            query.disableValidation();
        }
        if (sort){
            // in case we have a sorting defined we also need to handle the sort order:
            // default order -> asc
            // asc will become an empty prefix in front or the sorting field
            // desc will become a '-' prefix
            def order = (queryParams.containsKey('order') && queryParams.get('order') == 'desc' )? '-' : ''
            query.order(order+sort)
        }
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
                boolean returnOne = !(m[0][1] == "All")
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
     * add setParams method to morphias query object for pagination handling
     */
    public static void enhanceMorphiaQueryClass() {

        def metaClass = Query.metaClass

        /**
         * construct a params array from current settings
         */
        metaClass.setParams = { Map params ->
            /* @var delegate Query */
            // sort
            if (params.containsKey('sort')) {
                String sortField = params.sort.toString()
                String order = (params.containsKey('order') && params.order == 'desc' )? '-' : ''
                delegate.order(order+sortField)
            }

            // offset
            if (params.containsKey('offset')) delegate.offset(NumberUtils.toInt(params.offset, 0))

            // limit
            if (params.containsKey('max')) delegate.limit(NumberUtils.toInt(params.max, -1))
        }
    }

    /**
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * COPY & PASTE FROM org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin START
     * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     * added triggerEvent to validate method: triggerEvent(EVENT_BEFORE_VALIDATE, delegate)
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
                triggerEvent(EVENT_BEFORE_VALIDATE, delegate)
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
