/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{ExceptionUtils, CommonOptions}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.At
import fastparse.Parsed.{Failure, Success}
import fastparse.internal.Lazy
import fastparse.*

import java.nio.file.Path
import java.util.concurrent.{ExecutorService, Executors}
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.unused
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/** A trait for tracking errors generated by fastparse */
trait ParsingErrors {

  private val messages: mutable.ListBuffer[Messages.Message] = mutable.ListBuffer.empty[Messages.Message]

  protected def messagesNonEmpty: Boolean = synchronized { messages.nonEmpty }

  private def addMessage(message: Messages.Message): Unit = {
    synchronized {
      messages.append(message)
    }
  }

  protected def messagesAsList: Messages.Messages = {
    synchronized { messages.toList }
  }

  def error(loc: At, message: String, context: String = ""): Unit = {
    val msg = Messages.Message(loc, message, Messages.Error, context)
    addMessage(msg)
  }

  def warning(loc: At, message: String, context: String = ""): Unit = {
    val msg = Messages.Message(loc, message, Messages.Warning, context)
    addMessage(msg)
  }

  private def mkTerminals(list: List[Lazy[String]]): String = {
    list
      .map(_.force)
      .map {
        case s: String if s.startsWith("char-pred")  => "pattern"
        case s: String if s.startsWith("chars-with") => "pattern"
        case s: String if s == "fail"                => "whitespace after keyword"
        case s: String                               => s
      }
      .distinct
      .sorted
      .mkString("(", " | ", ")")
  }

  def makeParseFailureError(failure: Failure, input: RiddlParserInput): Unit = {
    val location = input.location(failure.index)
    val trace = failure.trace()
    val msg = trace.terminals.value.size match {
      case 0 => "Expected nothing"
      case 1 => s"Expected " + mkTerminals(trace.terminals.value)
      case _ => s"Expected one of " + mkTerminals(trace.terminals.value)
    }
    val context = trace.groups.render
    error(location, msg, context)
  }

  def makeParseFailureError(exception: Throwable, loc: At = At.empty, context: String = ""): Unit = {
    val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    error(loc, message, context)
  }
}
