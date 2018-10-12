package org.interactivegraph.server.util

import java.io.{File, StringWriter}
import java.util.Properties

import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.ToolManager
import org.apache.velocity.tools.config.DefaultKey

import scala.collection.JavaConversions

/**
  * Created by bluejoe on 2018/2/13.
  */
object VelocityUtils {
  val pro = new Properties();
  val toolManager = new ToolManager();
  toolManager.configure("tools.xml");

  pro.setProperty("input.encoding", "UTF-8");
  pro.setProperty("output.encoding", "UTF-8");
  val ve = new VelocityEngine(pro);

  def parse(expr: String, context: Map[String, Any]): Any = {
    val vc = toolManager.createContext();
    val writer = new StringWriter();

    context.foreach(kv => vc.put(kv._1,
      //is a scala Map?
      if (kv._2.isInstanceOf[Map[_, _]]) {
        JavaConversions.mapAsJavaMap(kv._2.asInstanceOf[Map[_, _]])
      }
      else {
        kv._2
      }));

    try {
      if (expr.startsWith("=")) {
        val expr1 = expr.substring(1);
        ve.evaluate(vc, writer, "", s"#set($$__VAR=$expr1)");
        vc.get("__VAR");
      }
      else {
        ve.evaluate(vc, writer, "", expr);
        writer.getBuffer.toString.trim
      }
    }
    catch {
      case e: Throwable =>
        throw new WrongExpressionException(expr, e);
    }
  }
}

class WrongExpressionException(msg: String, e: Throwable) extends RuntimeException(msg, e) {

}

@DefaultKey("fileTool")
class FileSystemTool {
  def exists(path: String) = new File(path).exists();
}