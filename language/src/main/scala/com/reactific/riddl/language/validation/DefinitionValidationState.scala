package com.reactific.riddl.language.validation

import com.reactific.riddl.language.ast.At
import com.reactific.riddl.language.Messages.*
import com.reactific.riddl.language.AST.*

/** Unit Tests For DefinitionValidationState */
trait DefinitionValidationState extends BasicValidationState {


  def checkOptions[T <: OptionValue](options: Seq[T], loc: At): this.type = {
    check(
      options.sizeIs == options.distinct.size,
      "Options should not be repeated",
      Error,
      loc
    )
  }

  def checkOption[A <: RiddlValue](
    opt: Option[A],
    name: String,
    thing: Definition
  )(folder: (this.type, A) => this.type): this.type = {
    opt match {
      case None =>
        addMissing(thing.loc, s"$name in ${thing.identify} should not be empty")
      case Some(x) =>
        val s1 = checkNonEmptyValue(x, "Condition", thing, MissingWarning)
        folder(s1, x)
    }
  }


  private def checkUniqueContent(definition: Definition): this.type = {
    val allNames = definition.contents.map(_.id.value)
    val uniqueNames = allNames.toSet
    if (allNames.size != uniqueNames.size) {
      val duplicateNames = allNames.toSet.removedAll(uniqueNames)
      addError(
        definition.loc,
        s"${definition.identify} has duplicate content names:\n${
          duplicateNames
            .mkString("  ", ",\n  ", "\n")
        }"
      )
    } else {
      this
    }
  }

  def checkDefinition(
    parents: Seq[Definition],
    definition: Definition
  ): this.type = {
    var result: this.type = this
      .check(
        definition.id.nonEmpty | definition.isImplicit,
        "Definitions may not have empty names",
        Error,
        definition.loc
      )
      .checkIdentifierLength(definition)
      .checkUniqueContent(definition)
      .check(!definition.isVital || definition.hasAuthors,
        "Vital definitions should have an author reference",
        MissingWarning,
        definition.loc
      ).stepIf(definition.isVital) { vs: this.type =>
      definition.asInstanceOf[WithAuthors].authors.foldLeft[this.type](vs) {
        case (vs: this.type, authorRef) =>
          pathIdToDefinition(authorRef.pathId, parents) match {
            case None => vs.addError(authorRef.loc, s"${authorRef.format} is not defined")
            case _ => vs
          }
        case (_, _) => vs
      }
    }

    val path = symbolTable.pathOf(definition)
    if (!definition.id.isEmpty) {
      val matches = result.lookup[Definition](path)
      if (matches.isEmpty) {
        result = result.addSevere(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!"
        )
      } else if (matches.sizeIs >= 2) {
        val parentGroups = matches.groupBy(result.symbolTable.parentOf(_))
        parentGroups.get(parents.headOption) match {
          case Some(head :: tail) if tail.nonEmpty =>
            result = result.addWarning(
              head.id.loc,
              s"${definition.identify} has same name as other definitions " +
                s"in ${head.identifyWithLoc}:  " +
                tail.map(x => x.identifyWithLoc).mkString(",  ")
            )
          case Some(head :: tail) if tail.isEmpty =>
            result = result.addStyle(
              head.id.loc,
              s"${definition.identify} has same name as other definitions: " +
                matches.filterNot(_ == definition).map(x => x.identifyWithLoc)
                  .mkString(",  ")
            )
          case _ =>
          // ignore
        }
      }
    }
    result
  }

  def checkContainer(
    parents: Seq[Definition],
    container: Definition
  ): this.type = {
    val parent: Definition = parents.headOption.getOrElse(RootContainer.empty)
    checkDefinition(parents, container).check(
      container.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.loc
    )
  }

  def checkDescription[TD <: DescribedValue](
    id: String,
    value: TD
  ): this.type = {
    val description: Option[Description] = value.description
    val shouldCheck: Boolean = {
      value.isInstanceOf[Type] |
        (value.isInstanceOf[Definition] && value.nonEmpty)
    }
    if (description.isEmpty && shouldCheck) {
      this.check(
        predicate = false,
        s"$id should have a description",
        MissingWarning,
        value.loc
      )
    } else if (description.nonEmpty) {
      val desc = description.get
      this.check(
        desc.nonEmpty,
        s"For $id, description at ${desc.loc} is declared but empty",
        MissingWarning,
        desc.loc
      )
    } else this
  }

  def checkDescription[TD <: Definition](
    definition: TD
  ): this.type = {
    checkDescription(definition.identify, definition)
  }
}