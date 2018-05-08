package org.interactivegraph.server

import java.io.OutputStream
import java.util.{Map => JMap}

import org.interactivegraph.server.util.JsonUtils

/**
  * Created by bluejoe on 2018/2/6.
  */
class UnrecognizedCommandException(command: String)
  extends RuntimeException(s"unrecognized command: $command") {
}

trait CommandRegistry {
  def knows(command: String): Boolean;

  def from(command: String): Command;
}

trait ContentTag {
  def setContentType(ct: String);

  def setContentLength(len: Int);

  def setCharacterEncoding(en: String);
}

trait Command {
  def execute(params: Params, ct: ContentTag, out: OutputStream);
}

trait JsonOutput extends Command {
  override final def execute(params: Params, ct: ContentTag, out: OutputStream): Unit = {
    ct.setCharacterEncoding("utf-8");
    ct.setContentType("text/json");
    out.write(JsonUtils.toJSONString(execute(params)).getBytes("utf-8"));
  }

  def execute(params: Params): Map[String, Any];
}

trait Params {
  def getInt(name: String): Int;
  def getString(name: String): String;
  def getStringArray(name: String):Array[String];
  def contains(name: String): Boolean;
}