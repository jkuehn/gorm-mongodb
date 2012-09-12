package grails.plugins.mongodb.test

import org.acme.Contact
import org.acme.Project
import org.acme.Task
import org.bson.types.ObjectId
import com.mongodb.DBRef
import com.google.code.morphia.Key

public class BasicPersistenceTests extends GroovyTestCase {

  void testValidate() {
    def p = new Project(name: "")

    assertFalse "should not have validated", p.validate()
    assertNull "should not have saved", p.save()

    println p.errors.allErrors

    assertEquals "should have 1 error", p.errors.allErrors.size(), 1
    assertEquals "name should be in error", p.errors.allErrors[0].field, "name"

    p.delete()

    def p2 = Project.get(p.id)
    assertNull "project should be deleted", p2
  }

  void testSaveAndGet() {

    def p1 = new Project(name: "InConcert")

    p1.startDate = new Date()
    p1.pass = "transient test"

    p1.save()

    assertNotNull "should have saved new project", p1
    assertNotNull "should have retrieved id of new project", p1.id
    assertNotNull "should have set dateCreated", p1.dateCreated
    assertNotNull "should have set lastUpdated", p1.lastUpdated

    println "id from saved project is ${p1.id}"

    def p2 = Project.get(p1.id)

    assertNotNull "should have retrieved a project", p2
    assertEquals "project ids should be equal", p1.id, p2.id
    assertTrue "the project field 'pass' is transient and should not have been saved", p1.pass != p2.pass

    Thread.currentThread().sleep(200);

    p2.save()
    assertTrue "lastUpdated should be different", !p1.lastUpdated.equals(p2.lastUpdated)
    assertTrue "p2.lastUpdated should be after p1.lastUpdated", p1.lastUpdated.before(p2.lastUpdated)

    def t1 = new Task()
    t1.taskId = "${p2.id}-task"
    t1.name = "task"
    t1.projectId = p2.createKey()
    t1.startDate = new Date()
    t1.description = "This is the description."
    t1.estimatedHours = 5
    t1.pass = "transient test"

    t1.save()
    println "task save errors:"
    println t1.errors?.allErrors

    def t2 = Task.findByDescription(t1.description) // test dynamic finders
    assertNotNull "should have retrieved a task", t2
    assertTrue "the task field 'pass' is transient and should not have been saved", t1.pass != t2.pass

    p2.delete()
    t2.delete()
  }

  void testGetVariants() {
      def p1 = new Project(name: "InConcert2")
      p1.startDate = new Date()
      p1.save()

      assertNotNull "should have saved new project", p1
      assertNotNull "should have retrieved id of new project", p1.id

      DBRef dbRef = p1.createDBRef()
      Key key = p1.createKey()

      assertEquals "fetch using get() by DBRef should work", Project.get(dbRef)?.id, p1.id
      assertEquals "fetch using get() by Key should work", Project.get(key)?.id, p1.id
      assertEquals "fetch using get() by Id should work", Project.get(p1.id)?.id, p1.id

      p1.delete()
  }

  void testUpdateAndDelete() {
    def id = ObjectId.get()

    def p = Project.get(id)
    if (!p) {
      p = new Project()

      p.id = id
      p.startDate = new Date()
      p.name = "Test Project"

      println "project ${p.id} is new."

      p.save()
    } else {
      println "project ${p.id} was read."
    }

    p.save()

    assertNotNull "should have saved a project", p
    assertEquals "should have saved project with id = '${id}'", p.id, id

    p.delete()
  }

  void testAddToMethods() {
    def projectId = ObjectId.get()

    def c = new Contact()

    c.name = "Tom Jones 2"
    c.company = "Acme, Corp."
    c.save()

    def p = new Project(name: "TJ Project")
    p.addToTeammembers c
    p.save()

    assertNotNull "should have retrieved id of new project", p.id

    p = Project.get(p.id)
    assertNotNull "should have retrieved new project", p
    assertNotNull "should have added one teammember", p.teammembers

    p.delete()
    c.delete()
  }

  void testGetAllCountAll() {
    def companyName = "Acme, Corp."

    Contact.deleteAll([company: companyName])
    assertEquals "there should be no tested objects in collection", 0, Contact.count([company: companyName])

    def allCount = Contact.count()

    def c1 = new Contact(name: "Tom Jones 1", company: companyName)
    c1.save()
    assertNotNull "Contact 1 should have been saved", c1.id

    def c2 = new Contact(name: "Tom Jones 2", company: companyName)
    c2.save()
    assertNotNull "Contact 2 should have been saved", c2.id

    // test getAll
    def all = Contact.getAll([c1.id, c2.id])?.toList()
    println all
    assertEquals "getAll should fetch the two contacts", 2, all.size()
    assertTrue "getAll should contain contact 1", all*.id.contains(c1.id)
    assertTrue "getAll should contain contact 2", all*.id.contains(c2.id)

    assertEquals "countAll should count the saved objects", 2, Contact.count([company: companyName])

    assertEquals "list method should return 1 instance", 1, Contact.list(max:1).toList().size()
    assertEquals "list method should return 2 instances", 2, Contact.list(max:2).toList().size()

    def expectedInstanceCount = allCount + 2
    assertEquals "there should be ${expectedInstanceCount} instances", expectedInstanceCount, Contact.count()

    c1.delete()
    c2.delete()

    assertEquals "countAll should return 0", 0, Contact.count([company: companyName])
  }

  void testEvents() {
    def taskId = "manualTaskID1234"
    def t = new Task(taskId: taskId, name: "Testing events", description: "Here we are")

    // before validate
    boolean testValidate = false
    t.beforeValidate = {
      println "beforeValidate"
      testValidate = true
    }
    t.validate()
    assertTrue "beforeValidate should have been called", testValidate

    // before and after save test
    boolean testBeforeSave = false
    boolean testAfterSave = false
    testValidate = false
    t.beforeSave = {
      println "beforeSave"
      testBeforeSave = true
    }
    t.afterSave = {
      println "afterSave"
      testAfterSave = true
    }
    t.save()
    assertTrue "beforeValidate should have been called", testValidate
    assertTrue "beforeSave should have been called", testBeforeSave
    assertTrue "afterSave should have been called", testAfterSave

    // before and after delete test
    boolean testBeforeDelete = false
    boolean testAfterDelete = false
    t.beforeDelete = {
      println "beforeDelete"
      testBeforeDelete = true
    }
    t.afterDelete = {
      println "afterDelete"
      testAfterDelete = true
    }
    t.delete()
    assertTrue "beforeDelete should have been called", testBeforeDelete
    assertTrue "afterDelete should have been called", testAfterDelete
  }

//  void testRefresh() {
//    def c = new Contact(name: "Tom Jones", company: "Acme, Corp.")
//    c.save()
//
//    def c2 = Contact.get(c.id)
//    c2.name = 'Tommy Jones'
//    c2.save()
//
//    assertTrue "Names of contacts should differ", !c.name.equals(c2.name)
//
//    def x = c.refresh()
//    println x.name
//    assertEquals "Names of contacts should be equal", c.name, c2.name
//
//    c.delete()
//  }

  void testComplexObject() {
    def projectId = ObjectId.get()

    def c = new Contact()

    c.name = "Tom Jones"
    c.company = "Acme, Corp."
    c.save()

    assertNotNull "should have saved new contact", c
    assertNotNull "should have retrieved id of new contact", c.id

    def taskname = "TJ Task"
    def t = new Task(taskId: "manualTaskID", name: taskname, description: "Here we are")
    t.save()
    println "testComplexObject:task:"
    println t.errors?.allErrors
    assertFalse "task should save without errors", ((boolean)t.errors?.allErrors)

    def p = new Project(id: projectId, name: "TJ Project", manager: c, mainTask: t)
    p.save()
    assertNotNull "should have retrieved id of new project", p.id

    def p2 = Project.get(p.id)
    println p
    println p2
    assertEquals "project ids should be equal", p.id, p2?.id
    assertEquals "embedded task name should fit", p2.mainTask.name, taskname
    assertEquals "referenced contact id should be correct", p2.manager.id, c.id

    c.delete()
    p.delete()
  }

  void testRefQuery() {
    def c = new Contact()
    c.name = "Tom Jones"
    c.company = "Acme, Corp."
    c.save()
    assertNotNull "should have retrieved id of new contact", c.id

    def p = new Project(name:"Testproject---123", manager: c)
    p.save()
    assertNotNull "should have retrieved id of new project", p.id

    // fetch by contact
    def p2 = Project.find([manager: c])
    assertNotNull "should find project by contact", p2

    p.delete()
    c.delete()
  }

  void testUpdateMethod() {
    def t = new Task(taskId: "Update me good", name: "Task that will be updated!", actualHours: 10, dateCreated: new Date() - 1)
    def t2 = new Task(taskId: "Update me good 2", name: "Task that will be updated too!", actualHours: 10)
    t.save()
    t2.save()
    println "testUpdateMethod:task:"
    println t.errors?.allErrors

    t = Task.get(t.taskId) // get from db

    assertEquals "task should have the right actualHours value", 10, t.actualHours

    // update on instance
    t.update {
      inc "actualHours", 5
    }

    t = Task.get(t.taskId) // get from db
    assertNotNull "task should have been reretrieved from db", t
    assertNotNull "correct task should have been reretrieved from db", t.taskId
    assertEquals "task should have incremented actualHours value", 15, t.actualHours


    // updateFirst
    Task.updateFirst(['actualHours >=': 10]) { inc "actualHours", 1 }
    t = Task.get(t.taskId) // refresh from db
    t2 = Task.get(t2.taskId) // refresh from db
    assertEquals "updateFirst should update only one entity", 26, t.actualHours + t2.actualHours

    // update multiple
    Task.update(['actualHours >=': 10]) { inc "actualHours", 1 }
    t = Task.get(t.taskId) // refresh from db
    t2 = Task.get(t2.taskId) // refresh from db
    assertNotNull "task should have been reretrieved from db", t
    assertNotNull "correct task should have been reretrieved from db", t.taskId
    assertNotNull "task should have been reretrieved from db", t2
    assertNotNull "correct task should have been reretrieved from db", t2.taskId
    assertEquals "tasks should have incremented actualHours value", 28, t.actualHours + t2.actualHours
    println t
    println t2

    t.delete()
    t2.delete()
  }
}