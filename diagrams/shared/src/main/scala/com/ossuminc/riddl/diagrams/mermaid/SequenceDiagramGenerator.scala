/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams.mermaid

/** A mermaid diagram generator for making sequence diagrams
  */
trait SequenceDiagramGenerator extends MermaidDiagramGenerator {
  def kind: String = "sequenceDiagram"
  frontMatter()
  addLine("sequenceDiagram").incr.addIndent("autonumber")
}
