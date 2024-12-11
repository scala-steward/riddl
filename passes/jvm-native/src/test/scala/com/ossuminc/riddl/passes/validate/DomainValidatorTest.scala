/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Pass
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.utils.CommonOptions
import org.scalatest.TestData

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends AbstractValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in { (td: TestData) =>
      val source =
        """
          |domain foo is { ??? } 
          |domain foo is { ??? }
          |""".stripMargin
      val rpi = RiddlParserInput(source, td)
      Riddl.parseAndValidate(rpi) match {
        case Left(errors) => fail(errors.justErrors.format)
        case Right(result) =>
          val theErrors: Messages = result.messages.justErrors
          theErrors mustBe empty
          val messages = result.messages.format
          val notOccur = "Domain 'foo' overloads Domain 'foo' at empty(2:1->3:1)"
          messages must include(notOccur)
      }
    }

    "allow author information" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """author Reid is {
                |    name: "Reid Spencer"
                |    email: "reid@ossuminc.com"
                |    organization: "Ossum Inc."
                |    title: "President"
                |  } with { described as "identifying" }
                |domain foo is { ??? } with {
                |  by author Reid
                |  described as "example"
                |}
                |""".stripMargin,
        td
      )
      Riddl.parseAndValidate(rpi) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          val domain: Domain = result.root.domains.head
          val author: Author = result.root.authors.head
          domain must be(empty)
          domain.contents must be(empty)
          val expectedAuthor =
            Author(
              (1, 1, rpi),
              Identifier((1, 8, rpi), "Reid"),
              LiteralString((2, 11, rpi), "Reid Spencer"),
              LiteralString((3, 12, rpi), "reid@ossuminc.com"),
              Some(LiteralString((4, 19, rpi), "Ossum Inc.")),
              Some(LiteralString((5, 12, rpi), "President")),
              None,
              Contents(
                BlockDescription(
                  (6, 12, rpi),
                  Seq(LiteralString((6, 25, rpi), "identifying"))
                )
              )
            )
          domain.authors must be(empty)
          author must be(expectedAuthor)
          val expectedAuthorRef =
            AuthorRef((8, 3, rpi), PathIdentifier((8, 13, rpi), Seq("Reid")))
          domain.authorRefs mustNot be(empty)
          domain.authorRefs.head must be(expectedAuthorRef)
      }
    }
    "identify useless domain hierarchy" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          |domain foo is {
          |  domain bar is { ??? }
          |}""".stripMargin,
        td
      )
      parseAndValidateDomain(input) { (domain: Domain, _: RiddlParserInput, messages: Messages) =>
        domain mustNot be(empty)
        domain.contents mustNot be(empty)
        messages mustNot be(empty)
        messages.isOnlyIgnorable mustBe true
        messages.find(_.message.contains("Singly nested")) mustNot be(empty)
      }
    }
  }
}