package grails.plugins.mongodb.test

import org.acme.inheritance.*

public class InheritanceTests extends GroovyTestCase {

  void testFilterSubclasses() {

    Person.deleteAll()

    def p = new Person()
    p.name = "Max"
    p.save()

    p = new Person()
    p.name = "Bob"
    p.save()

    def u = new User()
    u.name = "Shrek"
    u.role = "Oger"
    u.save()

  }

}