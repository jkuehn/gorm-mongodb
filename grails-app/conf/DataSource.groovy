def currentHost = java.net.InetAddress.getLocalHost().getHostName().toLowerCase()

mongodb {
    if (currentHost == 'rem-juri') {
        replicaSet = [ "localhost:27017"]
    } else if (System.getProperty('user.name').toLowerCase() == 'juri') {
        replicaSet = [ "192.168.1.101:27017"]
    } else {
        replicaSet = [ "localhost:27017"]
    }
//  host = 'lbserver'
    //  port = 27017
    databaseName = 'gorm_test'
}


dataSource {
    pooled = true
//    driverClassName = "org.hsqldb.jdbcDriver"
//    driverClassName = "org.h2.Driver"
    username = "sa"
    password = ""
}
hibernate {
    cache.use_second_level_cache=true
    cache.use_query_cache=true
    cache.provider_class='net.sf.ehcache.hibernate.EhCacheProvider'
}
// environment specific settings
environments {
    development {
        dataSource {
            dbCreate = "create-drop" // one of 'create', 'create-drop','update'
//            url = "jdbc:hsqldb:mem:devDB"
        }
    }
    test {
        dataSource {
            dbCreate = "update"
//            url = "jdbc:hsqldb:mem:testDb"
        }
    }
    production {
        dataSource {
            dbCreate = "update"
//            url = "jdbc:hsqldb:file:prodDb;shutdown=true"
        }
    }
}