/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import org.scalatest.fixture

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.TestData

/** Unit Tests For CommonParser */
class CommonParserTest extends ParsingTest {

  "NonWhiteSpaceParsers" should {
    "handle a literalString" in {  (td: TestData) =>
      val content = "This is a literal string with"
      val text = s""""$content""""
      val input = RiddlParserInput(text, td)
      val testParser = TestParser(input)
      testParser.expect[LiteralString](testParser.literalString(_)) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(ls)      => ls.s must be(content)
    }
  }

  "CommonParser" should {
    "location should construct from pair" in { (td: TestData) =>
      val loc = At((1, 1))
      loc.line mustBe 1
      val column = loc.col
      column mustBe 1
    }

    "descriptions can be URLs" in { (td: TestData) =>
      import com.ossuminc.riddl.utils.URL
      val input = RiddlParserInput(
        """domain foo is { ??? } with { described at https://www.wordnik.com/words/phi }""".stripMargin,
        URL.empty,
        td
      )
      parseDomainDefinition(input, identity) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, _)) =>
          val expected = Domain(
            (1, 1),
            Identifier((1, 8), "foo"),
            contents = Contents.empty,
            descriptives = Seq(
              URLDescription(
                (1, 30),
                URL("https://www.wordnik.com/words/phi")
              )
            )
          )
          domain.toString mustBe expected.toString
      }
    }

    "literal strings can handle any chars except \"" in { (td: TestData) =>
      val input: RiddlParserInput = RiddlParserInput(
        """"special chars: !@#$%^&*()_+-={}[];':,.<>/?~`
           | regular chars: abcdefghijklmnopqrstuvwxyz 0123456789
           | tab and newline chars:
           |"""".stripMargin,
        td
      )
      parse[LiteralString, LiteralString](
        input,
        StringParser("").literalString(_),
        identity
      ) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((actual, _)) =>
          val expected = LiteralString((1, 1), input.data.drop(1).dropRight(1))
          actual mustBe expected

      }
    }
    "literal strings can successfully escape a quote" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is { ??? } with { briefly as "this is an \"explanation\"" } """,
        td
      )
      parseDefinition[Domain](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((domain: Domain, _)) =>
          domain.briefString must be("this is an \\\"explanation\\\"")
      }
    }
    "OWASP email address works" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type EmailAddress = Pattern("
          |^[a-zA-Z0-9_+&*-] + (?:\\.[a-zA-Z0-9_+&*-] + )*@(?:[a-zA-Z0-9-]+\\.) + [a-zA-Z]{2,7}
          |")
          |""".stripMargin,
        td
      )
      parseDefinition[Type](input) match {
        case Left(errors) =>
          fail(errors.format)
        case Right((content: Type, _)) =>
          content.typEx match {
            case Pattern(_, Seq(LiteralString(_, str))) =>
              java.util.regex.Pattern.compile(str)
            case _ => fail("Expected a Pattern")
          }
      }
    }
    "literal strings can contain whitespace, hex and unicode escapes" in { (td: TestData) =>
      val input = RiddlParserInput(""""\\b\\n\\r\\t\\f\\x04\\u000a"""".stripMargin, td)
      parse[LiteralString, LiteralString](
        input,
        StringParser("").literalString(_),
        identity
      ) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((actual, rpi)) =>
          actual mustBe LiteralString((1, 1, rpi), input.data.drop(1).dropRight(1))
      }
    }
  }
}
