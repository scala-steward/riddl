package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.DomainDef
import com.yoppworks.ossum.riddl.language.AST.EntityDef
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage

/** Unit Tests For EntityValidatorTest */
class EntityValidatorTest extends ValidatingTest {

  "EntityValidator" should {
    "catch missing things" in {
      val input = "entity Hamburger is SomeType {}"
      parseAndValidate[EntityDef](input) {
        case (model: EntityDef, msgs: Seq[ValidationMessage]) =>
          val msgs = Validation.validate(model, Validation.defaultOptions)
          msgs.size mustEqual 3
          msgs.exists(_.message.contains("is not defined")) mustBe true
          msgs.exists(_.message.contains("entity must consume a channel")) mustBe true
          msgs.exists(_.message.contains("should have explanations")) mustBe true
      }
    }
    "error on persistent entity with no event producer" in {
      val input =
        """
          |domain foo {
          |channel EntityChannel {
          |  commands { Foo } events {} queries {} results {}
          |}
          |context bar {
          |entity Hamburger is SomeType {
          |  options {  aggregate persistent }
          |  consumes channel EntityChannel
          |  produces channel EntityChannel
          |}
          |}
          |}
          |""".stripMargin
      parseAndValidate[DomainDef](input) {
        case (model: DomainDef, msgs: Seq[ValidationMessage]) =>
          val errors = msgs.filter(_.kind.isError)
          errors mustNot be(empty)
          errors.exists(
            _.message.contains(
              "Persistent Entity 'Hamburger' requires a channel"
            )
          ) mustBe true
      }
    }
  }
}