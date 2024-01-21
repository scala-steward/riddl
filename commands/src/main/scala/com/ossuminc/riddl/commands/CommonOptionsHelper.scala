/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.utils.{RiddlBuildInfo, SysLogger}

import scopt.DefaultOEffectSetup
import scopt.DefaultOParserSetup
import scopt.OParser
import scopt.OParserBuilder
import scopt.OParserSetup
import scopt.RenderingMode

import scala.concurrent.duration.FiniteDuration

import java.io.File
import java.util.Calendar

/** Handle processing of Language module's CommonOptions */
object CommonOptionsHelper {

  private def year: Int = Calendar.getInstance().get(Calendar.YEAR)
  private val start: String = RiddlBuildInfo.startYear
  val blurb: String =
    s"""RIDDL Compiler © $start-$year Ossum Inc. All rights reserved."
       |Version: ${RiddlBuildInfo.version}
       |
       |This program parses, validates and translates RIDDL sources to other kinds
       |of documents. RIDDL is a language for system specification based on Domain
       |Drive Design, Reactive Architecture, and distributed system principles.
       |
       |""".stripMargin

  lazy val commonOptionsParser: OParser[Unit, CommonOptions] = {
    val builder: OParserBuilder[CommonOptions] = OParser.builder[CommonOptions]

    import builder.*

    OParser.sequence(
      programName("riddlc"),
      head(blurb),
      opt[Unit]('t', name = "show-times")
        .optional()
        .action((_, c) => c.copy(showTimes = true))
        .text("Show parsing phase execution times"),
      opt[Unit]('i', name = "show-include-times")
        .optional()
        .action((_, c) => c.copy(showIncludeTimes = true))
        .text("Show parsing of included files execution times"),
      opt[Unit]('d', "dry-run")
        .optional()
        .action((_, c) => c.copy(dryRun = true))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose")
        .optional()
        .action((_, c) => c.copy(verbose = true))
        .text("Provide verbose output detailing actions taken by riddlc"),
      opt[Unit]('D', "debug")
        .optional()
        .action((_, c) => c.copy(debug = true))
        .text("Enable debug output. Only useful for riddlc developers"),
      opt[Unit]('q', "quiet")
        .optional()
        .action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('a', "noANSIMessages")
        .optional()
        .action((_, c) => c.copy(noANSIMessages = true))
        .text("Do not print messages with ANSI formatting"),
      opt[Unit]('w', name = "suppress-warnings")
        .optional()
        .action((_, c) =>
          c.copy(
            showWarnings = false,
            showMissingWarnings = false,
            showStyleWarnings = false,
            showUsageWarnings = false
          )
        )
        .text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "suppress-missing-warnings")
        .optional()
        .action((_, c) => c.copy(showMissingWarnings = false))
        .text("Suppress warnings about things that are missing"),
      opt[Unit]('s', name = "suppress-style-warnings")
        .optional()
        .action((_, c) => c.copy(showStyleWarnings = false))
        .text("Suppress warnings about questionable input style. "),
      opt[Unit]('u', name = "suppress-usage-warnings")
        .optional()
        .action((_, c) => c.copy(showUsageWarnings = false))
        .text("Suppress warnings about usage of definitions. "),
      opt[Unit](name = "suppress-info-messages")
        .optional()
        .action((_, c) => c.copy(showInfoMessages = false))
        .text("Suppress information output"),
      opt[Unit]('w', name = "hide-warnings")
        .optional()
        .action((_, c) =>
          c.copy(
            showWarnings = false,
            showMissingWarnings = false,
            showStyleWarnings = false,
            showUsageWarnings = false
          )
        ),
      opt[Unit]('m', name = "hide-missing-warnings")
        .optional()
        .action((_, c) => c.copy(showMissingWarnings = false))
        .text("Hide warnings about things that are missing"),
      opt[Unit]('s', name = "hide-style-warnings")
        .optional()
        .action((_, c) => c.copy(showStyleWarnings = false))
        .text("Hide warnings about questionable input style. "),
      opt[Unit]('u', name = "hide-usage-warnings")
        .optional()
        .action((_, c) => c.copy(showUsageWarnings = false))
        .text("Hide warnings about usage of definitions. "),
      opt[Unit](name = "hide-info-messages")
        .optional()
        .action((_, c) => c.copy(showInfoMessages = false))
        .text("Hide information output"),
      opt[File]('P', name = "plugins-dir")
        .optional()
        .action((file, c) => c.copy(pluginsDir = Some(file.toPath)))
        .text("Load riddlc command extension plugins from this directory."),
      opt[Boolean]('S', name = "sort-warnings-by-location")
        .optional()
        .action((_, c) => c.copy(sortMessagesByLocation = true))
        .text(
          "Print all messages sorted by the file name and line number in which they occur."
        ),
      opt[Int]('x', name = "max-parallel-processing")
        .optional()
        .action((v, c) => c.copy(maxParallelParsing = v))
        .text(
          "Controls the maximum number of include files that will be parsed in parallel"
        ),
      opt[Int](name = "max-include-wait")
        .optional()
        .action((v, c) => c.copy(maxIncludeWait = FiniteDuration(v, "seconds")))
        .text("Maximum time that parsing an include file will wait for it to complete"),
      opt[Boolean]("warnings-are-fatal")
        .optional()
        .action((_, c) => c.copy(warningsAreFatal = true))
        .text(
          "Makes validation warnings fatal to encourage code perfection"
        )
    )
  }

  def parseCommonOptions(
    args: Array[String]
  ): (Option[CommonOptions], Array[String]) = {
    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError: Option[Boolean] = Option(false)

      override def renderingMode: RenderingMode.TwoColumns.type =
        RenderingMode.TwoColumns
    }

    val dontTerminate: DefaultOEffectSetup = new DefaultOEffectSetup {
      val log: SysLogger = SysLogger()
      override def displayToOut(msg: String): Unit = { log.info(msg) }

      override def displayToErr(msg: String): Unit = { log.error(msg) }

      override def reportError(msg: String): Unit = { log.error(msg) }

      override def reportWarning(msg: String): Unit = { log.warn(msg) }

      // ignore terminate
      override def terminate(exitState: Either[String, Unit]): Unit = ()
    }

    val saneArgs = args.map(_.trim).filter(_.nonEmpty)
    val options = saneArgs.takeWhile(_.startsWith("-"))
    val remainingOptions = saneArgs.dropWhile(_.startsWith("-"))
    val (common, effects1) = OParser.runParser[CommonOptions](
      commonOptionsParser,
      options,
      com.ossuminc.riddl.language.CommonOptions(),
      setup
    )
    OParser.runEffects(effects1, dontTerminate)
    common -> remainingOptions
  }
}
