import grails.plugins.mongodb.MongoPluginSupport
import org.springframework.context.ApplicationContext
import grails.plugins.mongodb.MongoDomainClass
import grails.plugins.mongodb.MongoHolderBean
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import grails.plugins.mongodb.MongoDomainClassArtefactHandler

class GormMongodbGrailsPlugin {
  // the plugin version
  def version = "0.4"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "1.3 > *"
  // the other plugins this plugin depends on
  def dependsOn = [core: '1.3 > *']

  // load after hibernate to avoid conflicts with domain artefacts
  def loadAfter = ['core', 'controllers', 'domainClass', 'hibernate']

  // resources that are excluded from plugin packaging
  def pluginExcludes = [
      "grails-app/views/error.gsp",
      "grails-app/controllers/**", // they exist only for testing purposes
      "grails-app/conf/Config.groovy",
      "grails-app/mongo/**",
      "lib/hibernate*" //  see issue GRAILS-6341 NoClassDefFoundError: org/hibernate/mapping/Value when Hibernate not present
  ]

  def author = "Juri Kuehn"
  def authorEmail = "juri.kuehn at gmail.com"
  def title = "Grails MongoDB plugin"
  def description = '''GORM Layer for the superfast, highly scalable, schemafree, document oriented database MongoDB'''

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
    def morphia = ctx.getBean('mongo').morphia
    application.MongoDomainClasses.each { GrailsDomainClass domainClass ->
      if (!(domainClass instanceof MongoDomainClass)) return // process mongo domains only

      // add dynamic finders, validation, querying methods etc
      MongoPluginSupport.enhanceDomainClass(domainClass, application, ctx)

      // add domain class to mapper
      println "adding domain " + domainClass.getClazz() + " to morphia"
      morphia.map(domainClass.getClazz())
    }
  }

  /**
   * in order to reload the mongoclasses grails app has to be reloaded
   */
  def onChange = {event ->
    if (grails.plugins.mongodb.MongoDomainClassArtefactHandler.isMongoDomainClass(event.source)) {
      // reload needed, reregistering spring beans, enhancing domainclass, evaluating constraints etc
      log.info("MongoDB domain ${event.source} changed, reloading.")
      restartContainer()
    }
  }
}
