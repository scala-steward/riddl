package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.{Adaptor, Domain}

/** Unit Tests For ConsumerTest */
class AdaptorTest extends ValidatingTest {

  "Adaptors" should {
    "handle undefined body" in {
      val input = """adaptor PaymentAdapter for context Foo is {
                    |  ???
                    |}
                    |""".stripMargin
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(_) => succeed
      }
    }

    "allow message actions" in {
      val input = """domain ignore is { context Foo is {
                    |type ItHappened = event { abc: String described as "abc" } described as "?"
                    |type LetsDoIt = command { bcd: String described as "abc" } described as "?"
                    |adaptor PaymentAdapter for context Foo is {
                    |  adapt sendAMessage is {
                    |    from event ItHappened to command LetsDoIt as {
                    |      ???
                    |    }
                    |  } explained as "?"
                    |} explained as "?"
                    |} explained as "?"
                    |} explained as "?"
                    |""".stripMargin
      parseAndValidate[Domain](input) { (_, messages) => messages mustBe empty }
    }
  }
}
