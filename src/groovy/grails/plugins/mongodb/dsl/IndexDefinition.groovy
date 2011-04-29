package grails.plugins.mongodb.dsl

protected class IndexDefinition {
  public String name
  public List fields = []
  public boolean unique
  public boolean dropDups
  public boolean sparse

  public String toString ( ) {
    return "IndexDefinition{" +
        "name='" + name + '\'' +
        ", fields=" + fields +
        ", unique=" + unique +
        ", dropDups=" + dropDups +
        '}' ;
  }
}
