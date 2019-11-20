package com.yoppworks.ossum.riddl.translator

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.Folding
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.SymbolTable
import com.yoppworks.ossum.riddl.language.Translator
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.auto._

/** A Translator that generates Paradox documentation */
class ParadoxTranslator extends Translator {

  case class ParadoxConfig(
    showTimes: Boolean = false,
    showWarnings: Boolean = true,
    showMissingWarnings: Boolean = false,
    showStyleWarnings: Boolean = false,
    inputPath: Option[Path] = None,
    outputRoot: Path = Path.of("target")
  ) extends Configuration

  type CONF = ParadoxConfig

  val defaultConfig = ParadoxConfig()

  override def loadConfig(path: Path): ConfigReader.Result[ParadoxConfig] = {
    ConfigSource.file(path).load[ParadoxConfig]
  }

  case class ParadoxState(
    config: ParadoxConfig,
    generatedFiles: Seq[File] = Seq.empty[File]
  ) extends Folding.State[ParadoxState]
      with State {
    def step(f: ParadoxState => ParadoxState): ParadoxState = f(this)

    def addFile(f: File): ParadoxState = {
      this.copy(generatedFiles = this.generatedFiles :+ f)
    }
  }

  def translate(
    root: RootContainer,
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File] = {
    val state: ParadoxState = ParadoxState(config, Seq.empty[File])
    val symtab: SymbolTable = SymbolTable(root)
    val dispatcher = ParadoxFolding(root, root, state, symtab, logger)
    dispatcher.foldLeft(root, root, state).generatedFiles
  }

  case class ParadoxFolding(
    container: Container,
    definition: Definition,
    state: ParadoxState,
    symTab: SymbolTable,
    log: Riddl.Logger
  ) extends Folding.Folding[ParadoxState] {

    private val rootDir: Path =
      state.config.outputRoot.resolve("riddl-paradox")
    Files.createDirectories(rootDir)

    private def mkContainerPath(container: Container): Path = {
      symTab.parentsOf(container).foldRight(rootDir) {
        case (c, p) =>
          p.resolve(c.id.value)
      }
    }

    def writeFile(path: Path, content: StringBuilder): ParadoxState = {
      writeFile(path, content.toString)
    }

    def writeFile(path: Path, content: String): ParadoxState = {
      try {
        val directory = path.getParent
        Files.createDirectories(directory)
        val file = path.toFile
        val w = new PrintWriter(file)
        w.write(content)
        w.flush()
        w.close()
        state.addFile(file)
      } catch {
        case xcptn: Exception =>
          log.error(s"Error writing file $path: ${xcptn.getMessage}")
          state
      }

    }

    def mkRef(ref: Reference): String = {
      s"""* [${ref.id.format}]("${ref.id.value.mkString("/")}.md")"""
    }

    def mkDefRef(definition: Definition): String = {
      definition match {
        case c: Container =>
          s"""* [${c.identify}]("${c.id.value.toLowerCase}/index.md")"""
        case d: Definition =>
          s"""* [${d.identify}]("${d.id.value}.md")"""
      }
    }

    def mkDescription(desc: Option[Description]): String = {
      desc match {
        case None =>
          "No description.\n"
        case Some(d) =>
          s"""
          |## Briefly
          |${d.brief.map(_.s).mkString("\n")}
          |
          |## Details
          |${d.details.map(_.s).mkString("\n")}
          |
          |${mkFields("Fields", d.fields)}
          |
          |## See Also
          |${d.citations.map(x => "* " + x.s).mkString("\n")}
          |"""
      }
    }

    def mkFields(
      section: String,
      fields: Map[Identifier, Seq[LiteralString]]
    ): String = {
      val sb = new StringBuilder
      sb.append(s"## $section")
      fields.foreach {
        case (id, strs) =>
          sb.append(s"### ${id.value}")
          sb.append(strs.map(_.s).mkString("\n"))
      }
      sb.toString()
    }

    def mkTypeExpression(typeEx: AST.TypeExpression): String = {
      typeEx match {
        case Strng(_, min, max) =>
          (min, max) match {
            case (Some(n), Some(x)) =>
              s"String($n,$x"
            case (None, Some(x)) =>
              s"String(,$x"
            case (Some(n), None) =>
              s"String($n)"
            case (None, None) =>
              s"String"
          }
        case URL(_, scheme) =>
          s"URL${scheme.map(s => "\"" + s.s + "\"")}"
        case Bool(_)        => "Boolean"
        case Number(_)      => "Number"
        case Integer(_)     => "Integer"
        case Decimal(_)     => "Decimal"
        case Date(_)        => "Date"
        case Time(_)        => "Time"
        case DateTime(_)    => "DateTime"
        case TimeStamp(_)   => "TimeStamp"
        case LatLong(_)     => "LatLong"
        case Nothing(_)     => "Nothing"
        case TypeRef(_, id) => id.value.mkString(".")
        case AST.Enumeration(_, of, _) =>
          def doTypex(t: Option[TypeExpression]): String = {
            t match {
              case None        => ""
              case Some(typex) => mkTypeExpression(typex)
            }
          }
          s"any { ${of.map(e => e.id.value + doTypex(e.value)).mkString(" ")} }"
        case AST.Alternation(_, of, _) =>
          s"one { ${of.map(mkTypeExpression).mkString(" or ")} }"
        case AST.Aggregation(_, of, _) =>
          s" {\n${of
            .map { f: Field =>
              s"  ${f.id.value}: ${mkTypeExpression(f.typeEx)} ${mkDescription(f.description)}"
            }
            .mkString(s",\n")}\n} "
        case AST.Mapping(_, from, to, _) =>
          s"mapping from ${mkTypeExpression(from)} to ${mkTypeExpression(to)}"
        case AST.RangeType(_, min, max, _) =>
          s"range from $min to $max "
        case AST.ReferenceType(_, id, _) =>
          s"reference to $id"
        case Pattern(_, pat, _) =>
          if (pat.size == 1) {
            "Pattern(\"" +
              pat.head.s +
              "\"" + s")"
          } else {
            s"Pattern(\n" +
              pat.map(l => "  \"" + l.s + "\"\n")
            s"\n)"
          }
        case UniqueId(_, id, _) =>
          s"Id(${id.value})"

        case Optional(_, typex)   => mkTypeExpression(typex) + "?"
        case ZeroOrMore(_, typex) => mkTypeExpression(typex) + "*"
        case OneOrMore(_, typex)  => mkTypeExpression(typex) + "+"

        case x: TypeExpression =>
          require(requirement = false, s"Unknown type $x")
          ""
      }
    }

    override def openDomain(
      state: ParadoxState,
      container: Container,
      domain: Domain
    ): ParadoxState = {
      val indexFile =
        mkContainerPath(container).resolve(domain.id.value).resolve("index.md")
      writeFile(
        indexFile,
        s"""
           |# Domain `${domain.id.value}`
           |${mkDescription(domain.description)}
           |
           |@@toc { depth=2 }
           |
           |@@@ index
           |
           |${domain.types.map(mkDefRef).mkString("\n")}
           |${domain.topics.map(mkDefRef).mkString("\n")}
           |${domain.contexts.map(mkDefRef).mkString("\n")}
           |${domain.interactions.map(mkDefRef).mkString("\n")}
           |
           |@@@
           |
           |""".stripMargin
      )
    }

    override def openContext(
      state: ParadoxState,
      container: Container,
      context: Context
    ): ParadoxState = {
      val indexFile =
        mkContainerPath(container).resolve(context.id.value).resolve("index.md")
      writeFile(
        indexFile,
        s"""
           |# Context `${context.id.value}`
           |${mkDescription(context.description)}
           |
           |## Options
           |${context.options.map(_.toString).mkString(", ")}
           |
           |@@toc { depth=2 }
           |
           |@@@ index
           |
           |${context.types.map(mkDefRef).mkString("\n")}
           |${context.entities.map(mkDefRef).mkString("\n")}
           |${context.adaptors.map(mkDefRef).mkString("\n")}
           |${context.interactions.map(mkDefRef).mkString("\n")}
           |
           |@@@
           |
           |""".stripMargin
      )
    }

    override def openTopic(
      state: ParadoxState,
      container: Container,
      topic: Topic
    ): ParadoxState = {
      val indexFile =
        mkContainerPath(container).resolve(topic.id.value).resolve("index.md")
      writeFile(
        indexFile,
        s"""
           |# Topic `${topic.id.value}`
           |${mkDescription(topic.description)}
           |
           |@@toc { depth=2 }
           |
           |@@@ index
           |
           |${topic.commands.map(mkDefRef).mkString("\n")}
           |${topic.events.map(mkDefRef).mkString("\n")}
           |${topic.queries.map(mkDefRef).mkString("\n")}
           |${topic.results.map(mkDefRef).mkString("\n")}
           |
           |@@@
           |
           |""".stripMargin
      )
    }
    override def openEntity(
      state: ParadoxState,
      container: Container,
      entity: Entity
    ): ParadoxState = {
      val indexFile =
        mkContainerPath(container).resolve(entity.id.value).resolve("index.md")
      val states = entity.states.map(
        s =>
          "* " + s.id.value + ": " +
            mkTypeExpression(s.typeEx).mkString("\n")
      )
      writeFile(
        indexFile,
        s"""
           |# Entity `${entity.id.value}`
           |${mkDescription(entity.description)}
           |## Options
           |${entity.options.map(_.toString).mkString(", ")}
           |
           |# States
           |$states
           |
           |@@toc { depth=2 }
           |
           |@@@ index
           |
           |${entity.consumers.map(mkDefRef).mkString("\n")}
           |${entity.functions.map(mkDefRef).mkString("\n")}
           |${entity.features.map(mkDefRef).mkString("\n")}
           |${entity.invariants.map(mkDefRef).mkString("\n")}
           |
           |@@@
           |
           |""".stripMargin
      )
    }

    override def openInteraction(
      state: ParadoxState,
      container: Container,
      interaction: Interaction
    ): ParadoxState = {
      val indexFile =
        mkContainerPath(container)
          .resolve(interaction.id.value)
          .resolve("index.md")
      writeFile(
        indexFile,
        s"""
           |# Interaction `${interaction.id.value}`
           |${mkDescription(interaction.description)}
           |
           |## Diagram
           |TBD
           |
           |@@toc { depth=2 }
           |
           |@@@ index
           |
           |${interaction.actions.map(mkDefRef).mkString("\n")}
           |
           |@@@
           |
           |""".stripMargin
      )
    }

    override def openAdaptor(
      state: ParadoxState,
      container: Container,
      adaptor: Adaptor
    ): ParadoxState = {
      super.openAdaptor(state, container, adaptor)
    }

    override def openFeature(
      state: ParadoxState,
      container: Container,
      feature: Feature
    ): ParadoxState = {
      super.openFeature(state, container, feature)
    }

    override def doType(
      state: ParadoxState,
      container: Container,
      typ: Type
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(typ.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Type `${typ.id.value}`
           |## Expression
           |${mkTypeExpression(typ.typ)}
           |${mkDescription(typ.description)}
           |""".stripMargin
      )
    }

    override def doAction(
      state: ParadoxState,
      container: Container,
      action: ActionDefinition
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(action.id.value + ".md")
      action match {
        case ma: MessageAction =>
          writeFile(
            indexFile,
            s"""
               |# Action `${action.id.value}`
               |* From: ${ma.sender.id.format}
               |* To: ${ma.receiver.id.format}
               |* Message: ${ma.message}
               |## Options
               |${ma.options.mkString(", ")}
               |## Reactions
               |${ma.reactions.map(x => "* " + x).mkString("\n")}
               |${mkDescription(action.description)}
               |""".stripMargin
          )
      }
    }

    override def doCommand(
      state: ParadoxState,
      container: Container,
      command: Command
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(command.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Command `${command.id.value}`
           |${mkDescription(command.description)}
           |## Value Object
           |${mkTypeExpression(command.typ)}
           |## Events Generated
           |${command.events.map(x => "* " + mkRef(x).mkString("\n"))}"
           |""".stripMargin
      )
    }

    override def doConsumer(
      state: ParadoxState,
      container: Container,
      consumer: Consumer
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(consumer.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Consumer `${consumer.id.value}`
           |${mkDescription(consumer.description)}
           |## Topic
           |${consumer.topic.id.format}
           |## Reactions
           |${consumer.clauses.map(_.toString).mkString("\n")}
           |""".stripMargin
      )
    }

    override def doEvent(
      state: ParadoxState,
      container: Container,
      event: Event
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(event.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Event `${event.id.value}`
           |${mkDescription(event.description)}
           |## Value Object
           |${mkTypeExpression(event.typ)}
           |""".stripMargin
      )
    }

    override def doExample(
      state: ParadoxState,
      container: Container,
      example: Example
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(example.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Command `${example.id.value}`
           |${mkDescription(example.description)}
           |## Givens
           |
           |${example.givens.map(g => "* " + g.fact.mkString("\n  "))}
           |
           |## Whens
           |
           |${example.whens.map(g => "* " + g.situation.mkString("\n  "))}
           |
           |## Thens
           |
           |${example.thens.map(g => "* " + g.result.mkString("\n  "))}
           |""".stripMargin
      )

    }

    override def doFunction(
      state: ParadoxState,
      container: Container,
      function: Function
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(function.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Function `${function.id.value}`
           |## Inputs
           |${function.input.map(mkTypeExpression)}
           |## Outputs
           |${mkTypeExpression(function.output)}
           |${mkDescription(function.description)}
           |""".stripMargin
      )

    }

    override def doInvariant(
      state: ParadoxState,
      container: Container,
      invariant: Invariant
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(invariant.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Invariant `${invariant.id.value}`
           |${mkDescription(invariant.description)}
           |## Expression
           |${invariant.expression.map(x => x.s + "\n")}
           |""".stripMargin
      )
    }

    override def doQuery(
      state: ParadoxState,
      container: Container,
      query: Query
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(query.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Command `${query.id.value}`
           |${mkDescription(query.description)}
           |## Value Object
           |${mkTypeExpression(query.typ)}
           |## Result Type
           |${mkRef(query.result)}"
           |""".stripMargin
      )
    }

    override def doResult(
      state: ParadoxState,
      container: Container,
      result: Result
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(result.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Command `${result.id.value}`
           |${mkDescription(result.description)}
           |## Value Object
           |${mkTypeExpression(result.typ)}
           |""".stripMargin
      )

    }

    override def doTranslationRule(
      state: ParadoxState,
      container: Container,
      rule: TranslationRule
    ): ParadoxState = {
      val indexFile = mkContainerPath(container)
        .resolve(container.id.value)
        .resolve(rule.id.value + ".md")
      writeFile(
        indexFile,
        s"""
           |# Rule `${rule.id.value}`
           |${mkDescription(rule.description)}
           |""".stripMargin
      )

    }
  }
}
