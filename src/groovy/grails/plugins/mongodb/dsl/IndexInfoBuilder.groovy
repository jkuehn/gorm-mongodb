package grails.plugins.mongodb.dsl

import com.google.code.morphia.utils.IndexDirection
import com.google.code.morphia.utils.IndexFieldDef

/**
 * Evaluates index definitions in mongo domain classes
 */
class IndexInfoBuilder {

    def indexes = []
    def errors = []

    def methodMissing(String name, args) {
        if (args.size() < 1 || !(args[0] instanceof Map)) return

        def params = args[0]

        def id = new IndexDefinition()
        id.name = name
        id.unique = (boolean)params.unique?:false
        id.dropDups = (boolean)params.dropDups?:false

        if (params.fields instanceof Map) params.fields?.each { fieldname, sorting ->
            def sortingClean = IndexDirection.ASC // asc by default

            if (sorting instanceof Number) {
                if (sorting == -1) sortingClean = IndexDirection.DESC
            } else if (sorting instanceof String) {
                if (sorting.toLowerCase().equals("desc")) sortingClean = IndexDirection.DESC
            } else if (sorting instanceof IndexDirection) {
                sortingClean = sorting
            }

            id.fields << new IndexFieldDef(fieldname, sortingClean)
        }

        if (!id.fields) {
            errors << "Missing or malformed field definitions for index $name"
        } else {
            indexes << id
        }
    }
}