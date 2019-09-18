package com.yoppworks.ossum.idddl.parse

sealed trait AST

object AST {

  case class Error(message: String) extends AST
  sealed trait Terminal extends AST
  sealed trait Type extends AST
  sealed trait CardinalizedType extends Type
  sealed trait Def extends AST {
    def children: Seq[Def]
  }

  case object `type` extends Terminal
  case object domain extends Terminal
  case object command extends Terminal
  case object event extends Terminal
  case object query extends Terminal
  case object result extends Terminal
  case object lcb extends Terminal
  case object rcb extends Terminal
  case object eq extends Terminal

  case object String extends Type
  case object Boolean extends Type
  case object Number extends Type
  case object Id extends Type
  case object Date extends Type
  case object Time extends Type
  case object TimeStamp extends Type
  case object URL extends Type
  case class NamedType(typeName: String) extends Type
  case class Enumeration(of: Seq[String]) extends Type
  case class Alternation(of: Seq[Type]) extends Type
  case class Aggregation(of: Map[String, Type]) extends Type

  case class Optional(element: Type) extends CardinalizedType
  case class ZeroOrMore(of: Type) extends CardinalizedType
  case class OneOrMore(of: Type) extends CardinalizedType

  case class CommandDef(name: String, typ: Type) extends Def {
    def children = Seq.empty[Def]
  }
  case class EventDef(name: String, typ: Type, resultsFrom: String)
      extends Def { def children = Seq.empty[Def] }
  case class QueryDef(name: String, typ: Type) extends Def {
    def children = Seq.empty[Def]
  }
  case class ResponseDef(name: String, typ: Type, respondsTo: String)
      extends Def { def children = Seq.empty[Def] }

  case class DomainPath(path: Seq[String], name: String)
  case class DomainDef(name_path: DomainPath, children: Seq[Def]) extends Def
  case class ContextDef(name: String, children: Seq[Def]) extends Def
  case class TypeDef(name: String, typ: Type) extends Def {
    def children = Seq.empty[Def]
  }

}