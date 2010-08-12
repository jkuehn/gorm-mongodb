package org.acme

import com.google.code.morphia.annotations.Reference

class Project {

  String id

  String name
  Date startDate
  String frequency

  Date dateCreated
  Date lastUpdated

  @Reference
  Task mainTask

  @Reference
  Contact manager

  transient String pass = "pass"

  static constraints = {
    id nullable: true
    name blank: false
    startDate nullable: true
    frequency nullable: true
    manager nullable: true
    mainTask nullable: true
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
