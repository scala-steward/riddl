/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Parsing rules for entity definitions */
private[parsing] trait EntityParser  {
  this: ProcessorParser & StreamingParser =>

  private def state[u: P]: P[State] = {
    P(
      location ~ Keywords.state ~ identifier ~/ (of | is) ~ typeRef ~/ briefly ~ description
    )./.map { case (loc, id, typRef, brief, description) =>
      State(loc, id, typRef, brief, description)
    }
  }

  private def entityInclude[u: P]: P[Include[EntityContents]] = {
    include[u, EntityContents](entityDefinitions(_))
  }

  private def entityDefinitions[u: P]: P[Seq[EntityContents]] = {
    P(
      handler(StatementsSet.EntityStatements) | function | invariant | state | entityInclude | inlet | outlet |
        typeDef | term | authorRef | comment | constant | option
    ).asInstanceOf[P[EntityContents]]./.rep(1)
  }

  private def entityBody[u: P]: P[Seq[EntityContents]] = {
    P(
      undefined(Seq.empty[EntityContents])./ | entityDefinitions./
    )
  }

  def entity[u: P]: P[Entity] = {
    P(
      location ~ Keywords.entity ~/ identifier ~ is ~ open ~/ entityBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      checkForDuplicateIncludes(contents)
      Entity(loc, id, contents, brief, description)
    }
  }
}