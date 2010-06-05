package grails.plugins.mongodb.test

import org.acme.Task
import com.google.code.morphia.Datastore
import com.google.code.morphia.DatastoreImpl

/**
 * test the dynamic finders
 *
 * @author Juri Kuehn
 */
class StaticMethodsTests extends GroovyTestCase {

  def projectId = "UnnrealProject------"
  def taskList

  void testDynamicFinders() {
    assertEquals "findByProjectId should find all testobjects", Task.findByProjectId(projectId)*.taskId, taskList*.taskId
    assertEquals "subsequent call to now cached findByProjectId should find all testobjects", Task.findByProjectId(projectId)*.taskId, taskList*.taskId

    // test sorting
    def found = Task.findByProjectIdAndNameGreaterThan(projectId, "Sim", [sort: "estimatedHours"]).toList()
    println "Task List:"
    taskList.each { println it.toString() }
    println "Found entities: "
    found.each { println it.toString() }

    assertEquals "Task 1 should be at 0 place in sorted result", found[0].taskId, taskList[0].taskId
    assertEquals "Task 4 should be at 1 place in sorted result", found[1].taskId, taskList[3].taskId
    assertEquals "Task 2 should be at 2 place in sorted result", found[2].taskId, taskList[1].taskId
  }

  void testStaticMethods() {

    assertEquals "findAll by projectId should find all testobjects", Task.findAll([projectId: projectId])*.taskId, taskList*.taskId
    assertEquals "find should find the searched task", Task.find(["name >": "Simp"], [sort: "-estimatedHours"])?.taskId, taskList[1].taskId
  }

  void testDatastore() {
    Datastore ds = Task.getDatastore()

    def t = ds.get(Task.class, taskList[0].taskId)
    println t.toString()

    assertEquals "datastore returned the correct task object", t.taskId, taskList[0].taskId
  }

  /**
   * create 4 tasks to mess around with
   */
  void setUp() {
    Task.delete([projectId: projectId])
    taskList = []
    taskList << new Task(name: "Simple Task 1", estimatedHours: 10, projectId: projectId)   // 0
    taskList << new Task(name: "Simple Task 2", estimatedHours: 100, projectId: projectId)  // 1
    taskList << new Task(name: "Complex Task", estimatedHours: 30, projectId: projectId)    // 2
    taskList << new Task(name: "Simulated Task", estimatedHours: 20, projectId: projectId)  // 3

    taskList*.save()

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
    assertTrue "at least 4 tasks should be in the db", Task.count() >= 4

    Task.delete([projectId: projectId])

    assertEquals "all tasks should been removed", Task.count(), 0
  }
}
