/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.{At, CommonOptions}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages.SevereError
import com.ossuminc.riddl.language.Messages.errors
import com.ossuminc.riddl.language.Messages.highestSeverity
import com.ossuminc.riddl.language.Messages.severes
import com.ossuminc.riddl.utils.*
import com.ossuminc.riddl.passes.PassesResult
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import scopt.OParser
import scopt.OParserBuilder

import java.io.File
import java.nio.file.Path
import scala.annotation.unused
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.util.control.NonFatal

  /** The service interface for Riddlc command plugins */
trait Command[OPT <: CommandOptions: ClassTag](val pluginName: String):

  private val optionsClass : Class[?] = classTag[OPT].runtimeClass

  /** Provide an scopt OParser for the commands options type, OPT
    * @return
    *   A pair: the OParser and the default values for OPT
    */
  def getOptionsParser: (OParser[Unit, OPT], OPT)

  def parseOptions(
    args: Array[String]
  ): Option[OPT] = {
    val (parser, default) = getOptionsParser
    val (result, effects) = OParser.runParser(parser, args, default)
    OParser.runEffects(effects)
    result
  }

  /** Provide a typesafe/Config reader for the commands options. This reader should read an object having the same name
    * as the command. The fields of that object must correspond to the fields of the OPT type.
    * @return
    *   A pureconfig.ConfigReader[OPT] that knows how to read OPT
    */
  def getConfigReader: ConfigReader[OPT]

  def loadOptionsFrom(
    configFile: Path,
    log: Logger,
    commonOptions: CommonOptions = CommonOptions()
  ): Either[Messages, OPT] = {
    if commonOptions.verbose then {
      log.info(s"Reading command options from: $configFile")
    }
    ConfigSource.file(configFile).load[OPT](getConfigReader) match {
      case Right(value) =>
        if commonOptions.verbose then {
          log.info(s"Read command options from $configFile")
        }
        if commonOptions.debug then {
          println(StringHelpers.toPrettyString(value, 1))
        }
        Right(value)
      case Left(fails) =>
        Left(
          errors(
            s"Errors while reading $configFile:\n" + fails.prettyPrint(1)
          )
        )
    }
  }

  /** Execute the command given the options. Error should be returned as Left(messages) and not directly logged. The log
    * is for verbose or debug output
    *
    * @param options
    *   The command specific options
    * @param commonOptions
    *   The options common to all commands
    * @param log
    *   A logger for logging errors, warnings, and info
    * @return
    *   Either a set of Messages on error or a Unit on success
    */
  def run(
   @unused options: OPT,
   @unused commonOptions: CommonOptions,
   @unused log: Logger,
   @unused outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] =
    Left(
      severes(
        s"""In command '$pluginName':
         |the CommandPlugin.run(OPT,CommonOptions,Logger) method was not overridden""".stripMargin
      )
    )
  end run

  def run(
    args: Array[String],
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path] = None
  ): Either[Messages, PassesResult] =
    val maybeOptions: Option[OPT] = parseOptions(args)
    maybeOptions match
      case Some(opts: OPT) =>
        val command = args.mkString(" ")
        if commonOptions.verbose then { log.info(s"Running command: $command") }
        val result = Timer.time(command, show = commonOptions.showTimes, log) {
          run(opts, commonOptions, log, outputDirOverride)
        }
        result
      case None => Left(errors(s"Failed to parse $pluginName options"))
    end match
  end run

  private type OptionPlacer[V] = (V, OPT) => OPT
  protected val builder: OParserBuilder[OPT] = OParser.builder[OPT]
  import builder.*

  def inputFile(f: OptionPlacer[File]): OParser[File, OPT] =
    arg[File]("input-file")
      .required()
      .action((v, c) => f(v, c))
      .text("required riddl input file to read")

  def outputDir(f: OptionPlacer[File]): OParser[File, OPT] =
    opt[File]('o', "output-dir")
      .optional()
      .action((v, c) => f(v, c))
      .text("required output directory for the generated output")

  protected def replaceInputFile(
    options: OPT,
    @unused inputFile: Path
  ): OPT = options

  def resolveInputFileToConfigFile(
    options: OPT,
    commonOptions: CommonOptions,
    configFile: Path
  ): OPT =
    options.inputFile match
      case Some(inFile) =>
        val parent = Option(configFile.getParent) match
          case Some(path) => path
          case None       => Path.of(".")
        val input = parent.resolve(inFile)
        val result = replaceInputFile(options, input)
        if commonOptions.debug then
          val pretty = StringHelpers.toPrettyString(
            result,
            1,
            Some(s"Loaded these options:${System.lineSeparator()}")
          )
          println(pretty)
        end if
        result
      case None => options
    end match
  end resolveInputFileToConfigFile
end Command
