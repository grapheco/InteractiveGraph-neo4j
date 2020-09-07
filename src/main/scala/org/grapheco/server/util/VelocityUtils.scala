package org.grapheco.server.util

import java.io.{File, FileOutputStream, StringWriter}
import java.util.Properties

import cn.pidb.blob.Blob
import cn.pidb.engine.blob.{BlobIO, InlineBlob, RemoteBlob}
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.tools.ToolManager
import org.apache.velocity.tools.config.DefaultKey
import org.neo4j.values.storable.{BlobValue, ValueWriter}
import org.springframework.util.ClassUtils
import scala.collection.JavaConversions
import java.io.FileOutputStream
import java.io.IOException

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
  ve.init(props)
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
        var value = vc.get("__VAR");
        //if is a blob
        if(value.isInstanceOf[Blob]){
          //get blob
          var result:String = ""
          try {
            val data = value.asInstanceOf[Blob].toBytes()
            val path = ClassUtils.getDefaultClassLoader.getResource("").getPath.replace("/WEB-INF/classes","") + "static/"
            val tool = new FileSystemTool()
            result = tool.filesave(data,path, System.currentTimeMillis.toString+".jpg")
          }
          catch{
            case e:Throwable =>
              print(e.toString)
          }
          //TODO url
          return "http://localhost:9999/graphserver/static/"+result
        }
        return value
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
