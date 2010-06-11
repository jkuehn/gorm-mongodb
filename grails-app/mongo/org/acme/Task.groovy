package org.acme

import com.google.code.morphia.annotations.Entity
import com.google.code.morphia.annotations.Id
import com.google.code.morphia.annotations.Transient
import grails.plugins.mongodb.MongoEntity

@MongoEntity
class Task {

  @Id
  String taskId

  String projectId
  String name

  Date startDate
  Date completionDate
  Integer estimatedHours
  Integer actualHours

  String description

  Date dateCreated
  Date lastUpdated

  int version

  transient String pass = "pass"

  static constraints = {
    projectId blank: true
    description nullable: true
    name blank: false
    actualHours nullable: true
    startDate nullable: true
    completionDate nullable: true
  }

  def beforeSave = {
//    println "Task before save: $taskId"
  }

  def afterSave = {
//    println "Task after save: $taskId"
  }

  def beforeDelete = {
//    println "Task before delete: $taskId"
  }

  def afterDelete = {
//    println "Task after delete: $taskId"
  }


  public String toString ( ) {
    return "Task{" +
        "taskId='" + taskId + '\'' +
        ", projectId='" + projectId + '\'' +
        ", name='" + name + '\'' +
        ", startDate=" + startDate +
        ", completionDate=" + completionDate +
        ", estimatedHours=" + estimatedHours +
        ", actualHours=" + actualHours +
        ", description='" + description + '\'' +
        ", dateCreated=" + dateCreated +
        ", lastUpdated=" + lastUpdated +
        ", pass='" + pass + '\'' +
        '}' ;
  }}
