package com.ossuminc.riddl.hugo.mermaid

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.diagrams.mermaid.DataFlowDiagram
import java.nio.file.Path
import org.scalatest.TestData

class DataFlowDiagramTest extends ValidatingTest {
  "DataFlowDiagram" should {
    "generate a simple diagram correctly" in { (td: TestData) =>
      val path = Path.of("language/jvm/src/test/input/everything.riddl")
      val input = RiddlParserInput.fromCwdPath(path)
      simpleParseAndValidate(input) match {
        case Left(messages) => fail(messages.justErrors.format)
        case Right(passesResult: PassesResult) =>

          val dfd = DataFlowDiagram(passesResult)
          val domains = AST.getTopLevelDomains(passesResult.root)
          val contexts = AST.getContexts(domains.head)
          val actual = dfd.generate(contexts.head)
          val expected = """flowchart LR
                           |Commands[\"Outlet Source.Commands"\]
                           |Commands[/"Inlet Sink.Commands"/]
                           |APlant[{"Context Everything.APlant"}]
                           |command ACommand["OnMessageClause adaptCommands.command ACommand"]
                           |Commands -- Type 'DoAThing' --> Commands
                           |""".stripMargin
          actual must be(expected)
      }
    }
  }
}
