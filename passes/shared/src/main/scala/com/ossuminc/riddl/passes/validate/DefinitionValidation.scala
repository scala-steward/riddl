/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.utils.PlatformContext

/** A Trait that defines typical Validation checkers for validating definitions */
trait DefinitionValidation(using pc: PlatformContext) extends BasicValidation:
  def symbols: SymbolsOutput

  private def checkUniqueContent(definition: Branch[?]): Unit = {
    val allNamedValues = definition.contents.definitions
    val allNames = allNamedValues.map(_.identify)
    if allNames.distinct.size < allNames.size then {
      val duplicates: Map[String, Seq[Definition]] =
        allNamedValues.groupBy(_.identify).filterNot(_._2.size < 2)
      if duplicates.nonEmpty then {
        val details = duplicates
          .map { case (_: String, defs: Seq[Definition]) =>
            defs.map(_.identifyWithLoc).mkString(", and ")
          }
          .mkString("", "\n  ", "\n")
        messages.addError(
          definition.errorLoc,
          s"${definition.identify} has duplicate content names:\n  $details"
        )
      }
    }
  }

  def checkDefinition(
    parents: Parents,
    definition: Definition
  ): Unit = {
    checkIdentifierLength(definition)
    definition match
      case vd: VitalDefinition[?] =>
        checkMetadata(vd)
        vd.authorRefs.foreach { (authorRef: AuthorRef) =>
          pathIdToDefinition(authorRef.pathId, definition.asInstanceOf[Branch[?]] +: parents) match
            case None =>
              messages.addError(
                authorRef.loc,
                s"${authorRef.format} is not defined"
              )
            case _ =>
          end match
        }
      case _ => ()
    end match

    val path = symbols.pathOf(definition)
    if !definition.id.isEmpty then {
      val matches = symbols.lookup[Definition](path)
      if matches.isEmpty then {
        messages.addSevere(
          definition.id.loc,
          s"'${definition.id.value}' evaded inclusion in symbol table!"
        )
      }
    }
  }

  def checkContents(
    container: Branch[?],
    parents: Parents
  ): Unit =
    val parent: Branch[?] = parents.headOption.getOrElse(Root.empty)
    check(
      container.contents.definitions.nonEmpty || container.isInstanceOf[Field],
      s"${container.identify} in ${parent.identify} should have content",
      MissingWarning,
      container.errorLoc
    )
  end checkContents

  def checkContainer(
    parents: Parents,
    container: Branch[?]
  ): Unit = {
    checkDefinition(parents, container)
    checkContents(container, parents)
    checkUniqueContent(container)
  }
  def checkMetadata(definition: Definition): Unit =
    checkMetadata(definition.identify, definition, definition.errorLoc)

  def checkMetadata(identity: String, definition: WithMetaData, loc: At): Unit =
    check(
      definition.metadata.nonEmpty,
      s"Metadata in $identity should not be empty",
      MissingWarning,
      loc
    )
    var hasAuthorRef = false
    var hasDescription = false
    for { meta <- definition.metadata.toSeq } do {
      meta match
        case bd: BriefDescription =>
          check(
            bd.brief.s.length < 80,
            s"In $identity, brief description at ${bd.loc.format} is too long. Max is 80 chars",
            Warning,
            bd.loc
          )
        case bd: BlockDescription =>
          check(
            bd.lines.nonEmpty && !bd.lines.forall(_.s.isEmpty),
            s"For $identity, description at ${bd.loc.format} is declared but empty",
            MissingWarning,
            bd.loc
          )
          check(
            bd.lines.nonEmpty,
            s"For $identity, description is declared but empty",
            MissingWarning,
            bd.loc
          )

          hasDescription = true
        case ud: URLDescription =>
          check(
            ud.url.isValid,
            s"For $identity, description at ${ud.loc.format} has an invalid URL: ${ud.url}",
            Error,
            ud.loc
          )
          hasDescription = true
        case t: Term =>
          check(
            t.definition.length >= 10,
            s"${t.identify}'s definition is too short. It must be at least 10 characters'",
            Warning,
            t.loc
          )
        case o: OptionValue =>
          check(
            o.name.length >= 3,
            s"Option ${o.name}'s name is too short. It must be at least 3 characters'",
            StyleWarning,
            o.loc
          )
        case _: AuthorRef        => hasAuthorRef = true
        case _: StringAttachment => () // No validation needed
        case _: FileAttachment   => () // No validation needed
        case _: ULIDAttachment   => () // No validation needed
        case _: Description      => () // No validation needed
        case _: Comment          => () // No validation needed
    }
    check(hasDescription, s"$identity should have a description", MissingWarning, loc)
  end checkMetadata
end DefinitionValidation
