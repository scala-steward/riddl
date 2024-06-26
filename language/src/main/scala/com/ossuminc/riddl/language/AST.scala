/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.{OccursInProjector, ProcessorRef}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{Keyword, RiddlParserInput}

import java.net.URL
import java.nio.file.Path
import scala.concurrent.Future
import scala.reflect.{ClassTag, classTag}
import scala.annotation.tailrec
import scala.io.{Codec, BufferedSource}

/** Abstract Syntax Tree This object defines the model for representing RIDDL as an Abstract Syntax Tree. This raw AST
  * has no referential integrity, it just results from applying the parsing rules to the input. The RawAST models
  * produced from parsing are syntactically correct but have no semantic validation.
  */
object AST {

  ///////////////////////////////////////////////////////////////////////////////////////////////////////// RIDDL VALUES

  /** The root trait of all parsed values. If a parser returns something, its a [[RiddlValue]]. Every node in the AST is
    * a RiddlNode. Subclasses implement the definitions in various ways because this is the most abstract notion of what
    * is parsed.
    */
  sealed trait RiddlValue {

    /** The location in the parse at which this RiddlValue occurs */
    def loc: At

    /** Format the node to a string in a form suitable for use in error messages */
    def format: String

    /** Whether or not this instance has an id: [[Identifier]] field or not */
    def isIdentified: Boolean = false

    def isAnonymous: Boolean = true

    /** Determine if this [[RiddlValue]] contains other values or not */
    def isContainer: Boolean = false

    /** Determine if this [[RiddlValue]] is the top most container, appearing at the root of the AST */
    def isRootContainer: Boolean = false

    /** Determine if this [[RiddlValue]] has definitions it contains */
    def hasDefinitions: Boolean = false

    /** Determine if this [[RiddlValue]] is a definition or not */
    def isDefinition: Boolean = false

    /** determine if this [[RiddlValue]] is empty or not. Non-containers are always empty */
    def isEmpty: Boolean = true

    /** determines if this [[RiddlValue]] is a comment or not */
    def isComment: Boolean = false

    /** determines if this node is a vital node or not */
    def isVital: Boolean = false

    /** determines if this [[RiddlValue]] is a processor (handles messages) or not */
    def isProcessor: Boolean = false

    /** determines if this [[RiddlValue]] has any options set or not */
    def hasOptions: Boolean = false

    /** determines if this [[RiddlValue]]defines any [[Author]]s or not */
    def hasAuthors: Boolean = false

    /** determines if this [[RiddlValue]] references any [[Author]]s or not */
    def hasAuthorRefs: Boolean = false

    /** determines if this [[RiddlValue]] contains any type definitions */
    def hasTypes: Boolean = false

    /** determines if this [[RiddlValue]] has any includes in it */
    def hasIncludes: Boolean = false

    /** determines if this [[RiddlValue]] defines a description */
    def hasDescription: Boolean = false

    /** determines if this [[RiddlValue]] defines a brief description */
    def hasBriefDescription: Boolean = false

    /** implements the nonEmpty function based on the isEmpty function */
    @deprecatedOverriding(
      "nonEmpty is defined as !isEmpty; override isEmpty instead"
    ) final def nonEmpty: Boolean = !isEmpty

    /** Provide a string to specify the kind of thing this value is */
    def kind: String = this.getClass.getSimpleName

  }

  /** Represents a literal string parsed between quote characters in the input
    *
    * @param loc
    *   The location in the input of the opening quote character
    * @param s
    *   The parsed value of the string content
    */
  case class LiteralString(loc: At, s: String) extends RiddlValue {
    override def format = s"\"$s\""

    /** Only empty if the string is empty too */
    override def isEmpty: Boolean = s.isEmpty
  }

  object LiteralString {

    /** definition of the empty LiteralString */
    val empty: LiteralString = LiteralString(At.empty, "")
  }

  /** A RiddlValue that is a parsed identifier, typically the name of a definition.
    *
    * @param loc
    *   The location in the input where the identifier starts
    * @param value
    *   The parsed value of the [[Identifier]]
    */
  case class Identifier(loc: At, value: String) extends RiddlValue {

    /** Convert to a printable form */
    override def format: String = value

    override def isEmpty: Boolean = value.isEmpty
  }

  object Identifier {

    /** Definition of the empty [[Identifier]] */
    val empty: Identifier = Identifier(At.empty, "")
  }

  /** Represents a segmented identifier to a definition in the model. Path Identifiers are parsed from a dot-separated
    * list of identifiers in the input. Path identifiers are used to reference other definitions in the model.
    *
    * @param loc
    *   Location in the input of the first letter of the path identifier
    * @param value
    *   The list of strings that make up the path identifier
    */
  case class PathIdentifier(loc: At, value: Seq[String]) extends RiddlValue {
    override def format: String = { value.mkString(".") }

    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  }

  object PathIdentifier {

    /** The empty [[PathIdentifier]] */
    val empty: PathIdentifier = PathIdentifier(At.empty, Seq.empty[String])
  }

  /** The description of a definition. All definitions have a name and an optional description. This class provides the
    * description part.
    */
  sealed trait Description extends RiddlValue {

    /** All kinds of [[Description]] have a location provided by an [[At]] value. */
    def loc: At

    /** All kinds of [[Description]] must be able to yield the lines of markdown equivalent */
    def lines: Seq[LiteralString]

    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.isEmpty)

    override def hasDescription: Boolean = lines.nonEmpty

  }

  /** Companion class for Description only to define the empty value */
  object Description {

    /** The empty [[Description]] definition */
    lazy val empty: Description = new Description {
      val loc: At = At.empty
      val lines = Seq.empty[LiteralString]
      def format: String = ""
    }
  }

  /** An implementation of a [[Description]] that implements the lines directly as [[LiteralString]] */
  case class BlockDescription(
    loc: At = At.empty,
    lines: Seq[LiteralString] = Seq.empty[LiteralString]
  ) extends Description {
    def format: String = ""
  }

  /** An implementation of [[Description]] that provides the description in a Markdown file */
  case class FileDescription(loc: At, file: Path) extends Description {
    lazy val lines: Seq[LiteralString] =
      val src = scala.io.Source.fromFile(file.toFile)(Codec.UTF8)
      src.getLines().toSeq.map(LiteralString(loc, _))
    def format: String = file.toAbsolutePath.toString
  }

  /** An implementation of [[Description]] that provides the description at a URL */
  case class URLDescription(loc: At, url: java.net.URL) extends Description {
    lazy val lines: Seq[LiteralString] =
      val source = scala.io.Source.fromURL(url)(Codec.UTF8)
      source.getLines().toSeq.map(LiteralString(loc.copy(), _))
    override def format: String = url.toExternalForm
  }

  /** A trait to add a brief description string to a RiddlValue */
  sealed trait BrieflyDescribedValue extends RiddlValue {
    def brief: Option[LiteralString]
    def briefValue: String = {
      brief.map(_.s).getOrElse("No brief description.")
    }
    override def hasBriefDescription: Boolean = brief.exists(_.s.nonEmpty)
  }

  /** Base trait of all [[RiddlValue]]s that have an optional Description */
  sealed trait DescribedValue extends RiddlValue {
    def description: Option[Description]
    override def hasDescription: Boolean = description.exists(_.hasDescription)
  }

  /** A frequently use type alias for a Seq of [[RiddlValue]] */
  type Contents[+CV <: RiddlValue] = Seq[CV]

  /** The extension of a Seq of [[RiddlValue]] for ease of access to the contents of the Seq */
  extension [CV <: RiddlValue](container: Contents[CV])
    /** Extract the elements of the [[Contents]] that have identifiers (are definitions, essentially) */
    private def identified: Contents[CV] = container.filter(_.isIdentified)

    /** Extract the elements of the [[Contents]] that are the type of the type parameter T
      *
      * @tparam T
      *   THe kind of [[RiddlValue]] sought in the [[Contents]]
      * @return
      *   The Seq of type `T` found in the [[Contents]]
      */
    def filter[T <: RiddlValue: ClassTag]: Contents[T] = {
      val theClass = classTag[T].runtimeClass
      container.filter(x => theClass.isAssignableFrom(x.getClass)).map(_.asInstanceOf[T])
    }

    /** Returns the elements of the [[Contents]] that are [[VitalDefinition]]s */
    def vitals: Contents[VitalDefinition[?]] = container.filter[VitalDefinition[?]]

    /** Returns the elememts of the [[Contents]] that are [[Processor]]s */
    def processors: Contents[Processor[?]] = container.filter[Processor[?]]

    /** Find the first element of the [[Contents]] that has the provided `name` */
    def find(name: String): Option[CV] =
      identified.find(d => d.isIdentified && d.asInstanceOf[WithIdentifier].id.value == name)

    /** Find the first element of the [[Contents]] that */
    def namedValues: Contents[CV & NamedValue] = container.filter(_.isIdentified).map(_.asInstanceOf[CV & NamedValue])

    /** Returns the [[Include]] elements of [[Contents]] */
    def includes: Contents[Include[?]] = container.filter[Include[?]].map(_.asInstanceOf[Include[?]])

    /** find the elements of the [[Contents]] that are [[Definition]]s */
    def definitions: Contents[Definition] = container.filter[Definition].map(_.asInstanceOf[Definition])

  /** Base trait of any [[RiddlValue]] that Contains other [[RiddlValue]]
    *
    * @tparam CV
    *   The kind of contained value that is contained within.
    */
  sealed trait Container[+CV <: RiddlValue] extends RiddlValue {
    def contents: Contents[CV]

    override def isEmpty: Boolean = contents.isEmpty

    /** Force all subclasses to return true as they are containers */
    final inline override def isContainer: Boolean = true

    /** The list of contained [[Definition]]s */
    final def definitions: Contents[Definition] = contents.definitions

    /** The list of contained [[NamedValue]]s */
    final def namedValues: Contents[CV & NamedValue] = contents.namedValues
  }

  /** This trait represents the base trait of all comments recognized by the parser */
  sealed trait Comment
      extends RiddlValue
      with OccursAtRootScope
      with OccursInVitalDefinitions
      with OccursInProcessors
      with OccursInHandler
      with OccursInGroup
      with Statement {
    final inline override def isComment: Boolean = true
  }

  /** The AST Representation of a single line comment in the input. LineComments can only occur after the closing brace,
    * }, of a definition. The comment is stored within the [[Definition]]
    *
    * @param loc
    *   Location in the input of the // comment introducer
    * @param text
    *   The text of the comment, everything after the // to the end of line
    */
  case class LineComment(loc: At, text: String = "") extends Comment {
    def format: String = "//" + text + "\n"

  }

  /** The AST representation of a comment that can span across lines and is inline with the definitions.
    *
    * @param loc
    *   The locaiton at which the comment occurs
    * @param lines
    *   The lines of the comment without line terminators
    */
  case class InlineComment(loc: At, lines: Seq[String] = Seq.empty) extends Comment {
    def format: String = lines.mkString("/*", "\n * ", "*/")
  }

  /** Base trait for option values for any option of a definition.
    *
    * @param loc
    *   The location at which the OptionValue occurs
    * @param name
    *   The name of the option
    * @param args
    *   THe arguments of the option as [[LiteralString]] which may be empty
    */
  case class OptionValue(loc: At, name: String, args: Seq[LiteralString] = Seq.empty)
      extends RiddlValue
      with OccursInVitalDefinitions
      with OccursInProcessors {
    override def format: String = "option " + name + args.map(_.format).mkString("(", ", ", ")")
  }

  /** A [[RiddlValue]] that is named via the [[WithIdentifier]] trait */
  sealed trait NamedValue extends RiddlValue with WithIdentifier

  /** A [[Container]] that is also a [[NamedValue]] */
  sealed trait NamedContainer[CV <: RiddlValue] extends NamedValue with Container[CV]

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////// WITHS
  ////////////// Defines a bunch of traits that can be used to compose the definitions via trait inheritance

  /** A trait that includes an `id` field and various methods to support it. This is used by [[NamedValue]],
    * [[Definition]] and any other thing that needs to be identified by name.
    */
  sealed trait WithIdentifier extends RiddlValue {

    /** the name/identifier of this value. All definitions have one */
    def id: Identifier

    final override inline def isIdentified: Boolean = true

    /** This one has an identifier so it is never anonymous */
    override final inline def isAnonymous: Boolean = id.value.isEmpty

    /** Convert the identifier into its string format */
    def identify: String = {
      if id.isEmpty then {
        s"Anonymous $kind"
      } else {
        s"$kind '${id.format}'"
      }
    }

    def identifyWithLoc: String = s"$identify at $loc"
  }

  /** A trait that includes the brief and description fields to a RiddlValue. All of the definitions have these */
  sealed trait WithDocumentation extends RiddlValue {

    /** The optional brief description as a single (short) line of text */
    def brief: Option[LiteralString]

    /** The optional [[Description]] which can be provided in several such as [[BlockDescription]], [[FileDescription]],
      * or [[URLDescription]]
      */
    def description: Option[Description]
  }

  /** A trait that includes the `comments` field to extract the comments from the contents */
  sealed trait WithComments
      extends Container[RiddlValue]
      with OccursInVitalDefinitions
      with OccursInProcessors
      with OccursInFunction
      with OccursAtRootScope {
    lazy val comments: Contents[Comment] = contents.filter[Comment]
  }

  /** Added to definitions that support includes */
  sealed trait WithIncludes[CT <: RiddlValue]
      extends Container[CT]
      with OccursInVitalDefinitions
      with OccursInProcessors
      with OccursAtRootScope {
    lazy val includes: Contents[Include[CT]] = contents.filter[Include[CT]]
    final override def hasIncludes = true
  }

  /** Added to definitions that support a list of term definitions */
  sealed trait WithTerms extends Container[RiddlValue] with OccursInVitalDefinitions with OccursInProcessors {
    lazy val terms: Contents[Term] = contents.filter[Term]
  }

  /** A trait that provides the [[AuthorRef]] to indicate who authored the definition */
  sealed trait WithAuthorRefs extends Container[RiddlValue] with OccursInVitalDefinitions with OccursInProcessors {
    lazy val authorRefs: Contents[AuthorRef] = contents.filter[AuthorRef]

    override def hasAuthorRefs: Boolean = authorRefs.nonEmpty
  }

  /** Base trait that can be used in any definition that takes options and ensures the options are defined, can be
    * queried, and formatted.
    */
  sealed trait WithOptions extends Container[RiddlValue] {
    lazy val options: Contents[OptionValue] = contents.filter[OptionValue]

    def hasOption(name: String): Boolean = options.exists(_.name == name)

    def getOptionValue(name: String): Option[OptionValue] = options.find(_.name == name)

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty

    override def hasOptions: Boolean = options.nonEmpty
  }

  /** Base trait of any definition that is a container and contains types */
  sealed trait WithTypes extends Container[RiddlValue] with OccursInProcessors with OccursInFunction {
    lazy val types: Contents[Type] = contents.filter[Type]
    override def hasTypes: Boolean = types.nonEmpty
  }

  /** Base trait to use in any definition that can define a constant */
  sealed trait WithConstants extends Container[RiddlValue] with OccursInProcessors {
    lazy val constants: Contents[Constant] = contents.filter[Constant]
  }

  /** Base trait to use in any [[Definition]] that can define an invariant */
  sealed trait WithInvariants extends Container[RiddlValue] with OccursInProcessors {
    lazy val invariants: Contents[Invariant] = contents.filter[Invariant]
  }

  /** Base trait to use in any [[Definition]] that can define a [[Function]] */
  sealed trait WithFunctions extends Container[RiddlValue] with OccursInProcessors {
    lazy val functions: Contents[Function] = contents.filter[Function]
  }

  /** Base trait to use in any [[Definition]] that can define a [[Handler]] */
  sealed trait WithHandlers extends Container[RiddlValue] with OccursInProcessors {
    lazy val handlers: Contents[Handler] = contents.filter[Handler]
  }

  /** Base trait to use in any [[Definition]] that can define an [[Inlet]] */
  sealed trait WithInlets extends Container[RiddlValue] with OccursInProcessors {
    lazy val inlets: Contents[Inlet] = contents.filter[Inlet]
  }

  /** Base trait to use in any [[Definition]] that can define an [[Outlet]] */
  sealed trait WithOutlets extends Container[RiddlValue] with OccursInProcessors {
    lazy val outlets: Contents[Outlet] = contents.filter[Outlet]
  }

  /** Base trait to use in any [[Definition]] that can define a [[State]] */
  sealed trait WithStates extends Container[RiddlValue] with OccursInEntity {
    lazy val states: Contents[State] = contents.filter[State]
  }

  /** Base trait to use in any [[Definition]] that can define a [[Group]] */
  sealed trait WithGroups extends Container[RiddlValue] with OccursInApplication {
    lazy val groups: Contents[Group] = contents.filter[Group]
  }

  /** Base trait to use in a [[AST.Function]] to define the [[AST.Statement]]s that form the body of that function */
  sealed trait WithStatements extends Container[RiddlValue] with OccursInProcessors with OccursInFunction {
    lazy val statements: Contents[Statement] = contents.filter[Statement]
  }

  /** Base trait to use in a [[Domain]] to define the bounded [[Context]] it contains */
  sealed trait WithContexts extends Container[RiddlValue] with OccursInDomain {
    lazy val contexts: Contents[Context] = contents.filter[Context]
  }

  /** Base trait to use in any [[Definition]] that can define [[Author]]s */
  sealed trait WithAuthors extends Container[RiddlValue] with OccursInDomain {
    lazy val authors: Contents[Author] = contents.filter[Author]

    override def hasAuthors: Boolean = authors.nonEmpty
  }

  /** Base trait to use in any [[Definition]] that can define [[User]]s */
  sealed trait WithUsers extends Container[RiddlValue] with OccursInDomain {
    def users: Contents[User] = contents.filter[User]
  }

  /** Base trait to use in any [[Definition]] that can define [[Epic]]s */
  sealed trait WithEpics extends Container[RiddlValue] with OccursInDomain {
    lazy val epics: Contents[Epic] = contents.filter[Epic]
  }

  /** Base trait to use in any [[Definition]] that can define [[Application]]s */
  sealed trait WithApplications extends Container[RiddlValue] with OccursInDomain {
    lazy val applications: Contents[Application] = contents.filter[Application]
  }

  /** Base trait to use in any [[Definition]] that can define [[Domain]]s */
  sealed trait WithDomains extends Container[RiddlValue] with OccursInDomain {
    lazy val domains: Contents[Domain] = contents.filter[Domain]
  }

  /** Base trait to use in any [[Definition]] that can define [[Projector]]s */
  sealed trait WithProjectors extends Container[RiddlValue] with OccursInContext {
    lazy val projectors: Contents[Projector] = contents.filter[Projector]
  }

  /** Base trait to use in any [[Definition]] that can define [[Repository]]s */
  sealed trait WithRepositories extends Container[RiddlValue] with OccursInContext {
    lazy val repositories: Contents[Repository] = contents.filter[Repository]
  }

  /** Base trait to use in any [[Definition]] that can define [[Entity]]s */
  sealed trait WithEntities extends Container[RiddlValue] with OccursInContext {
    lazy val entities: Contents[Entity] = contents.filter[Entity]
  }

  /** Base trait to use in any [[Definition]] that can define [[Streamlet]]s */
  sealed trait WithStreamlets extends Container[RiddlValue] with OccursInContext {
    lazy val streamlets: Contents[Streamlet] = contents.filter[Streamlet]
  }

  /** Base trait to use in any [[Definition]] that can define [[Connector]]s */
  sealed trait WithConnectors extends Container[RiddlValue] with OccursInContext {
    lazy val connectors: Contents[Connector] = contents.filter[Connector]
  }

  /** Base trait to use in any [[Definition]] that can define [[Adaptor]]s */
  sealed trait WithAdaptors extends Container[RiddlValue] with OccursInContext {
    lazy val adaptors: Contents[Adaptor] = contents.filter[Adaptor]
  }

  /** Base trait to use in any [[Definition]] that can define [[Saga]]s */
  sealed trait WithSagas extends Container[RiddlValue] with OccursInContext {
    lazy val sagas: Contents[Saga] = contents.filter[Saga]
  }

  /** Base trait to use in any [[Definition]] that can define [[SagaStep]]s */
  sealed trait WithSagaSteps extends Container[RiddlValue] with OccursInSaga {
    lazy val sagaSteps: Contents[SagaStep] = contents.filter[SagaStep]
  }

  /** Base trait to use in any [[Definition]] that can define [[UseCase]]s */
  sealed trait WithUseCases extends Container[RiddlValue] with OccursInEpic {
    lazy val cases: Contents[UseCase] = contents.filter[UseCase]
  }

  //////////////////////////////////////////////////////////////////////////////////////////////// ABSTRACT DEFINITIONS
  ///// This section defines various abstract things needed by the rest of the definitions

  /** The list of definitions to which a reference cannot be made */
  type NonReferencableDefinitions = Author | User | Enumerator | Group | Root | SagaStep | Term | Handler | Invariant

  /** THe list of RiddlValues that are not Definitions for excluding them in match statements */
  type NonDefinitionValues = LiteralString | Identifier | PathIdentifier | Description | Interaction | Include[?] |
    IncludeHolder[?] | TypeExpression | Comment | OptionValue | Reference[?] | Statement | StreamletShape |
    AdaptorDirection | UserStory | MethodArgument | Schema

  /** Base trait of values defined at the root (top of file) scope */
  sealed trait OccursAtRootScope extends RiddlValue

  /** Base trait of any definition that is in the content of an adaptor */
  sealed trait OccursInAdaptor extends RiddlValue

  /** Base trait of any definition that is in the content of an Application */
  sealed trait OccursInApplication extends RiddlValue

  /** Base trait of any definition that is in the content of a Group */
  sealed trait OccursInGroup extends RiddlValue

  /** Base trait of any definition that is in the content of an Output */
  sealed trait OccursInOutput extends RiddlValue

  /** Base trait of any definition that is in the content of an Input */
  sealed trait OccursInInput extends RiddlValue

  /** Base trait of any definition that is in the content of a context */
  sealed trait OccursInContext extends RiddlValue

  /** Base trait of any definition that is in the content of a domain */
  sealed trait OccursInDomain extends RiddlValue

  /** Base trait of any value used in the definition of an entity */
  sealed trait OccursInEntity extends RiddlValue

  /** Base trait of definitions that are in the body of a Story definition */
  sealed trait OccursInEpic extends RiddlValue

  /** Base trait of any definition that is in the content of a function. */
  sealed trait OccursInFunction extends RiddlValue

  /** Base trait of definitions that are part of a Handler Definition */
  sealed trait OccursInHandler extends RiddlValue

  /** Base trait of any definition that occurs in the body of a projector */
  sealed trait OccursInProjector extends RiddlValue

  /** Base trait of definitions defined in a repository */
  sealed trait OccursInRepository extends RiddlValue

  /** Base trait of definitions that are part of a Saga Definition */
  sealed trait OccursInSaga extends RiddlValue

  /** Base trait of definitions define within a Streamlet */
  sealed trait OccursInStreamlet extends RiddlValue

  /** Base trait of definitions that are part of a Saga Definition */
  sealed trait OccursInState extends RiddlValue

  /** Any definition that is part of a Type's Definition */
  sealed trait OccursInType extends RiddlValue

  /** Base trait of definitions in a UseCase, typically interactions */
  sealed trait OccursInUseCase extends RiddlValue

  /** A trait to define the definitions that can be included in the definition of a VitalDefinition */
  sealed trait OccursInVitalDefinitions
      extends OccursInAdaptor
      with OccursInApplication
      with OccursInContext
      with OccursInDomain
      with OccursInEntity
      with OccursInFunction
      with OccursInStreamlet
      with OccursInProjector
      with OccursInRepository
      with OccursInSaga
      with OccursInEpic

  /** A trait for all the definitions that are are considered to be Processors */
  sealed trait OccursInProcessors
      extends OccursInAdaptor
      with OccursInApplication
      with OccursInContext
      with OccursInEntity
      with OccursInProjector
      with OccursInRepository
      with OccursInStreamlet
      with OccursInSaga

  ////////////////////////////////////////////////////////////////////////////////////////////////////////// DEFINITIONS
  //////// The Abstract classes for defining Definitions by using the foregoing traits

  /** Base trait for all definitions requiring an identifier for the definition and providing the identify method to
    * yield a string that provides the kind and name
    */
  sealed trait Definition
      extends NamedContainer[RiddlValue]
      with DescribedValue
      with BrieflyDescribedValue
      with WithComments {

    /** True iff there are contained definitions */
    override def hasDefinitions: Boolean = contents.definitions.nonEmpty

    /** Yes anything deriving from here is a definition */
    override def isDefinition: Boolean = true

    def isAppRelated: Boolean = false
  }

  /** A definition with no content */
  sealed trait LeafDefinition extends Definition {
    override def isEmpty: Boolean = contents.isEmpty && description.isEmpty && brief.isEmpty

    final override def contents: Contents[RiddlValue] = Seq.empty[RiddlValue]

    final override def hasDefinitions: Boolean = false
  }

  /** The base class of the primary, or vital, definitions. Most of the important definitions are derivatives of this
    * sealed trait. All vital definitions contain comments, documentation, options, authors that defined it, include
    * statements, and term definitions.
    * @tparam CT
    */
  sealed trait VitalDefinition[CT <: RiddlValue]
      extends Definition
      with Container[CT]
      with WithComments
      with WithDocumentation
      with WithOptions
      with WithAuthorRefs
      with WithIncludes[CT]
      with WithTerms {

    final override def isVital: Boolean = true
  }

  /** Definition of a Processor. This is a base class for all Processor definitions (things that have inlets, outlets,
    * handlers, functions, and take messages directly with a reference). Processors are the active portion of a model
    * since they handle messages and do the associated processing.
    */
  sealed trait Processor[CT <: RiddlValue]
      extends VitalDefinition[CT]
      with WithTypes
      with WithConstants
      with WithInvariants
      with WithFunctions
      with WithHandlers
      with WithInlets
      with WithOutlets {

    final override def isProcessor: Boolean = true

  }

  ///////////////////////////////////////////////////////////////////////////////////////////////// UTILITY DEFINITIONS
  //// The types defined in this section provide utility to the other definitions for includes and references.

  /** A value to hold the result of an Include while it is being included asynchronously
    *
    * @param loc
    *   The location at which the include occurs in the input
    *
    * @param future
    *   The future deliverable of the result of parsing the inclusion; either a list of error messages or the
    *   RiddlParserInput for the included content and a list of the values parsed
    * @tparam CT
    *   The type of values expected at the top level of the
    */
  case class IncludeHolder[CT <: RiddlValue](
    loc: At = At.empty,
    origin: String = "",
    maxDelay: scala.concurrent.duration.FiniteDuration,
    future: Future[Contents[CT]]
  ) extends RiddlValue
      with OccursInVitalDefinitions
      with OccursAtRootScope {
    def format: String = s"include \"$origin\""
    override def toString: String = format
  }

  /** A value to record an inclusion of a file while parsing.
    *
    * @param loc
    *   The location of the include statement in the source
    * @param contents
    *   The Vital Definitions read from the file
    * @param origin
    *   The string that indicates the origin of the inclusion
    */
  case class Include[CT <: RiddlValue](
    loc: At = At.empty,
    origin: String = "",
    contents: Contents[CT] = Seq.empty[CT]
  ) extends RiddlValue
      with Container[CT]
      with OccursInVitalDefinitions
      with OccursAtRootScope {

    override def isRootContainer: Boolean = true

    def format: String = s"include \"$origin\""
    override def toString: String = format
  }

  /** A reference to a definition of a specific type.
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed abstract class Reference[+T <: NamedValue: ClassTag] extends RiddlValue {

    /** The Path identifier to the referenced definition
      */
    def pathId: PathIdentifier

    /** The optional identifier of the reference to be used locally in some other reference.
      */
    def id: Option[Identifier] = None

    /** @return
      *   String A string that describes this reference
      */
    def identify: String = {
      s"${classTag[T].runtimeClass.getSimpleName} ${
          if id.nonEmpty then {
            id.map(_.format + ": ")
          } else ""
        }'${pathId.format}'${loc.toShort}"
    }

    override def isEmpty: Boolean = pathId.isEmpty
  }

  /** Base trait of a reference to definitions that can accept a message directly via a reference
    *
    * @tparam T
    *   The kind of reference needed
    */
  sealed trait ProcessorRef[+T <: Processor[?]] extends Reference[T]

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// ROOT

  /** The root of the containment hierarchy, corresponding roughly to a level about a file.
    *
    * @param contents
    *   The sequence top level definitions contained by this root container
    */
  case class Root(
    contents: Seq[OccursAtRootScope] = Seq.empty[OccursAtRootScope]
  ) extends Definition
      with Container[OccursAtRootScope]
      with WithDomains
      with WithAuthors
      with WithComments
      with WithIncludes[OccursAtRootScope] {

    override def isRootContainer: Boolean = true

    def loc: At = At.empty

    override def id: Identifier = Identifier(loc, "Root")

    override def identify: String = "Root"

    override def identifyWithLoc: String = "Root"

    override def description: Option[Description] = None

    override def brief: Option[LiteralString] = None

    def format: String = ""
  }

  object Root {
    val empty: Root = apply(Seq.empty[OccursAtRootScope])
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// USER

  /** An User (Role) who is the initiator of the user story. Users may be persons or machines
    *
    * @param loc
    *   The location of the user in the source
    * @param id
    *   The name (role) of the user
    * @param is_a
    *   What kind of thing the user is
    * @param brief
    *   A brief description of the user
    * @param description
    *   A longer description of the user and its role
    */
  case class User(
    loc: At,
    id: Identifier,
    is_a: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursInDomain {
    def format: String = s"${Keyword.user} ${id.format} is ${is_a.format}"
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// USER

  /** A term definition for the glossary
    * @param loc
    *   The [[At]] at which this glossary term occurs
    * @param id
    *   The term being defined
    * @param brief
    *   The short definition of the term
    * @param description
    *   The longer definition of the term.
    */
  case class Term(
    loc: At,
    id: Identifier,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursInVitalDefinitions {

    def format: String = s"${Keyword.term} ${id.format} is ${brief.map(_.format)}"
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////// AUTHOR

  /** A value that holds the author's information
    *
    * @param loc
    *   The location of the author information
    * @param name
    *   The full name of the author
    * @param email
    *   The author's email address
    * @param organization
    *   The name of the organization the author is associated with
    * @param title
    *   The author's title within the organization
    * @param url
    *   A URL associated with the author
    * @param brief
    *   A optional short description of the author
    * @param description
    *   An optional long description of the author
    */
  case class Author(
    loc: At,
    id: Identifier,
    name: LiteralString,
    email: LiteralString,
    organization: Option[LiteralString] = None,
    title: Option[LiteralString] = None,
    url: Option[java.net.URL] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursAtRootScope
      with OccursInDomain {
    override def isEmpty: Boolean = {
      name.isEmpty && email.isEmpty && organization.isEmpty && title.isEmpty
    }

    def format: String = Keyword.author + " " + id.format
  }

  /** A reference to an [[Author]]
    * @param loc
    *   The [[At]] at which the reference is located
    * @param pathId
    *   The [[PathIdentifier]] providing the path to the [[Author]]
    */
  case class AuthorRef(loc: At, pathId: PathIdentifier)
      extends Reference[Author]
      with OccursInVitalDefinitions
      with OccursInFunction
      with OccursInProcessors {
    override def format: String = Keyword.author + " " + pathId.format
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////// TYPES

  /** Base trait of an expression that defines a type
    */
  sealed trait TypeExpression extends RiddlValue {
    def isAssignmentCompatible(other: TypeExpression): Boolean = {
      (other == this) || (other.getClass == this.getClass) ||
      (other.getClass == classOf[Abstract]) ||
      (this.getClass == classOf[Abstract])
    }
    def hasCardinality: Boolean = false
    def isAggregateOf(useCase: AggregateUseCase): Boolean = {
      this match {
        case AliasedTypeExpression(_, keyword, _) if keyword.compareToIgnoreCase(useCase.format) == 0 => true
        case AggregateUseCaseTypeExpression(_, usecase, _) if usecase == useCase                      => true
        case _                                                                                        => false
      }
    }
  }

  sealed trait NumericType extends TypeExpression {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  sealed trait IntegerTypeExpression extends NumericType
  sealed trait RealTypeExpression extends NumericType

  /** A TypeExpression that references another type by PathIdentifier
    * @param loc
    *   The location of the AliasedTypeExpression
    * @param pathId
    *   The path identifier to the aliased type
    */
  case class AliasedTypeExpression(loc: At, keyword: String, pathId: PathIdentifier) extends TypeExpression {
    override def format: String = s"$keyword ${pathId.format}"
  }

  /** Base of an enumeration for the four kinds of message types */
  sealed trait AggregateUseCase {
    override def toString: String = useCase
    def format: String = useCase
    def useCase: String
  }

  /** An enumerator value to distinguish command aggregates */
  case object CommandCase extends AggregateUseCase {
    @inline def useCase: String = "Command"
  }

  /** An enumerator value to distinguish event aggregates */
  case object EventCase extends AggregateUseCase {
    @inline def useCase: String = "Event"
  }

  /** An enumerator value to distinguish query aggregates */
  case object QueryCase extends AggregateUseCase {
    @inline def useCase: String = "Query"
  }

  /** An enumerator value  to distinguish result aggregates */
  case object ResultCase extends AggregateUseCase {
    @inline def useCase: String = "Result"
  }

  /** An enumerator value  to distinguish record aggregates */
  case object RecordCase extends AggregateUseCase {
    @inline def useCase: String = "Record"
  }

  /** An enumerator value to identify undistinguished aggregates */
  case object TypeCase extends AggregateUseCase {
    @inline def useCase: String = "Type"
  }

  /** Base trait of the cardinality for type expressions */
  sealed trait Cardinality extends TypeExpression {

    /** The [[TypeExpression]] that this cardinality expression modified */
    def typeExp: TypeExpression
    final override def hasCardinality: Boolean = true
  }

  /** A cardinality type expression that indicates another type expression as being optional; that is with a cardinality
    * of 0 or 1.
    *
    * @param loc
    *   The location of the optional cardinality
    * @param typeExp
    *   The type expression that is indicated as optional
    */
  case class Optional(loc: At, typeExp: TypeExpression) extends Cardinality {
    override def format: String = s"${typeExp.format}?"
  }

  /** A cardinality type expression that indicates another type expression as having zero or more instances.
    *
    * @param loc
    *   The location of the zero-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of zero or more.
    */
  case class ZeroOrMore(loc: At, typeExp: TypeExpression) extends Cardinality {
    override def format: String = s"${typeExp.format}*"
  }

  /** A cardinality type expression that indicates another type expression as having one or more instances.
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    */
  case class OneOrMore(loc: At, typeExp: TypeExpression) extends Cardinality {
    override def format: String = s"${typeExp.format}+"
  }

  /** A cardinality type expression that indicates another type expression as having a specific range of instances
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    * @param min
    *   The minimum number of items
    * @param max
    *   The maximum number of items
    */
  case class SpecificRange(
    loc: At,
    typeExp: TypeExpression,
    min: Long,
    max: Long
  ) extends Cardinality {
    override def format: String = s"${typeExp.format}{$min,$max}"
  }

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
    *
    * @param id
    *   the identifier (name) of the Enumerator
    * @param enumVal
    *   the optional int value
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   the description of the enumerator. Each Enumerator in an enumeration may define independent descriptions
    */
  case class Enumerator(
    loc: At,
    id: Identifier,
    enumVal: Option[Long] = None,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursInType {
    override def format: String = id.format
  }

  /** A type expression that defines its range of possible values as being one value from a set of enumerated values.
    *
    * @param loc
    *   The location of the enumeration type expression
    * @param enumerators
    *   The set of enumerators from which the value of this enumeration may be chosen.
    */
  case class Enumeration(loc: At, enumerators: Seq[Enumerator]) extends IntegerTypeExpression {
    override def format: String = "{ " + enumerators
      .map(_.format)
      .mkString(",") + " }"

  }

  /** A type expression that that defines its range of possible values as being any one of the possible values from a
    * set of other type expressions.
    *
    * @param loc
    *   The location of the alternation type expression
    * @param of
    *   The set of type expressions from which the value for this alternation may be chosen
    */
  case class Alternation(loc: At, of: Seq[AliasedTypeExpression]) extends TypeExpression {
    override def format: String =
      s"one of { ${of.map(_.format).mkString(", ")} }"
  }

  /** A type expression for a sequence of some other type expression
    *
    * @param loc
    *   Where this type expression occurs in the source code
    * @param of
    *   The type expression of the sequence's elements
    */
  case class Sequence(loc: At, of: TypeExpression) extends TypeExpression {
    override def format: String = s"sequence of ${of.format}"
  }

  /** A type expressions that defines a mapping from a key to a value. The value of a Mapping is the set of mapped key
    * -> value pairs, based on which keys have been provided values.
    *
    * @param loc
    *   The location of the mapping type expression
    * @param from
    *   The type expression for the keys of the mapping
    * @param to
    *   The type expression for the values of the mapping
    */
  case class Mapping(loc: At, from: TypeExpression, to: TypeExpression) extends TypeExpression {
    override def format: String = s"mapping from ${from.format} to ${to.format}"
  }

  /** A mathematical set of some other type of value
    *
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the set.
    */
  case class Set(loc: At, of: TypeExpression) extends TypeExpression {

    /** Format the node to a string */
    override def format: String = s"set of ${of.format}"
  }

  /** A graph of homogenous nodes. This implies the nodes are augmented with additional data to support navigation in
    * the graph but that detail is left to the implementation of the model.
    *
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the graph
    */
  case class Graph(loc: At, of: TypeExpression) extends TypeExpression {

    /** Format the node to a string */
    override def format: String = s"graph of ${of.format}"
  }

  /** A vector, table, or array of homogeneous cells.
    *
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the table
    * @param dimensions
    *   The size of the dimensions of the table. There can be as many dimensions as needed.
    */
  case class Table(loc: At, of: TypeExpression, dimensions: Seq[Long]) extends TypeExpression {
    override def format: String = s"table of ${of.format}(${dimensions.mkString(",")})"
  }

  /** A value that is replicated across nodes in a cluster. Usage requirements placement in a definition such as
    * [[Context]] or [[Entity]] that supports the `clustered` value for the `kind` option.
    *
    * @param loc
    *   Where the replica type expression occurs in the source
    * @param of
    *   The kind of data value that is replicated across cluster nodes. Because replicas imply use of a Conflict-free
    *   Replicated Data Type, the kind of type expression for `of` is restricted to numeric, set, and map types
    */
  case class Replica(loc: At, of: TypeExpression) extends TypeExpression {
    override def format: String = s"replica of ${of.format}"
  }

  /** The base trait of values of an aggregate type to provide the required `typeEx` field to give the
    * [[TypeExpression]] for that value of the aggregate
    */
  sealed trait AggregateValue extends OccursInType with WithIdentifier {
    def typeEx: TypeExpression
  }

  /** A definition that is a field of an aggregation type expressions. Fields associate an identifier with a type
    * expression.
    *
    * @param loc
    *   The location of the field definition
    * @param id
    *   The name of the field
    * @param typeEx
    *   The type of the field
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the field.
    */
  case class Field(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with AggregateValue
      with OccursInSaga
      with OccursInState
      with OccursInFunction
      with OccursInProjector {
    override def format: String = s"${id.format}: ${typeEx.format}"
  }

  /** An argument of a method value for an aggregate
    *
    * @param loc
    *   The parse location of the argument
    * @param name
    *   The name of the argument
    * @param typeEx
    *   The type of the argument as a [[TypeExpression]]
    */
  case class MethodArgument(
    loc: At,
    name: String,
    typeEx: TypeExpression
  ) extends RiddlValue {

    /** Format the node to a string */
    def format: String = s"$name: ${typeEx.format}"
  }

  /** A leaf definition that is a callable method (function) of an aggregate type expression. Methods associate an
    * identifier with a computed [[TypeExpression]].
    *
    * @param loc
    *   The location of the field definition
    * @param id
    *   The name of the field
    * @param args
    *   The type of the field
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the field.
    */
  case class Method(
    loc: At,
    id: Identifier,
    args: Seq[MethodArgument] = Seq.empty[MethodArgument],
    typeEx: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with AggregateValue
      with OccursInType
      with OccursInSaga
      with OccursInState
      with OccursInFunction
      with OccursInProjector {
    override def format: String = s"${id.format}(${args.map(_.format).mkString(", ")}): ${typeEx.format}"
  }

  /** A type expression that contains an aggregation of fields (named values) or methods (named functions)
    *
    * This is used as the [[TypeExpression]] of Aggregations and Messages
    */
  sealed trait AggregateTypeExpression(contents: Contents[RiddlValue])
      extends Container[RiddlValue]
      with TypeExpression
      with WithComments {
    def fields: Seq[Field] = contents.filter[Field]
    def methods: Seq[Method] = contents.filter[Method]
    override def format: String = s"{ ${contents.map(_.format).mkString(", ")} }"
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      other match {
        case oate: AggregateTypeExpression =>
          val validity: Seq[Boolean] = for
            ofield: AggregateValue <- oate.contents.filter[AggregateValue]
            named <- contents.find(ofield.id.value)
            myField: Field = named.asInstanceOf[Field] if named.isInstanceOf[Field]
            myTypEx = myField.typeEx
            oTypeEx = ofield.typeEx
          yield {
            myTypEx.isAssignmentCompatible(oTypeEx)
          }
          (validity.size == oate.contents.size) && validity.forall(_ == true)
        case _ =>
          super.isAssignmentCompatible(other)
      }
    }
  }

  /** A type expression that takes a set of named fields as its value.
    *
    * @param loc
    *   The location of the aggregation definition
    * @param contents
    *   The content of the aggregation
    */
  case class Aggregation(
    loc: At,
    contents: Seq[RiddlValue] = Seq.empty
  ) extends AggregateTypeExpression(contents)

  object Aggregation {
    def empty(loc: At = At.empty): Aggregation = { Aggregation(loc) }
  }

  /** A type expression for an aggregation that is marked as being one of the use cases. This is used for messages,
    * records, and other aggregate types that need to have their purpose distinguished.
    *
    * @param loc
    *   The location of the message type expression
    * @param usecase
    *   The kind of message defined
    * @param contents
    *   The contents of the message's aggregation
    */
  case class AggregateUseCaseTypeExpression(
    loc: At,
    usecase: AggregateUseCase,
    contents: Seq[RiddlValue] = Seq.empty
  ) extends AggregateTypeExpression(contents) {
    override def format: String = {
      usecase.format.toLowerCase() + " " + super.format
    }
  }

  /** A type expression whose value is a reference to an instance of an entity.
    *
    * @param loc
    *   The location of the reference type expression
    * @param entity
    *   The type of entity referenced by this type expression.
    */
  case class EntityReferenceTypeExpression(loc: At, entity: PathIdentifier) extends TypeExpression {
    override def format: String = s"entity ${entity.format}"
  }

  /** Base class of all pre-defined type expressions
    */
  abstract class PredefinedType extends TypeExpression {
    override def isEmpty: Boolean = true

    def loc: At

    override def format: String = kind
  }

  object PredefinedType {
    final def unapply(preType: PredefinedType): Option[String] =
      Option(preType.kind)
  }

  /** A type expression that defines a string value constrained by a Java Regular Expression
    *
    * @param loc
    *   The location of the pattern type expression
    * @param pattern
    *   The Java Regular Expression to which values of this type expression must obey.
    *
    * @see
    *   https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
    */
  case class Pattern(loc: At, pattern: Seq[LiteralString]) extends PredefinedType {
    override def format: String =
      s"$kind(${pattern.map(_.format).mkString(", ")})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[String_]
    }
  }

  /** A type expression for values of arbitrary string type, possibly bounded by length.
    *
    * @param loc
    *   The location of the Strng type expression
    * @param min
    *   The minimum length of the string (default: 0)
    * @param max
    *   The maximum length of the string (default: MaxInt)
    */
  case class String_(loc: At, min: Option[Long] = None, max: Option[Long] = None) extends PredefinedType {
    override inline def kind: String = "String"
    override def format: String = {
      if min.isEmpty && max.isEmpty then kind else s"$kind(${min.getOrElse("")},${max.getOrElse("")})"
    }

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Pattern]
    }
  }

  /** A type expression for values that ensure a unique identifier for a specific entity.
    *
    * @param loc
    *   The location of the unique identifier type expression
    * @param entityPath
    *   The path identifier of the entity type
    */
  case class UniqueId(loc: At, entityPath: PathIdentifier) extends PredefinedType {
    inline override def kind: String = "Id"

    override def format: String = s"$kind(${entityPath.format})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** The type representation of a national monetary currency
    * @param loc
    *   Location at which the currency type occurs
    * @param country
    *   The ISO 3166 A-3 three letter code for the country
    */
  case class Currency(loc: At, country: String) extends PredefinedType

  /** The simplest type expression: Abstract An abstract type expression is one that is not defined explicitly. It is
    * treated as a concrete type but without any structural or type information. This is useful for types that are
    * defined only at implementation time or for types whose variations are so complicated they need to remain abstract
    * at the specification level.
    * @param loc
    *   The location of the Bool type expression
    */
  case class Abstract(loc: At) extends PredefinedType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = true
  }

  case class UserId(loc: At) extends PredefinedType {
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || {
        other match
          case _: String_ => true
          case _          => false
      }
    }
  }

  /** A predefined type expression for boolean values (true / false)
    *
    * @param loc
    *   The location of the Bool type expression
    */
  case class Bool(loc: At) extends PredefinedType with IntegerTypeExpression {
    override def kind: String = "Boolean"
  }

  /** A predefined type expression for an arbitrary number value
    *
    * @param loc
    *   The location of the number type expression
    */
  case class Number(loc: At) extends PredefinedType with IntegerTypeExpression with RealTypeExpression {}

  /** A predefined type expression for an integer value
    *
    * @param loc
    *   The location of the integer type expression
    */
  case class Integer(loc: At) extends PredefinedType with IntegerTypeExpression

  case class Whole(loc: At) extends PredefinedType with IntegerTypeExpression

  case class Natural(loc: At) extends PredefinedType with IntegerTypeExpression

  /** A type expression that defines a set of integer values from a minimum value to a maximum value, inclusively.
    *
    * @param loc
    *   The location of the RangeType type expression
    * @param min
    *   The minimum value of the RangeType
    * @param max
    *   The maximum value of the RangeType
    */
  case class RangeType(loc: At, min: Long, max: Long) extends IntegerTypeExpression {
    override def format: String = s"$kind($min,$max)"
    inline override def kind: String = "Range"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  /** A predefined type expression for a decimal value including IEEE floating point syntax.
    *
    * @param loc
    *   The location of the decimal integer type expression
    */
  case class Decimal(loc: At, whole: Long, fractional: Long) extends RealTypeExpression {

    /** Format the node to a string */
    override def format: String = s"Decimal($whole,$fractional)"
  }

  /** A predefined type expression for a real number value.
    *
    * @param loc
    *   The location of the real number type expression
    */
  case class Real(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base unit for Current (amperes)
    * @param loc
    *   \- The locaitonof the current type expression
    */
  case class Current(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base unit for Length (meters)
    * @param loc
    *   The location of the current type expression
    */
  case class Length(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base Unit for Luminosity (candela)
    * @param loc
    *   The location of the luminosity expression
    */
  case class Luminosity(loc: At) extends PredefinedType with RealTypeExpression

  case class Mass(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base Unit for Mole (mole)
    * @param loc
    *   \- The location of the mass type expression
    */
  case class Mole(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base Unit for Temperature (Kelvin)
    * @param loc
    *   \- The location of the mass type expression
    */
  case class Temperature(loc: At) extends PredefinedType with RealTypeExpression

  sealed trait TimeType extends PredefinedType

  /** A predefined type expression for a calendar date.
    *
    * @param loc
    *   The location of the date type expression.
    */
  case class Date(loc: At) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a clock time with hours, minutes, seconds.
    *
    * @param loc
    *   The location of the time type expression.
    */
  case class Time(loc: At) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a calendar date and clock time combination.
    *
    * @param loc
    *   The location of the datetime type expression.
    */
  case class DateTime(loc: At) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Date] || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[ZonedDateTime] || other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  case class ZonedDateTime(loc: At, zone: Option[LiteralString] = None) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[ZonedDateTime] ||
      other.isInstanceOf[DateTime] || other.isInstanceOf[Date] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }

    override def format: String = s"ZonedDateTime(${zone.map(_.format).getOrElse("\"UTC\"")})"
  }

  /** A predefined type expression for a timestamp that records the number of milliseconds from the epoch.
    *
    * @param loc
    *   The location of the timestamp
    */
  case class TimeStamp(loc: At) extends TimeType {
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[Date] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a time duration that records the number of milliseconds between two fixed points
    * in time
    *
    * @param loc
    *   The location of the duration type expression
    */
  case class Duration(loc: At) extends TimeType

  /** A predefined type expression for a universally unique identifier as defined by the Java Virtual Machine.
    *
    * @param loc
    *   The location of the UUID type expression
    */
  case class UUID(loc: At) extends PredefinedType

  /** A predefined type expression for a Uniform Resource Locator of a specific schema.
    *
    * @param loc
    *   The location of the URL type expression
    * @param scheme
    *   The scheme to which the URL is constrained.
    */
  case class URL(loc: At, scheme: Option[LiteralString] = None) extends PredefinedType {
    override def format: String = s"$kind(${scheme.map(_.format).getOrElse("\"https\"")})"
  }

  /** A predefined type expression for a location on earth given in latitude and longitude.
    *
    * @param loc
    *   The location of the LatLong type expression.
    */
  case class Location(loc: At) extends PredefinedType

  enum BlobKind:
    case Text, XML, JSON, Image, Audio, Video, CSV, FileSystem

  case class Blob(loc: At, blobKind: BlobKind) extends PredefinedType {
    override def format: String = s"$kind($blobKind)"
  }

  /** A predefined type expression for a type that can have no values
    *
    * @param loc
    *   The location of the nothing type expression.
    */
  case class Nothing(loc: At) extends PredefinedType {
    override def isAssignmentCompatible(other: TypeExpression): Boolean = false
  }

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: AggregateUseCase

    override def format: String =
      s"${messageKind.useCase.toLowerCase} ${pathId.format}"
  }

  object MessageRef {
    lazy val empty: MessageRef = new MessageRef {
      def messageKind: AggregateUseCase = RecordCase

      override def pathId: PathIdentifier = PathIdentifier.empty

      override def loc: At = At.empty
    }
  }

  /** A Reference to a command message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  case class CommandRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = CommandCase
  }

  /** A Reference to an event message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  case class EventRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = EventCase
  }

  /** A reference to a query message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the query type
    */
  case class QueryRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = QueryCase
  }

  /** A reference to a result message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  case class ResultRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = ResultCase
  }

  /** A reference to a record message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  case class RecordRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = RecordCase
    override def isEmpty: Boolean =
      super.isEmpty && loc.isEmpty && pathId.isEmpty
  }

  /** A type definition which associates an identifier with a type expression.
    *
    * @param loc
    *   The location of the type definition
    * @param id
    *   The name of the type being defined
    * @param typ
    *   The type expression of the type being defined
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the type.
    */
  case class Type(
    loc: At,
    id: Identifier,
    typ: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Definition
      with Container[OccursInType]
      with OccursInProcessors
      with OccursInProjector
      with OccursInFunction
      with OccursInDomain {
    override def contents: Seq[OccursInType] = {
      typ match {
        case a: Aggregation                    => a.fields ++ a.methods
        case a: AggregateUseCaseTypeExpression => a.fields ++ a.methods
        case Enumeration(_, enumerators)       => enumerators
        case _                                 => Seq.empty[OccursInType]
      }
    }

    final override def kind: String = {
      typ match {
        case AggregateUseCaseTypeExpression(_, useCase, _) => useCase.useCase
        case _                                             => "Type"
      }
    }

    def format: String = ""
  }

  /** A reference to a type definition
    *
    * @param loc
    *   The location in the source where the reference to the type is made
    * @param keyword
    *   The keyword used to designate the type at the point of reference
    * @param pathId
    *   The path identifier of the reference type
    */
  case class TypeRef(
    loc: At = At.empty,
    keyword: String = "type",
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Type] {
    override def format: String = s"$keyword ${pathId.format}"
  }
  object TypeRef { def empty: TypeRef = TypeRef() }

  case class FieldRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Field] {
    override def format: String = s"field ${pathId.format}"
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////// CONSTANT

  /** A definition that represents a constant value for reference in behaviors
    *
    * @param loc
    *   The location in the source of the Constant
    * @param id
    *   The unique identifier of the Constant
    * @param typeEx
    *   The type expression goverining the range of values the constant can have
    * @param value
    *   The value of the constant
    * @param brief
    *   A brief descriptin of the constant
    * @param description
    *   A detailed description of the constant
    */
  case class Constant(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    value: LiteralString,
    brief: Option[LiteralString],
    description: Option[Description]
  ) extends LeafDefinition
      with OccursInProcessors
      with OccursInDomain {

    /** Format the node to a string */
    override def format: String =
      s"const ${id.format} is ${typeEx.format} = ${value.format}"
  }

  case class ConstantRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Constant] {
    override def format: String = s"constant ${pathId.format}"
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////// STATEMENTS

  /** Base trait of all Statements that can occur in OnClauses */
  sealed trait Statement extends RiddlValue with OccursInProcessors with OccursInFunction

  /** A statement whose behavior is specified as a text string allowing an arbitrary action to be specified handled by
    * RIDDL's syntax.
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param what
    *   The action to take (emitted as pseudo-code)
    */
  case class ArbitraryStatement(
    loc: At,
    what: LiteralString
  ) extends Statement {
    override def kind: String = "Arbitrary Statement"
    def format: String = what.format
  }

  /** A statement that is intended to generate a runtime error in the application or otherwise indicate an error
    * condition
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param message
    *   The error message to report
    */
  case class ErrorStatement(
    loc: At,
    message: LiteralString
  ) extends Statement {
    override def kind: String = "Error Statement"
    def format: String = s"error ${message.format}"
  }

  /** A statement that changes the focus of input in an application to a specific group
    *
    * @param loc
    *   The location of the statement
    * @param group
    *   The group that is the target of the input focus
    */
  case class FocusStatement(
    loc: At,
    group: GroupRef
  ) extends Statement {
    override def kind: String = "Focus Statement"
    def format: String = s"focus on ${group.format}"
  }

  /** A statement that sets a value of a field
    *
    * @param loc
    *   THe locaiton of the statement
    * @param field
    *   The field that is the target of the value change
    * @param value
    *   A description of the value to set as a [[LiteralString]]
    */
  case class SetStatement(
    loc: At,
    field: FieldRef,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Set Statement"
    def format: String = s"set ${field.format} to ${value.format}"
  }

  /** A statement that returns a value from a function
    *
    * @param loc
    *   The location in the source of the publish action
    * @param value
    *   The value to be returned
    */
  case class ReturnStatement(
    loc: At,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Return Statement"
    def format: String = s"return ${value.format}"
  }

  /** An action that sends a message to an [[Inlet]] or [[Outlet]].
    *
    * @param loc
    *   The location in the source of the send action
    * @param msg
    *   The constructed message to be sent
    * @param portlet
    *   The inlet or outlet to which the message is sent
    */
  case class SendStatement(
    loc: At,
    msg: MessageRef,
    portlet: PortletRef[Portlet]
  ) extends Statement {
    override def kind: String = "Send Statement"
    def format: String = s"send ${msg.format} to ${portlet.format}"
  }

  /** A statement that replies in a handler to a query
    *
    * @param loc
    *   The location in the source of the publish action
    * @param message
    *   The message to be returned
    */
  case class ReplyStatement(
    loc: At,
    message: MessageRef
  ) extends Statement {
    override def kind: String = "Reply Statement"
    def format: String = s"reply ${message.format}"
  }

  /** An statement that morphs the state of an entity to a new structure
    *
    * @param loc
    *   The location of the morph action in the source
    * @param entity
    *   The entity to be affected
    * @param state
    *   The reference to the new state structure
    */
  case class MorphStatement(
    loc: At,
    entity: EntityRef,
    state: StateRef,
    value: MessageRef
  ) extends Statement {
    override def kind: String = "Morph Statement"
    def format: String = s"morph ${entity.format} to ${state.format} with ${value.format}"
  }

  /** A statement that changes the behavior of an entity by making it use a new handler for its messages; named for the
    * "become" operation in Akka that does the same for an user.
    *
    * @param loc
    *   The location in the source of the become action
    * @param entity
    *   The entity whose behavior is to change
    * @param handler
    *   The reference to the new handler for the entity
    */
  case class BecomeStatement(
    loc: At,
    entity: EntityRef,
    handler: HandlerRef
  ) extends Statement {
    override def kind: String = "Become Statement"
    def format: String = s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the tell operator in Akka. Unlike using an
    * Portlet, this implies a direct relationship between the telling entity and the told entity. This action is
    * considered useful in "high cohesion" scenarios. Use [[SendStatement]] to reduce the coupling between entities
    * because the relationship is managed by a [[Context]] 's [[Connector]] instead.
    *
    * @param loc
    *   The location of the tell action
    * @param msg
    *   A constructed message value to send to the entity, probably a command
    * @param processorRef
    *   The processor to which the message is directed
    */
  case class TellStatement(
    loc: At,
    msg: MessageRef,
    processorRef: ProcessorRef[Processor[?]]
  ) extends Statement {
    override def kind: String = "Tell Statement"
    def format: String = s"tell ${msg.format} to ${processorRef.format}"
  }

  /** A statement that calls a function
    *
    * @param loc
    *   The location of the statement in the model
    * @param func
    *   The function to be called
    */
  case class CallStatement(
    loc: At,
    func: FunctionRef
  ) extends Statement {
    override def kind: String = "Call Statement"
    def format: String = "call ${func.format}"
  }

  /** A statement that suggests looping over the contents of a field with a non-zero cardinality, an Inlet or an outlet
    * @param loc
    *   The location of the statement in the model
    * @param ref
    *   The reference to the Field, outlet or Inlet
    * @param do_
    *   The set of statements to execute for each iteration_
    */
  case class ForEachStatement(
    loc: At,
    ref: FieldRef | OutletRef | InletRef,
    do_ : Seq[Statement]
  ) extends Statement {
    override def kind: String = "Foreach Statement"
    def format: String = s"foreach ${ref.format} do \n" +
      do_.map(_.format).mkString("\n") + "end\n"
  }

  /** A statement that represents a class if-condition-then-A-else-B construct for logic decitions.
    *
    * @param loc
    *   The location of the statement in the model
    * @param cond
    *   The conditional part of the if-then-else
    * @param thens
    *   The statements to execute if `cond` is true
    * @param elses
    *   The tsatements to execute if `cond` is false
    */
  case class IfThenElseStatement(
    loc: At,
    cond: LiteralString,
    thens: Seq[Statement],
    elses: Seq[Statement]
  ) extends Statement {
    override def kind: String = "IfThenElse Statement"
    def format: String = s"if ${cond.format} then\n{\n${thens.map(_.format).mkString("  ", "\n  ", "\n}") +
        (if elses.nonEmpty then " else {\n" + elses.map(_.format).mkString("  ", "\n  ", "\n}\n")
         else "\n")}"
  }

  /** A statement that terminates the On Clause */
  case class StopStatement(
    loc: At
  ) extends Statement {
    override def kind: String = "Stop Statement"
    def format: String = "stop ${func.format}"
  }

  /** A statement that reads data from a Repository
    *
    * @param loc
    *   The location of the statement in the model
    * @param keyword
    *   The keyword used to color the nature of the read operation
    * @param what
    *   A string describing what should be read
    * @param from
    *   A reference to the type from which the value should be read in the repository
    * @param where
    *   A string describing the conditions on the read (like a SQL WHERE clause)
    */
  case class ReadStatement(
    loc: At,
    keyword: String,
    what: LiteralString,
    from: TypeRef,
    where: LiteralString
  ) extends Statement {
    override def kind: String = "Read Statement"
    def format: String = s"$keyword ${what.format} from ${from.format} where ${where.s}"
  }

  /** A statement that describes a write to a repository
    *
    * @param loc
    *   The location of the statement in the model
    * @param keyword
    *   The keyword used to color the nature of teh write operation (e.g. update, append, etc.)
    * @param what
    *   A description of the data that should be written to the repository
    * @param to
    *   The [[TypeRef]] to the component of the Repository
    */
  case class WriteStatement(
    loc: At,
    keyword: String,
    what: LiteralString,
    to: TypeRef
  ) extends Statement {
    override def kind: String = "Write Statement"
    def format: String = s"$keyword ${what.format} to ${to.format}"
  }

  /** A statement that provides a definition of the computation to execute in a specific programming language
    *
    * @param loc
    *   The location of the statement
    * @param language
    *   The name of the programming language in which the `body` is written
    * @param body
    *   The code that should be executed by this statement.
    */
  case class CodeStatement(
    loc: At,
    language: LiteralString,
    body: String
  ) extends Statement {
    def format: String = s"```${language.s}$body```"
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////// ADAPTOR

  /** A trait that is the base trait of Adaptor directions */
  sealed trait AdaptorDirection extends RiddlValue {
    def loc: At
  }

  /** Represents an [[AdaptorDirection]] that is inbound (towards the bounded context the [[Adaptor]] was defined in)
    *
    * @param loc
    *   Location in the source of the adaptor direction
    */
  case class InboundAdaptor(loc: At) extends AdaptorDirection {
    def format: String = "from"
  }

  /** Represents an [[AdaptorDirection]] that is outbouand (towards a bounded context that is not the one that defined
    * the [[Adaptor]]
    */
  case class OutboundAdaptor(loc: At) extends AdaptorDirection {
    def format: String = "to"
  }

  /** Definition of an Adaptor. Adaptors are defined in Contexts to convert messages from another bounded context.
    * Adaptors translate incoming messages into corresponding messages using the ubiquitous language of the defining
    * bounded context. There should be one Adapter for each external Context
    *
    * @param loc
    *   Location in the parsing input
    * @param id
    *   Name of the adaptor
    * @param direction
    *   An indication of whether this is an inbound or outbound adaptor.
    * @param context
    *   A reference to the bounded context from which messages are adapted
    * @param contents
    *   The definitional contents of this Adaptor
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the adaptor.
    */
  case class Adaptor(
    loc: At,
    id: Identifier,
    direction: AdaptorDirection,
    context: ContextRef,
    contents: Seq[OccursInAdaptor] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[OccursInAdaptor]
      with OccursInContext
      with WithOptions {
    def format: String = Keyword.adaptor + " " + id.format
  }

  case class AdaptorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Adaptor] {
    override def format: String = Keyword.adaptor + " " + pathId.format
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////// FUNCTION

  /** A function definition which can be part of a bounded context or an entity.
    *
    * @param loc
    *   The location of the function definition
    * @param id
    *   The identifier that names the function
    * @param input
    *   An optional type expression that names and types the fields of the input of the function
    * @param output
    *   An optional type expression that names and types the fields of the output of the function
    * @param contents
    *   The set of types, functions, statements, authors, includes and terms that define this FUnction
    * @param statements
    *   The set of statements that define the behavior of the function
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the function.
    */
  case class Function(
    loc: At,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    contents: Contents[OccursInFunction] = Seq.empty,
    statements: Seq[Statement] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[OccursInFunction]
      with WithTypes
      with WithFunctions
      with OccursInFunction
      with OccursInProcessors {
    override def format: String = Keyword.function + " " + id.format
    final override inline def kind: String = "Function"
    override def isEmpty: Boolean = statements.isEmpty && input.isEmpty && output.isEmpty
  }

  /** A reference to a function.
    *
    * @param loc
    *   The location of the function reference.
    * @param pathId
    *   The path identifier of the referenced function.
    */
  case class FunctionRef(loc: At, pathId: PathIdentifier) extends Reference[Function] {
    override def format: String = Keyword.function + " " + pathId.format
  }

  /** An invariant expression that can be used in the definition of an entity. Invariants provide conditional
    * expressions that must be true at all times in the lifecycle of an entity.
    *
    * @param loc
    *   The location of the invariant definition
    * @param id
    *   The name of the invariant
    * @param condition
    *   The string representation of the condition that ought to be true
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the invariant.
    */
  case class Invariant(
    loc: At,
    id: Identifier,
    condition: Option[LiteralString] = Option.empty[LiteralString],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursInProcessors
      with OccursInState {
    override def isEmpty: Boolean = condition.isEmpty
    def format: String = Keyword.invariant + " " + id.format + condition.map(_.format)
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////// ON CLAUSE

  /** A sealed trait for the kinds of OnClause that can occur within a Handler definition.
    */
  sealed trait OnClause extends LeafDefinition with OccursInHandler {
    def statements: Seq[Statement]
  }

  /** Defines the actions to be taken when a message does not match any of the OnMessageClauses. OnOtherClause
    * corresponds to the "other" case of an [[Handler]].
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param statements
    *   A set of examples that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnOtherClause(
    loc: At,
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Other")

    override def isEmpty: Boolean = statements.isEmpty

    override def kind: String = "On Other"

    override def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param statements
    *   A set of statements that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnInitializationClause(
    loc: At,
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Init")

    override def isEmpty: Boolean = statements.isEmpty

    override def kind: String = "On Init"

    override def format: String = ""
  }

  /** Defines the actions to be taken when a particular message is received by an entity. [[OnMessageClause]]s are used
    * in the definition of a [[Handler]] with one for each kind of message that handler deals with.
    *
    * @param loc
    *   The location of the "on" clause
    * @param msg
    *   A reference to the message type that is handled
    * @param from
    *   Optional message generating
    * @param statements
    *   A set of statements that define the behavior when the [[msg]] is received.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnMessageClause(
    loc: At,
    msg: MessageRef,
    from: Option[(Option[Identifier], Reference[Definition])],
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(msg.loc, s"On ${msg.format}")

    override def isEmpty: Boolean = statements.isEmpty

    def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param statements
    *   A set of statements that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnTerminationClause(
    loc: At,
    statements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Term")

    override def isEmpty: Boolean = statements.isEmpty

    override def kind: String = "On Term"

    override def format: String = ""
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////// HANDLER

  /** A named handler of messages (commands, events, queries) that bundles together a set of [[OnMessageClause]]
    * definitions and by doing so defines the behavior of an entity. Note that entities may define multiple handlers and
    * switch between them to change how it responds to messages over time or in response to changing conditions
    *
    * @param loc
    *   The location of the handler definition
    * @param id
    *   The name of the handler.
    * @param contents
    *   The set of [[OnMessageClause]] definitions and comments that define how the entity responds to received
    *   messages.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the handler
    */
  case class Handler(
    loc: At,
    id: Identifier,
    contents: Seq[OccursInHandler] = Seq.empty[OccursInHandler],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Definition
      with Container[OccursInHandler]
      with OccursInAdaptor
      with OccursInApplication
      with OccursInContext
      with OccursInEntity
      with OccursInState
      with OccursInRepository
      with OccursInStreamlet
      with OccursInProjector {
    override def isEmpty: Boolean = clauses.isEmpty

    def clauses: Seq[OnClause] = contents.filter[OnClause]

    def format: String = s"${Keyword.handler} ${id.format}"
  }

  /** A reference to a Handler
    *
    * @param loc
    *   The location of the handler reference
    * @param pathId
    *   The path identifier of the referenced handler
    */
  case class HandlerRef(loc: At, pathId: PathIdentifier) extends Reference[Handler] {
    def format: String = Keyword.handler + " " + pathId.format
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////////////////// STATE

  /** Represents the state of an entity. The MorphAction can cause the state definition of an entity to change.
    *
    * @param loc
    *   The location of the state definition
    * @param id
    *   The name of the state definition
    * @param typ
    *   A reference to a type definition that provides the range of values that the state may assume.
    * @param handlers
    *   The handler definitions that may occur when this state is active
    * @param invariants
    *   Expressions of boolean logic that must always evaluate to true before and after an entity changes when this
    *   state is active.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the state.
    */
  case class State(
    loc: At,
    id: Identifier,
    typ: TypeRef,
    handlers: Seq[Handler] = Seq.empty[Handler],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Definition
      with Container[OccursInState]
      with OccursInEntity {
    override def contents: Seq[OccursInState] = handlers ++ invariants
    def format: String = Keyword.state + " " + id.format
  }

  /** A reference to an entity's state definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced state definition
    */
  case class StateRef(loc: At, pathId: PathIdentifier) extends Reference[State] {
    def format: String = Keyword.state + " " + pathId.format
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////// ENTITY

  /** Definition of an Entity
    *
    * @param loc
    *   The location in the input
    * @param id
    *   The name of the entity
    * @param contents
    *   The definitional content of this entity: handlers, states, functions, invariants, etc.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the entity
    */
  case class Entity(
    loc: At,
    id: Identifier,
    contents: Seq[OccursInEntity] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description]
  ) extends Processor[OccursInEntity]
      with WithStates
      with WithOptions
      with OccursInContext {
    override def format: String = Keyword.entity + " " + id.format
  }

  /** A reference to an entity
    *
    * @param loc
    *   The location of the entity reference
    * @param pathId
    *   The path identifier of the referenced entity.
    */
  case class EntityRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Entity] {
    def format: String = Keyword.entity + " " + pathId.format
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////// REPOSITORY

  enum RepositorySchemaKind:
    case Other, Flat, Relational, TimeSeries, Graphical, Hierarchical, Star, Document, Columnar, Vector

  /** The repository schema defined as an identifier of the schema, a general kind of intended schema, and the
    * representation of the schema as data node types (vertices, tables, vectors, etc.), a list of named connections
    * between pairs of the data nodes (foreign keys, parent/child, arbitrary graph nodes, etc.), and indices on specific
    * fields of the data nodes.
    * @param loc
    *   The location at which the schema occurs
    * @param id
    *   The name of this schema
    * @param schemaKind
    *   One of the RepositorySchemaKinds for a general sense of the repository intention
    * @param data
    *   A list of the named primary data nodes (tables, vectors, vertices)
    * @param connectors
    *   A list of named relations between primary data nodes #indices A list of fields in the ((data)) or ((connectors)
    *   that are considered indexed for faster retrieval
    */
  case class Schema(
    loc: At,
    id: Identifier,
    schemaKind: RepositorySchemaKind = RepositorySchemaKind.Other,
    data: Map[Identifier, TypeRef],
    connectors: Map[Identifier, (TypeRef, TypeRef)],
    indices: Seq[FieldRef]
  ) extends OccursInRepository {
    def format: String = Keyword.schema + " " + id.format + s" is $schemaKind"
  }

  /** A RIDDL repository is an abstraction for anything that can retain information(e.g. messages for retrieval at a
    * later time. This might be a relational database, NoSQL database, data lake, API, or something not yet invented.
    * There is no specific technology implied other than the retention and retrieval of information. You should think of
    * repositories more like a message-oriented version of the Java Repository Pattern than any particular kind
    * ofdatabase.
    *
    * @see
    *   https://java-design-patterns.com/patterns/repository/#explanation
    * @param loc
    *   Location in the source of the Repository
    * @param id
    *   The unique identifier for this Repository
    * @param contents
    *   The definitional content of this Repository: types, handlers, inlets, outlets, etc.
    * @param brief
    *   A brief description of this repository
    * @param description
    *   A detailed description of this repository
    */
  case class Repository(
    loc: At,
    id: Identifier,
    contents: Contents[OccursInRepository] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[OccursInRepository]
      with OccursInContext
      with WithOptions {
    def format: String = Keyword.entity + " " + id.format
  }

  /** A reference to a repository definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class RepositoryRef(loc: At, pathId: PathIdentifier)
      extends Reference[Repository]
      with ProcessorRef[Projector]
      with OccursInProjector {
    override def format: String = s"repository ${pathId.format}"
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////// PROJECTOR

  /** Projectors get their name from Euclidean Geometry but are probably more analogous to a relational database view.
    * The concept is very simple in RIDDL: projectors gather data from entities and other sources, transform that data
    * into a specific record type, and support querying that data arbitrarily.
    *
    * @see
    *   https://en.wikipedia.org/wiki/View_(SQL)).
    * @see
    *   https://en.wikipedia.org/wiki/Projector_(mathematics)
    * @param loc
    *   Location in the source of the Projector
    * @param id
    *   The unique identifier for this Projector
    * @param contents
    *   The content of this Projectors' definition
    * @param brief
    *   A brief description of this Projector
    * @param description
    *   A detailed description of this Projector
    */
  case class Projector(
    loc: At,
    id: Identifier,
    contents: Contents[OccursInProjector] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[OccursInProjector]
      with OccursInContext
      with WithOptions {
    lazy val repositories: Seq[RepositoryRef] = contents.filter[RepositoryRef]
    def format: String = Keyword.projector + " " + id.format
  }

  /** A reference to an context's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class ProjectorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Projector] {
    override def format: String = Keyword.projector + " " + pathId.format
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////// CONTEXT

  /** A bounded context definition. Bounded contexts provide a definitional boundary on the language used to describe
    * some aspect of a system. They imply a tightly integrated ecosystem of one or more microservices that share a
    * common purpose. Context can be used to house entities, read side projectors, sagas, adaptations to other contexts,
    * apis, and etc.
    *
    * @param loc
    *   The location of the bounded context definition
    * @param id
    *   The name of the context
    * @param contents
    *   The definitional content for this Context
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the context
    */
  case class Context(
    loc: At,
    id: Identifier,
    contents: Contents[OccursInContext] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[OccursInContext]
      with WithProjectors
      with WithRepositories
      with WithEntities
      with WithStreamlets
      with WithConnectors
      with WithAdaptors
      with WithSagas
      with WithOptions
      with OccursInDomain {
    def format: String = Keyword.context + " " + id.format
  }

  object Context {
    lazy val empty: Context = Context(At.empty, Identifier.empty)
  }

  /** A reference to a bounded context
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier for the referenced context
    */
  case class ContextRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Context] {
    override def format: String = s"context ${pathId.format}"
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////// STREAMLET

  /** A sealed trait for Inlets and Outlets */
  sealed trait Portlet extends LeafDefinition with OccursInProcessors

  /** A streamlet that supports input of data of a particular type.
    *
    * @param loc
    *   The location of the Inlet definition
    * @param id
    *   The name of the inlet
    * @param type_
    *   The type of the data that is received from the inlet
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the Inlet
    */
  case class Inlet(
    loc: At,
    id: Identifier,
    type_ : TypeRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Portlet
      with LeafDefinition
      with OccursInProcessors {
    def format: String = s"inlet ${id.format} is ${type_.format}"
  }

  /** A streamlet that supports output of data of a particular type.
    *
    * @param loc
    *   The location of the outlet definition
    * @param id
    *   The name of the outlet
    * @param type_
    *   The type expression for the kind of data put out
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the Outlet.
    */
  case class Outlet(
    loc: At,
    id: Identifier,
    type_ : TypeRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Portlet
      with LeafDefinition
      with OccursInProcessors {
    def format: String = s"outlet ${id.format} is ${type_.format}"
  }

  /** A connector between an [[com.ossuminc.riddl.language.AST.Outlet]] and an [[com.ossuminc.riddl.language.AST.Inlet]]
    * that flows a particular [[com.ossuminc.riddl.language.AST.Type]].
    * @param loc
    *   The location at which the connector is defined
    * @param id
    *   The unique identifier of the connector
    * @param from
    *   The origin Outlet of the connector
    * @param to
    *   THe destination Inlet of the connector
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the connector
    */
  case class Connector(
    loc: At,
    id: Identifier,
    from: OutletRef = OutletRef.empty,
    to: InletRef = InletRef.empty,
    options: Seq[OptionValue] = Seq.empty[OptionValue],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description]
  ) extends LeafDefinition
      with OccursInContext {
    def hasOption(name: String): Boolean = options.exists(_.name == name)
    override def format: String = Keyword.connector + " " + id.format

    override def isEmpty: Boolean = super.isEmpty && from.isEmpty && to.isEmpty && options.isEmpty
  }

  sealed trait StreamletShape extends RiddlValue {
    def keyword: String
  }

  case class Void(loc: At) extends StreamletShape {
    def format: String = "void"

    def keyword: String = "void"
  }

  case class Source(loc: At) extends StreamletShape {
    def format: String = "source"

    def keyword: String = "source"
  }

  case class Sink(loc: At) extends StreamletShape {
    def format: String = "sink"

    def keyword: String = "sink"
  }

  case class Flow(loc: At) extends StreamletShape {
    def format: String = "flow"

    def keyword: String = "flow"
  }

  case class Merge(loc: At) extends StreamletShape {
    def format: String = "merge"

    def keyword: String = "merge"
  }

  case class Split(loc: At) extends StreamletShape {
    def format: String = "split"

    def keyword: String = "split"
  }

  case class Router(loc: At) extends StreamletShape {
    def format: String = "router"

    def keyword: String = "router"
  }

  /** Definition of a Streamlet. A computing element for processing data from [[Inlet]]s to [[Outlet]]s. A processor's
    * processing is specified by free text statements in [[Handler]]s. Streamlets come in various shapes: Source, Sink,
    * Flow, Merge, Split, and Router depending on how many inlets and outlets they have
    *
    * @param loc
    *   The location of the Processor definition
    * @param id
    *   The name of the processor
    * @param shape
    *   The shape of the processor's inputs and outputs
    * @param contents
    *   The definitional content for this Context
    * @param description
    *   An optional description of the processor
    */
  case class Streamlet(
    loc: At,
    id: Identifier,
    shape: StreamletShape,
    contents: Contents[OccursInStreamlet] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[OccursInStreamlet]
      with WithOptions
      with OccursInContext {
    final override def kind: String = shape.getClass.getSimpleName
    def format: String = shape.keyword + " " + id.format

    shape match {
      case Source(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.isEmpty),
          s"Invalid Source Streamlet ins: ${outlets.size} == 1, ${inlets.size} == 0"
        )
      case Sink(_) =>
        require(
          isEmpty || (outlets.isEmpty && inlets.size == 1),
          "Invalid Sink Streamlet"
        )
      case Flow(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.size == 1),
          "Invalid Flow Streamlet"
        )
      case Merge(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.size >= 2),
          "Invalid Merge Streamlet"
        )
      case Split(_) =>
        require(
          isEmpty || (outlets.size >= 2 && inlets.size == 1),
          "Invalid Split Streamlet"
        )
      case Router(_) =>
        require(
          isEmpty || (outlets.size >= 2 && inlets.size >= 2),
          "Invalid Router Streamlet"
        )
      case Void(_) =>
        require(
          isEmpty || (outlets.isEmpty && inlets.isEmpty),
          "Invalid Void Stream"
        )
    }

  }

  /** A reference to an context's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class StreamletRef(loc: At, keyword: String, pathId: PathIdentifier) extends ProcessorRef[Streamlet] {
    override def format: String = s"$keyword ${pathId.format}"
  }

  /** Sealed base trait of references to [[Inlet]]s or [[Outlet]]s
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed trait PortletRef[+T <: Portlet] extends Reference[T]

  /** A reference to an [[Inlet]]
    *
    * @param loc
    *   The location of the inlet reference
    * @param pathId
    *   The path identifier of the referenced [[Inlet]]
    */
  case class InletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Inlet] {
    override def format: String = s"inlet ${pathId.format}"
  }
  object InletRef { def empty: InletRef = InletRef(At.empty, PathIdentifier.empty) }

  /** A reference to an [[Outlet]]
    *
    * @param loc
    *   The location of the outlet reference
    * @param pathId
    *   The path identifier of the referenced [[Outlet]]
    */
  case class OutletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Outlet] {
    override def format: String = s"outlet ${pathId.format}"
  }
  object OutletRef { def empty: OutletRef = OutletRef(At.empty, PathIdentifier.empty) }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////////////// SAGA

  /** The definition of one step in a saga with its undo step and example.
    *
    * @param loc
    *   The location of the saga action definition
    * @param id
    *   The name of the SagaAction
    * @param doStatements
    *   The command to be done.
    * @param undoStatements
    *   The command that undoes [[doStatements]]
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga action
    */
  case class SagaStep(
    loc: At,
    id: Identifier,
    doStatements: Seq[Statement] = Seq.empty[Statement],
    undoStatements: Seq[Statement] = Seq.empty[Statement],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursInSaga {
    def format: String = s"step ${id.format}"
  }

  /** The definition of a Saga based on inputs, outputs, and the set of [[SagaStep]]s involved in the saga. Sagas define
    * a computing action based on a variety of related commands that must all succeed atomically or have their effects
    * undone.
    *
    * @param loc
    *   The location of the Saga definition
    * @param id
    *   The name of the saga
    * @param input
    *   A definition of the aggregate input values needed to invoke the saga, if any.
    * @param output
    *   A definition of the aggregate output values resulting from invoking the saga, if any.
    * @param contents
    *   The definitional content for this Context
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga.
    */
  case class Saga(
    loc: At,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    contents: Contents[OccursInSaga] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[OccursInSaga]
      with WithSagaSteps
      with WithOptions
      with OccursInContext
      with OccursInDomain {
    override def format: String = Keyword.saga + " " + id.format
    override def isEmpty: Boolean = super.isEmpty && input.isEmpty && output.isEmpty

  }

  case class SagaRef(loc: At, pathId: PathIdentifier) extends Reference[Saga] {
    def format: String = s"saga ${pathId.format}"
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////// EPIC

  /** A reference to an User using a path identifier
    *
    * @param loc
    *   THe location of the User in the source code
    * @param pathId
    *   The path identifier that locates the User
    */
  case class UserRef(loc: At, pathId: PathIdentifier) extends Reference[User] {
    def format: String = s"user ${pathId.format}"
  }

  sealed trait Interaction extends DescribedValue with BrieflyDescribedValue with OccursInUseCase

  sealed trait GenericInteraction extends Interaction {
    def relationship: LiteralString
  }

  /** One abstract step in an Interaction between things. The set of case classes associated with this sealed trait
    * provide more type specificity to these three fields.
    */
  sealed trait TwoReferenceInteraction extends GenericInteraction {
    def from: Reference[Definition]

    def to: Reference[Definition]
  }

  sealed trait InteractionContainer extends Interaction with Container[Interaction | Comment] with WithComments {

    /** Format the node to a string */
    override def format: String = s"Interaction"
  }

  /** An interaction expression that specifies that each contained expression should be executed in parallel
    *
    * @param loc
    *   Location of the parallel group
    * @param contents
    *   The expressions to execute in parallel
    * @param brief
    *   A brief description of the parallel group
    */
  case class ParallelInteractions(
    loc: At,
    contents: Contents[Interaction | Comment] = Seq.empty[Interaction | Comment],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends InteractionContainer {
    override def kind: String = "Parallel Interaction"
  }

  /** An interaction expression that specifies that each contained expression should be executed in strict sequential
    * order
    *
    * @param loc
    *   Location of the sequence
    * @param contents
    *   The interactions to execute in sequence
    * @param brief
    *   A brief description of the sequence group
    * @param description
    *   A longer description of the sequence
    */
  case class SequentialInteractions(
    loc: At,
    contents: Contents[Interaction | Comment] = Seq.empty[Interaction | Comment],
    brief: Option[LiteralString],
    description: Option[Description] = None
  ) extends InteractionContainer {
    override def kind: String = "Sequential Interaction"
  }

  /** An interaction expression that specifies that its contents are optional
    *
    * @param loc
    *   The location of the optional group
    * @param contents
    *   The optional expressions
    * @param brief
    *   A brief description of the optional group
    */
  case class OptionalInteractions(
    loc: At,
    contents: Contents[Interaction | Comment] = Seq.empty[Interaction | Comment],
    brief: Option[LiteralString],
    description: Option[Description] = None
  ) extends InteractionContainer {
    override def kind: String = "Optional Interaction"
  }

  /** An [[GenericInteraction]] that is vaguely written as a textual description */
  case class VagueInteraction(
    loc: At,
    from: LiteralString,
    relationship: LiteralString,
    to: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Vague Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** A [[GenericInteraction]] that involves sending a message between the interacting components
    *
    * @param loc
    *   The location of the interaction definition
    * @param from
    *   The definition that originates the interaction
    * @param message
    *   The message that is sent to the `to` component
    * @param to
    *   A [[Reference]] to the [[Processor]] that receives the sent `message`
    * @param brief
    *   A brief description of this interaction
    * @param description
    *   A full description of this interaction
    */
  case class SendMessageInteraction(
    loc: At,
    from: Reference[Definition],
    message: MessageRef,
    to: ProcessorRef[?],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    def relationship: LiteralString = {
      LiteralString(message.loc, s"sends ${message.format} to")
    }

    override def kind: String = "Send Message Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** An arbitrary interaction step. The abstract nature of the relationship is
    *
    * @param loc
    *   The location of the step
    * @param from
    *   A reference to the source of the interaction
    * @param relationship
    *   A literal spring that specifies the arbitrary relationship
    * @param to
    *   A reference to the destination of the interaction
    * @param brief
    *   A brief description of the interaction step
    */
  case class ArbitraryInteraction(
    loc: At,
    from: Reference[Definition],
    relationship: LiteralString,
    to: Reference[Definition],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends TwoReferenceInteraction {
    override def kind: String = "Arbitrary Interaction"

    def format: String = s"${from.format} ${relationship.s} ${to.format}"

  }

  /** An [[TwoReferenceInteraction]] between a [[Definition]] and itself
    *
    * @param loc
    *   The location at which the interaction occurs
    * @param from
    *   A reference to a [[Definition]] from which the relationship extends and to which it returns.
    * @param relationship
    *   A textual description of the relationship
    * @param brief
    *   A brief description of the interaction for documentation purposes
    * @param description
    *   A full description of the interaction for documentation purposes
    */
  case class SelfInteraction(
    loc: At,
    from: Reference[Definition],
    relationship: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends TwoReferenceInteraction {
    override def kind: String = "Self Interaction"
    override def to: Reference[Definition] = from
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** An interaction where an User receives output
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The User that is being focused
    * @param to
    *   The Group that is the target of the focus
    * @param brief
    *   A brief description of this interaction
    */
  case class FocusOnGroupInteraction(
    loc: At,
    from: UserRef,
    to: GroupRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends TwoReferenceInteraction {
    override def kind: String = "Focus On Group"
    override def relationship: LiteralString =
      LiteralString(loc + (6 + from.pathId.format.length), "focuses on")
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** An interaction between a ser and a URL
    * @param loc
    *   THe location of the interaction in the model
    * @param from
    *   The user from which the interaction emanates
    * @param url
    *   The URL towards which the user is directed
    * @param brief
    *   A brief description for documentation purposes
    * @param description
    *   A more full description of the interaction for documentation purposes
    */
  case class DirectUserToURLInteraction(
    loc: At,
    from: UserRef,
    url: java.net.URL,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    def relationship: LiteralString =
      LiteralString(loc + (6 + from.pathId.format.length), "directed to ")
    override def kind: String = "Direct User To URL"
    def format: String = s"${from.format} ${relationship.s} ${url.toExternalForm}"
  }

  /** An interaction where an User receives output
    * @param loc
    *   The locaiton of the interaction in the source
    * @param from
    *   The output received
    * @param relationship
    *   THe name of the relationship
    * @param to
    *   THe user that receives the output
    * @param brief
    *   A brief description of this interaction
    */
  case class ShowOutputInteraction(
    loc: At,
    from: OutputRef,
    relationship: LiteralString,
    to: UserRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends TwoReferenceInteraction {
    override def kind: String = "Show Output Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** A interaction where a User selects an command generating item
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The user providing the input
    * @param to
    *   The input definition that receives the input
    * @param brief
    *   A description of this interaction step
    */
  case class SelectInputInteraction(
    loc: At,
    from: UserRef,
    to: InputRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends TwoReferenceInteraction {
    override def kind: String = "Select Input Interaction"
    def format: String = s"${from.format} selects ${to.format}"
    def relationship: LiteralString = LiteralString(loc, "selects")
  }

  /** A interaction where and User provides input
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The user providing the input
    * @param to
    *   The input definition that receives the input
    * @param brief
    *   A description of this interaction step
    */
  case class TakeInputInteraction(
    loc: At,
    from: UserRef,
    to: InputRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends TwoReferenceInteraction {
    override def kind: String = "Take Input Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
    def relationship: LiteralString = LiteralString(loc, "Provides data to")
  }

  /** The definition of a Jacobsen Use Case RIDDL defines these epics by allowing a linkage between the user and RIDDL
    * applications or bounded contexts.
    * @param loc
    *   Where in the source this use case occurs
    * @param id
    *   The unique identifier for this use case
    * @param contents
    *   The interactions between users and system components that define the use case.
    * @param brief
    *   A brief description of this use case
    * @param description
    *   A longer description of this use case
    */
  case class UseCase(
    loc: At,
    id: Identifier,
    userStory: UserStory = UserStory(),
    contents: Contents[Interaction | Comment] = Seq.empty[Interaction | Comment],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Definition
      with Container[Interaction | Comment]
      with OccursInEpic {
    override def kind: String = "UseCase"
    override def format: String = s"case ${id.format}"
  }

  /** An agile user story definition in the usual "As a {role} I want {capability} so that {benefit}" style.
    *
    * @param loc
    *   Location of the user story
    * @param user
    *   The user, or instigator, of the story.
    * @param capability
    *   The capability the user wishes to utilize
    * @param benefit
    *   The benefit of that utilization
    */
  case class UserStory(
    loc: At = At.empty,
    user: UserRef = UserRef(At.empty, PathIdentifier.empty),
    capability: LiteralString = LiteralString.empty,
    benefit: LiteralString = LiteralString.empty
  ) extends RiddlValue {
    def format: String = {
      user.format + " wants to " + capability.s + " so that " + benefit.s
    }
    override def isEmpty: Boolean = loc.isEmpty && user.isEmpty && capability.isEmpty && benefit.isEmpty
  }

  /** The definition of an Epic that bundles multiple Jacobsen Use Cases into an overall story about user interactions
    * with the system. This define functionality from the perspective of users (men or machines) interactions with the
    * system that is part of their role.
    *
    * @param loc
    *   The location of the Epic definition
    * @param id
    *   The name of the Epic
    * @param userStory
    *   The [[UserStory]] (per agile and xP) that provides the overall big picture of this Epic
    * @param shownBy
    *   A list of URLs to visualizations or other materials related to the epic
    * @param contents
    *   The definitional content for this Context
    * @param brief
    *   A brief description (one sentence) for use in the glossary and summaries.
    * @param description
    *   An more detailed description of the Epic
    */
  case class Epic(
    loc: At,
    id: Identifier,
    userStory: Option[UserStory] = Option.empty[UserStory],
    shownBy: Seq[java.net.URL] = Seq.empty[java.net.URL],
    contents: Seq[OccursInEpic] = Seq.empty,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[OccursInEpic]
      with WithUseCases
      with WithOptions
      with OccursInDomain {

    override def isEmpty: Boolean = contents.isEmpty && shownBy.isEmpty && userStory.isEmpty

    override def format: String = s"$kind ${id.format}"
  }

  /** A reference to a Story definintion.
    * @param loc
    *   Location of the StoryRef
    * @param pathId
    *   The path id of the referenced Story
    */
  case class EpicRef(loc: At, pathId: PathIdentifier) extends Reference[Epic] {
    def format: String = s"epic ${pathId.format}"
  }

  ///////////////////////////////////////////////////////////////////////////////////////////////////////// APPLICATION

  /** A group of GroupDefinition that can be treated as a whole. For example, a form, a button group, etc.
    * @param loc
    *   The location of the group
    * @param id
    *   The unique identifier of the group
    * @param elements
    *   The list of GroupDefinition
    * @param brief
    *   A brief description of the group
    * @param description
    *   A more detailed description of the group
    */
  case class Group(
    loc: At,
    alias: String,
    id: Identifier,
    shownBy: Option[java.net.URL] = None,
    elements: Seq[OccursInGroup] = Seq.empty[OccursInGroup],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Definition
      with Container[OccursInGroup]
      with OccursInApplication
      with OccursInGroup {
    override def isAppRelated: Boolean = true

    override lazy val contents: Seq[OccursInGroup] = { elements }

    override def identify: String = s"$alias ${id.value}"

    /** Format the node to a string */
    override def format: String = s"group ${id.value}"
  }

  /** A Reference to a Group
    *
    * @param loc
    *   The At locator of the group reference
    * @param keyword
    *   The keyword used to introduce the Group
    * @param pathId
    *   The path to the referenced group
    */
  case class GroupRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Group] {
    def format: String = s"$keyword ${pathId.format}"
  }

  /** A Group contained within a group
    *
    * @param loc
    *   Location of the contained group
    * @param id
    *   The name of the group contained
    * @param group
    *   The contained group as a reference to that group
    */
  case class ContainedGroup(
    loc: At,
    id: Identifier,
    group: GroupRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with OccursInGroup {

    def format: String = s"contains ${id.format} as ${group.format}"
  }

  /** A UI Element that presents some information to the user
    *
    * @param loc
    *   Location of the view in the source
    * @param id
    *   unique identifier oof the view
    * @param putOut
    *   A result reference for the data too be presented
    * @param outputs
    *   Any contained outputs
    * @param brief
    *   A brief description of the view
    * @param description
    *   A detailed description of the view
    */
  case class Output(
    loc: At,
    nounAlias: String,
    id: Identifier,
    verbAlias: String,
    putOut: TypeRef | ConstantRef | LiteralString,
    outputs: Seq[OccursInOutput] = Seq.empty[OccursInOutput],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Definition
      with Container[OccursInOutput]
      with OccursInApplication
      with OccursInOutput
      with OccursInGroup {
    override def kind: String = if nounAlias.nonEmpty then nounAlias else super.kind
    override def isAppRelated: Boolean = true

    override def identify: String = s"$verbAlias ${id.value}"

    override lazy val contents: Seq[OccursInOutput] = outputs

    /** Format the node to a string */
    override def format: String = s"$kind ${id.value} $verbAlias ${putOut.format}"
  }

  /** A reference to an Output using a path identifier
    *
    * @param loc
    *   The location of the ViewRef in the source code
    * @param pathId
    *   The path identifier that refers to the View
    */
  case class OutputRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Output] {
    def format: String = s"$keyword ${pathId.format}"
  }

  /** An Input is a UI Element to allow the user to provide some data to the application. It is analogous to a form in
    * HTML
    *
    * @param loc
    *   Location of the Give
    * @param id
    *   Name of the give
    * @param putIn
    *   a Type reference of the type given by the user
    * @param brief
    *   A brief description of the Give
    * @param description
    *   a detailed description of the Give
    */
  case class Input(
    loc: At,
    nounAlias: String,
    id: Identifier,
    verbAlias: String,
    putIn: TypeRef,
    inputs: Seq[OccursInInput] = Seq.empty[OccursInInput],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Definition
      with Container[OccursInInput]
      with OccursInApplication
      with OccursInGroup
      with OccursInInput {
    override def kind: String = if nounAlias.nonEmpty then nounAlias else super.kind
    override def isAppRelated: Boolean = true

    override lazy val contents: Seq[OccursInInput] = inputs

    override def identify: String = s"$verbAlias ${id.value}"

    /** Format the node to a string */
    override def format: String = {
      s"$kind $verbAlias ${putIn.format}"
    }
  }

  /** A reference to an Input using a path identifier
    *
    * @param loc
    *   THe location of the GiveRef in the source code
    * @param pathId
    *   The path identifier that refers to the Give
    */
  case class InputRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Input] {
    def format: String = s"$keyword ${pathId.format}"
  }

  /** An application from which a person, robot, or other active agent (the user) will obtain information, or to which
    * that user will provided information.
    * @param loc
    *   The location of the application in the source
    * @param id
    *   The unique identifier for the application
    * @param contents
    *   The definitional content for this Context
    * @param brief
    *   A brief description of the application
    * @param description
    *   A longer description of the application.
    */
  case class Application(
    loc: At,
    id: Identifier,
    contents: Seq[OccursInApplication] = Seq.empty,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Processor[OccursInApplication]
      with WithGroups
      with OccursInDomain {
    override def isAppRelated: Boolean = true
    override def format: String = Keyword.application + " " + id.format
  }

  /** A reference to an Application using a path identifier
    *
    * @param loc
    *   THe location of the ApplicationRef in the source code
    * @param pathId
    *   The path identifier that refers to the Application
    */
  case class ApplicationRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Application] {
    def format: String = Keyword.application + " " + pathId.format
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////// DOMAIN

  /** The definition of a domain. Domains are the highest building block in RIDDL and may be nested inside each other to
    * form a hierarchy of domains. Generally, domains follow hierarchical organization structure but other taxonomies
    * and ontologies may be modelled with domains too.
    *
    * @param loc
    *   The location of the domain definition
    * @param id
    *   The name of the domain
    * @param contents
    *   The definitional content for this Context
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the domain.
    */
  case class Domain(
    loc: At,
    id: Identifier,
    contents: Seq[OccursInDomain] = Seq.empty,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends VitalDefinition[OccursInDomain]
      with OccursAtRootScope
      with WithTypes
      with WithAuthors
      with WithAuthorRefs
      with WithContexts
      with WithUsers
      with WithApplications
      with WithEpics
      with WithSagas
      with WithDomains
      with WithOptions
      with OccursInDomain {
    override def format: String = Keyword.domain + " " + id.format
  }

  /** A reference to a domain definition
    *
    * @param loc
    *   The location at which the domain definition occurs
    * @param pathId
    *   The path identifier for the referenced domain.
    */
  case class DomainRef(loc: At, pathId: PathIdentifier) extends Reference[Domain] {
    override def format: String = s"domain ${pathId.format}"
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////////////// FUNCTIONS

  /** Find the authors for some definition
    *
    * @param defn
    *   The definition whose [[AST.Author]]s we are seeking
    * @param parents
    *   The parents of the definition whose [[AST.Author]]s we are seeking
    * @return
    *   The list of [[AST.Author]]s of definition
    */
  def findAuthors(
    defn: RiddlValue,
    parents: Seq[Container[RiddlValue]]
  ): Seq[AuthorRef] = {
    if defn.hasAuthorRefs then {
      defn.asInstanceOf[WithAuthorRefs].authorRefs
    } else {
      parents
        .find(d => d.isInstanceOf[WithAuthorRefs] && d.asInstanceOf[WithAuthorRefs].hasAuthorRefs)
        .map(_.asInstanceOf[WithAuthorRefs].authorRefs)
        .getOrElse(Seq.empty[AuthorRef])
    }
  }

  /** Get all the top level domain definitions even if they are in include statements
    * @param root
    *   The model's [[AST.Root]] node.
    * @return
    *   A Seq of [[AST.Domain]]s as a [[AST.Contents]] extension
    */
  def getTopLevelDomains(root: Root): Contents[Domain] = {
    root.domains ++ root.includes.flatMap(_.contents.filter[Domain])
  }

  /** Get all the first level nested domains of a domain even if they are in include statements
    * @param domain
    *   The parent [[AST.Domain]] whose subdomains will be returned
    * @return
    *   The subdomains of the provided domain as a [[AST.Contents]] extension
    */
  def getDomains(domain: Domain): Contents[Domain] = {
    domain.domains ++ domain.includes.flatMap(_.contents.filter[Domain])
  }

  /** Get the bounded contexts defined in a domain even if they are in includes of that domain
    * @param domain
    *   The domain whose contexts should be returned
    * @return
    *   A Seq of Context expressed as a [[AST.Contents]] extension
    */
  def getContexts(domain: Domain): Contents[Context] = {
    domain.contexts ++ domain.includes.flatMap(_.contents.filter[Context])
  }

  /** Get all the applications defined in a domain even if they are in includes of that domain
    * @param domain
    *   The domain whose applications should be returned
    * @return
    *   A Seq of [[AST.Application]] expressed as a [[AST.Contents]] extension
    */
  def getApplications(domain: Domain): Contents[Application] = {
    domain.applications ++ domain.includes.flatMap(_.contents.filter[Application])
  }

  /** get all the epics defined in a domain even if they are in includes of that domain
    *
    * @param domain
    *   The domain to examine for epics
    * @return
    *   A [[scala.Seq]] of [[AST.Epic]] expressed as a [[AST.Contents]] extension
    */
  def getEpics(domain: Domain): Contents[Epic] = {
    domain.epics ++ domain.includes.flatMap(_.contents.filter[Epic])
  }

  /** get all the entities defined in a context even if they are in includes of that domain
    *
    * @param context
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Entity]] expressed as a [[AST.Contents]] extension
    */
  def getEntities(context: Context): Contents[Entity] = {
    context.entities ++ context.includes.flatMap(_.contents.filter[Entity])
  }

  /** get all the authors defined in a domain even if they are in includes of that domain
    *
    * @param domain
    *   The domain to examine for authors
    * @return
    *   A Seq of [[AST.Author]] expressed as a [[AST.Contents]] extension
    */
  def getAuthors(domain: Domain): Contents[Author] = {
    val nested = domain.includes.flatMap(_.contents.filter[Author])
    domain.authors ++ domain.domains.flatMap(getAuthors) ++ nested
  }

  /** get all the authors defined in the root node even if they are in includes
    *
    * @param root
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Author]] expressed as a [[AST.Contents]] extension
    */
  def getAuthors(root: Root): Contents[Author] = {
    root.domains.flatMap(getAuthors)
  }

  /** get all the [[Author]]s defined in a [[Domain]] node even if they are in includes
    *
    * @param domain
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Author]] expressed as a [[AST.Contents]] extension
    */
  def getUsers(domain: Domain): Contents[User] = {
    val nested = domain.includes.flatMap(_.contents.filter[User])
    domain.users ++ domain.domains.flatMap(getUsers) ++ nested
  }

  /** Get the [[AST.User]] definitions found at the [[AST.Root]] level or in its [[AST.Include]]s
    * @param root
    *   The [[AST.Root]] node to examine
    * @return
    *   A Seq of [[AST.User]] expressed as a [[AST.Contents]] extension
    */
  def getUsers(root: Root): Contents[User] = {
    root.domains.flatMap(getUsers) ++ root.includes.flatMap(_.contents.filter[User])
  }

  extension (optLit: Option[LiteralString])
    /** An extension to an [[scala.Option[LiteralString]]] that makes extracting the content of the [[LiteralString]]
      * easier.
      * @return
      *   The content of the formatted LiteralString or "N/A" if it is not available
      */
    def format: String = optLit.map(_.format).getOrElse("N/A")

  /** A utility function for getting the kind of a type expression.
    *
    * @param te
    *   The type expression to examine
    *
    * @return
    *   A string indicating the kind corresponding to te
    */
  def errorDescription(te: TypeExpression): String = {
    te match {
      case AliasedTypeExpression(_, keyword, pid) => s"$keyword ${pid.format}"
      case Optional(_, typeExp)                   => errorDescription(typeExp) + "?"
      case ZeroOrMore(_, typeExp)                 => errorDescription(typeExp) + "*"
      case OneOrMore(_, typeExp)                  => errorDescription(typeExp) + "+"
      case e: Enumeration                         => s"Enumeration of ${e.enumerators.size} values"
      case a: Alternation                         => s"Alternation of ${a.of.size} types"
      case a: Aggregation                         => s"Aggregation of ${a.fields.size} fields"
      case Mapping(_, from, to) =>
        s"Map from ${errorDescription(from)} to ${errorDescription(to)}"
      case EntityReferenceTypeExpression(_, entity) =>
        s"Reference to entity ${entity.format}"
      case p: Pattern              => p.format
      case Decimal(_, whl, frac)   => s"Decimal($whl,$frac)"
      case RangeType(_, min, max)  => s"Range($min,$max)"
      case UniqueId(_, entityPath) => s"Id(${entityPath.format})"
      case m @ AggregateUseCaseTypeExpression(_, messageKind, _) =>
        s"${messageKind.format} of ${m.fields.size} fields and ${m.methods.size} methods"
      case pt: PredefinedType => pt.kind
      case _                  => "<unknown type expression>"
    }
  }

}
