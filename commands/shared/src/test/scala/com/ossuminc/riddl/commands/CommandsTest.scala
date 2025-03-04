/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

class CommandsTest extends CommandTestBase("commands/input/") {

  val inputFile = "language/input/rbbq.riddl"
  val hugoConfig = "commands/input/hugo.conf"
  val validateConfig = "commands/input/validate.conf"

  "Commands" should {
    "handle dump" in {
      val args = common ++ Seq("dump", inputFile)
      runCommand(args)
    }
    "handle from" in {
      FromCommand.Options().command mustBe "from"
      val args = common ++ Seq("from", validateConfig, "validate")
      runCommand(args)
    }
    
    "handle from with wrong file as input" in {
      val args = Array(
        "--verbose",
        "--show-style-warnings=false",
        "--show-missing-warnings=false",
        "from",
        "not-an-existing-file", // wrong file!
        "validate"
      )
      val rc = Commands.runMain(args)
      rc must not(be(0))
    }

    "handle from with wrong command as target" in {
      val args = Array(
        "--verbose",
        "--show-style-warnings=false",
        "--show-missing-warnings=false",
        "from",
        "commands/input/repeat-options.conf",
        "flumox" // unknown command
      )
      val rc = Commands.runMain(args)
      rc must not(be(0))
    }

    "handle parse" in {
      val args = common ++ Seq("parse", inputFile)
      runCommand(args)
    }
    "handle repeat" in {
      RepeatCommand.Options().command mustBe "repeat"
      val args = common ++ Seq("repeat", validateConfig, "validate", "1s", "2")
      runCommand(args)
    }
    "handle validate" in {
      val args = common ++ Seq("validate", inputFile)
      runCommand(args)
    }
  }
}
