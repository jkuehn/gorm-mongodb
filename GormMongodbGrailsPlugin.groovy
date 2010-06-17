import grails.plugins.mongodb.MongoPluginSupport
import org.springframework.context.ApplicationContext
import grails.plugins.mongodb.MongoDomainClass
import grails.plugins.mongodb.MongoHolderBean
import org.codehaus.groovy.grails.commons.GrailsDomainClass

class GormMongodbGrailsPlugin {
  // the plugin version
  def version = "0.2.1"
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

  // TODO Fill in these fields
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

    // mongo domain classes are registered as beans by the domainClass plugin
  }

  def doWithDynamicMethods = { ApplicationContext ctx ->
    def morphia = ctx.getBean('mongo').morphia
    application.domainClasses.each { GrailsDomainClass domainClass ->
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
