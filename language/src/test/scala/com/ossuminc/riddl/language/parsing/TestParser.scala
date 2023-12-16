package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.{Context, Definition, Domain, RiddlNode, RootContainer}
import com.ossuminc.riddl.language.Messages.Messages
import fastparse.P
import org.scalatest.matchers.must.Matchers

import scala.reflect.{ClassTag, classTag}

case class TestParser(input: RiddlParserInput, throwOnError: Boolean = false)
  extends TopLevelParser(input)
    with Matchers {
  push(input)

  def parse[T <: RiddlNode, U <: RiddlNode](
    parser: P[?] => P[T],
    extract: T => U
  ): Either[Messages, (U, RiddlParserInput)] = {
    expect[T](parser).map(x => extract(x._1) -> x._2)
  }

  protected def parserFor[T <: Definition: ClassTag]: P[?] => P[T] = {
    val parser: P[?] => P[?] = classTag[T].runtimeClass match {
      case x if x == classOf[AST.Type]       => typeDef(_)
      case x if x == classOf[AST.Domain]     => domain(_)
      case x if x == classOf[AST.Context]    => context(_)
      case x if x == classOf[AST.Entity]     => entity(_)
      case x if x == classOf[AST.Adaptor]    => adaptor(_)
      case x if x == classOf[AST.Invariant]  => invariant(_)
      case x if x == classOf[AST.Function]   => function(_)
      case x if x == classOf[AST.Streamlet]  => streamlet(_)
      case x if x == classOf[AST.Saga]       => saga(_)
      case x if x == classOf[AST.Repository] => repository(_)
      case x if x == classOf[AST.Projector]  => projector(_)
      case x if x == classOf[AST.Epic]       => epic(_)
      case x if x == classOf[AST.Connector]  => connector(_)
      case _ =>
        throw new RuntimeException(
          s"No parser defined for ${classTag[T].runtimeClass}"
        )
    }
    parser.asInstanceOf[P[?] => P[T]]
  }

  def parseRootContainer: Either[Messages, RootContainer] = {
    parseRootContainer(withVerboseFailures = true)
  }

  def parseTopLevelDomains: Either[Messages, RootContainer] = {
    parseRootContainer(withVerboseFailures = true)
  }

  def parseTopLevelDomain[TO <: RiddlNode](
                                            extract: RootContainer => TO
                                          ): Either[Messages, (TO, RiddlParserInput)] = {
    parseRootContainer(withVerboseFailures = true).map { case root: RootContainer =>
      extract(root) -> current
    }
  }

  def parseDefinition[FROM <: Definition: ClassTag, TO <: RiddlNode](
                                                                      extract: FROM => TO
                                                                    ): Either[Messages, (TO, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    val result = expect[FROM](parser)
    result.map(x => extract(x._1) -> x._2)
  }

  def parseDefinition[
    FROM <: Definition: ClassTag
  ]: Either[Messages, (FROM, RiddlParserInput)] = {
    val parser = parserFor[FROM]
    expect[FROM](parser)
  }

  def parseDomainDefinition[TO <: RiddlNode](
                                              extract: Domain => TO
                                            ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Domain, TO](domain(_), extract)
  }

  def parseContextDefinition[TO <: RiddlNode](
                                               extract: Context => TO
                                             ): Either[Messages, (TO, RiddlParserInput)] = {
    parse[Context, TO](context(_), extract)
  }
}