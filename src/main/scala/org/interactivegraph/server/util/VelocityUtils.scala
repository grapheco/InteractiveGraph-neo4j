package org.interactivegraph.server.util

import java.io.StringWriter
import java.util.Properties

import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.ToolManager

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

  def parse(expr: String, context: Map[String, Any]): String = {
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

    ve.evaluate(vc, writer, "", expr);
    writer.toString;
  }
}
