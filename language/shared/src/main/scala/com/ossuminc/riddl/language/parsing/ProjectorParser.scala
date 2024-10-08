/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

/** Unit Tests For FunctionParser */
private[parsing] trait ProjectorParser {
  this: ProcessorParser & StreamingParser =>

  private def projectorInclude[u: P]: P[Include[ProjectorContents]] = {
    include[u, ProjectorContents](projectorDefinitions(_))
  }

  private def updates[u: P]: P[RepositoryRef] = {
    P(
      location ~ Keywords.updates ~ repositoryRef
    ).map { case (_, ref) =>
      ref
    }
  }

  private def projectorDefinitions[u: P]: P[Seq[ProjectorContents]] = {
    P(
      processorDefinitionContents(StatementsSet.ProjectorStatements) | updates | projectorInclude
    ).asInstanceOf[P[ProjectorContents]]./.rep(1)
  }

  private def projectorBody[u: P]: P[Seq[ProjectorContents]] = {
    P(
      undefined(Seq.empty[ProjectorContents]) | projectorDefinitions
    )
  }

  /** Parses projector definitions, e.g.
    *
    * {{{
    *   projector myView is {
    *     foo: Boolean
    *     bar: Integer
    *   }
    * }}}
    */
  def projector[u: P]: P[Projector] = {
    P(
      location ~ Keywords.projector ~/ identifier ~ is ~ open ~ projectorBody ~ close ~ withMetaData
    )./.map { case (loc, id, contents, descriptives) =>
      checkForDuplicateIncludes(contents)
      Projector(loc, id, contents.toContents, descriptives.toContents)
    }
  }
}
