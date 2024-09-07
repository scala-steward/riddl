/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.{CommonOptions, RiddlFilesTestBase}
import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Pass.standardPasses
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass, PrettifyState}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput, Riddl}
import org.apache.commons.io.FileUtils
import org.scalatest.{Assertion, TestData}

import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path

/** Test The PrettifyPass's ability to generate consistent output */
class PrettifyPassTest extends RiddlFilesTestBase {

  def checkAFile(rootDir: Path, file: File): Assertion = { checkAFile(file) }

  def runPrettify(source: RiddlParserInput, run: String): String = {
    val passes = standardPasses ++ Seq(
      { (input: PassInput, outputs: PassesOutput) =>
        val options = PrettifyPass.Options(flatten=true)
        PrettifyPass(input, outputs, options)
      }
    )
    Riddl.parseAndValidate(source, CommonOptions(), shouldFailOnError = true, passes) match {
      case Left(messages) =>
        val errors = messages.justErrors
        require(errors.nonEmpty, "No actual errors!")
        fail(
          s"Errors on $run generation:\n" + errors.format +
            s"\nIn Source:\n ${source.data}\n" + "\n"
        )
      case Right(result) =>
        val prettifyOutput = result.outputOf[PrettifyOutput](PrettifyPass.name).get
        prettifyOutput.state.filesAsString
    }
  }

  /** Parse and prettify a file twice and compare the original with the third version. */
  def checkAFile(
    file: File
  ): Assertion = {
    val input1 = RiddlParserInput.fromCwdPath(file.toPath)
    val output1 = runPrettify(input1, "first")
    FileUtils.writeStringToFile(new File("target/prettify-1.txt"), output1, Charset.forName("UTF-8"))
    val input2 = RiddlParserInput(output1,"firstGeneration")
    val output2 = runPrettify(input2, "second")
    FileUtils.writeStringToFile(new File("target/prettify-2.txt"), output2, Charset.forName("UTF-8"))
    val input3 = RiddlParserInput(output2,"secondGeneration")
    val output3 = runPrettify(input3, "third")
    FileUtils.writeStringToFile(new File("target/prettify-3.txt"), output3, Charset.forName("UTF-8"))
    output1 mustEqual output2
    output2 mustEqual output3
    output3 mustEqual output1
  }

  "PrettifyPass" should {
    "check domains" in { (_: TestData) =>
      processADirectory("passes/jvm/src/test/input/domains")
    }
    "check enumerations" in { (_: TestData) =>
      processADirectory("passes/jvm/src/test/input/enumerations")
    }
    "check mappings" in { (_: TestData) =>
      processADirectory("passes/jvm/src/test/input/mappings")
    }
    "check ranges" in { (_: TestData) =>
      processADirectory("passes/jvm/src/test/input/ranges")
    }
    "check everything.riddl" in { (_: TestData) =>
      processAFile("language/jvm/src/test/input/everything.riddl")
    }
    "check petstore.riddl" in { (_: TestData) =>
      processAFile("language/jvm/src/test/input/petstore.riddl")
    }
    "check rbbq.riddl" in { (_: TestData) =>
      processAFile("language/jvm/src/test/input/rbbq.riddl")
      println("done")
    }
  }

  "PrettifyOutput" must {
    "construct" in { _ =>
      val ps = PrettifyState(flatten=true)
      ps.numFiles must be(1)
      val po = PrettifyOutput(Root.empty, Messages.empty, ps)
      po.messages must be(empty)
    }
  }
}