package grails.plugins.mongodb.dsl

import com.google.code.morphia.utils.IndexDirection
import com.google.code.morphia.utils.IndexFieldDef

/**
 * Evaluates index definitions in mongo domain classes
 *
 * changes to initial proposal:
 * * "columns" changed to "fields"
 * * fields defined as list, not as map, where the order is not perserved
 */
class IndexInfoBuilder {

  def indexes = []
  def errors = []

  def asc = { String field ->
    __createIndexField(field, IndexDirection.ASC)
  }

  def desc = { String field ->
    __createIndexField(field, IndexDirection.DESC)
  }

  private __createIndexField(String name, IndexDirection direction) {
    new IndexFieldDef(name, direction)
  }

  def methodMissing(String name, args) {
    if (args.size() < 1 || !(args[0] instanceof Map)) return

    def params = args[0]

    def id = new IndexDefinition()
    id.name = name
    id.unique = (boolean)params.unique?:false
    id.dropDups = (boolean)params.dropDups?:false
    id.sparse = (boolean)params.sparse?:false

    if (params.fields instanceof List) params.fields.each { field ->
      if (!field) return // dont bother

      if (!(field instanceof IndexFieldDef)) { // sorting direction defaults to ASC
        field = __createIndexField(field.toString(), IndexDirection.ASC)
      }

      id.fields << field
    }

    if (!id.fields) {
      errors << "Missing or malformed field definitions for index $name"
    } else {
      indexes << id
    }
  }
}