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

private[parsing] trait HandlerParser {
  this: ReferenceParser & StatementParser & CommonParser =>

  private def onOtherClause[u: P](set: StatementsSet): P[OnOtherClause] = {
    P(
      location ~ Keywords.onOther ~ is ~/ pseudoCodeBlock(set) ~ briefly ~ description
    ).map(t => OnOtherClause.apply.tupled(t))
  }

  private def onInitClause[u: P](set: StatementsSet): P[OnInitializationClause] = {
    P(
      location ~ Keywords.onInit ~ is ~/ pseudoCodeBlock(set) ~ briefly ~ description
    ).map(t => OnInitializationClause.apply.tupled(t))
  }

  private def onTermClause[u: P](set: StatementsSet): P[OnTerminationClause] = {
    P(
      location ~ Keywords.onTerm ~ is ~/ pseudoCodeBlock(set) ~ briefly ~ description
    ).map(t => OnTerminationClause.apply.tupled(t))
  }

  private def maybeName[u: P]: P[Option[Identifier]] = {
    P((identifier ~ Punctuation.colon).?)
  }

  private def messageOrigins[u: P]: P[Reference[Definition]] = {
    P(inletRef | processorRef | userRef | epicRef)
  }

  private def onMessageClause[u: P](set: StatementsSet): P[OnMessageClause] = {
    location ~ Keywords.on ~ messageRef ~
      (Readability.from ~ maybeName ~~ messageOrigins).? ~ is ~/ pseudoCodeBlock(set) ~
      briefly ~ description
  }.map(tpl => OnMessageClause.apply.tupled(tpl))

  private def onClauses[u: P](set: StatementsSet): P[Seq[OccursInHandler]] = {
    P(onInitClause(set) | onOtherClause(set) | onTermClause(set) | onMessageClause(set) | comment).rep(0)
  }

  private def handlerBody[u: P](set: StatementsSet): P[Seq[OccursInHandler]] = {
    undefined(Seq.empty[OccursInHandler]) | onClauses(set)
  }

  def handler[u: P](set: StatementsSet): P[Handler] = {
    P(
      Keywords.handler ~/ location ~ identifier ~ is ~ open ~
        handlerBody(set) ~ close ~ briefly ~ description
    )./.map { case (loc, id, clauses, brief, description) =>
      Handler(loc, id, clauses, brief, description)
    }
  }

  def handlers[u: P](set: StatementsSet): P[Seq[Handler]] = handler(set).rep(0)

}
