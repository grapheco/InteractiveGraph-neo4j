package org.interactivegraph.server

import java.io.OutputStream
import java.util.{Map => JMap}

import com.google.gson.JsonObject
import org.interactivegraph.server.util.JsonUtils

/**
  * Created by bluejoe on 2018/2/6.
  */
class UnrecognizedCommandException(command: String)
  extends RuntimeException(s"unrecognized command: $command") {
}

trait CommandExecutorRegistry {
  def executorOf(command: String): Option[CommandExecutor];
}

trait ContentTag {
  def setContentType(ct: String);

  def setContentLength(len: Int);

  def setCharacterEncoding(en: String);
}

trait CommandExecutor {
  def initialize(setting: Setting);

  def execute(requestBody: JsonObject, ct: ContentTag, out: OutputStream);
}

trait JsonCommandExecutor extends CommandExecutor {
  override final def execute(requestBody: JsonObject, ct: ContentTag, out: OutputStream): Unit = {
    ct.setCharacterEncoding("utf-8");
    ct.setContentType("text/json");

    val responseBody = execute(requestBody);
    out.write(JsonUtils.stringify(responseBody).getBytes("utf-8"));
  }

  def execute(request: JsonObject): Map[String, _];
}