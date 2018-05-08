package org.interactivegraph.server.commands

import java.io.{File, FileInputStream, OutputStream}

import org.apache.commons.io.IOUtils
import org.interactivegraph.server.commands.neo4j.{GraphMetaDB, Neo4jConnector}
import org.interactivegraph.server.{Command, ContentTag, JsonOutput, Params}
import org.neo4j.driver.v1.Session
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._

class LazyCommandFromFile extends Command {
  var _source: File = null;

  def setSource(file: File): Unit = {
    _source = file;
  }

  override def execute(params: Params, ct: ContentTag, out: OutputStream) = {
    IOUtils.copy(new FileInputStream(_source), out);
  }
}

