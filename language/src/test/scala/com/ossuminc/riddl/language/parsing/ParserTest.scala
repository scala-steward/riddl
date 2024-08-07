/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST

import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global

/** Unit Tests For Parsing */
class ParserTest extends ParsingTest {

  "ParserContext" must {
    "throw on underflow" in {
      val riddlParserInput = RiddlParserInput("")
      val testParser = TestParser(riddlParserInput)
      testParser
    }
  }

  "Parser" must {
    "report bad syntax" in {
      val input = "Flerkins are evil but cute"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          val head = errors.head.message
          head must include("Expected one of")
          head must include("\"author\"")
          head must include("\"domain\"")
        case Right(_) => fail("Invalid syntax should make an error")
      }
    }
    "ensure keywords are distinct" in {
      val input = "domainfoois { author nobody is { ??? } } \n"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          errors.head.message must include("white space after keyword")
        case Right(_) => fail("'domainfoois' should be flagged as needing whitespace after a keyword")
      }
    }
    "handle missing }" in {
      val input = "domain foo is { author nobody is { ??? } \n"
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          errors must not be empty
          if errors.head.message.startsWith("Expected one of (") && errors.head.message.contains("context") then succeed
          else fail(errors.format)
        case Right(_) => fail("Missing closing brace should make an error")
      }
    }
    "allow an empty funky-name domain" in {
      val input = RiddlParserInput("domain 'foo-fah|roo' is { ??? }")
      parseTopLevelDomain(input, _.contents.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe
            Domain((1, 1, rpi), Identifier((1, 8, rpi), "foo-fah|roo"))
      }
    }
    "allow nested domains" in {
      val input = RiddlParserInput("""domain foo is {
                                     |domain bar is { ??? }
                                     |}
                                     |""".stripMargin)
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents mustBe Seq[Domain](
            Domain(
              (1, 1, input),
              Identifier((1, 8, input), "foo"),
              contents = Seq(Domain((2, 1, input), Identifier((2, 8, input), "bar")))
            )
          )
      }
    }
    "allow multiple domains" in {
      val input = RiddlParserInput("""domain foo is { ??? }
                                     |domain bar is { ??? }
                                     |""".stripMargin)
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents mustBe Seq[Domain](
            Domain((1, 1, input), Identifier((1, 8, input), "foo")),
            Domain((2, 1, input), Identifier((2, 8, input), "bar"))
          )
      }
    }
    "allow major definitions to be stubbed with ???" in {
      val input = RiddlParserInput(
        """domain one is { ??? }
          |domain two is {
          |  context one is {
          |    router b is { ??? }
          |  }
          |  context two is {
          |    function foo is { ??? }
          |    term expialidocious is described by { ??? }
          |    entity one is { ??? }
          |    entity two is {
          |      type twoState is { foo: Integer }
          |      state entityState of twoState is { ??? }
          |      handler one  is { ??? }
          |      function one is { ??? }
          |      invariant one is "???"
          |    }
          |    adaptor one from context over.consumption is { ??? }
          |  }
          |}
          |""".stripMargin
      )
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) =>
          content.contents must not be empty
          succeed
      }
    }
    "allow context definitions in domains" in {
      val input = RiddlParserInput("domain foo is { context bar is { ??? } }")
      parseDomainDefinition[Context](input, _.contexts.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe
            Context((1, 17, rpi), id = Identifier((1, 25, rpi), "bar"))
      }
    }
    "allow options on context definitions" in {
      val input = RiddlParserInput(
        """context bar is {
          |  option service
          |  option wrapper
          |  option gateway
          |}
          |""".stripMargin
      )
      parseContextDefinition[Context](input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe Context(
            (1, 1, rpi),
            Identifier((1, 9, rpi), "bar"),
            Seq(
              OptionValue((2, 10, rpi), "service", Seq.empty),
              OptionValue((3, 10, rpi), "wrapper", Seq.empty),
              OptionValue((4, 10, rpi), "gateway", Seq.empty)
            )
          )
      }
    }
    "allow type definitions in contexts" in {
      val rpi =
        RiddlParserInput("""type Vikings = any of {
                           |  Ragnar Lagertha Bjorn Floki Rollo Ivar Aslaug Ubbe
                           |}""".stripMargin)
      parseInContext(rpi, _.types.head) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          val expected = Type(
            (2, 1, rpi),
            Identifier((2, 6, rpi), "Vikings"),
            Enumeration(
              (2, 16, rpi),
              Seq(
                Enumerator(
                  (3, 3, rpi),
                  Identifier((3, 3, rpi), "Ragnar"),
                  None
                ),
                Enumerator(
                  (3, 10, rpi),
                  Identifier((3, 10, rpi), "Lagertha"),
                  None
                ),
                Enumerator(
                  (3, 19, rpi),
                  Identifier((3, 19, rpi), "Bjorn"),
                  None
                ),
                Enumerator(
                  (3, 25, rpi),
                  Identifier((3, 25, rpi), "Floki"),
                  None
                ),
                Enumerator(
                  (3, 31, rpi),
                  Identifier((3, 31, rpi), "Rollo"),
                  None
                ),
                Enumerator(
                  (3, 37, rpi),
                  Identifier((3, 37, rpi), "Ivar"),
                  None
                ),
                Enumerator(
                  (3, 42, rpi),
                  Identifier((3, 42, rpi), "Aslaug"),
                  None
                ),
                Enumerator((3, 49, rpi), Identifier((3, 49, rpi), "Ubbe"), None)
              )
            )
          )
          content mustBe expected
      }
    }
    "allow invariant definitions" in {
      val input = RiddlParserInput(
        """invariant large is "x is greater or equal to 10" """
      )
      parseDefinition[Invariant](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe Invariant(
            (1, 11, rpi),
            Identifier((1, 11, rpi), "large"),
            Option(LiteralString((1, 20, rpi), "x is greater or equal to 10")),
            None,
            None
          )
      }
    }
    "allow entity definitions" in {
      val input = RiddlParserInput("""entity Hamburger is {
         |  option transient
         |  option aggregate
         |  type Foo is { x: String }
         |  state BurgerState of type BurgerStruct {
         |  handler BurgerHandler is {} }
         |}
         |""".stripMargin)
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          val expected = Entity(
            (1, 1, rpi),
            Identifier((1, 8, rpi), "Hamburger"),
            Seq(
              OptionValue((2, 10, rpi), "transient", Seq.empty),
              OptionValue((3, 10, rpi), "aggregate", Seq.empty),
              Type(
                (4, 3, rpi),
                Identifier((4, 8, rpi), "Foo"),
                Aggregation(
                  (4, 15, rpi),
                  List(Field((4, 17, rpi), Identifier((4, 17, rpi), "x"), String_((4, 20, rpi))))
                )
              ),
              State(
                (5, 3, rpi),
                Identifier((5, 9, rpi), "BurgerState"),
                TypeRef((5, 24, rpi), "type", PathIdentifier((5, 29, rpi), List("BurgerStruct"))),
                List(Handler((6, 11, rpi), Identifier((6, 11, rpi), "BurgerHandler")))
              )
            )
          )
          content mustBe expected
      }
    }
    "allow adaptor definitions" in {
      val input =
        RiddlParserInput("adaptor fuzz from context foo.bar is { ??? }")
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((content, rpi)) =>
          content mustBe Adaptor(
            (1, 1, rpi),
            Identifier((1, 9, rpi), "fuzz"),
            InboundAdaptor((1, 14, rpi)),
            ContextRef(
              (1, 19, rpi),
              PathIdentifier((1, 27, rpi), Seq("foo", "bar"))
            ),
            Seq.empty
          )
      }
    }

    "allow functions" in {
      val input = """
                    |function foo is {
                    |  requires { b : Boolean}
                    |  returns { i : Integer}
                    |  body { ??? }
                    |}
                    |""".stripMargin

      parseDefinition[Function](RiddlParserInput(input)) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((function, _)) =>
          function must matchPattern {
            case Function(
                  _,
                  Identifier(_, "foo"),
                  Some(Aggregation(_, Seq(Field(_, Identifier(_, "b"), Bool(_), _, _)))),
                  Some(Aggregation(_, Seq(Field(_, Identifier(_, "i"), Integer(_), _, _)))),
                  _,
                  _,
                  _,
                  _
                ) =>
          }
      }
    }
    "support Replica types in Contexts" in {
      val input: String =
        """domain foo {
          |  context bar is {
          |    type crdt is replica of Integer
          |  }
          |}
          |""".stripMargin
      parseDefinition[Domain](input) match {
        case Left(errors) => fail(errors.format)
        case Right((domain, rpi)) =>
          val typ = domain.contexts.head.types.head
          typ.typ mustBe Replica((3, 18, rpi), Integer((3, 29, rpi)))
      }
    }
    "parse from a complex file" in {
      val rpi = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
      parseTopLevelDomains(rpi) match {
        case Left(errors) =>
          fail(errors.format)
        case Right(root) =>
          /*
          // Top Level Author
          author Reid is { name: "Reid" email: "reid@ossum.biz" }

         // A top level domain
        domain Everything is {
          // How to mark a definition with the author that created it
          by author Reid

          type SomeType is String // <-- that's a type

          /* This is another way to do a comment, just like C/C++ */
          command DoAThing is { thingField: Integer }
           */
          root.contents.startsWith(
            Seq(
              LineComment((1, 1, rpi), "Top Level Author"),
              Author(
                (2, 1, rpi),
                Identifier((2, 8, rpi), "Reid"),
                LiteralString((2, 23, rpi), "Reid"),
                LiteralString((2, 37, rpi), "reid@ossum.biz")
              )
            )
          )
      }
    }
  }
}
