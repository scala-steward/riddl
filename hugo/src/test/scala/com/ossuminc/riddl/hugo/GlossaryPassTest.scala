package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.*

class GlossaryPassTest extends ValidatingTest {

  val dir = "hugo/src/test/input/"

  "GlossaryPass" must {
    "product glossary entries" in {
      parseAndValidateTestInput("glossary entries", "everything.riddl", dir) { case (root, pr: PassesResult) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new GlossaryPass(pr.input, pr.outputs, HugoPass.Options())
          val output: GlossaryOutput = Pass.runPass[GlossaryOutput](pr.input, pr.outputs, pass)
          output.entries.size must be(58) // TODO: Enhance this test to check entry contents
      }
    }
  }

  "GlossaryEntry" must {
    "construct without links" in {
      val ge = GlossaryEntry("foo", "Context", "Just a testing sample", Seq.empty)
      ge.link must be("")
      ge.sourceLink must be("")
    }
  }
}
