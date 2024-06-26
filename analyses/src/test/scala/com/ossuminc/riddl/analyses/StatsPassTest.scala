package com.ossuminc.riddl.analyses

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{AST, CommonOptions, Messages}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult}
import com.ossuminc.riddl.passes.validate.ValidatingTest
import org.scalatest.*
import java.nio.file.Path 

class StatsPassTest extends ValidatingTest {

  "DefinitionStats" must {
    "default correctly" in {
      val ds = DefinitionStats()
      ds.kind mustBe ""
      ds.isEmpty mustBe true
      ds.descriptionLines mustBe 0
      ds.numSpecifications mustBe 0
      ds.numCompleted mustBe 0L
      ds.numContained mustBe 0L
      ds.numAuthors mustBe 0L
      ds.numTerms mustBe 0L
      ds.numOptions mustBe 0L
      ds.numIncludes mustBe 0L
      ds.numStatements mustBe 0L
    }
  }

  "StatsOutput" must {
    "default correctly" in {
      val so = StatsOutput()
      so.messages mustBe empty
      so.maximum_depth mustBe 0
      so.categories mustBe Map.empty
    }
  }

  "StatsPass" must {
    "generate statistics" in {
      val rpi = RiddlParserInput(Path.of("language/src/test/input/everything.riddl"))
      parseValidateAndThen(rpi) { (pr: PassesResult, root: AST.Root, rpi: RiddlParserInput, messages: Messages.Messages) =>
        if messages.justErrors.nonEmpty then
          fail(messages.justErrors.format)
        else
          val input = PassInput(root, CommonOptions())
          val outputs = pr.outputs
          val pass = StatsPass(input, outputs)
          val statsOutput: StatsOutput = Pass.runPass[StatsOutput](input, outputs, pass)
          if statsOutput.messages.nonEmpty then fail(statsOutput.messages.format)
          statsOutput.maximum_depth > 0 mustBe true
          statsOutput.categories mustNot be(empty)
          statsOutput.categories.size mustBe(28)
          val ksAll: KindStats = statsOutput.categories("All") 
          ksAll.count mustBe 27
          ksAll.numEmpty mustBe 33
          ksAll.numStatements mustBe 4
          succeed
      }
    }
  }
}
