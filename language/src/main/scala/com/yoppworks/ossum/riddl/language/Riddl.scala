package com.yoppworks.ossum.riddl.language

import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage

/** Primary Interface to Riddl Language parsing and validating */
object Riddl {

  trait Logger {
    def severe(s: => String): Unit
    def error(s: => String): Unit
    def warn(s: => String): Unit
    def info(s: => String): Unit
  }

  case object SysLogger extends Logger {
    override def severe(s: => String): Unit = {
      System.err.println("[severe] " + s)
    }

    override def error(s: => String): Unit = {
      System.err.println("[error] " + s)
    }

    override def warn(s: => String): Unit = {
      System.err.println("[warning] " + s)
    }

    override def info(s: => String): Unit = {
      System.err.println("[info] " + s)
    }
  }

  trait Options {
    def showTimes: Boolean
    def showWarnings: Boolean
    def showMissingWarnings: Boolean
    def showStyleWarnings: Boolean
  }

  /** Runs a code block and returns its result, and prints its execution time to stdout.
    * Execution time is only written if `show` is set to `true`.
    *
    * e.g.
    *
    * timer("my-stage", true) { 1 + 1 } // 2
    *
    * prints: Stage 'my-stage': 0.000 seconds
    *
    * @param stage The name of the stage, is included in output message
    * @param show if `true`, then message is printed, otherwise not
    * @param f the code block to execute
    *
    * @return The result of running `f`
    */
  def timer[T](stage: String, show: Boolean = true)(f: => T): T =
    RiddlImpl.timer(Clock.systemUTC(), System.out, stage, show)(f)

  def parse(
    path: Path,
    logger: Logger,
    options: Options
  ): Option[RootContainer] = {
    if (Files.exists(path)) {
      val input = new FileParserInput(path)
      parse(input, logger, options)
    } else {
      logger.error(s"Input file `${path.toString} does not exist.")
      None
    }
  }

  def parse(
    input: RiddlParserInput,
    logger: Logger,
    options: Options
  ): Option[RootContainer] = {
    timer("parse", options.showTimes) {
      TopLevelParser.parse(input) match {
        case Left(errors) =>
          errors.map(_.format).foreach(logger.error(_))
          logger.info(s"Syntax Errors: ${errors.length}")
          None
        case Right(root) =>
          Some(root)
      }
    }
  }

  def validate(
    source: String,
    root: RootContainer,
    log: Logger,
    options: Options
  ): Option[RootContainer] = {
    val messages: Seq[ValidationMessage] =
      Validation.validate[RootContainer](root)
    if (messages.nonEmpty) {
      val (warns, errs) = messages.partition(_.kind.isWarning)
      val (severe, errors) = errs.partition(_.kind.isSevereError)
      val missing = warns.filter(_.kind.isMissing)
      val style = warns.filter(_.kind.isStyle)
      val warnings = warns.filterNot(x => x.kind.isMissing | x.kind.isStyle)
      if (options.showMissingWarnings) {
        missing.map(_.format(source)).foreach(log.warn(_))
      }
      if (options.showStyleWarnings) {
        style.map(_.format(source)).foreach(log.warn(_))
      }
      if (options.showWarnings) {
        warnings.map(_.format(source)).foreach(log.warn(_))
      }
      log.info(s"""Validation Warnings: ${warns.length}""")
      errors.map(_.format(source)).foreach(log.error(_))
      log.info(s"""Validation Errors: ${errors.length} errors""")
      severe.map(_.format(source)).foreach(log.severe(_))
      log.info(s"""Severe Errors: ${errors.length} errors""")
      if (errs.nonEmpty) {
        None
      } else {
        Some(root)
      }
    } else {
      Some(root)
    }
  }

  def parseAndValidate(
    input: RiddlParserInput,
    logger: Logger,
    options: Options
  ): Option[RootContainer] = {
    parse(input, logger, options) match {
      case Some(root) =>
        validate(input.origin, root, logger, options)
      case None =>
        None
    }
  }

  def parseAndValidate(
    path: Path,
    logger: Logger,
    options: Options
  ): Option[RootContainer] = {
    parse(path, logger, options) match {
      case Some(root) =>
        val source = path.toString
        validate(source, root, logger, options)
      case None =>
        None
    }
  }
}

/** Private implementation details which allow for more testability */
private[language] object RiddlImpl {

  /** Runs a code block and returns its result, while recording its execution time, according to the passed clock.
    * Execution time is written to `out`, if `show` is set to `true`.
    *
    * e.g.
    *
    * timer(Clock.systemUTC(), System.out, "my-stage", true) { 1 + 1 } // 2
    *
    * prints: Stage 'my-stage': 0.000 seconds
    *
    * @param clock the clock that provides the start/end times to compute execution time
    * @param out the PrintStream to write execution time information to
    * @param stage The name of the stage, is included in output message
    * @param show if `true`, then message is printed, otherwise not
    * @param f the code block to execute
    *
    * @return The result of running `f`
    */
  def timer[T](
    clock: Clock,
    out: PrintStream,
    stage: String,
    show: Boolean
  )(
    f: => T
  ): T = {
    if (show) {
      val start = clock.millis()
      val result = f
      val stop = clock.millis()
      val delta = stop - start
      val seconds = delta / 1000
      val milliseconds = delta % 1000
      out.println(f"Stage '$stage': $seconds.$milliseconds%03d seconds")
      result
    } else {
      f
    }
  }
}