/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.passes.resolve.ResolutionOutput
import com.ossuminc.riddl.passes.symbols.SymbolsOutput
import com.ossuminc.riddl.utils.pc

import scala.reflect.{ClassTag, classTag}
import scala.util.matching.Regex

/** Validation infrastructure needed for all kinds of definition validation */
trait BasicValidation {

  def symbols: SymbolsOutput
  def resolution: ResolutionOutput
  protected def messages: Messages.Accumulator

  def parentOf(definition: Definition): Parent = {
    symbols.parentOf(definition).getOrElse(Root.empty)
  }

  def parentsOf(definition: Definition): Parents = {
    symbols.parentsOf(definition)
  }

  def lookup[T <: Definition: ClassTag](id: Seq[String]): List[T] = {
    symbols.lookup[T](id)
  }

  def pathIdToDefinition(
    pid: PathIdentifier,
    parents: Parents
  ): Option[Definition] = {
    if pid.value.length == 1 then
      // Let's try the symbol table
      symbols.lookup[Definition](pid.value.reverse).headOption
    else
      parents.headOption.flatMap { (head: Parent) =>
        resolution.refMap.definitionOf[Definition](pid, head)
      }
  }

  @inline
  def resolvePath[T <: Definition](
    pid: PathIdentifier,
    parents: Parents
  ): Option[T] = {
    pathIdToDefinition(pid, parents).map(_.asInstanceOf[T])
  }

  def checkPathRef[T <: Definition: ClassTag](
    pid: PathIdentifier,
    parents: Parents
  ): Option[T] = {
    if pid.value.isEmpty then
      val tc = classTag[T].runtimeClass
      val message =
        s"An empty path cannot be resolved to ${article(tc.getSimpleName)}"
      messages.addError(pid.loc, message)
      Option.empty[T]
    else resolvePath[T](pid, parents)
  }

  def checkRef[T <: Definition: ClassTag](
    reference: Reference[T],
    parents: Parents
  ): Option[T] = {
    checkPathRef[T](reference.pathId, parents)
  }

  def checkRefAndExamine[T <: Definition: ClassTag](
    reference: Reference[T],
    parents: Parents
  )(examiner: T => Unit): this.type = {
    checkPathRef[T](reference.pathId, parents).foreach { (resolved: T) =>
      examiner(resolved)
    }
    this
  }

  private def checkMaybeRef[T <: Definition: ClassTag](
    reference: Option[Reference[T]],
    parents: Parents
  ): Option[T] = {
    reference.flatMap { ref =>
      checkPathRef[T](ref.pathId, parents)
    }
  }

  def checkTypeRef(
    ref: TypeRef,
    parents: Parents
  ): Option[Type] = {
    checkRef[Type](ref, parents)
  }

  def checkMessageRef(
    ref: MessageRef,
    parents: Parents,
    kinds: Seq[AggregateUseCase]
  ): this.type = {
    if ref.isEmpty then {
      messages.addError(ref.pathId.loc, s"${ref.identify} is empty")
      this
    } else {
      checkRefAndExamine[Type](ref, parents) { (definition: Definition) =>
        definition match {
          case Type(_, _, typ, _) =>
            typ match {
              case AggregateUseCaseTypeExpression(_, mk, _) =>
                check(
                  kinds.contains(mk),
                  s"'${ref.identify} should be one of these message types: ${kinds.mkString(",")}" +
                    s" but is ${article(mk.useCase)} type instead",
                  Error,
                  ref.pathId.loc
                )
              case te: TypeExpression =>
                messages.addError(
                  ref.pathId.loc,
                  s"'${ref.identify} should reference one of these types: ${kinds.mkString(",")} but is a ${AST
                      .errorDescription(te)} type " +
                    s"instead"
                )
            }
          case _ =>
            messages.addError(
              ref.pathId.loc,
              s"${ref.identify} was expected to be one of these types; ${kinds.mkString(",")}, but is ${article(definition.kind)} instead"
            )
        }
      }
    }
  }

  private val vowels: Regex = "[aAeEiIoOuU]".r

  def article(thing: String): String = {
    val article = if vowels.matches(thing.substring(0, 1)) then "an" else "a"
    s"$article $thing"
  }

  def check(
    predicate: Boolean = true,
    message: => String,
    kind: KindOfMessage,
    loc: At
  ): this.type = {
    if !predicate then messages.add(Message(loc, message, kind))
    this
  }

  def checkSequence[A](elements: Seq[A])(check: A => Unit): this.type = {
    elements.foreach(check(_))
    this
  }

  def checkOverloads(): this.type = {
    symbols.foreachOverloadedSymbol { (defs: Seq[Seq[Definition]]) =>
      this.checkSequence(defs) { defList =>
        defList.toList match {
          case Nil =>
            // shouldn't happen
            messages.addSevere(At.empty, "Empty list from Symbols.foreachOverloadedSymbol")
          case head :: tail =>
            tail match
              case last :: Nil =>
                messages.addStyle(last.loc, s"${last.identify} overloads ${head.identifyWithLoc}")
              case _ =>
                val tailStr: String = tail.map(d => d.identifyWithLoc).mkString(s",\n  ")
                messages.addStyle(head.loc, s"${head.identify} overloads:\n  $tailStr")
        }
      }
    }
    this
  }

  def checkIdentifierLength[T <: Definition](d: T, min: Int = 3): this.type = {
    if d.id.value.nonEmpty && d.id.value.length < min then {
      messages.addStyle(
        d.id.loc,
        s"${d.kind} identifier '${d.id.value}' is too short. The minimum length is $min"
      )
    }
    this
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): this.type = {
    check(
      value.nonEmpty,
      message = s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkNonEmptyValue(
    value: RiddlValue,
    name: String,
    thing: Definition,
    loc: At,
    kind: KindOfMessage,
    required: Boolean
  ): this.type = {
    check(
      value.nonEmpty,
      message = s"$name in ${thing.identify} at $loc ${if required then "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    kind: KindOfMessage = Error,
    required: Boolean = false
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} ${if required then "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkNonEmpty(
    list: Seq[?],
    name: String,
    thing: Definition,
    loc: At,
    kind: KindOfMessage,
    required: Boolean
  ): this.type = {
    check(
      list.nonEmpty,
      s"$name in ${thing.identify} at $loc ${if required then "must" else "should"} not be empty",
      kind,
      thing.loc
    )
  }

  def checkCrossContextReference(ref: PathIdentifier, definition: Definition, container: Definition): Unit = {
    symbols.contextOf(definition) match {
      case Some(definitionContext) =>
        symbols.contextOf(container) match {
          case Some(containerContext) =>
            if definitionContext != containerContext then
              val formatted = ref.format 
              messages.add(
                style(
                  s"Path Identifier $formatted at ${ref.loc} references ${definition.identify} in " +
                    s"${definitionContext.identify} but occurs in ${container.identify} in ${containerContext.identify}." +
                    " Cross-context references are ill-advised as they lead to model confusion and violate " +
                    "the 'bounded' aspect of bounded contexts",
                  ref.loc.extend(formatted.length)
                )
              )
            else ()
          case None => ()
        }
      case None => ()
    }
  }
}
