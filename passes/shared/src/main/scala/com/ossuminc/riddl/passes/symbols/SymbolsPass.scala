/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.symbols.Symbols.*
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.symbols.Symbols.{Parentage, SymTab, SymTabItem}
import com.ossuminc.riddl.utils.PlatformContext

import scala.annotation.unused
import scala.collection.mutable

object SymbolsPass extends PassInfo[PassOptions] {
  val name: String = "Symbols"
  def creator(options: PassOptions = PassOptions.empty)(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => SymbolsPass(in, out)
  }
}

/** Symbol Table for Validation and other purposes. This symbol table is built from the AST model after syntactic
  * parsing is complete. It will also work for any sub-tree of the model that is rooted by a ParentDefOf[Definition]
  * node.
  *
  * The symbol tree contains a mapping from leaf name to the entire list of parent definitions (symbols) as well as a
  * mapping from definitions to their parents (parentage). Bot maps are built during a single pass of the AST.
  *
  * @param input
  *   The output of the parser pass is the input to SymbolPass
  */
case class SymbolsPass(input: PassInput, outputs: PassesOutput)(using pc: PlatformContext) extends Pass(input,
  outputs) {

  override def name: String = SymbolsPass.name

  private val symTab: SymTab = mutable.HashMap.empty[String, Seq[SymTabItem]]

  private val parentage: Parentage = mutable.HashMap.empty[Definition, Parents]

  override def postProcess(root: PassRoot @unused): Unit = ()

  private def rootLessParents(parents: Parents): Parents = {
    parents.filter {
      case _: Root                        => false // Roots don't have names and don't matter
      case x: Definition if x.isAnonymous => false // Parents with no names don't count
      case _                              => true // Everything else is fair game
    }
  }

  def process(definition: RiddlValue, parents: ParentStack): Unit = {
    definition match {
      case _: Root                          => // Root doesn't have a name
      case _: NonDefinitionValues           => // none of these can have names
      case nv: Definition if nv.isAnonymous => // Nameless things, like includes, aren't stored
      case nv: Definition if nv.id.isEmpty  => // Empty names are not stored
      case namedValue: Definition => // NOTE: Anything with a name goes in symbol table
        val name = namedValue.id.value
        if name.nonEmpty then {
          val parentsCopy: Parents = rootLessParents(parents.toParents)
          val existing = symTab.getOrElse(name, Seq.empty[SymTabItem])
          val pairToAdd = namedValue -> parentsCopy
          if existing.contains(pairToAdd) then
            // no need to put a duplicate
            ()
          else
            val included: Seq[SymTabItem] = existing :+ pairToAdd
            symTab.update(name, included)
            parentage.update(namedValue, parentsCopy)
          end if
        } else {
          messages.addError(namedValue.loc, "Non implicit value with empty name should not happen")
        }
      // case rv: RiddlValue => // everything should be handled above
      //    assert(false, s"SymTab didn't process: $rv") // NOTE: nothing else has a name
    }
  }

  override def result(root: PassRoot): SymbolsOutput = {
    if pc.options.debug then
      println(symTab.toPrettyString)
    end if
    SymbolsOutput(root, Messages.empty, symTab, parentage)
  }

  override def close(): Unit = ()

}
