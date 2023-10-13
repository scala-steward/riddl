/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.passes.symbols

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.passes.PassOutput
import com.reactific.riddl.passes.symbols.Symbols.{Parentage, SymTab}

import scala.collection.mutable
import scala.reflect.{ClassTag, classTag}

object Symbols {
  type Parents = Seq[Definition]
  type Parentage = mutable.HashMap[Definition, Parents]
  type SymTabItem = (Definition, Parents)
  type SymTab = mutable.HashMap[String, Seq[SymTabItem]]

  val emptySymTab = mutable.HashMap.empty[String, Seq[SymTabItem]]
  val emptyParentage = mutable.HashMap.empty[Definition, Parents]
}

/** Output from the Symbols Pass
  * @param messages
  *   The error messages generated by the pass
  * @param symTab
  *   The SymbolTable that handles identifier translation to definitions
  */
case class SymbolsOutput(
  messages: Messages.Messages = Messages.empty,
  symTab: SymTab = Symbols.emptySymTab,
  parentage: Parentage = Symbols.emptyParentage
) extends PassOutput {

  /** Get the parent of a definition
    *
    * @param definition
    *   The definition whose parent is to be sought.
    * @return
    *   optionally, the parent definition of the given definition
    */
  def parentOf(definition: Definition): Option[Definition] =
    parentage.get(definition) match {
      case Some(container) => container.headOption
      case None            => None
    }

  /** Get all parents of a definition
    *
    * @param definition
    *   The defintiion whose parents are to be sought.
    * @return
    *   the sequence of ParentDefOf parents or empty if none.
    */
  def parentsOf(definition: Definition): Seq[Definition] = {
    parentage.get(definition) match {
      case Some(list) => list
      case None       => Seq.empty[Definition]
    }
  }

  def contextOf(definition: Definition): Option[Context] = {
    definition match {
      case c: Context =>
        Some(c)
      case _ =>
        val parents = parentsOf(definition)
        val tail = parents.dropWhile(_.getClass != classOf[Context])
        val result = tail.headOption.asInstanceOf[Option[Context]]
        result
    }
  }

  /** Get the full path of a definition
    *
    * @param definition
    *   The definition for which the path name is sought.
    * @return
    *   A list of strings from leaf to root giving the names of the definition and its parents.
    */
  def pathOf(definition: Definition): Seq[String] = {
    definition.id.value +: parentsOf(definition).map(_.id.value)
  }

  private def hasSameParentNames(id: Seq[String], parents: Symbols.Parents): Boolean = {
    val containerNames = id.drop(1)
    val parentNames = parents.map(_.id.value)
    containerNames.zip(parentNames).forall { case (containerName, parentName) =>
      containerName == parentName
    }
  }

  /** The result of a lookupSymbol request A lookupSymbol request returns a list of tuples that contain the generic
    * definition, as a Definition, and, if the definition matches the type of interest, D, then an Option[D] for
    * convenience.
    */
  type LookupResult[D <: Definition] = List[(Definition, Option[D])]

  /** Look up a symbol in the table
    *
    * @param id
    *   The multi-part identifier of the symbol, from leaf to root, that is from most nested to least nested.
    * @tparam D
    *   The expected type of definition
    * @return
    *   A list of matching definitions of 2-tuples giving the definition as a Definition type and optionally as the
    *   requested type
    */
  def lookupSymbol[D <: Definition: ClassTag](
    id: Seq[String]
  ): LookupResult[D] = {
    require(id.nonEmpty, "No name elements provided to lookupSymbol")
    val clazz = classTag[D].runtimeClass
    val nameList = id.reverse
    nameList.headOption match
      case None =>
        List.empty
      case Some(leafName) =>
        symTab.get(leafName) match {
          case Some(set) =>
            set
              .filter { case (_: Definition, parents: Seq[Definition]) =>
                // whittle down the list of matches to the ones whose parents names
                // have the same as the nameList provided
                hasSameParentNames(nameList, parents)
              }
              .map { case (d: Definition, _: Seq[Definition]) =>
                // If a name match is also the same type as desired by the caller
                // then give them the definition in the requested type, optionally
                if clazz.isInstance(d) then { (d, Option(d.asInstanceOf[D])) }
                else { (d, None) }
              }
              .toList
          case None =>
            // Symbol wasn't found
            List.empty
        }
  }

  /** Look up a symbol in the table
    *
    * @param names
    *   The multi-part identifier of the symbol, from leaf to root, that is from most nested to least nested.
    * @return
    *   A list of matching definitions of 2-tuples giving the definition as a Definition type and optionally as the
    *   requested type
    */
  def lookupParentage(
    names: Seq[String]
  ): List[Symbols.SymTabItem] = {
    names.headOption match
      case None =>
        require(names.nonEmpty, "No name elements provided to lookupSymbol")
        List.empty[Symbols.SymTabItem]
      case Some(leafName) =>
        symTab.get(leafName) match {
          case Some(set) =>
            set.filter { case (_: Definition, parents: Seq[Definition]) =>
              // whittle down the list of matches to the ones whose parents names
              // have the same as the nameList provided
              hasSameParentNames(names, parents)
            }.toList
          case None =>
            // Symbol wasn't found
            List.empty[Symbols.SymTabItem]
        }
  }

  def lookup[D <: Definition: ClassTag](
    ref: Reference[D]
  ): List[D] = { lookup[D](ref.pathId.value) }

  def lookup[D <: Definition: ClassTag](
    id: Seq[String]
  ): List[D] = {
    val clazz = classTag[D].runtimeClass
    id.headOption match
      case None =>
        require(id.nonEmpty, "Provided id is empty")
        List.empty[D]
      case Some(leafName) =>
        symTab.get(leafName) match {
          case Some(set) =>
            val result = set
              .filter { case (d: Definition, parents: Symbols.Parents) =>
                if clazz.isInstance(d) then {
                  // It is in the result set as long as the container names
                  // given in the provided id are the same as the container
                  // names in the symbol table.
                  hasSameParentNames(id, parents)
                } else { false }
              }
              .map(_._1.asInstanceOf[D])
            result.toList
          case None => List.empty[D]
        }
  }

  def foreachOverloadedSymbol(process: Seq[Seq[Definition]] => Unit): Unit = {
    val overloads = symTab.filterNot(_._1.isEmpty).filter(_._2.size > 1)
    val defs = overloads.toSeq.map(_._2).map(_.map(_._1).toSeq)
    process(defs)
  }
}
