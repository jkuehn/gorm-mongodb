package org.acme
class Task {
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
  
	static indexes = {
		idx_date unique:true, dropDups:false, columns:[dateCreated:"asc", lastUpdated:"desc"]
		idx_project colums:[projectId:'asc']
	}
	
  static constraints = {
    projectId blank: true
    description nullable: true
    name blank: false
    actualHours nullable: true
    estimatedHours nullable: true
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
