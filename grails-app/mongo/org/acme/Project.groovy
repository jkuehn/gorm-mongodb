package org.acme

class Project {
	String id
  String name
  Date startDate
  String frequency

  Date dateCreated
  Date lastUpdated

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
