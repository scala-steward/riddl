/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.translate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassCreator, PassInput, PassCreators, PassesOutput, PassesResult, Riddl}
import com.ossuminc.riddl.utils.{PlatformContext, CommonOptions, Logger}

import java.nio.file.Path

/** Base class of all Passes that translate the AST to some other form.
  *
  * @param input
  *   The input to be translated
  * @param outputs
  *   The prior outputs from preceding passes
  */
abstract class TranslatingPass(input: PassInput, outputs: PassesOutput)(using PlatformContext)
    extends Pass(input, outputs)
