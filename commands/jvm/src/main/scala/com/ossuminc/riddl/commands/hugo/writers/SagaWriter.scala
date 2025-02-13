/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo.writers
import com.ossuminc.riddl.language.AST.*

trait SagaWriter { this: MarkdownWriter =>

  private def emitSagaSteps(actions: Seq[SagaStep]): this.type = {
    h2("Saga Actions")
    actions.foreach { step =>
      h3(step.identify)
      emitShortDefDoc(step)
      list(typeOfThing = "Do Statements", step.doStatements.toSeq.map(_.format), 4)
      list(typeOfThing = "Undo Statements", step.doStatements.toSeq.map(_.format), 4)
    }
    this
  }

  def emitSaga(saga: Saga, parents: Parents): Unit = {
    containerHead(saga)
    emitDefDoc(saga, parents)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaSteps(saga.sagaSteps)
    // emitProcessorDetails(saga, parents)
    emitTerms(saga.terms)
  }
}
