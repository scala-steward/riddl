/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.{Comment, *}
import com.ossuminc.riddl.language.At
import fastparse.{P, *}
import fastparse.MultiLineWhitespace.*

import java.net.URI
import java.nio.file.Files
import scala.reflect.{ClassTag, classTag}
import scala.concurrent.Future


/** Common Parsing Rules */
private[parsing] trait CommonParser extends Readability with NoWhiteSpaceParsers with ParsingContext {

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
            (Keywords.url ~ is ~ httpUrl).?)) ~ close ~ briefly ~ description
    ).map { case (loc, id, (name, email, org, title, url), brief, description) =>
      Author(loc, id, name, email, org, title, url, brief, description)
    }
  end author


  def importDef[u: P]: P[OccursInDomain] = {
    P(
      location ~ Keywords.import_ ~ Keywords.domain ~ identifier ~ from ~ literalString
    ).map { case(loc, id, litStr) =>
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

  def briefly[u: P]: P[Option[LiteralString]] = {
    P(Keywords.briefly ~/ literalString)
  }.?
  
  def briefDescription[u:P]: P[BriefDescription] = {
    P(location ~ Keywords.briefly ~ literalString).map {
      case (loc, brief: LiteralString) => BriefDescription(loc, brief)
    }
  }

  def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~
        (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~
        close) | literalString.map(Seq(_))
    )
  }

  def blockDescription[u: P]: P[BlockDescription] = {
    P(location ~ docBlock).map(tpl => BlockDescription(tpl._1, tpl._2))
  }

  def fileDescription[u: P](implicit ctx: P[?]): P[URLDescription] = {
    P(location ~ Keywords.file ~ literalString).map { case (loc, file) =>
      val url = ctx.input.asInstanceOf[RiddlParserInput].root.resolve(file.s)
      URLDescription(loc, url)
    }
  }

  def urlDescription[u: P]: P[URLDescription] = {
    P(location ~ httpUrl).map { case (loc, url) =>
      URLDescription(loc, url)
    }
  }
  
  def description[u: P]: P[Option[Description]] = P(
    P(
      Keywords.described ~/
        ((byAs ~ blockDescription) | (in ~ fileDescription) |
          (at ~ urlDescription))
    )
  ).?

  private def inlineComment[u: P]: P[InlineComment] = {
    P(
      location ~ "/*" ~ until('*', '/')
    ).map { case (loc, comment) =>
      val lines = comment.split('\n').toList
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

  def open[u: P]: P[Unit] = { P(Punctuation.curlyOpen) }

  def close[u: P]: P[Unit] = { P(Punctuation.curlyClose) }

  def include[u: P, CT <: RiddlValue](parser: P[?] => P[Seq[CT]]): P[Include[CT]] = {
    P(location ~ Keywords.include ~ literalString)./.map {
      case (loc: At, str: LiteralString) =>
        doIncludeParsing[CT](loc, str.s, parser)
    }
  }


  private def maybeOptionWithArgs[u: P](
    validOptions: => P[String]
  ): P[(At, String, Seq[LiteralString])] = {
    P(
      location ~ validOptions ~
        (Punctuation.roundOpen ~ literalString.rep(0, Punctuation.comma) ~
          Punctuation.roundClose).?
    ).map {
      case (loc, opt, Some(maybeArgs)) => (loc, opt, maybeArgs)
      case (loc, opt, None)            => (loc, opt, Seq.empty[LiteralString])
    }
  }

  extension (map: Map[Class[RiddlValue], Seq[RiddlValue]])
    def extract[T <: RiddlValue: ClassTag]: Seq[T] = {
      val clazzTag = classTag[T].runtimeClass
      map
        .get(clazzTag.asInstanceOf[Class[RiddlValue]])
        .fold(Seq.empty[T])(_.map(_.asInstanceOf[T]))
    }

  def mapTo[T <: RiddlValue](seq: Option[Seq[RiddlValue]]): Seq[T] = {
    seq.fold(Seq.empty[T])(_.map(_.asInstanceOf[T]))
  }

  private def hostString[u: P]: P[String] = {
    P(CharsWhile { ch => ch.isLetterOrDigit || ch == '-' }.rep(1, ".", 32)).!
  }

  private def portNum[u: P]: P[String] = {
    P(CharsWhileIn("0-9").rep(min = 1, max = 5)).!
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

  def term[u: P]: P[Term] = {
    P(location ~ Keywords.term ~ identifier ~ is ~ briefly ~ description)./.map(tpl =>
      Term.apply.tupled(tpl)
    )
  }

  def invariant[u: P]: P[Invariant] = {
    P(
      Keywords.invariant ~/ location ~ identifier ~ is ~ (
        undefined(Option.empty[LiteralString]) | literalString.map(Some(_))
        ) ~ briefly ~ description
    ).map { case (loc, id, condition, brief, description) =>
      Invariant(loc, id, condition, brief, description)
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
}