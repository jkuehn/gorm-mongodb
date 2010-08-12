includeTargets << grailsScript("_GrailsClean")

// myPluginDir
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