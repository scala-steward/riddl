/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{CommonOptions, Messages}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import org.scalatest.TestData

class AuthorTest extends ValidatingTest {

  "Authors" should {
    "be defined empty in domains" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "not supported in contexts" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is { context bar is { author FooBar is { ??? } } }""".stripMargin,td)
      parseDomainDefinition(input, identity) match {
        case Left(msgs) =>
          msgs.isOnlyIgnorable must be(false)
          msgs.head.message must include("Expected one of")
        case Right(_) => fail("should not have parsed")
      }
    }
    "author must be defined" in { (td: TestData) =>
      val input = RiddlParserInput(s"""domain foo is { by author Bar }""",td)
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) { case (_, _, msgs) =>
        val errs = msgs.justErrors
        errs mustNot be(empty)
        assertValidationMessage(errs, Messages.Error, "author Bar is not defined")
        assertValidationMessage(
          errs,
          Messages.Error,
          """Path 'Bar' was not resolved, in Domain 'foo'
              |because the sought name, 'Bar', was not found in the symbol table,
              |and it should refer to an Author""".stripMargin
        )
      }
    }
    "referenced from Application" in { (td: TestData) =>
      val input = RiddlParserInput(
        """author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |domain Foo is {
          |  application Bar  is { by author Reid }
          |}
          |""".stripMargin,td)
      Riddl.parseAndValidate(input) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          result.messages.isOnlyIgnorable must be(true)
      }
    }
    "referenced from Context" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |  context Bar  is { by author Reid }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "referencable from neighbor Domain" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |}
          |domain Bar  is { by author Reid }
          |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.hasErrors must be(false)
      }
    }
    "referenced from sub-domain" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |  domain Bar  is { by author Foo.Reid }
          |}
          |""".stripMargin, td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "referenced from Entity" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |  context Bar  is {
          |    by author Reid
          |    entity Bar is { by author Reid  }
          |  }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
        msgs.isOnlyWarnings must be(true)
      }
    }
    "referenced from Function" in { (td: TestData) =>
      val input = RiddlParserInput(
      """domain Foo is {
        |  author Reid is {
        |    name: "Reid Spencer"
        |    email: "reid@ossum.biz"
        |  }
        |  context Bar {
        |    function FooBar is {
        |      by author Reid
        |      body { ??? }
        |    }
        |  }
        |}
        |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
        msgs.isOnlyWarnings must be(true)
      }
    }
    "referenced from Repository" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |  context Bar is {
          |    repository FooBar  is { by author Reid }
          |  }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "referenced from an Epic" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Foo is {
          |  author Reid is {
          |    name: "Reid Spencer"
          |    email: "reid@ossum.biz"
          |  }
          |  user U is "foo"
          |  epic Bar is {
          |   user U wants to "hum" so that "haw"
          |   by author Reid  
          |  }
          |}
          |""".stripMargin,td)
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
  }
}