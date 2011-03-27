package org.acme.inheritance

import com.google.code.morphia.annotations.Entity

@Entity("Person")
class User extends Person {

  String role

}
