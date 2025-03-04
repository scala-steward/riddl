/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{ec, pc, Await, CommonOptions, PathUtils, PlatformContext}

import java.nio.file.Path
import org.scalatest.{Assertion, TestData}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt

/** Unit Tests For the ResolutionPass */
class PathResolutionTest extends SharedResolvingTest {

  "PathResolution" must {
    "resolve language / rbbq.riddl" in { (td: TestData) =>
      val url = PathUtils.urlFromCwdPath(Path.of("language/input/domains/rbbq.riddl"))
      val future = RiddlParserInput.fromURL(url, td).map { input =>
        parseAndResolve(input) { (_, _) => succeed }
      }
      Await.result(future, 10.seconds)
    }
    "resolves everything in passes rbbq.riddl" in { (td: TestData) =>
      def onSuccess(in: PassInput, out: PassesOutput): Assertion =
        val refMap = out.resolution.refMap
        refMap.definitionOf[Entity]("ReactiveBBQ.Customers.Customer") must not be (empty)
        refMap.definitionOf[Type]("ReactiveBBQ.Empty") must not be (empty)
        refMap.definitionOf[Type]("IP4Address") must not be (empty)
        refMap.definitionOf[Type]("OrderViewType") must not be (empty)
        refMap.definitionOf[Type]("OrderViewer.AField") must not be (empty)
        refMap.definitionOf[Type]("CustomerId") must not be (empty)
        refMap.definitionOf[Type]("OrderId") must not be (empty)
        refMap.definitionOf[Type]("AccrualEvent") must not be (empty)
        refMap.definitionOf[Type]("AwardEvent") must not be (empty)
        refMap.definitionOf[Type]("RewardEvent") must not be (empty)
        refMap.definitionOf[Context]("ReactiveBBQ.Payments") must not be (empty)
        refMap.definitionOf[Type]("Order.Fields") must not be (empty)
        refMap.definitionOf[Type]("Payment.Fields") must not be (empty)
        refMap.definitionOf[Entity]("MenuItem") must not be (empty)
        refMap.definitionOf[Type]("MenuItem.Fields") must not be (empty)
        refMap.definitionOf[Type]("MenuItemRef") must not be (empty)
        refMap.definitionOf[Entity]("Location") must not be (empty)
        refMap.definitionOf[Type]("Reservation.Fields") must not be (empty)
        refMap.definitionOf[Type]("ReservationValue") must not be (empty)
      end onSuccess

      def onFailure(messages: Messages): Assertion = fail(messages.justErrors.format)

      val url = PathUtils.urlFromCwdPath(Path.of("passes/input/rbbq.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        parseAndResolve(rpi)(onSuccess)(onFailure)
      }
      Await.result(future, 10.seconds)
    }

    "resolves everything in dokn.riddl" in { (td: TestData) =>
      def onSuccess(in: PassInput, out: PassesOutput): Assertion =
        val refMap = out.resolution.refMap
        refMap.definitionOf[Entity]("dokn.Companies.Company") must not be (empty)
        refMap.definitionOf[Type]("MobileNumber") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Notes.Note") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Media.Medium") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Locations.Location") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Notes.Note") must not be (empty)
        refMap.definitionOf[Type]("dokn.Companies.Company.CompanyEvent") must not be (empty)
        refMap.definitionOf[Outlet]("CompanyEvents_out") must not be (empty)
        refMap.definitionOf[Inlet]("CompanyEvents_in") must not be (empty)
        refMap.definitionOf[Type]("Address") must not be (empty)
        refMap.definitionOf[Type]("EmailAddress") must not be (empty)
        refMap.definitionOf[Type]("CompanyAdded") must not be (empty)
        refMap.definitionOf[Type]("Company.fields") must not be (empty)
        refMap.definitionOf[Type]("AddCompany") must not be (empty)
        refMap.definitionOf[Type]("DriverCommands") must not be (empty)
        refMap.definitionOf[Type]("DriverEvents") must not be (empty)
        refMap.definitionOf[Type]("Driver.fields") must not be (empty)
        refMap.definitionOf[Type]("AddDriverToCompany") must not be (empty)
        refMap.definitionOf[Type]("RemoveDriverFromCompany") must not be (empty)
        refMap.definitionOf[Type]("DriverAddedToCompany") must not be (empty)
        refMap.definitionOf[Type]("DriverRemovedFromCompany") must not be (empty)
        refMap.definitionOf[Outlet]("Driver_out") must not be (empty)
        refMap.definitionOf[Type]("LocationId") must not be (empty)
        refMap.definitionOf[Type]("Note.fields") must not be (empty)
        refMap.definitionOf[Type]("Medium.fields") must not be (empty)
        refMap.definitionOf[Type]("dokn.Address") must not be (empty)
        refMap.definitionOf[Type]("NoteList") must not be (empty)
        refMap.definitionOf[Inlet]("CompanyEvents_in") must not be (empty)
        refMap.definitionOf[Field]("LocationBase.address") must not be (empty)
        refMap.definitionOf[Inlet]("Driver_in") must not be (empty)
        refMap.definitionOf[Type]("dokn.Address") must not be (empty)
        refMap.definitionOf[Type]("dokn.Address") must not be (empty)
      end onSuccess

      def onFailure(messages: Messages): Assertion = fail(messages.justErrors.format)

      val url = PathUtils.urlFromCwdPath(Path.of("language/input/dokn.riddl"))
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        parseAndResolve(rpi)(onSuccess)(onFailure)
      }
      Await.result(future, 10.seconds)
    }
  }
}
