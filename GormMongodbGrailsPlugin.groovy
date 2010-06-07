import grails.plugins.mongodb.MongoDomainClassArtefactHandler
import grails.plugins.mongodb.MongoPluginSupport
import org.springframework.context.ApplicationContext
import grails.plugins.mongodb.MongoDomainClass
import org.springframework.beans.factory.config.MethodInvokingFactoryBean
import org.codehaus.groovy.grails.validation.GrailsDomainClassValidator

class GormMongodbGrailsPlugin {
  // the plugin version
  def version = "0.1.1"
  // the version or versions of Grails the plugin is designed for
  def grailsVersion = "1.3 > *"
  // the other plugins this plugin depends on
  def dependsOn = [core: '1.3 > *']

  def loadAfter = ['core', 'domainClass']

  // resources that are excluded from plugin packaging
  def pluginExcludes = [
      "grails-app/views/error.gsp",
      "grails-app/controllers/**", // they exist only for testing purposes
      "grails-app/conf/Config.groovy",
      "grails-app/mongo/**",
      "lib/hibernate*" //  see issue GRAILS-6341 NoClassDefFoundError: org/hibernate/mapping/Value when Hibernate not present
  ]

  // TODO Fill in these fields
  def author = "Juri Kuehn"
  def authorEmail = "juri.kuehn at gmail.com"
  def title = "Grails MongoDB plugin"
  def description = '''GORM Layer for the superfast, highly scalable, schemafree, document oriented database MongoDB'''

  // URL to the plugin's documentation
  def documentation = "http://grails.org/plugin/gorm-mongodb"

  def artefacts = [grails.plugins.mongodb.MongoDomainClassArtefactHandler]

  def watchedResources = [
      'file:./grails-app/mongo/**',
  ]

  def doWithSpring = { ApplicationContext ctx ->
    // register mongo domains as beans
    application.MongoDomainClasses.each { MongoDomainClass dc ->

      // Note the use of Groovy's ability to use dynamic strings in method names!
      "${dc.fullName}"(dc.getClazz()) {bean ->
        bean.singleton = false
        bean.autowire = "byName"
      }

      "${dc.fullName}DomainClass"(MethodInvokingFactoryBean) {
        targetObject = ref("grailsApplication", true)
        targetMethod = "getArtefact"
        arguments = [MongoDomainClassArtefactHandler.TYPE, dc.fullName]
      }

      "${dc.fullName}PersistentClass"(MethodInvokingFactoryBean) {
        targetObject = ref("${dc.fullName}DomainClass")
        targetMethod = "getClazz"
      }

      "${dc.fullName}Validator"(GrailsDomainClassValidator) {
        messageSource = ref("messageSource")
        domainClass = ref("${dc.fullName}DomainClass")
        grailsApplication = ref("grailsApplication", true)
      }
    }
  }

  def doWithDynamicMethods = { ApplicationContext ctx ->
    application.MongoDomainClasses.each { MongoDomainClass domainClass ->
      // add dynamic finders, validation, querying methods etc
      MongoPluginSupport.enhanceDomainClass(domainClass, application, ctx)
    }
  }

  /**
   * in order to reload the mongoclasses grails app has to be reloaded
   */
  def onChange = {event ->
    if (grails.plugins.mongodb.MongoDomainClassArtefactHandler.isMongoDomainClass(event.source)) {
      // reload needed, reregistering spring beans, enhancing domainclass, evaluating constraints etc
      log.info("MongoDB domain ${event.source} changed, reloading.")
      application.rebuild()
    }
  }
}
