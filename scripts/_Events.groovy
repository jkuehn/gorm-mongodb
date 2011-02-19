import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler

includeTargets << grailsScript("_GrailsClean")

def mongoAstPath = "${mongodbMorphiaPluginDir}/src/groovy/grails/plugins/mongodb/ast"
def mongoAstDest = "${projectWorkDir}/ast/gorm-mongodb"

eventCleanStart = {
        ant.delete(dir:mongoAstDest)
}

eventCompileStart = {
        ant.mkdir(dir:"${mongoAstDest}/META-INF")
        ant.groovyc(destdir: mongoAstDest,
                                encoding: "UTF-8") {
                        src(path: mongoAstPath)
//                        src(path: "${mongoAstPath}/java")
//                        javac() // to compile java classes using the javac compiler
        }

        ant.copy(todir:"${mongoAstDest}/META-INF") {
                        fileset dir:"${mongoAstPath}/META-INF"
        }

        grailsSettings.compileDependencies << new File(mongoAstDest)
        classpathSet=false
        classpath()
}


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
