package grails.plugins.mongodb.test

import org.acme.Task
import com.google.code.morphia.Key
import org.acme.Project
import org.bson.types.ObjectId
import com.mongodb.BasicDBObject
import com.mongodb.DBCollection

/**
 * test the dynamic finders
 *
 * @author Juri Kuehn
 */
class StaticMethodsTests extends GroovyTestCase {

  def projectId = new Key(Project.class, ObjectId.get())
  def taskList

  void testDynamicFinders() {
    assertEquals "findByProjectId should find all testobjects", Task.findAllByProjectId(projectId)*.taskId.sort(), taskList*.taskId.sort()
    assertEquals "subsequent call to now cached findByProjectId should find all testobjects", Task.findAllByProjectId(projectId)*.taskId.sort(), taskList*.taskId.sort()

    // test sorting
    def found = Task.findAllByProjectIdAndNameGreaterThan(projectId, "Sim", [sort: "estimatedHours"]).toList()
    println "Task List:"
    taskList.each { println it.toString() }
    println "Found entities: "
    found.each { println it.toString() }

    assertEquals "Task 1 should be at 0 place in sorted result", found[0].taskId, taskList[0].taskId
    assertEquals "Task 4 should be at 1 place in sorted result", found[1].taskId, taskList[3].taskId
    assertEquals "Task 2 should be at 2 place in sorted result", found[2].taskId, taskList[1].taskId
  }

  void testStaticMethods() {
    assertEquals "findAll by projectId should find all testobjects", Task.findAll([projectId: projectId])*.taskId.sort(), taskList*.taskId.sort()
    assertEquals "find should find the searched task", Task.find(["name >": "S"], [sort: "-estimatedHours"])?.taskId, taskList[1].taskId
  }

  /**
   * test deleteOne and deleteAll
   */
  void testStaticDeleteMethods() {

    assertEquals "Database should contain tested objects (1)", taskList.size(), Task.findAll([projectId: projectId]).size()

    // deleteOne
    Task.deleteOne(taskList[0].taskId)

    assertEquals "Database should contain tested objects (2)", taskList.size()-1, Task.findAll([projectId: projectId]).size()

    println "before delete: " + Task.findAll([projectId: projectId]).toList()

    // deleteAll by ids list
    def toDel = [taskList[1].taskId, taskList[2].taskId]
    Task.deleteAll(toDel)

    println "after delete: " + Task.findAll([projectId: projectId]).toList()

    assertEquals "Database should contain tested objects (3)", taskList.size()-3, Task.findAll([projectId: projectId]).size()

    setUp() // get the objects into db again
    assertEquals "Database should contain tested objects (4)", taskList.size(), Task.findAll([projectId: projectId]).size()

    // deleteAll by query
    Task.deleteAll(["name >": "Sim"]) // should match 2 projects
    assertEquals "Database should contain tested objects (5)", taskList.size()-3, Task.findAll([projectId: projectId]).size()
  }

  void testStaticUpdateMethods() {
    assertEquals "Database should contain tested objects (1)", taskList.size(), Task.findAll([projectId: projectId]).size()

    println "manipulating " + taskList[0]
    // update by id
    def prevVal = taskList[0].estimatedHours
    Task.update(taskList[0].taskId, {
      inc 'estimatedHours'
    })
    def curTask = Task.get(taskList[0].taskId)
    println "manipulated " + curTask
    assertEquals "estimatedHours should have been updated", prevVal+1, curTask.estimatedHours


    println "manipulating " + taskList[1]
    // update by query
    prevVal = taskList[1].estimatedHours
    Task.update([estimatedHours: prevVal], {
      inc 'estimatedHours'
    })
    curTask = Task.get(taskList[1].taskId)
    println "manipulated " + curTask
    assertEquals "estimatedHours should have been updated", prevVal+1, curTask.estimatedHours
  }

  void testCollectionAccess() {
    String pName = "******TestProject12345567890"

    assert (Project.collection instanceof DBCollection)

    Project.collection.insert(new BasicDBObject('name', pName))

    def p = Project.findOneByName(pName)

    assertNotNull "should find project", p

    println "found manually inserted project: " + p

    p.delete()
  }

  /**
   * create 4 tasks to mess around with
   */
  void setUp() {
    Task.deleteAll([projectId: projectId])
    def taskCount = 1
    taskList = []
    taskList << new Task(taskId: "testTask" + (taskCount++), name: "Simple Task 1", estimatedHours: 10, projectId: projectId)   // 0
    taskList << new Task(taskId: "testTask" + (taskCount++), name: "Simple Task 2", estimatedHours: 100, projectId: projectId)  // 1
    taskList << new Task(taskId: "testTask" + (taskCount++), name: "Complex Task", estimatedHours: 30, projectId: projectId)    // 2
    taskList << new Task(taskId: "testTask" + (taskCount++), name: "Simulated Task", estimatedHours: 20, projectId: projectId)  // 3

    taskList*.save()

    // do something else here, id is always set - maybe count by projectId
    assertNotNull "task 1 should save successfully", taskList[0].taskId
    assertNotNull "task 2 should save successfully", taskList[1].taskId
    assertNotNull "task 3 should save successfully", taskList[2].taskId
    assertNotNull "task 4 should save successfully", taskList[3].taskId
  }

  /**
   * remove those tasks
   * @return
   */
  void tearDown() {
    Task.deleteAll([projectId: projectId])

    assertEquals "all tasks should been removed", Task.findAll([projectId: projectId]).size(), 0
  }
}
