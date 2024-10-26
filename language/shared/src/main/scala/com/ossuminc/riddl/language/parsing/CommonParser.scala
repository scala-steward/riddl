/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{PlatformContext, URL}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*
import wvlet.airframe.ulid.ULID

import java.net.URI
import java.nio.file.Files
import scala.reflect.{ClassTag, classTag}
import scala.concurrent.Future

/** Common Parsing Rules */
private[parsing] trait CommonParser(using io: PlatformContext)
    extends ReferenceParser
    with Readability
    with NoWhiteSpaceParsers
    with ParsingContext {

  def open[u: P]: P[Unit] = {
    P(Punctuation.curlyOpen)
  }

  def close[u: P]: P[Unit] = {
    P(Punctuation.curlyClose)
  }

  def author[u: P]: P[Author] =
    P(
      location ~ Keywords.author ~/ identifier ~ is ~ open ~
        (undefined(
          (
            LiteralString(At(), ""),
            LiteralString(At(), ""),
            Option.empty[LiteralString],
            Option.empty[LiteralString],
            Option.empty[URL]
          )
        ) |
          (Keywords.name ~ is ~ literalString ~ Keywords.email ~ is ~
            literalString ~ (Keywords.organization ~ is ~ literalString).? ~
            (Keywords.title ~ is ~ literalString).? ~
            (Keywords.url ~ is ~ httpUrl).?)) ~ close ~ withMetaData
    ).map { case (loc, id, (name, email, org, title, url), descriptives) =>
      Author(loc, id, name, email, org, title, url, descriptives.toContents)
    }
  end author

  def importDef[u: P]: P[OccursInDomain] = {
    P(
      location ~ Keywords.import_ ~ Keywords.domain ~ identifier ~ from ~ literalString
    ).map { case (loc, id, litStr) =>
      doImport(loc, id, litStr)
    }
  }

  def undefined[u: P, RT](f: => RT): P[RT] = {
    P(Punctuation.undefinedMark./).map(_ => f)
  }

  def literalStrings[u: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  private def markdownLines[u: P]: P[Seq[LiteralString]] = {
    P(markdownLine.rep(1))
  }

  def maybe[u: P](keyword: String): P[Unit] = P(keyword).?

  def briefDescription[u: P]: P[BriefDescription] = {
    P(location ~ Keywords.briefly ~ byAs.? ~ literalString).map { case (loc, brief: LiteralString) =>
      BriefDescription(loc, brief)
    }
  }

  private def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~
        (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~
        close) | literalString.map(Seq(_))
    )
  }

  def description[u: P](implicit ctx: P[?]): P[Description] =
    P(
      location ~ Keywords.described ~ (
        (byAs ~/ docBlock) |
          (at ~/ httpUrl) |
          (in ~/ Keywords.file ~ literalString)
      )
    ).map {
      case (loc, strings: Seq[LiteralString]) =>
        BlockDescription(loc, strings)
      case (loc, url: URL) =>
        URLDescription(loc, url)
      case (loc, file: LiteralString) =>
        val url = ctx.input.asInstanceOf[RiddlParserInput].root.resolve(file.s)
        URLDescription(loc, url)
    }

  def maybeDescription[u: P]: P[Option[Description]] =
    P(description).?

  private def inlineComment[u: P]: P[InlineComment] = {
    P(
      location ~ "/*" ~ until('*', '/')
    ).map { case (loc, comment) =>
      val actual = comment.dropRight(2) // we don't want the */ in the comment text
      val lines = actual.split('\n').toList
      InlineComment(loc, lines)
    }
  }

  private def endOfLineComment[u: P]: P[LineComment] = {
    P(location ~ "//" ~ toEndOfLine).map { case (loc, comment) =>
      LineComment(loc, comment)
    }
  }

  def comment[u: P]: P[Comment] = {
    P(inlineComment | endOfLineComment)
  }

  def comments[u: P]: P[Seq[Comment]] = {
    P(comment).rep(0)
  }

  private def wholeNumber[u: P]: P[Long] = {
    CharIn("0-9").rep(1).!.map(_.toLong)
  }

  def integer[u: P]: P[Long] = {
    CharIn("+\\-").? ~~ wholeNumber
  }

  private def simpleIdentifier[u: P]: P[String] = {
    P(CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_\\-").?).!
  }

  private def quotedIdentifier[u: P]: P[String] = {
    P("'" ~~ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~~ "'")
  }

  private def anyIdentifier[u: P]: P[String] = {
    P(simpleIdentifier | quotedIdentifier)
  }

  def identifier[u: P]: P[Identifier] = {
    P(location ~ anyIdentifier).map { case (loc, value) => Identifier(loc, value) }
  }

  def pathIdentifier[u: P]: P[PathIdentifier] = {
    P(location ~ anyIdentifier ~~ (Punctuation.dot ~~ anyIdentifier).repX(0)).map { case (loc, first, strings) =>
      PathIdentifier(loc, first +: strings)
    }
  }

  def term[u: P]: P[Term] = {
    P(
      location ~ Keywords.term ~ identifier ~ is ~ docBlock ~ withMetaData
    )./.map { case (loc, id, definition, descriptives) =>
      Term(loc, id, definition, descriptives.toContents)
    }
  }

  def mimeType[u: P]: P[String] = {
    P(
      ("application" | "audio" | "example" | "font" |
        "image" | "model" | "text" | "video") ~~ "/" ~~ CharIn("a-z\\-.+").rep(1)
    ).!
  }

  def fileAttachment[u: P]: P[FileAttachment] = {
    P(
      location ~ Keywords.attachment ~ identifier ~ is ~ mimeType ~ in ~ Keywords.file ~ literalString
    ).map { case (loc, id, mimeType, fileName) =>
      FileAttachment(loc, id, mimeType, fileName)
    }
  }

  def stringAttachment[u: P]: P[StringAttachment] =
    P(
      location ~ Keywords.attachment ~ identifier ~ is ~ mimeType ~ as ~ literalString
    ).map { case (loc, id, mimeType, value) =>
      StringAttachment(loc, id, mimeType, value)
    }

  def ulidAttachment[u:P]: P[ULIDAttachment] =
    P(
      location ~ Keywords.attachment ~ "ULID" ~ is ~ literalString
    ).map { case (loc, ulidString) =>
      val ulid = ULID.fromString(ulidString.s)
      ULIDAttachment(loc, ulid)
    }
  end ulidAttachment

  def metaData[u: P]: P[MetaData] =
    P(briefDescription | description | term | authorRef | fileAttachment | stringAttachment | ulidAttachment)
      .asInstanceOf[P[MetaData]]

  def withMetaData[u: P]: P[Seq[MetaData]] = {
    P(
      Keywords.`with` ~ open ~ (undefined(Seq.empty[MetaData]) | metaData.rep(1)) ~ close
    ).?./.map {
      case Some(list: Seq[MetaData]) =>
        list
      case None =>
        Seq.empty
    }
  }

  def include[u: P, CT <: RiddlValue](parser: P[?] => P[Seq[CT]]): P[Include[CT]] = {
    P(location ~ Keywords.include ~ literalString)./.map { case (loc: At, str: LiteralString) =>
      doIncludeParsing[CT](loc, str.s, parser)
    }
  }

  private def hostString[u: P]: P[String] = {
    P(CharsWhile { ch => ch.isLetterOrDigit || ch == '-' }.rep(1, ".", 32)).!
  }

  private def portNum[u: P]: P[String] = {
    P(CharsWhileIn("0-9").rep(min = 1, max = 5)).!.map { (numStr: String) =>
      val num = numStr.toInt
      if num > 0 && num < 65535 then numStr
      else
        error(s"Invalid port number: $numStr. Must be in range 0 <= port < 65536")
        "0"
    }
  }

  private def urlPath[u: P]: P[String] = {
    P(
      CharsWhile(ch => ch.isLetterOrDigit || "/-?#/.=".contains(ch))
        .rep(min = 0, max = 240)
    ).!
  }

  def httpUrl[u: P]: P[URL] = {
    P(
      "http" ~ "s".? ~ "://" ~ hostString ~ (":" ~ portNum).? ~ "/" ~ urlPath.?
    ).!.map(URL)
  }

  def invariant[u: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ (
        undefined(Option.empty[LiteralString]) | literalString.map(Some(_))
      ) ~ withMetaData
    ).map { case (loc, id, condition, descriptives) =>
      Invariant(loc, id, condition, descriptives.toContents)
    }
  }

  def groupAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.group,
          "page",
          "pane",
          "dialog",
          "menu",
          "popup",
          "frame",
          "column",
          "window",
          "section",
          "tab",
          "flow",
          "block"
        ).!
      )
    )
  }

  def outputAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.output,
          "document",
          "list",
          "table",
          "graph",
          "animation",
          "picture"
        ).!
      )
    )
  }

  def inputAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.input,
          "form",
          "text",
          "button",
          "picklist",
          "selector",
          "item"
        ).!
      )
    )
  }

  def shownBy[u: P]: P[ShownBy] = {
    P(
      location ~ Keywords.shown ~ by ~ open ~ httpUrl.rep(1) ~ close
    ).map { case (loc, urls) =>
      ShownBy(loc, urls)
    }
  }

}
