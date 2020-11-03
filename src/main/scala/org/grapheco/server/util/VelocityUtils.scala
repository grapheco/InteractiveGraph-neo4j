package org.grapheco.server.util

import java.io.{File, FileOutputStream, StringWriter}
import java.util.Properties

import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.ToolManager
import org.apache.velocity.tools.config.DefaultKey
import org.springframework.util.ClassUtils

import scala.collection.JavaConversions
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress

import org.apache.commons.io.IOUtils
import org.neo4j.blob.{Blob, ManagedBlob}
import org.neo4j.driver.internal.value.{InlineBlob, RemoteBlob}

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
  val props = new Properties()
  props.put("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.SimpleLog4JLogSystem")
  props.put("runtime.log.logsystem.log4j.category", "velocity")
  props.put("runtime.log.logsystem.log4j.logger", "velocity")
  props.put("runtime.log.debug.stacktrace", "false")
  props.put("runtime.log.invalid.references", "false")
  ve.init(props)

  val PATH = ClassUtils.getDefaultClassLoader
    .getResource("")
    .getPath
    .replace("/WEB-INF/classes","") + "static/"
  //TODO port!!!!
//  val WEBPATH = s"http://${InetAddress.getLocalHost.getHostAddress}:9999/graphserver/static/"
  val WEBPATH = s"/graphserver/static/"
  val fileSystemTool = new FileSystemTool()

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
        val value = vc.get("__VAR");
        value match {
          case value: InlineBlob => {
            val key = value.id.asLiteralString()
            // TODO jpg?
            val filename = key+".jpg"
            if (!fileSystemTool.exists(PATH + filename)){
              fileSystemTool.filesave(value.toBytes(),PATH,filename)
            }
            WEBPATH + filename
          }
//          case value: ManagedBlob => {
//            val key = value.id.asLiteralString()
//            val filename = key+".jpg"
//            if (!fileSystemTool.exists(PATH + filename)){
//              fileSystemTool.filesave(value.toBytes(),PATH,filename)
//            }
//            WEBPATH + filename
//          }
          case value: RemoteBlob => {
            val key = value.id.asLiteralString()
            val filename = key+".jpg"
            if (!fileSystemTool.exists(PATH + filename)){
              val b = value.toBytes()
              fileSystemTool.filesave(b,PATH,filename)
            }
            WEBPATH + filename
          }
          case _ => {
            value
          }
        }
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

  @throws[IOException]
  def filesave(file: Array[Byte], filePath: String, fileName: String): String = { //目标目录
    val targetfile = new File(filePath)
    if (!targetfile.exists) targetfile.mkdirs
    //二进制流写入
    val out = new FileOutputStream(filePath + fileName)
    out.write(file)
    out.flush()
    out.close()
    return  fileName
  }
}
