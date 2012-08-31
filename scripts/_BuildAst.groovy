/**
 * Build an ast transformations jar right away
 */

includeTargets << grailsScript("_GrailsClean")
includeTargets << grailsScript("_GrailsCompile")


// Because we make use of ASTTransformations, make sure that everything is clean
// after installing the plugin.
cleanAll()

/**
 * build ast jar
 */
def pluginDir = mongodbMorphiaPluginDir
if (pluginDir) {

    def mongoAstSrcDir = new File("${mongodbMorphiaPluginDir}/src/groovy/grails/plugins/mongodb/ast")
    def mongoAstBuildDir = new File(((String)grailsSettings.grailsVersion).startsWith("1")?grailsSettings.pluginClassesDir:grailsSettings.pluginBuildClassesDir, "ast")
    def mongoAstDestDir = new File(pluginDir, "lib")

    // create work dir
    ant.mkdir(dir:mongoAstBuildDir)

    // compile ast classes
    ant.groovyc(destdir: mongoAstBuildDir, encoding: "UTF-8", classpathref:"grails.compile.classpath") {
        src(path: mongoAstSrcDir)
    }

    // add service descriptor
    ant.copy(todir:new File(mongoAstBuildDir, 'META-INF')) {
        fileset dir:"${mongoAstSrcDir}/META-INF"
    }

    // package jar
    ant.jar(destfile: new File(mongoAstDestDir, 'mongodb-morphia-ast.jar'), basedir: mongoAstBuildDir)

    // cleanup
    ant.delete(dir:mongoAstBuildDir)

}


