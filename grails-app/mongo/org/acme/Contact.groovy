package org.acme

import grails.plugins.mongodb.MongoEntity
import com.google.code.morphia.annotations.Entity

@Entity
@MongoEntity
class Contact {

  transient mongo // spring bean, dependency injection

  String name
  String company

  static constraints = {
    company nullable: true
  }

  public String toString ( ) {
    return "Contact{" +
        "id='" + id + '\'' +
        "name='" + name + '\'' +
        ", company='" + company + '\'' +
        '}' ;
  }
}


