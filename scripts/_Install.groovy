//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//

includeTargets << grailsScript("_GrailsClean")
includeTargets << grailsScript("_GrailsCompile")


// Because we make use of ASTTransformations, make sure that everything is clean
// after installing the plugin.
cleanAll()

// make the mongodb domains folder
ant.mkdir(dir:"${basedir}/grails-app/mongo")

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

