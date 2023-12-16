/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*
import Readability.*
import java.net.URL

private[parsing] trait EpicParser {
  this: CommonParser with ReferenceParser =>

  private def optionalIdentifier[u: P](keyword: String): P[Identifier] = {
    P(
      keyword.map(_ => Identifier.empty) | (identifier ~ keyword)
    )
  }

  private def vagueStep[u: P]: P[VagueInteraction] = {
    P(
      location ~ optionalIdentifier("") ~ is ~ literalString ~ literalString ~ literalString ~/
        briefly ~ description ~ comments
    )./.map { case (loc, id, from, relationship, to, brief, description, comments) =>
      VagueInteraction(loc, id, from, relationship, to, brief, description, comments)
    }
  }

  private def arbitraryStep[u: P]: P[ArbitraryInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.from) ~/ anyInteractionRef ~
        literalString ~ Readability.to.? ~ anyInteractionRef ~/ briefly ~ description ~ comments
    )./.map { case (loc, id, from, ls, to, brief, desc, comments) =>
      ArbitraryInteraction(loc, id, from, ls, to, brief, desc, comments)
    }
  }

  private def sendMessageStep[u: P]: P[SendMessageInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.send) ~/ messageRef ~
        Readability.from ~ anyInteractionRef ~ Readability.to ~ anyInteractionRef ~/
        briefly ~ description ~ comments
    )./.map { case (loc, id, message, from, to, brief, description, comments) =>
      SendMessageInteraction(loc, id, from, message, to, brief, description, comments)
    }
  }

  private def selfProcessingStep[u: P]: P[SelfInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.for_) ~/
        anyInteractionRef ~ is ~ literalString ~/ briefly ~ description ~ comments
    )./.map { case (loc, id, fromTo, proc, brief, description, comments) =>
      SelfInteraction(loc, id, fromTo, proc, brief, description, comments)
    }
  }

  private def focusOnGroupStep[u: P]: P[FocusOnGroupInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.focus) ~/ userRef ~ Readability.on ~
        groupRef ~/ briefly ~ description ~ comments
    )./.map { case (loc, id, userRef, groupRef, brief, description, comments) =>
      FocusOnGroupInteraction(loc, id, userRef, groupRef, brief, description, comments)
    }
  }

  private def directUserToURL[u: P]: P[DirectUserToURLInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.direct) ~/ userRef ~/ Readability.to ~ httpUrl ~/
        briefly ~ description ~ comments
    )./.map { case (loc, id, user, url, brief, description, comments) =>
      DirectUserToURLInteraction(loc, id, user, url, brief, description, comments)
    }
  }

  private def showOutputStep[u: P]: P[ShowOutputInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.show) ~/
        outputRef ~ Readability.to ~ userRef ~/ briefly ~ description ~ comments
    )./.map { case (loc, id, from, to, brief, description, comments) =>
      ShowOutputInteraction(loc, id, from, LiteralString.empty, to, brief, description, comments)
    }
  }

  private def takeInputStep[u: P]: P[TakeInputInteraction] = {
    P(
      location ~ optionalIdentifier(Keyword.take) ~/
        inputRef ~ Readability.from ~ userRef ~/ briefly ~ description ~ comments
    )./.map { case (loc, id, input, user, brief, description, comments) =>
      TakeInputInteraction(
        loc,
        id,
        from = user,
        relationship = LiteralString.empty,
        to = input,
        brief,
        description,
        comments
      )
    }
  }

  private def stepInteractions[u: P]: P[Interaction] = {
    P(
      Keywords.step ~ (focusOnGroupStep | directUserToURL | takeInputStep | showOutputStep | selfProcessingStep |
        sendMessageStep | arbitraryStep | vagueStep)
    )
  }

  private def sequentialInteractions[u: P]: P[SequentialInteractions] = {
    P(
      location ~ Keywords.sequence ~ identifier.? ~ open ~ interactions ~ close ~
        briefly ~ description ~ comments
    )./.map { case (loc, id, steps, brief, description, comments) =>
      SequentialInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, description, comments)
    }
  }

  private def optionalInteractions[u: P]: P[OptionalInteractions] = {
    P(
      location ~ Keywords.optional ~ identifier.? ~/ open ~ interactions ~ close ~
        briefly ~ description ~ comments
    )./.map { case (loc, id, steps, brief, description, comments) =>
      OptionalInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, description, comments)
    }
  }

  private def parallelInteractions[u: P]: P[ParallelInteractions] = {
    P(
      location ~ Keywords.parallel ~ identifier.? ~/ open ~
        interactions ~ close ~ briefly ~ description ~ comments
    )./.map { case (loc, id, steps, brief, description, comments) =>
      ParallelInteractions(loc, id.getOrElse(Identifier.empty), steps, brief, description, comments)
    }
  }

  private def interactions[u: P]: P[Seq[Interaction]] = {
    P(
      parallelInteractions | optionalInteractions | sequentialInteractions | stepInteractions
    ).rep(1, Punctuation.comma./)
  }

  private def useCase[u: P]: P[UseCase] = {
    P(
      location ~ Keywords.case_ ~/ identifier ~ is ~ open ~
        (undefined(
          (Option.empty[UserStory], Seq.empty[TwoReferenceInteraction])
        )./ | (userStory.? ~ interactions)) ~
        close ~ briefly ~ description ~ comments
    ).map { case (loc, id, (userStory, contents), brief, description, comments) =>
      UseCase(loc, id, userStory.getOrElse(UserStory()), contents, brief, description, comments)
    }
  }

  def userStory[u: P]: P[UserStory] = {
    P(
      location ~ userRef ~ Readability.wants ~ Readability.to.? ~
        literalString ~ Readability.so ~ Readability.that.? ~ literalString
    ).map { case (loc, user, capability, benefit) =>
      UserStory(loc, user, capability, benefit)
    }
  }

  def shownBy[u: P]: P[Seq[java.net.URL]] = {
    P(
      Keywords.shown ~ Readability.by ~ open ~
        httpUrl.rep(0, Punctuation.comma) ~ close
    ).?.map { (x: Option[Seq[java.net.URL]]) => x.getOrElse(Seq.empty[java.net.URL]) }
  }

  private def epicOptions[u: P]: P[Seq[EpicOption]] = {
    options[u, EpicOption](StringIn(RiddlOption.technology, RiddlOption.sync).!) {
      case (loc, RiddlOption.sync, _)          => EpicSynchronousOption(loc)
      case (loc, RiddlOption.technology, args) => EpicTechnologyOption(loc, args)
    }
  }

  private def epicInclude[u: P]: P[Include[EpicDefinition]] = {
    include[EpicDefinition, u](epicDefinitions(_))
  }

  private def epicDefinitions[u: P]: P[Seq[EpicDefinition]] = {
    P(useCase | term | epicInclude).rep(1)
  }

  type EpicBody = (
    Option[UserStory],
    Seq[java.net.URL],
    Seq[EpicDefinition]
  )

  private def epicBody[u: P]: P[EpicBody] = {
    P(
      undefined(
        (
          Option.empty[UserStory],
          Seq.empty[java.net.URL],
          Seq.empty[EpicDefinition]
        )
      )./ |
        (userStory.? ~ shownBy ~ epicDefinitions)./
    )
  }

  def epic[u: P]: P[Epic] = {
    P(
      location ~ Keywords.epic ~/ identifier ~ authorRefs ~ is ~ open ~
        epicOptions ~ epicBody ~ close ~
        briefly ~ description ~ comments
    ).map {
      case (
            loc,
            id,
            authors,
            options,
            (userStory, shownBy, definitions),
            briefly,
            description,
            comments
          ) =>
        val groups = definitions.groupBy(_.getClass)
        val terms = mapTo[Term](groups.get(classOf[Term]))
        val includes = mapTo[Include[EpicDefinition]](
          groups.get(
            classOf[Include[EpicDefinition]]
          )
        )
        val cases = mapTo[UseCase](groups.get(classOf[UseCase]))

        Epic(
          loc,
          id,
          userStory,
          shownBy,
          cases,
          authors,
          includes,
          options,
          terms,
          briefly,
          description,
          comments
        )
    }
  }

}