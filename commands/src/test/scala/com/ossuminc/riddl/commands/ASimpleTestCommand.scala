/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command, CommandOptions}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.{PlatformContext, Logger}
import pureconfig.{ConfigCursor, ConfigReader}
import scopt.OParser

import java.nio.file.Path

object ASimpleTestCommand {
  case class Options(inputFile: Option[Path] = None, arg1: String = "") extends CommandOptions {
    val command: String = "test"
  }
}

/** A pluggable command for testing plugin commands! */
class ASimpleTestCommand(using io: PlatformContext) extends Command[ASimpleTestCommand.Options]("test") {
  import ASimpleTestCommand.Options
  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(
      cmd("test").children(
        arg[String]("input-file").action((s, to) => to.copy(inputFile = Some(Path.of(s)))),
        arg[String]("arg1").action((s, to) => to.copy(arg1 = s)).validate { a1 =>
          if a1.nonEmpty then { Right(()) }
          else { Left("All argument keys must be nonempty") }
        }
      )
    ) -> Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      objCur <- cur.asObjectCursor
      contentCur <- objCur.atKey("test")
      contentObjCur <- contentCur.asObjectCursor
      inputFileRes <- contentObjCur.atKey("input-file")
      inputF <- inputFileRes.asString
      arg1Res <- contentObjCur.atKey("arg1")
      str <- arg1Res.asString
    yield { Options(inputFile = Some(Path.of(inputF)), arg1 = str) }
  }

  override def run(
    options: Options,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    io.log.info(s"arg1: '${options.arg1}''")
    Right(PassesResult())
  }
}
