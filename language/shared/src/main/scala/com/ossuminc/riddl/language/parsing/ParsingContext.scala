/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, PlatformContext, Timer}
import com.ossuminc.riddl.utils.SeqHelpers.*
import fastparse.*
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import scala.annotation.unused
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/** Unit Tests For ParsingContext */
trait ParsingContext(using io: PlatformContext) extends ParsingErrors {

  import com.ossuminc.riddl.utils.URL
  import fastparse.P

  def parseRule[RESULT <: RiddlValue](
    rpi: RiddlParserInput,
    rule: P[?] => P[RESULT],
    withVerboseFailures: Boolean = false
  )(validate: (result: Either[Messages, RESULT], input: RiddlParserInput, index: Int) => Either[Messages, RESULT] = {
    (result: Either[Messages, RESULT], _: RiddlParserInput, _: Int) => result
  }): Either[Messages, RESULT] = {
    try {
      fastparse.parse[RESULT](rpi, rule(_), withVerboseFailures) match {
        case Success(root, index) =>
          if messagesNonEmpty then validate(Left(messagesAsList), rpi, index)
          else validate(Right(root), rpi, index)
          end if
        case failure: Failure =>
          makeParseFailureError(failure, rpi)
          validate(Left(messagesAsList), rpi, 0)
      }
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, At.empty)
        validate(Left(messagesAsList), rpi, 0)
    }
  }

  def location[u: P](implicit ctx: P[?]): P[At] = {
    // NOTE: This isn't strictly kosher because of the cast but as long as we
    // NOTE: always use a RiddlParserInput, should be safe enough. This is
    // NOTE: required because of includes and concurrent parsing
    P(Index.map(idx => ctx.input.asInstanceOf[RiddlParserInput].location(idx)))
  }

  def doImport(
    loc: At,
    domainName: Identifier,
    url: LiteralString
  )(implicit ctx: P[?]): Domain = {
    // TODO: implement importDomain, issue #72
    Domain(At(), Identifier(At(), "NotImplemented"))
    // importDomain(url)
  }

  def doIncludeParsing[CT <: RiddlValue](loc: At, path: String, rule: P[?] => P[Seq[CT]])(implicit
    ctx: P[?]
  ): Include[CT] = {
    import com.ossuminc.riddl.utils.{PlatformContext, URL}
    val newURL = if URL.isValid(path) then {
      URL(path)
    } else {
      val name: String = {
        if path.endsWith(".riddl") then path
        else path + ".riddl"
      }
      ctx.input.asInstanceOf[RiddlParserInput].root.parent.resolve(name)
    }
    try {
      import com.ossuminc.riddl.utils.Await
      given  x: ExecutionContext = io.ec
      val future: Future[Include[CT]] = io.load(newURL).map { (data: String) =>
        val rpi = RiddlParserInput(data, newURL)
        val contents = doParse[CT](loc, rpi, newURL, rule)
        Include(loc, newURL, contents.toContents)
      }
      Await.result(future, 300)
    } catch {
      case NonFatal(exception) =>
        makeParseFailureError(exception, loc, s"while including '$path'")
        Include[CT](loc, newURL, Contents.empty[CT])
    }
  }

  private def doParse[CT <: RiddlValue](loc: At, rpi: RiddlParserInput, url: URL, rule: P[?] => P[Seq[CT]])(implicit
    ctx: P[?]
  ): Seq[CT] = {
    fastparse.parse[Seq[CT]](rpi, rule(_), verboseFailures = true) match {
      case Success(content, _) =>
        if messagesNonEmpty then Seq.empty[CT]
        else if content.isEmpty then
          error(loc, s"Parser could not translate '${rpi.origin}''", s"while including '$url''")
        end if
        content
      case failure: Failure =>
        makeParseFailureError(failure, rpi)
        Seq.empty[CT]
    }
  }

  def checkForDuplicateIncludes[CT <: RiddlValue](contents: Seq[CT]): Unit = {
    import com.ossuminc.riddl.language.Finder
    val allIncludes = Finder(contents.toContents).findByType[Include[?]]
    val distinctIncludes = allIncludes.distinctBy(_.origin)
    for {
      incl <- distinctIncludes
      copies = allIncludes.filter(_.origin == incl.origin) if copies.size > 1
    } yield {
      val copyList = copies.map(i => i.origin.toExternalForm + i.loc.toShort).mkString(", ")
      val message = s"Duplicate include origin detected in $copyList"
      warning(incl.loc, message, "while merging includes")
    }
  }
}
