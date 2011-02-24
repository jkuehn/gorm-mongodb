package org.acme

import com.google.code.morphia.annotations.Reference
import org.bson.types.ObjectId
import com.google.code.morphia.annotations.Entity
import grails.plugins.mongodb.ast.TransformationConfiguration

@TransformationConfiguration(injectId = true)
class Project {

  String name = "You forgot to give me a name"
  Date startDate
  String frequency

  Date dateCreated
  Date lastUpdated

  @Reference
  Task mainTask // test references

  @Reference
  Contact manager

  @Reference
  List<Contact> teammembers

  transient String pass = "pass"

  static constraints = {
    id nullable: true
    name blank: false
    startDate nullable: true
    frequency nullable: true
    manager nullable: true
    mainTask nullable: true
    teammembers nullable: true
  }

  public String toString ( ) {
    return "Project{" +
        "id='" + id + '\'' +
        ", name='" + name + '\'' +
        ", startDate=" + startDate +
        ", frequency='" + frequency + '\'' +
        ", dateCreated=" + dateCreated +
        ", lastUpdated=" + lastUpdated +
        ", mainTask=" + mainTask +
        ", manager=" + manager +
        ", pass='" + pass + '\'' +
        '}' ;
  }}
