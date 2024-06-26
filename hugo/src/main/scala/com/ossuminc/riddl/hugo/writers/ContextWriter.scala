package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.mermaid
import com.ossuminc.riddl.hugo.mermaid.ContextMapDiagram
import com.ossuminc.riddl.language.AST.{Context, Definition, OccursInContext}

trait ContextWriter { this: MarkdownWriter =>

  private def emitContextMap(context: Context, diagram: Option[ContextMapDiagram]): Unit = {
    if diagram.nonEmpty then
      h2("Context Map")
      val lines = diagram.get.generate
      emitMermaidDiagram(lines)
    end if
  }

  def emitContext(context: Context, parents: Seq[Definition]): Unit = {
    containerHead(context)
    val maybeDiagram = generator.diagrams.contextDiagrams.get(context).map(data => mermaid.ContextMapDiagram(context, data))
    emitVitalDefinitionDetails(context, parents)
    emitContextMap(context, maybeDiagram)
    emitOptions(context.options)
    definitionToc("Entities", context.entities)
    definitionToc("Adaptors", context.adaptors)
    definitionToc("Sagas", context.sagas)
    definitionToc("Streamlets", context.streamlets)
    list("Connectors", context.connectors)
    emitProcessorDetails[OccursInContext](context, parents)
    // TODO: generate a diagram for the processors and pipes
  }

}
