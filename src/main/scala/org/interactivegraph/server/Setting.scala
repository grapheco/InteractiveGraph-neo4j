package org.interactivegraph.server

/**
  * Created by bluejoe on 2018/10/7.
  */
class Setting {
  var _executors: CommandExecutorRegistry = _;

  def setCommandExecutorRegistry(value: CommandExecutorRegistry) = _executors = value;
}