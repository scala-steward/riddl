package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.EntityAggregate
import com.yoppworks.ossum.riddl.language.AST.EntityPersistent
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.Location
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.AST.Topic
import com.yoppworks.ossum.riddl.language.Validation.SevereError
import com.yoppworks.ossum.riddl.language.Validation.ValidationMessage
import com.yoppworks.ossum.riddl.language.Validation.ValidationState
import com.yoppworks.ossum.riddl.language.Validation.Warning
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must

class ValidationTest extends AnyWordSpec with must.Matchers {
  "ValidationMessage#format" should {
    "produce a correct string" in {
      ValidationMessage(Location(1, 2), "the_message", Warning)
        .format("the_source") mustBe
        s"Warning: the_source(1:2): the_message"
    }

    "ValidationState" should {
      "parentOf" should {
        "find the parent of an existant child" in {
          val topic = Topic(Location(), Identifier(Location(), "bar"))
          val domain = Domain(
            Location(),
            Identifier(Location(), "foo"),
            topics = topic :: Nil
          )

          ValidationState(SymbolTable(domain)).parentOf(topic) mustBe domain
        }
        "not find the parent of a non-existant child" in {
          val topic = Topic(Location(), Identifier(Location(), "bar"))
          val domain =
            Domain(Location(), Identifier(Location(), "foo"), topics = Nil)

          ValidationState(SymbolTable(domain))
            .parentOf(topic) mustBe RootContainer.empty
        }
        "checkNonEmpty" in {
          ValidationState(SymbolTable(RootContainer.empty))
            .checkNonEmpty(Nil, "foo", RootContainer.empty)
            .msgs mustBe List(
            ValidationMessage(
              Location(),
              "foo in RootContainer 'root' should not be empty",
              Validation.Error
            )
          )
          ValidationState(SymbolTable(RootContainer.empty))
            .checkNonEmpty(List(1, 2, 3), "foo", RootContainer.empty)
            .msgs mustBe Nil
        }
        "checkOptions" in {
          ValidationState(SymbolTable(RootContainer.empty))
            .checkOptions(
              List(
                EntityAggregate(Location()),
                EntityPersistent(Location()),
                EntityAggregate(Location())
              ),
              Location()
            )
            .msgs mustBe List(
            ValidationMessage(
              Location(),
              "Options should not be repeated",
              Validation.Error
            )
          )
          ValidationState(SymbolTable(RootContainer.empty))
            .checkOptions(List(1, 2, 3), Location())
            .msgs mustBe Nil
        }
      }
    }
  }
  "ValidationMessageKind" should {
    "have correct field values" in {
      // TODO: probably most things should not be severe errors
      Validation.MissingWarning.isWarning mustBe true
      Validation.MissingWarning.isError mustBe false
      Validation.MissingWarning.isSevereError mustBe true
      Validation.MissingWarning.toString mustBe "Missing"

      Validation.StyleWarning.isWarning mustBe true
      Validation.StyleWarning.isError mustBe false
      Validation.StyleWarning.isSevereError mustBe true
      Validation.StyleWarning.toString mustBe "Style"

      Validation.Warning.isWarning mustBe true
      Validation.Warning.isError mustBe false
      Validation.Warning.isSevereError mustBe true
      Validation.Warning.toString mustBe "Warning"

      Validation.Error.isWarning mustBe false
      Validation.Error.isError mustBe true
      Validation.Error.isSevereError mustBe true
      Validation.Error.toString mustBe "Error"

      SevereError.isWarning mustBe false
      SevereError.isError mustBe true
      SevereError.isSevereError mustBe true
      SevereError.toString mustBe "Severe"
    }
  }

}