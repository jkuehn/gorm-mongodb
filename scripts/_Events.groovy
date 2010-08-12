import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import grails.plugins.mongodb.MongoDomainClassArtefactHandler

includeTargets << grailsScript("_GrailsClean")

def mongoAstPath = "${gormMongodbPluginDir}/src/groovy/grails/plugins/mongodb/ast"
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
def tweakForGenerate = {
  // do it the dirty way
  MongoDomainClassArtefactHandler.TYPE = DomainClassArtefactHandler.TYPE

  // registering artefacts doesnt work, because a new grailsapplication instance is constructed in generate
//  loadApp()
//  grailsApp.MongoDomainClasses.each {
//    println it
//    grailsApp.addArtefact(DomainClassArtefactHandler.TYPE, new MongoDomainClass(it.clazz))
//  }
}

eventUberGenerateStart = tweakForGenerate

eventGenerateForOneStart = tweakForGenerate

