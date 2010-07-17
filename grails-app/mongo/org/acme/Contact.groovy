package org.acme

import grails.plugins.mongodb.MongoEntity
import com.google.code.morphia.annotations.Entity

@Entity(noClassnameStored = true)
@MongoEntity
class Contact {

  transient mongo // spring bean, dependency injection

  String name
  String company

  static constraints = {
    company nullable: true
  }

  static mapping = {
    name index: 'name_idx'
    company index: 'name_idx, company_idx'
  }

  public String toString ( ) {
    return "Contact{" +
        "id='" + id + '\'' +
        "name='" + name + '\'' +
        ", company='" + company + '\'' +
        '}' ;
  }
}


