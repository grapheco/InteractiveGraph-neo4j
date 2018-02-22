package org.interactivegraph.server

import java.util.{Map => JMap}

import scala.collection.JavaConversions

/**
  * Created by bluejoe on 2018/2/5.
  */
class DefaultCommandRegistry extends CommandRegistry {
  val _commandsMap = collection.mutable.Map[String, Command]();

  def setCommandsMap(extra: JMap[String, Command]): Unit = {
    _commandsMap ++= JavaConversions.mapAsScalaMap(extra).toMap;
  }

  def knows(command: String): Boolean = {
    _commandsMap.contains(command);
  }

  def from(command: String): Command = {
    _commandsMap(command);
  }
}

