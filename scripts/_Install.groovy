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

// Because we make use of ASTTransformations, make sure that everything is clean
// after installing the plugin.
cleanAll()

// make the mongodb domains folder
ant.mkdir(dir:"${basedir}/grails-app/mongo")