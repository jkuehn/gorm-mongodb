package grails.plugins.mongodb.ast

import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.control.CompilePhase
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Id
import com.google.code.morphia.annotations.Version
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.PropertyNode
import org.apache.commons.lang.StringUtils
import com.google.code.morphia.annotations.Entity
import java.lang.reflect.Modifier
import com.google.code.morphia.annotations.Transient
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.ast.ClassHelper
import org.bson.types.ObjectId
import org.codehaus.groovy.ast.expr.ConstantExpression

/**
 *
 * @author: Juri Kuehn
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class MongoDomainASTTransformation implements ASTTransformation {
  private static final Log log = LogFactory.getLog(MongoDomainASTTransformation.class)

  private static final String IDENTITY = GrailsDomainClassProperty.IDENTITY
  private static final String VERSION = GrailsDomainClassProperty.VERSION

  private static final ClassNode MORPHIA_ENTITY = new ClassNode(Entity)

  private static final ClassNode MORPHIA_ID = new ClassNode(Id)
  private static final ClassNode MORPHIA_VERSION = new ClassNode(Version)
  private static final ClassNode MORPHIA_TRANSIENT = new ClassNode(Transient)

  private static final ClassNode MORPHIA_EMBEDDED = new ClassNode(Embedded)

  private static final ClassNode STRING_TYPE = new ClassNode(String)
  private static final ClassNode LONG_TYPE = ClassHelper.long_TYPE

  public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
    if (nodes.length != 1 || !(nodes[0] instanceof ModuleNode)) {
      throw new RuntimeException("Internal error: expecting [ModuleNode] but got: " + Arrays.asList(nodes))
    }

    // process all classes within grails-app/mongo
    boolean isMongoDir = false
    if (sourceUnit.name =~ /grails-app.mongo/) {
      isMongoDir = true
    }
    nodes[0].getClasses().each { ClassNode owner ->
      if (!isMongoDir && !owner.getAnnotations(MORPHIA_ENTITY)) return // do not process this class
      AnnotationNode configNode = getTransformationConfiguration(owner)

      if (isAnnotationMemberTrue(configNode, 'injectEntityType')) injectEntityType(owner)
      if (isAnnotationMemberTrue(configNode, 'injectId')) injectIdProperty(owner)
      if (isAnnotationMemberTrue(configNode, 'injectVersion')) injectVersionProperty(owner)
      if (isAnnotationMemberTrue(configNode, 'annotateTransients')) annotateTransients(owner)
      if (isAnnotationMemberTrue(configNode, 'annotateClosuresAsTransients')) annotateClosureAsTransients(owner)
    }
  }

  private AnnotationNode getTransformationConfiguration(ClassNode classNode) {
    def annotationNodes = classNode.getAnnotations(new ClassNode(TransformationConfiguration))
    if (annotationNodes.size() < 1) return null

    annotationNodes[0]
  }

  private boolean isAnnotationMemberTrue(AnnotationNode configNode, String memberName) {
    if (!configNode) return true
    def member = configNode.getMember(memberName)

    return (!member || member.isTrueExpression())
  }

  private void injectEntityType(ClassNode classNode) {
    // annotate with morphias Entity if not already the case
    if (classNode.getAnnotations(MORPHIA_ENTITY).size() > 0) return; // already annotated

    AnnotationNode entityAN = new AnnotationNode(MORPHIA_ENTITY)
    entityAN.setMember('noClassnameStored', ConstantExpression.TRUE) // by default, do not store class name
    classNode.addAnnotation(entityAN)
  }

  private void injectIdProperty(ClassNode classNode) {
	if (classNode.getAnnotations(MORPHIA_EMBEDDED).size() > 0) return //shouldn't inject id for embedded objects

    if (classNode.fields.findAll({ it.getAnnotations(MORPHIA_ID) }).size() > 0) {
      // there is an id annotation already, nothing to do for us
      return
    }

    // if superclass has an id property, then skip injecting onto this one
    ClassNode superClass = classNode.getSuperClass()

    //loop through superclasses to make sure no ancestors have the id property
    while (superClass) {
      if(superClass.fields.findAll({ it.getAnnotations(MORPHIA_ID) }).size() > 0) return
      superClass = superClass.getSuperClass()
    }

    // annotate node id if present, otherwise inject id property
    PropertyNode identity = getProperty(classNode, IDENTITY)

    if (!identity) { // there is no id property at all
      log.debug("Adding property [" + IDENTITY + "] to class [" + classNode.getName() + "]")
      identity = classNode.addProperty(IDENTITY, Modifier.PUBLIC, new ClassNode(ObjectId), null, null, null)
    }

    // now add annotation
    identity.getField().addAnnotation(new AnnotationNode(MORPHIA_ID))
  }

  /**
   * add @Transient annotation on transient fields
   * @param classNode
   */
  private void annotateTransients(ClassNode classNode) {
    def transients = classNode.getFields().findAll { FieldNode f -> f.getModifiers() & Modifier.TRANSIENT }

    transients.each { FieldNode f ->
      if (f.getAnnotations(MORPHIA_TRANSIENT).size() > 0) return // this one is annotated already

      f.addAnnotation(new AnnotationNode(MORPHIA_TRANSIENT))
    }
  }

  private void injectVersionProperty(ClassNode classNode) {
    if (classNode.fields.findAll({ it.getAnnotations(MORPHIA_VERSION) }).size() > 0) {
      // there is an version annotation already, nothing to do for us
      return
    }

    // annotate node if present, otherwise inject version property
    PropertyNode version = getProperty(classNode, VERSION)

    if (!version) { // there is no version property at all - inject one for compatibility reasons
      log.debug("Adding property [" + VERSION + "] to class [" + classNode.getName() + "]")
      version = classNode.addProperty(VERSION, Modifier.PUBLIC, LONG_TYPE, null, null, null)
      // set it transient, because it is unwanted anyway
      version.getField().setModifiers(Modifier.TRANSIENT)
      version.getField().addAnnotation(new AnnotationNode(MORPHIA_TRANSIENT))
    } else {
      // add annotation if field already there
      version.getField().addAnnotation(new AnnotationNode(MORPHIA_VERSION))
      // version must be long
      if (version.type.typeClass != Long.class) {
        log.warn("Changing the type of property [" + VERSION + "] of class [" + classNode.getName() + "] to Long.")
        version.field.type = LONG_TYPE
      }
    }
  }

  private void annotateClosureAsTransients(ClassNode classNode) {
		/*Find all closures.*/
    def closures = classNode.getFields().findAll { FieldNode f -> f.getType().toString() == "java.lang.Object"}

    closures.each { FieldNode f ->
      if (f.getAnnotations(MORPHIA_TRANSIENT).size() > 0) return // this one is annotated already
      f.addAnnotation(new AnnotationNode(MORPHIA_TRANSIENT))
    }
	}

  private PropertyNode getProperty(ClassNode classNode, String propertyName) {
    if (classNode == null || StringUtils.isBlank(propertyName))
      return null

    // find the given class property
    // do we need to deal with parent classes???
    for (PropertyNode pn: classNode.properties) {
      if (pn.getName().equals(propertyName) && !pn.isPrivate()) {
        return pn
      }
    }

    return null
  }

  private removeProperty(ClassNode classNode, String propertyName) {
    if (log.isDebugEnabled()) {
      log.debug("Removing property [" + propertyName + "] from class [" + classNode.getName() + "]")
    }

    // remove the property from the fields and properties arrays
    for (int i = 0; i < classNode.fields.size(); i++) {
      if (classNode.fields[i].name == propertyName) {
        classNode.fields.remove(i)
        break
      }
    }
    for (int i = 0; i < classNode.properties.size(); i++) {
      if (classNode.properties[i].name == propertyName) {
        classNode.properties.remove(i)
        break
      }
    }
  }
}
