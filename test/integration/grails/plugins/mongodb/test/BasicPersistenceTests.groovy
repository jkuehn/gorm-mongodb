package grails.plugins.mongodb.test

import org.acme.Contact
import org.acme.Project
import org.acme.Task
import org.bson.types.ObjectId

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
    t1.projectId = p2.makeKey()
    t1.startDate = new Date()
    t1.description = "This is the description."
    t1.estimatedHours = 5
    t1.pass = "transient test"

    t1.save()
    println "task save errors:"
    println t1.errors?.allErrors

    def t2 = Task.findOneByDescription(t1.description) // test dynamic finders
    assertNotNull "should have retrieved a task", t2
    assertTrue "the task field 'pass' is transient and should not have been saved", t1.pass != t2.pass

    p2.delete()
    t2.delete()
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
    def t = new Task(taskId: "Update me good", name: "Task that will be updated!", actualHours: 10)
    t.save()
    println "testUpdateMethod:task:"
    println t.errors?.allErrors

    t = Task.get(t.taskId) // get from db

    assertEquals "task should have the right actualHours value", 10, t.actualHours

    t.update {
      inc "actualHours", 5
    }

    t = Task.get(t.taskId) // get from db
    assertNotNull "task should have been reretrieved from db", t
    assertNotNull "correct task should have been reretrieved from db", t.taskId
    assertEquals "task should have incremented actualHours value", 15, t.actualHours

    t.delete()
  }
}