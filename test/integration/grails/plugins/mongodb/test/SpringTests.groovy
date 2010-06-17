package grails.plugins.mongodb.test

import org.acme.Contact

/**
 * @author: Juri Kuehn
 */
class SpringTests extends GroovyTestCase {

  void testBeanAutowiring() {

    def m = new Contact()
    assertNotNull "mongo instance should be autowired to class created with new", m.mongo

    m = new Contact(name:"joe")
    assertNotNull "mongo instance should be autowired to class created with new with parameters", m.mongo

    m.save()
    assertNotNull "contact instance should have been saved", m.id

    def m2 = Contact.get(m.id)
    assertNotNull "contact should be fetched by get", m2
    assertNotNull "mongo instance should be autowired to class fetched with get", m2.mongo

    m2 = Contact.find([name:"joe"]) // uses internally findAll
    assertNotNull "contact should be fetched by find", m2
    assertNotNull "mongo instance should be autowired to class fetched with find", m2.mongo

    // @todo add findAll test as soon as autowiring works

    m.delete()
  }

}
