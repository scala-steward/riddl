/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassesResult}
import com.ossuminc.riddl.utils.{pc, ec}
import com.ossuminc.riddl.utils.{Await, CommonOptions, PathUtils}

import java.nio.file.Path
import org.scalatest.TestData
import scala.concurrent.duration.DurationInt

/** Unit Tests For ValidationPass */
class ValidationPassTest extends AbstractValidatingTest {

  // Turn off bombastic minor warnings
  "ValidationPass" should {
    "parse and validate rbbq.riddl" in { (td: TestData) =>
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/domains/rbbq.riddl"))
      val input = RiddlParserInput.fromURL(url, td).map { rpi =>
        pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
          parseAndValidateAggregate(rpi) { (vo: PassesResult) =>
            val errors = vo.messages.justErrors
            if errors.size != 0 then info(errors.format)
            errors.size mustBe 0
            if vo.refMap.size != 29 then
              // info(vo.refMap.toString)
              fail("refMap.size != 29")
            end if
            vo.usage.usesSize must be(47)
            vo.usage.usedBySize must be(23)
            vo.refMap.size must be(29)
          }
        }
      }
    }
  }

  "Validate All Things" must {
    var sharedRoot: Root = Root.empty

    "parse correctly" in { (td: TestData) =>
      val rootFile = "language/input/full/domain.riddl"
      val url = PathUtils.urlFromCwdPath(Path.of(rootFile))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        val parseResult = parseTopLevelDomains(rpi)
        parseResult match {
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            sharedRoot = root
            pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
              val result = Pass.runStandardPasses(root)
              if result.messages.hasErrors then fail(result.messages.format)
              else result.root.mustBe(sharedRoot)
            }
        }
      }
      Await.result(future, 10.seconds)
    }
    "handle includes" in { (td: TestData) =>
      val incls = sharedRoot.domains.head.includes
      incls mustNot be(empty)
      incls.head.contents.isEmpty mustNot be(true)
      incls.head.contents.head.getClass mustBe classOf[Context]
      incls(1).contents.head.getClass mustBe classOf[Context]
    }
    "have terms and author refs in contexts" in { (td: TestData) =>
      val includes = sharedRoot.domains.head.includes
      includes mustNot be(empty)
      val apps = includes.head.contents.filter[Context]
      apps mustNot be(empty)
      apps.head mustBe a[Context]
      val app = apps.head
      app.terms mustNot be(empty)
      app.hasAuthors mustBe false
      app.hasAuthorRefs mustBe true
      app.authorRefs mustNot be(empty)
    }
  }
}
