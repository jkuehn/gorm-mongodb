package org.acme

import com.google.code.morphia.annotations.Entity
import com.google.code.morphia.utils.AbstractMongoEntity

@Entity
class Contact extends AbstractMongoEntity {

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


