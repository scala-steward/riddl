package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{URL,PlatformContext,DOMPlatformContext}
import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.must.Matchers

import scala.concurrent.{ExecutionContext, Future}

class TopLevelParserTest extends AsyncFunSpec with Matchers:
  implicit override def executionContext: ExecutionContext =
    scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  describe("TopLevelParser") {
    it("do some parsing") {
      given io: PlatformContext = DOMPlatformContext()
      val url = URL("https://raw.githubusercontent.com/ossuminc/riddl-examples/main/src/riddl/dokn/dokn.riddl")
      val future = io.load(url)
      future.map { data =>
        val input = RiddlParserInput(data, "parsing")
        TopLevelParser.parseInput(input) match {
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            root.domains.head.id.value must be("dokn")
        }
      }
    }
  }
end TopLevelParserTest
