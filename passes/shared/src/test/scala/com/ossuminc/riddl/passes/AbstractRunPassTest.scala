/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, SysLogger}
import org.scalatest.Suite

abstract class AbstractRunPassTest(using PlatformContext) extends AbstractValidatingTest {

  def runPassesWith(
    input: RiddlParserInput,
    passToRun: PassCreator
  ): PassesResult = {
    TopLevelParser.parseInput(input, true) match {
      case Left(messages) => fail(messages.format)
      case Right(root: Root) =>
        val passesToRun = Pass.standardPasses :+ passToRun
        val passInput = PassInput(root)
        val result = Pass.runThesePasses(passInput, passesToRun)
        if result.messages.hasErrors then fail(result.messages.justErrors.format)
        result
    }
  }
}
