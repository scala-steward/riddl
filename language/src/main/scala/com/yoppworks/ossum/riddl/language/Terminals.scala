package com.yoppworks.ossum.riddl.language

/** Terminal symbol definitions in various categories */
object Terminals {

  object Punctuation {
    val asterisk = "*"
    val at = "@"
    val comma = ","
    val colon = ":"
    val curlyOpen = "{"
    val curlyClose = "}"
    val dot = "."
    val equals = "="
    val ellipsis = "..."
    val ellipsisQuestion = "...?"
    val plus = "+"
    val question = "?"
    val quote = "\""
    val roundOpen = "("
    val roundClose = ")"
    val squareOpen = "["
    val squareClose = "]"
    val undefined = "???"
    val verticalBar = "|"

    val all: Seq[String] = Seq(
      comma,
      colon,
      dot,
      equals,
      quote,
      quote,
      curlyOpen,
      curlyClose,
      squareOpen,
      squareClose,
      roundOpen,
      roundClose,
      undefined,
      verticalBar
    )
  }

  object Options {
    final val actor = "actor"
    final val aggregate = "aggregate"
    final val async = "async"
    final val available = "available"
    final val concept = "concept"
    final val consistent = "consistent"
    final val device = "device"
    final val eventSourced = "event sourced"
    final val function = "function"
    final val gateway = "gateway"
    final val kind = "kind"
    final val messageQueue = "mq"
    final val parallel = "parallel"
    final val reply = "reply"
    final val sequential = "sequential"
    final val service = "service"
    final val sync = "sync"
    final val value = "value"
    final val wrapper = "wrapper"
    final val stateMachine = "fsm"
    final val transient = "transient"
  }

  object Keywords {
    final val accepted = "accepted"
    final val action = "action"
    final val adapt = "adapt"
    final val adaptor = "adaptor"
    final val all = "all"
    final val any = "any"
    final val append = "append"
    final val ask = "ask"
    final val background = "background"
    final val become = "become"
    final val benefit = "benefit"
    final val brief = "brief"
    final val but = "but"
    final val call = "call"
    final val capability = "capability"
    final val causing = "causing"
    final val command = "command"
    final val commands = "commands"
    final val condition = "condition"
    final val consumer = "consumer"
    final val context = "context"
    final val described = "described"
    final val details = "details"
    final val do_ = "do"
    final val domain = "domain"
    final val each = "each"
    final val else_ = "else"
    final val entity = "entity"
    final val event = "event"
    final val events = "events"
    final val example = "example"
    final val execute = "execute"
    final val explained = "explained"
    final val feature = "feature"
    final val function = "function"
    final val given_ = "given"
    final val handler = "handler"
    final val handles = "handles"
    final val import_ = "import"
    final val include = "include"
    final val inlet = "inlet"
    final val input = "input"
    final val interaction = "interaction"
    final val invariant = "invariant"
    final val items = "items"
    final val joint = "joint"
    final val many = "many"
    final val mapping = "mapping"
    final val message = "message"
    final val morph = "morph"
    final val on = "on"
    final val one = "one"
    final val option = "option"
    final val optional = "optional"
    final val options = "options"
    final val outlet = "outlet"
    final val output = "output"
    final val pipe = "pipe"
    final val plant = "plant"
    final val processor = "processor"
    final val publish = "publish"
    final val query = "query"
    final val queries = "queries"
    final val range = "range"
    final val reference = "reference"
    final val remove = "remove"
    final val requires = "requires"
    final val result = "result"
    final val results = "results"
    final val reverted = "reverted"
    final val role = "role"
    final val saga = "saga"
    final val scenario = "scenario"
    final val see = "see"
    final val set = "set"
    final val state = "state"
    final val story = "story"
    final val tell = "tell"
    final val then_ = "then"
    final val topic = "topic"
    final val transform = "transform"
    final val transmit = "transmit"
    final val `type` = "type"
    final val value = "value"
    final val when = "when"
    final val yields = "yields"
  }

  object Predefined {
    final val Boolean = "Boolean"
    final val Date = "Date"
    final val DateTime = "DateTime"
    final val Decimal = "Decimal"
    final val Duration = "Duration"
    final val Id = "Id"
    final val Integer = "Integer"
    final val LatLong = "LatLong"
    final val Nothing = "Nothing"
    final val Number = "Number"
    final val Pattern = "Pattern"
    final val Real = "Real"
    final val String = "String"
    final val Time = "Time"
    final val TimeStamp = "TimeStamp"
    final val UniqueId = "UniqueId"
    final val URL = "URL"
    final val UUID = "UUID"
  }

  object Readability {
    final val and = "and"
    final val are = "are"
    final val as = "as"
    final val by = "by"
    final val for_ = "for"
    final val from = "from"
    final val is = "is"
    final val of = "of"
    final val on = "on"
    final val to = "to"
  }

  object Operators {
    final val and = "and"
    final val call = "call"
    final val if_ = "if"
    final val not = "not"
    final val or = "or"
    final val plus = "+"
    final val minus = "-"
    final val times = "*"
    final val div = "/"
    final val mod = "%"
  }
}
