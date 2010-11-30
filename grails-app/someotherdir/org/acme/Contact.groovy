package org.acme

import com.google.code.morphia.annotations.Entity

/**
 * For testing domain classes outside of grails-app/mongo directory
 */
@Entity(noClassnameStored = false)
class Contact {

  // autoinjected id

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


