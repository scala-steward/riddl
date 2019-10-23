package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.RootContainer
import fastparse.Parsed.Failure
import fastparse.Parsed.Success

import fastparse._

/** Top level parsing rules */
abstract class AbstractTopLevelParser extends DomainParser {

  def annotated_input(index: Int): String = {
    input.slice(0, index) + "^" + input.slice(index, input.length)
  }

  def expect[T](parser: P[_] => P[T]): Either[String, T] = {
    fastparse.parse(input, parser(_)) match {
      case Success(content, _) =>
        Right(content)
      case failure @ Failure(_, index, _) =>
        val marked_up = annotated_input(index)
        val trace = failure.trace()
        Left(s"""Parse of '$marked_up' failed at position $index"
                |${trace.longAggregateMsg}
                |""".stripMargin)
    }
  }
}

case class TopLevelParser(input: RiddlParserInput)
    extends AbstractTopLevelParser

object TopLevelParser {

  def parse(
    input: RiddlParserInput
  ): Either[String, RootContainer] = {
    val tlp = TopLevelParser(input)
    tlp.expect(tlp.root(_)).map(RootContainer(_))
  }

}