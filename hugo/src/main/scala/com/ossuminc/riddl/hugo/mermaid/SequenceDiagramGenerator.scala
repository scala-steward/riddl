package com.ossuminc.riddl.hugo.mermaid

/** A mermaid diagram generator for making sequence diagrams
  */
trait SequenceDiagramGenerator extends MermaidDiagramGenerator {
  def kind: String = "sequenceDiagram"
  frontMatter()
  addLine("sequenceDiagram").incr.addIndent("autonumber")
}
