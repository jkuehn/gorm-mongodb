import grails.plugins.mongodb.MongoPluginSupport
import org.springframework.context.ApplicationContext
import grails.plugins.mongodb.MongoDomainClass
import grails.plugins.mongodb.MongoHolderBean
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import grails.plugins.mongodb.MongoDomainClassArtefactHandler
import com.google.code.morphia.Morphia
import com.google.code.morphia.Datastore
import com.google.code.morphia.mapping.MappedClass

class MongodbMorphiaGrailsPlugin {

    def license = "APACHE"
    def developers = [
          [ name: "Juri Kuehn", email: "juri.kuehn@gmail.com" ]
    ]
    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPGORMMONGODB" ]
    def scm = [ url: "https://github.com/jkuehn/gorm-mongodb" ]

    // the plugin version
    def version = "0.7.8"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3.4 > *"
    // the other plugins this plugin depends on
    def dependsOn = [core: '1.3.4 > *']

    def loadAfter = ['core', 'controllers', 'domainClass']

    // resources that are excluded from plugin packaging
    def pluginExcludes = [
          "grails-app/views/error.gsp",
          "grails-app/controllers/**", // they exist only for testing purposes
          "grails-app/conf/Config.groovy",
          "grails-app/mongo/**",
          "grails-app/someotherdir/**",
    ]

    def author = "Juri Kuehn"
    def authorEmail = "juri.kuehn at gmail.com"
    def title = "Alternative MongoDB GORM based on the Morphia library"
    def description = '''GORM implementation for the MongoDB NoSQL database based on the Morphia library'''

    // URL to the plugin's documentation
    def documentation = "http://wiki.github.com/jkuehn/gorm-mongodb/"

    def artefacts = [grails.plugins.mongodb.MongoDomainClassArtefactHandler]

    def watchedResources = [
          'file:./grails-app/mongo/**',
    ]

    def doWithSpring = { ApplicationContext ctx ->
        // register the mongo bean, which will provide access to configured mongo and morphia instances
        mongo(MongoHolderBean) { bean ->
            bean.autowire = 'constructor'
        }

        // register all mongo domains as beans
        application.MongoDomainClasses.each { GrailsDomainClass dc ->
            // Note the use of Groovy's ability to use dynamic strings in method names!
            "${dc.fullName}"(dc.clazz) { bean ->
                bean.singleton = false
                bean.autowire = "byName"
            }
            "${dc.fullName}DomainClass"(MethodInvokingFactoryBean) { bean ->
                targetObject = ref("grailsApplication", true)
                targetMethod = "getArtefact"
                bean.lazyInit = true
                arguments = [MongoDomainClassArtefactHandler.TYPE, dc.fullName]
            }
            "${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) { bean ->
                targetObject = ref("${dc.fullName}DomainClass")
                bean.lazyInit = true
                targetMethod = "getClazz"
            }
            "${dc.fullName}Validator"(GrailsDomainClassValidator) { bean ->
                messageSource = ref("messageSource")
                bean.lazyInit = true
                domainClass = ref("${dc.fullName}DomainClass")
                grailsApplication = ref("grailsApplication", true)
            }
        }
    }

    def doWithDynamicMethods = { ApplicationContext ctx ->
        Morphia morphia = ctx.getBean('mongo').morphia
        Datastore datastore = ctx.getBean('mongo').datastore

        application.MongoDomainClasses.each { GrailsDomainClass domainClass ->
            try {
                if (!(domainClass instanceof MongoDomainClass)) return // process mongo domains only

                // add dynamic finders, validation, querying methods etc
                MongoPluginSupport.enhanceDomainClass(domainClass, application, ctx)

                // add domain class to mapper
                println "adding domain " + domainClass.getClazz() + " to morphia"
                morphia.map(domainClass.getClazz())
            } catch (e) {
                log.error ("Error processing mongodb domain $domainClass: " + e.message)
            }
        }

        // add fetch method to morphias Key
        com.google.code.morphia.Key.metaClass.fetch = {
            // @todo waiting until getClassFromKind works
            Class clazz = delegate.kindClass
            if (!clazz) {
                String kind = delegate.kind
                for (MappedClass mc in morphia.getMapper().getMappedClasses()) {
                    if (mc.getCollectionName().equals(kind)) {
                        clazz = mc.getClazz()
                        break
                    }
                }
            }
            datastore.getByKey(clazz, delegate)
        }

        MongoPluginSupport.enhanceMorphiaQueryClass()
    }

    /**
     * in order to reload the mongoclasses grails app has to be reloaded
     */
    def onChange = {event ->
        if (event?.source instanceof Class &&
              grails.plugins.mongodb.MongoDomainClassArtefactHandler.isMongoDomainClass(event.source)) {
            // reload needed, reregistering spring beans, enhancing domainclass, evaluating constraints etc
            log.info("MongoDB domain ${event.source} changed, reloading.")
            restartContainer()
        }
    }
}
