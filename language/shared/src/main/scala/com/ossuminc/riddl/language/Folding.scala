/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*

import scala.collection.mutable
import scala.scalajs.js.annotation._

/** An object for distinguishing several functions as ways to fold the model */
@JSExportTopLevel("Folding")
object Folding {

  /** Folding with state from an element of type V
    *
    * @param container
    *   The container node of CV which must be its direct container
    * @param empty
    *   The initial empty state of the result. this is a "fold" after all :)
   * @foldIt
   *    The function that folds eacy entry in `container`
    * @param f
    *   The folding function which takes 3 arguments and returns an `S` (list the initial `state`)
    * @tparam S
    *   The type of the state for folding
    * @tparam CV
    *   The type of the element being folded
    * @return
    *   The resulting state of type `S`
    */
  @JSExport
  def foldEachDefinition[S, CV <: RiddlValue](
    container: Container[CV],
    empty: S
  )(foldIt: (S, CV) => S): S =
    container.contents.foldLeft(empty) { case (next, child) => foldIt(next, child) }

  /** A Typical foldLeft as with [[scala.collection.Seq]] but utilizing a stack of parents as well.
    * @param zeroValue
    *   The "zero" value for the fold, the value at which folding starts
    * @param parents
    *   The parents of the `value` node
    * @param top
    *   The containing top node of `value`
    * @param f
    *   The folder function which is passed the state [S], the node or its container, and the list of parents
    * @tparam S
    *   The type of the state
    * @tparam CT
    *   The type of nodes to fold over
    * @return
    *   The folded state
    * @see
    *   [[scala.collection.Seq.foldLeft()]]
    */
  @JSExport final def foldLeftWithStack[S, CT <: RiddlValue](
    zeroValue: S,
    top: Container[CT],
    parents: ParentStack
  )(f: (S, CT | Container[CT], Parents) => S): S = {
    val initial = f(zeroValue, top, parents.toParents)
    top match
      case p: Branch[?] => parents.push(p)
      case _         => ()
    end match
    val value =
      try {
        top.contents.foldLeft(initial) { (next, value) =>
          value match {
            case c: Container[CT] if c.nonEmpty => foldLeftWithStack(next, c, parents)(f)
            case v: CT                          => f(next, v, parents.toParents)
          }
        }
      } finally {
        top match
          case p: Branch[?] => parents.pop()
          case _         => ()
        end match
      }
    value
  }
}
