/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl


import com.ossuminc.riddl.commands.{Commands, RunCommandSpecBase}
import org.scalatest.Assertion

class RiddlcCommandsTest extends RunCommandSpecBase {

  val inputFile = "commands/src/test/input/rbbq.riddl"
  val hugoConfig = "commands/src/test/input/hugo.conf"
  val validateConfig = "commands/src/test/input/validate.conf"
  val outputDir: String => String =
    (name: String) => s"riddlc/target/test/$name"

  "Riddlc Commands" should {
    "tell about riddl" in {
      val args = Seq("about")
      runWith(args)
    }
    "provide help" in {
      val args = Seq("help")
      runWith(args)
    }
    "generate info" in {
      val args = Seq("info")
      runWith(args)
    }
    "print version" in {
      val args = Seq("version")
      runWith(args)
    }
  }

  override def runWith(args: Seq[String]): Assertion = {
    RIDDLC.main(args.toArray)
    succeed
  }
}
