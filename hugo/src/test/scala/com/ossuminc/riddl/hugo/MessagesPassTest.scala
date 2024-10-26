package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.Pass
import com.ossuminc.riddl.passes.validate.JVMAbstractValidatingTest
import com.ossuminc.riddl.utils.{pc,ec}

import org.scalatest.TestData

class MessagesPassTest extends JVMAbstractValidatingTest {

  val dir = "hugo/src/test/input/"

  "MessagesPass" must {
    "generate MessageInfo" in { (td: TestData) =>
      parseAndValidateTestInput("ToDoItem", "everything.riddl", dir) { case (root, pr) =>
        if pr.messages.hasErrors then
          val errors = pr.messages.justErrors.format
          fail(errors)
        else
          val pass = new MessagesPass(pr.input, pr.outputs, HugoPass.Options())
          val output: MessageOutput = Pass.runPass[MessageOutput](pr.input, pr.outputs, pass)
          output.collected.size must be(6)
      }
    }
  }
}
