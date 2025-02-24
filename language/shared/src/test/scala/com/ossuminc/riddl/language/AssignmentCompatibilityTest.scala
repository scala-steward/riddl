/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.AbstractTestingBasis
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Unit Tests For AssignmentCompatibility */
class AssignmentCompatibilityTest extends AbstractTestingBasis {

  val abstrct = Abstract(At.empty)
  val datetime = DateTime(At.empty)
  val timestamp = TimeStamp(At.empty)
  val date = Date(At.empty)
  val time = Time(At.empty)
  val number = Number(At.empty)
  val integer = Integer(At.empty)
  val real = Real(At.empty)
  val decimal = Decimal(At.empty, 8, 3)
  val range = AST.RangeType(At.empty, 0, 100)
  val nothing = AST.Nothing(At.empty)
  val string = AST.String_(At.empty)

  "AssignmentCompatibility" should {
    "check compatibility of Date" in {
      date.isAssignmentCompatible(timestamp) must be(true)
      date.isAssignmentCompatible(datetime) must be(true)
      date.isAssignmentCompatible(date) must be(true)
      date.isAssignmentCompatible(abstrct) must be(true)
      date.isAssignmentCompatible(string) must be(true)
      date.isAssignmentCompatible(time) must be(false)
      date.isAssignmentCompatible(nothing) must be(false)
    }
    "check compatibility of DateTime " in {
      datetime.isAssignmentCompatible(datetime) must be(true)
      datetime.isAssignmentCompatible(timestamp) must be(true)
      datetime.isAssignmentCompatible(abstrct) must be(true)
      datetime.isAssignmentCompatible(date) must be(true)
      datetime.isAssignmentCompatible(string) must be(true)
      datetime.isAssignmentCompatible(number) must be(false)
    }

    "check compatibility of Nothing " in {
      nothing.isAssignmentCompatible(datetime) must be(false)
      nothing.isAssignmentCompatible(timestamp) must be(false)
      nothing.isAssignmentCompatible(abstrct) must be(false)
      nothing.isAssignmentCompatible(date) must be(false)
      nothing.isAssignmentCompatible(number) must be(false)
      nothing.isAssignmentCompatible(string) must be(false)
    }

    "check compatibility of Time" in {
      time.isAssignmentCompatible(datetime) must be(true)
      time.isAssignmentCompatible(timestamp) must be(true)
      time.isAssignmentCompatible(abstrct) must be(true)
      time.isAssignmentCompatible(string) must be(true)
      time.isAssignmentCompatible(date) must be(false)
      time.isAssignmentCompatible(integer) must be(false)
      time.isAssignmentCompatible(number) must be(false)
    }

    "check compatibility of TimeStamp" in {
      timestamp.isAssignmentCompatible(string) must be(true)
      timestamp.isAssignmentCompatible(timestamp) must be(true)
      timestamp.isAssignmentCompatible(datetime) must be(true)
      timestamp.isAssignmentCompatible(date) must be(true)
      timestamp.isAssignmentCompatible(abstrct) must be(true)
    }
  }
}
