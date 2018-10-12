package org.interactivegraph.server

import java.util.{Map => JMap}

import scala.collection.JavaConversions

/**
  * Created by bluejoe on 2018/2/5.
  */
class DefaultCommandExecutorRegistry extends CommandExecutorRegistry {
  var _classNamePattern: Option[String] = None;

  val _commandsMap = collection.mutable.Map[String, CommandExecutor]();

  def setExtraCommands(extra: JMap[String, CommandExecutor]): Unit = {
    _commandsMap ++= JavaConversions.mapAsScalaMap(extra).toMap;
  }

  def setClassNamePattern(classNamePattern: String) = _classNamePattern = Some(classNamePattern);

  def executorOf(command: String): Option[CommandExecutor] = {
    _commandsMap.get(command).orElse({
      _classNamePattern.map { x =>
        Class.forName(x.replace("_", command.capitalize)).newInstance().asInstanceOf[CommandExecutor];
      }
    })
  }
}