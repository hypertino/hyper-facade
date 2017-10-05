package com.hypertino.facade.utils

/*
Top-level fields:
?fields=a,b,c

Top-level + inner fields:
?fields=a,b,c.x,c.y
OR
?fields=a,b,c.{x,y}

*/

case class SelectField(name: String, children: Map[String, SelectField])

class SelectFieldsParseError(reason: String, fields: String) extends RuntimeException(s"Can't parse $fields: $reason")

object SelectFields {
  def apply(selector: String): Map[String, SelectField] = {
    val it = selector.toIterator
    recursive(selector, it, 0)
  }

  private def recursive(selector: String, it: Iterator[Char], level: Int): Map[String, SelectField] = {
    val sb = new StringBuilder
    val currentLevel = Map.newBuilder[String, SelectField]
    var exit = false
    var expectsEndBrace = false

    while (it.hasNext && !exit) {
      val c = it.next()
      c match {
        case '.' ⇒
          if (sb.isEmpty) {
            throw new SelectFieldsParseError("Character was expected before '.'", selector)
          }
          val inner = recursive(selector, it, level + 1)
          val field = sb.toString()
          currentLevel += field → SelectField(field, inner)
          exit = !expectsEndBrace && level > 0
          sb.clear()

        case ',' ⇒
          if (sb.nonEmpty) {
            val field = sb.toString()
            currentLevel += field → SelectField(field, Map.empty)
          }
          exit = !expectsEndBrace && level > 0
          sb.clear()

        case '{' ⇒
          if (sb.nonEmpty) {
            throw new SelectFieldsParseError("'{' was expected after '.'", selector)
          }
          expectsEndBrace = true

        case '}' ⇒
          //          if (!expectsEndBrace) {
          //            throw new SelectFieldsParseError("'}' was unexpected", selector)
          //          }
          exit = level > 0
          expectsEndBrace = false

        case _ ⇒ sb.append(c)
      }
    }
    if (exit && expectsEndBrace) {
      throw new SelectFieldsParseError("'}' was expected", selector)
    }
    if (sb.nonEmpty) {
      val field = sb.toString()
      currentLevel += field → SelectField(field, Map.empty)
    }
    currentLevel.result()
  }
}

