package grails.plugins.mongodb.test

import com.mongodb.BasicDBObjectBuilder
import com.mongodb.BasicDBObject
import com.google.code.morphia.Datastore
import org.acme.Project
import org.acme.Task
import com.google.code.morphia.query.Query

/**
 * tests the mongo bean and access to the wired mongodb driver
 *
 * @author: Juri Kuehn
 */
class LowLevelTests extends GroovyTestCase {

  def mongo

  void testLowLevelAccess() {
    String testedCollection = "unlikelyToHaveThatBrrrr"

    println mongo.db.collectionNames

    // put one object into a new collection
    BasicDBObjectBuilder dbb = new BasicDBObjectBuilder();
    dbb.add("field1", 1343)
       .add("field2", "hoho")
       .push("submap").add("sub1", 1).add("sub2", 2);
    mongo.db.getCollection(testedCollection).insert(dbb.get());

    // test this collection
    assertEquals "tested collection should have 1 element", 1, mongo.db.getCollection(testedCollection).count
    assertNotNull "inserted element should be in db", mongo.db.getCollection(testedCollection).findOne(new BasicDBObject("field2", "hoho"))

    // remove it
    mongo.db.getCollection(testedCollection).drop()
    assertFalse "tested collection should have been removed", mongo.db.collectionNames.contains(testedCollection)

    // test returned instance
    assertTrue "mongo.mongo should return a Mongo instance", (mongo.mongo instanceof com.mongodb.Mongo)
    assertTrue "mongo.db should return a DB instance", (mongo.db instanceof com.mongodb.DB)
    assertTrue "mongo.morphia should return a Morphia instance", (mongo.morphia instanceof com.google.code.morphia.Morphia)
    assertTrue "mongo.datastore should return a Datastore instance", (mongo.datastore instanceof com.google.code.morphia.Datastore)
  }

  void testSaveAndDelete() {
    Datastore ds = mongo.datastore

    def p = new Project(name: "Testprojekt")
    def t = new Task(name: "Testtask")

    ds.save(p)
    ds.save(t)

    println p
    println t

    assertNotNull "datastore should return project ", ds.get(Project.class, p.id)
    assertNotNull "datastore should return task ", ds.get(Task.class, t.taskId)

    ds.delete(p)
    ds.delete(t)

    assertNull "datastore should have deleted project ", ds.get(Project.class, p.id)
    assertNull "datastore should have deleted task ", ds.get(Task.class, t.taskId)
  }

}
