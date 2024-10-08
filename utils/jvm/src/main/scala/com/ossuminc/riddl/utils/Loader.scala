/*
 * Copyright 2024 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.scalajs.js.annotation.JSExportTopLevel

/** This is the JVM version of the Loader utility. It is used to load file content in UTF-8 via a URL as a String and
  * returning the Future that will obtain it. Further processing can be chained onto the future. This handles the I/O
  * part of parsing in a platform specific way.
  */
@JSExportTopLevel("Loader")
case class Loader(url: URL) {
  require(url.isValid, "Cannot load from an invalid URL")

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.{ExecutionContext, Future}
  import scala.io.Source
  import scala.scalajs.js.annotation.JSExport

  @JSExport
  def load: Future[String] = {
    val source: Source = {
      import scala.io.Codec
      url.scheme match {
        case file: String if file == URL.fileScheme =>
          import java.io.FileNotFoundException
          import java.nio.file.{Files,Path}
          val path: Path =
            if url.basis.nonEmpty && url.path.nonEmpty then
              Path.of("/" + url.basis + "/" + url.path )
            else if url.basis.isEmpty && url.path.nonEmpty then 
              Path.of(url.path)
            else if url.basis.nonEmpty && url.path.isEmpty then
              Path.of("/" + url.basis)
            else 
              throw new IllegalStateException("URL is invalid!")
            end if
          if Files.exists(path) then Source.fromFile(path.toFile)(Codec.UTF8)
          else throw FileNotFoundException(s"While loading $path")
        case _ =>
          val jurl = java.net.URI(url.toExternalForm).toURL
          Source.fromURL(jurl)(Codec.UTF8)
      }
    }
    Future {
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    }
  }
}
