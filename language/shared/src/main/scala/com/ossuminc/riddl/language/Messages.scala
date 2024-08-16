/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{ExceptionUtils, Logger}

import scala.collection.mutable
import scala.io.AnsiColor.*
import scala.scalajs.js.annotation.*

/** This module handles everything needed to deal with the message output of the `riddlc` compiler */
@JSExportTopLevel("Messages")
object Messages {

  /** A sealed base trait for the kinds of messages that can be created each with their own existence test */
  sealed trait KindOfMessage extends Ordered[KindOfMessage] {
    def isSevereError: Boolean = false

    def isError: Boolean = false

    def isWarning: Boolean = false

    def isMissing: Boolean = false

    def isStyle: Boolean = false

    def isUsage: Boolean = false

    def isInfo: Boolean = false

    def severity: Int

    def isIgnorable: Boolean = severity < Warning.severity
    def isActionable: Boolean = severity >= Warning.severity

    def compare(that: KindOfMessage): Int = { this.severity - that.severity }
  }

  /** A case object for the Info kind of message */
  case object Info extends KindOfMessage {
    override def isInfo: Boolean = true
    override def toString: String = "Info"
    def severity = 0
  }

  /** A case object for the Style kind of warning message */
  case object StyleWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isStyle: Boolean = true

    override def toString: String = "Style"
    def severity = 1
  }

  /** A case object for the Missing kind of warning message */
  case object MissingWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isMissing: Boolean = true

    override def toString: String = "Missing"

    def severity = 2
  }

  /** A case object for the Usage kind of warning message */
  case object UsageWarning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def isUsage: Boolean = true

    override def isMissing: Boolean = false

    override def toString: String = "Usage"

    def severity = 3

  }

  /** A case object for the generic kind of warning message */
  case object Warning extends KindOfMessage {
    override def isWarning: Boolean = true

    override def toString: String = "Warning"
    def severity = 4
  }

  /** A case object for Error messages */
  case object Error extends KindOfMessage {
    override def isError: Boolean = true

    override def toString: String = "Error"
    def severity = 5
  }

  /** A case object for Severe Error messages */
  case object SevereError extends KindOfMessage {
    override def isError: Boolean = true

    override def isSevereError: Boolean = true

    override def toString: String = "Severe"
    def severity = 6
  }

  /** The system's notion of a newline for sensible error message termination */
  @JSExport val nl: String = System.lineSeparator()

  /** A Message instance. There are helper functions below to help you create these.
    *
    * @param loc
    *   The location in the model that generated the message
    * @param message
    *   The message text itself (there may be multiple lines, typically indent them 2 spaces, no tabs)
    * @param kind
    *   The kind of message as one of the case objects of [[KindOfMessage]]
    * @param context
    *   Additional context that indicates the conditions that produced the message
    */
  @JSExportTopLevel("Message")
  case class Message(loc: At, message: String, kind: KindOfMessage = Error, context: String = "")
      extends Ordered[Message] {

    def isInfo: Boolean = kind.isInfo
    def isMissing: Boolean = kind.isMissing
    def isWarning: Boolean = kind.isWarning
    def isStyle: Boolean = kind.isStyle
    def isUsage: Boolean = kind.isUsage
    def isError: Boolean = kind.isError
    def isSevere: Boolean = kind.isSevereError

    override def compare(that: Message): Int = {
      val comparison = this.loc.compare(that.loc)
      if comparison == 0 then { this.kind.compare(that.kind) }
      else comparison
    }

    /** A standard way of formatting the message. It is common to call this just before doing I/O with it */
    def format: String = {
      val ctxt = if context.nonEmpty then {
        s"${nl}Context: $context"
      } else ""
      val location = loc.toString
      val errorLine = loc.source.annotateErrorLine(loc).dropRight(1)
      if loc.isEmpty || errorLine.isEmpty then {
        s"$location$message$ctxt"
      } else {
        s"$location:$nl$message:$nl$errorLine$ctxt"
      }
    }
  }

  /** Generate a style warning */
  @JSExport def style(message: String, loc: At = At.empty): Message = {
    Message(loc, message, StyleWarning)
  }

  /** Generate a missing warning */
  @JSExport def missing(message: String, loc: At = At.empty): Message = {
    Message(loc, message, MissingWarning)
  }

  /** Generate a usage warning */
  @JSExport def usage(message: String, loc: At = At.empty): Message = {
    Message(loc, message, UsageWarning)
  }

  /** Generate an informative message */
  @JSExport def info(message: String, loc: At = At.empty): Message = {
    Message(loc, message, Info)
  }

  /** Generate a generic warning */
  @JSExport def warning(message: String, loc: At = At.empty): Message = {
    Message(loc, message, Warning)
  }

  /** Generate an error message */
  @JSExport
  def error(
    message: String,
    loc: At = At.empty
  ): Message = { Message(loc, message) }

  /** Generate an error message resulting from an exception received */
  private def exceptionToError(exception: Throwable, loc: At = At.empty, context: String = ""): Message = {
    val message = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    Message(loc, s"While $context: $message")
  }

  /** Generate a severe error message based on an exception received */
  @JSExport def severe(message: String, exception: Throwable, loc: At): Message = {
    val message2 = ExceptionUtils.getRootCauseStackTrace(exception).mkString("\n", "\n  ", "\n")
    Message(loc, message + ": " + message2, SevereError)
  }

  /** Generate a severe error message */
  @JSExport def severe(message: String, loc: At = At.empty): Message = {
    Message(loc, message, SevereError)
  }

  /** Generate a [[scala.List]] with a single error message in it */
  @JSExport def errors(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message))
  }

  /** Generate a [[scala.List]] with a single warning message in it */
  @JSExport def warnings(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message, Warning))
  }

  /** Generate a [[scala.List]] with a single server error message in it */
  @JSExport def severes(message: String, loc: At = At.empty): Messages = {
    List(Message(loc, message, Messages.SevereError))
  }

  /** A frequently used shortcut for a [[scala.collection.immutable.List]] of [[Message]]. Note that this
   * has an <code>extension</code> that extends the capability of the list.  */
  type Messages = List[Message]

  /** Extensions to [[scala.collection.immutable.List]] of [[Messages]] */
  extension (msgs: Messages) {
    /** Format all the messages with a newline in between them. */
    @JSExport def format: String = {
      msgs.map(_.format).mkString(System.lineSeparator())
    }
    /** Return true iff all the messages are only warnings  */
    @JSExport def isOnlyWarnings: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind > Warning)
    }
    /** Return true iff all the messages are considered ignorable (all warnings) */
    @JSExport def isOnlyIgnorable: Boolean = {
      msgs.isEmpty || !msgs.exists(_.kind >= Warning)
    }
    /** Return true iff at least one of the messages is an [[Error]] */
    @JSExport def hasErrors: Boolean = {
      msgs.nonEmpty && msgs.exists(_.kind >= Error)
    }
    /** Return true iff at least one of the messages is of a [[Warning]] type. */
    @JSExport def hasWarnings: Boolean = {
      msgs.nonEmpty && msgs.exists(_.kind < Error)
    }
    /** Return a filtered list of just the [[Info]] messages. */
    @JSExport def justInfo: Messages = msgs.filter(_.isInfo)
    /** Return a filtered list of just the [[MissingWarning]] messages. */
    @JSExport def justMissing: Messages = msgs.filter(_.isMissing)
    /** Return a filtered list of just the [[StyleWarning]] messages. */
    @JSExport def justStyle: Messages = msgs.filter(_.isStyle)
    /** Return a filtered list of just the [[UsageWarning]] messages. */
    @JSExport def justUsage: Messages = msgs.filter(_.isUsage)
    /** Return a filtered list of just the [[Warning]] messages. */
    @JSExport def justWarnings: Messages = msgs.filter(m => m.kind < Error && m.kind > Info)
    /** Return a filtered list of just the [[Error]] messages. */
    @JSExport def justErrors: Messages = msgs.filter(_.kind >= Error)
    /** Return a filtered list of just the [[Info]] messages. */
    @JSExport def highestSeverity: Int = msgs.foldLeft(0) { case (n, m) => Math.max(m.kind.severity, n) }
  }

  /** Canonical definition of an empty message list */
  @JSExport val empty: Messages = List.empty[Message]

  /** Format and log the <code>messages</code> to the <code>log</code> per the <code>options</code>
   *
   * @param messages
   *   The list of messages to log
   * @param log
   *   The [[com.ossuminc.riddl.utils.Logger]] instance to which the messages are sent.
   * @param options
   *   The [[com.ossuminc.riddl.language.CommonOptions]] that can control how the logged messages appear.
   * @return
   */
  @JSExport
  def logMessages(
    messages: Messages,
    log: Logger,
    options: CommonOptions
  ): Int = {
    val list = if options.sortMessagesByLocation then messages.sorted else messages
    if options.groupMessagesByKind then { logMessagesByGroup(list, options, log) }
    else { logMessagesRetainingOrder(list, log) }
    list.highestSeverity
  }

  private def logMessage(message: Message, log: Logger): Unit = {
    message.kind match {
      case Info           => log.info(message.format)
      case StyleWarning   => log.style(message.format)
      case MissingWarning => log.missing(message.format)
      case UsageWarning   => log.usage(message.format)
      case Warning        => log.warn(message.format)
      case Error          => log.error(message.format)
      case SevereError    => log.severe(message.format)
    }
  }

  private def logMessagesRetainingOrder(list: Messages, log: Logger): Unit = {
    list.foreach { msg => logMessage(msg, log) }
  }

  private def logMessagesByGroup(
    messages: Messages,
    commonOptions: CommonOptions,
    log: Logger
  ): Unit = {
    def logMsgs(kind: KindOfMessage, maybeMessages: Option[Seq[Message]]): Unit = {
      val messages = maybeMessages.getOrElse(Seq.empty[Message])
      if messages.nonEmpty then {
        kind match {
          case UsageWarning =>
            log.usage(s"""$kind Message Count: ${messages.length}""")
          case StyleWarning =>
            log.style(s"""$kind Message Count: ${messages.length}""")
          case MissingWarning =>
            log.missing(s"""$kind Message Count: ${messages.length}""")
          case Warning => // everything else is a warning
            log.warn(s"""$kind Message Count: ${messages.length}""")
          case Error =>
            log.error(s"""$kind Message Count: ${messages.length}""")
          case SevereError =>
            log.severe(s"""$kind Message Count: ${messages.length}""")
          case Info =>
            log.info(s"""$kind Message Count: ${messages.length}""")
        }
        messages.foreach { (msg: Message) => logMessage(msg, log) }
      }
    }
    if messages.nonEmpty then {
      val groups = messages.groupBy(_.kind)
      logMsgs(SevereError, groups.get(SevereError))
      logMsgs(Error, groups.get(Error))

      if commonOptions.showWarnings then {
        if commonOptions.showUsageWarnings then {
          logMsgs(UsageWarning, groups.get(UsageWarning))
        }
        if commonOptions.showMissingWarnings then {
          logMsgs(MissingWarning, groups.get(MissingWarning))
        }
        if commonOptions.showStyleWarnings then {
          logMsgs(StyleWarning, groups.get(StyleWarning))
        }
      }
      logMsgs(Info, groups.get(Info))
    }
  }

  /** A utility to help accumulate error messages with regards to the settings in the [[CommonOptions]] */
  @JSExportTopLevel("Accumulator")
  case class Accumulator(commonOptions: CommonOptions) {
    private val msgs: mutable.ListBuffer[Message] = mutable.ListBuffer.empty

    def size: Int = msgs.length

    @inline def isEmpty: Boolean = msgs.isEmpty
    @inline def nonEmpty: Boolean = !isEmpty

    @inline def toMessages: Messages = msgs.toList

    /** Add an arbitrary [[Message]] to the accumulated [[Messages]]
     *
     * @param message
     * The text of the message to add
     * @param loc
     * The location in the source related to the message.
     * @return
     * This type, so you can chain another call to this accumulator
     */
    @JSExport
    def add(msg: Message): this.type = {
      msg.kind match {
        case Warning =>
          if commonOptions.showWarnings then msgs.append(msg)
        case StyleWarning =>
          if commonOptions.showStyleWarnings && commonOptions.showWarnings then msgs.append(msg)
        case MissingWarning =>
          if commonOptions.showMissingWarnings && commonOptions.showWarnings then msgs.append(msg)
        case UsageWarning =>
          if commonOptions.showUsageWarnings && commonOptions.showWarnings then msgs.append(msg)
        case Info =>
          if commonOptions.showInfoMessages then msgs.append(msg)
        case Error | SevereError => msgs.append(msg)
      }
      this
    }

    /** Add a [[StyleWarning]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def style(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, StyleWarning))
    }

    /** Add an [[Info]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def info(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, Info))
    }

    /** Add a [[Warning]] message to the accumulated [[Messages]]
     *
     * @param message
     * The text of the message to add
     * @param loc
     * The location in the source related to the message.
     * @return
     * This type, so you can chain another call to this accumulator
     */
    @inline def warning(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, Warning))
    }

    /** Add an [[Error]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def error(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, Error))
    }

    /** Add a [[SevereError]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def severe(message: String, loc: At = At.empty): this.type = {
      add(Message(loc, message, SevereError))
    }

    /** Add a [[StyleWarning]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def addStyle(loc: At, msg: String): this.type = {
      add(Message(loc, msg, StyleWarning))
    }

    /** Add a [[UsageWarning]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def addUsage(loc: At, msg: String): this.type = {
      add(Message(loc, msg, UsageWarning))
    }

    /** Add a [[MissingWarning]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def addMissing(loc: At, msg: String): this.type = {
      add(Message(loc, msg, MissingWarning))
    }

    /** Add a [[Warning]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def addWarning(loc: At, msg: String): this.type = {
      add(Message(loc, msg, Warning))
    }

    /** Add an [[Error]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def addError(loc: At, msg: String): this.type = {
      add(Message(loc, msg, Error))
    }

    /** Add a [[SevereError]] message to the accumulated [[Messages]]
     *
     * @param message
     *   The text of the message to add
     * @param loc
     *   The location in the source related to the message.
     * @return
     *   This type, so you can chain another call to this accumulator
     */
    @inline def addSevere(loc: At, msg: String): this.type = {
      add(Message(loc, msg, SevereError))
    }
  }

  @JSExportTopLevel("Accumulator$")
  object Accumulator {
    val empty: Accumulator = new Accumulator(CommonOptions())
    def apply(commonOptions: CommonOptions): Accumulator = new Accumulator(commonOptions)
  }
}