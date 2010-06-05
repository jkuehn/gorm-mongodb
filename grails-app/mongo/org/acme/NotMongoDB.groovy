package org.acme

class NotMongoDB {

  String name
  Integer age
  Date lastUpdated

  static constraints = {
    name blank: false
  }

  public String toString ( ) {
    return "NotMongoDB{" +
        "name='" + name + '\'' +
        ", age=" + age +
        ", lastUpdated=" + lastUpdated +
        '}' ;
  }}
