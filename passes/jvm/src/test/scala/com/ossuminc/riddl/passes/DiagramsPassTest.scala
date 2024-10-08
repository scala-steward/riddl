package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Domain, Identifier, Root}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.diagrams.{ContextDiagramData, DiagramsPass, DiagramsPassOutput}
import com.ossuminc.riddl.passes.validate.ValidatingTest
import org.scalatest.TestData
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

class DiagramsPassTest extends ValidatingTest {

  "Diagrams Data" must {
    "construct ContextDiagramData" in { (td: TestData) =>
      val d = Domain(At(), Identifier(At(), "domain"))
      val contextDiagramData = ContextDiagramData(d)
      contextDiagramData.aggregates mustBe empty
      contextDiagramData.relationships mustBe empty
      contextDiagramData.domain mustBe d
    }
    "construct DiagramsPassOutput" in {(td: TestData) =>
      val diagramsPassOutput = DiagramsPassOutput()
      diagramsPassOutput.messages mustBe empty
      diagramsPassOutput.contextDiagrams mustBe empty
      diagramsPassOutput.dataFlowDiagrams mustBe empty
    }
  }
  "DiagramsPass" must {
    "be named correctly" in { (td: TestData) =>
      DiagramsPass.name mustBe "Diagrams"
    }
    "creator with empty PassesOutput yields IllegalArgumentException" in { (td: TestData) =>
      val creator = DiagramsPass.creator()
      val input = PassInput(Root())
      val outputs = PassesOutput()
      val pass = intercept[IllegalArgumentException] { creator(input, outputs) }
      pass.isInstanceOf[IllegalArgumentException] mustBe true
    }
    "generate diagrams output" in { (td: TestData) =>
      val input = RiddlParserInput.fromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"), td)
      parseValidateAndThen(input) {
        (passesResult: PassesResult, root: Root, rpi: RiddlParserInput, messages: Messages.Messages) =>
          val pass = new DiagramsPass(passesResult.input, passesResult.outputs)
          val output = Pass.runPass[DiagramsPassOutput](passesResult.input, passesResult.outputs, pass)
          output.messages.justErrors must be(empty)
          output.contextDiagrams must not be (empty)
          output.useCaseDiagrams must not be (empty)
          output.dataFlowDiagrams must be (empty) // FIXME: change to 'not be(empty)' when implemented
      }
    }
  }
}
