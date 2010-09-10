mongodb {
  host = '192.168.1.36'
  port = 27017
  database = 'gorm_test'
}
// The following properties have been added by the Upgrade process...
grails.views.default.codec="none" // none, html, base64
grails.views.gsp.encoding="UTF-8"

// + Hubert Chang
grails.doc.authors = "Juri Kuehn"
grails.doc.license = "Apache License"


log4j = {
    appenders {
      rollingFile name:"errorFile", file:"./target/err.log"
      rollingFile name:"infoFile", file:"./target/info.log"
    }
    root {
      additivity = false

      error 'errorFile'
      warn 'errorFile'
      info 'infoFile'
//      debug 'errorFile'
    }
    error  'org.codehaus.groovy.grails.web.servlet',  //  controllers
           'org.codehaus.groovy.grails.web.pages', //  GSP
           'org.codehaus.groovy.grails.web.sitemesh', //  layouts
           'org.codehaus.groovy.grails."web.mapping.filter', // URL mapping
           'org.codehaus.groovy.grails."web.mapping', // URL mapping
           'org.codehaus.groovy.grails.commons', // core / classloading
           'org.codehaus.groovy.grails.plugins', // plugins
           'org.codehaus.groovy.grails.orm.hibernate', // hibernate integration
           'org.springframework',
           'org.hibernate'
//    debug 'org.hibernate'
//    debug 'org.hibernate.SQL'
//       debug 'org.codehaus.groovy.grails.plugins.searchable'
//       trace 'org.compass'
    info 'grails.plugins.mongodb'
}