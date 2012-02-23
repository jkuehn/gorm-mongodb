import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

/**
 * Make generate-* work
 */
def doinGenerateAction = false // hacky hack
def tweakForGenerate = {
    doinGenerateAction = true
}
// use following two events to mark that we are currently generating
eventUberGenerateStart = tweakForGenerate
eventGenerateForOneStart = tweakForGenerate

/**
 * register artefacts as Domains here, so that we are modifying the same
 * grailsApp that is used to find domainClasses for generation
 */
eventConfigureAppEnd = {
    if (doinGenerateAction) {
        grailsApp.MongoDomainClasses.each {
            grailsApp.addArtefact(DomainClassArtefactHandler.TYPE, it)
        }
    }
}
