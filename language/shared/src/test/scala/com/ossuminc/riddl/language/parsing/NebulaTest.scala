package com.ossuminc.riddl.language.parsing

import org.scalatest.TestData

class NebulaTest extends NoJVMParsingTest {

  "Module" should {
    "be accepted at root scope" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |nebula is {
          | domain blah is { ??? }
          |}
          |""".stripMargin, td
      )
      parseNebula(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) => succeed
    }
  }

}

