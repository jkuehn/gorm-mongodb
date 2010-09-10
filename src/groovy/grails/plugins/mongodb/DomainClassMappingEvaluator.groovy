package grails.plugins.mongodb

/**
 * Evaluates the mapping DSL closure on domain classes
 *
 * @author: Juri Kuehn
 */
@Deprecated
class DomainClassMappingEvaluator {

    def indices = [:]

    def methodMissing(String name, args) {
        if (args.size() < 1 || !(args[0] instanceof Map)) return

        // extract index names
        if (args[0].containsKey('index')) {
            def idx = args[0].index
            if (idx instanceof String || idx instanceof GString) {
                def defIndices = idx.tokenize(',')*.trim()

                for (i in defIndices) {
                    if (!indices.containsKey(i)) indices[i] = []
                    indices[i] << name
                }
            }
        }
    }
}