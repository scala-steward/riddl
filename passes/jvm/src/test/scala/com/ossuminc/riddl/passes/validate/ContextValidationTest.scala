/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{ec, pc, Await}
import com.ossuminc.riddl.utils.PathUtils

import java.nio.file.Path
import org.scalatest.TestData

import scala.concurrent.duration.DurationInt

class ContextValidationTest extends JVMAbstractValidatingTest {

  "Context" should {
    "allow options" in { (td: TestData) =>
      val input =
        """option wrapper option service option gateway option package("foo") option technology("http")  """
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        msgs.filter(_.kind.isError) mustBe empty
        context.options.size mustBe 5
        context.options must contain(OptionValue((2, 9, rpi), "wrapper", Seq.empty))
        context.options must contain(OptionValue((2, 39, rpi), "gateway", Seq.empty))
        context.options must contain(OptionValue((2, 24, rpi), "service", Seq.empty))
        context.options must contain(OptionValue((2, 54, rpi), "package", Seq(LiteralString((2, 62, rpi), "foo"))))
        context.options must contain(OptionValue((2, 76, rpi), "technology", Seq(LiteralString((2, 87, rpi), "http"))))
      }
    }
    "allow types" in { (td: TestData) =>
      val input = """type C is Current
                    |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        msgs.filter(_.kind.isError) mustBe empty
        context.types.size mustBe 1
        context.types.head mustBe Type(
          (2, 2, rpi),
          Identifier((2, 7, rpi), "C"),
          Current((2, 12, rpi))
        )
      }
    }
    "allow functions" in { (td: TestData) =>
      val input =
        """function bar is {
          |  requires { i: Integer }
          |  returns { o: Integer }
          |  ???
          |}
          |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors mustBe empty
        context.functions.size mustBe 1
        val expected = Function(
          (2, 2, rpi),
          Identifier((2, 11, rpi), "bar"),
          input = Some(
            Aggregation(
              (3, 12, rpi),
              Contents(
                Field(
                  (3, 14, rpi),
                  Identifier((3, 14, rpi), "i"),
                  Integer((3, 17, rpi))
                )
              )
            )
          ),
          output = Some(
            Aggregation(
              (4, 11, rpi),
              Contents(
                Field(
                  (4, 13, rpi),
                  Identifier((4, 13, rpi), "o"),
                  Integer((4, 16, rpi))
                )
              )
            )
          )
        )
        context.functions.head mustBe expected
      }
    }
    "allow entities" in { (td: TestData) =>
      val input = """entity bar is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors must be(empty)
        val expected = Entity((2, 2, rpi), Identifier((2, 9, rpi), "bar"))
        context.entities.size mustBe 1
        context.entities.head mustBe expected
      }

    }
    "allow processors" in { (td: TestData) =>
      val input = """source foo is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors must be(empty)
        val expected = Streamlet(
          (2, 2, rpi),
          Identifier((2, 9, rpi), "foo"),
          Source((2, 2, rpi))
        )
        context.streamlets.size mustBe 1
        context.streamlets.head mustBe expected
      }
    }
    "allow projectors" in { (td: TestData) =>
      val input =
        """projector foo is {
          |  record one is { ??? }
          |  handler one is { ??? }
          |}
          |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors must be(empty)
        context.projectors.size mustBe 1
        val actual = context.projectors.head
        val expected =
          Projector(
            (2, 2, rpi),
            Identifier((2, 12, rpi), "foo"),
            Contents(
              Type(
                (3, 3, rpi),
                Identifier((3, 10, rpi), "one"),
                AggregateUseCaseTypeExpression(
                  (3, 17, rpi),
                  RecordCase,
                  Contents.empty
                )
              ),
              Handler(
                (4, 11, rpi),
                Identifier((4, 11, rpi), "one"),
                Contents.empty
              )
            )
          )
        actual mustBe expected
      }
    }

    "allow includes" in { (td: TestData) =>
      val name = "language/jvm/src/test/input/context/context-with-include.riddl"
      val path = Path.of(name)
      val url = PathUtils.urlFromCwdPath(path)
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        parseTopLevelDomains(rpi) match
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            root mustNot be(empty)
            root.contents mustNot be(empty)
            val d = root.domains.head
            d.contexts mustNot be(empty)
            val c = d.contexts.head
            c.includes mustNot be(empty)
            val inc = c.includes.head
            inc.contents.filter[Comment].headOption match
              case Some(c: Comment) => c.format.contains("foo")
              case None             => fail("test case should have term 'foo'")
            end match
        end match
      }
      Await.result(future, 10.seconds)
    }
  }
}
