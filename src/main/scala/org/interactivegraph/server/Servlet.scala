package org.interactivegraph.server

import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.WebApplicationContextUtils

import java.io.PrintWriter
import net.sf.json.JSONObject
import scala.collection.mutable.ArrayBuffer


/**
  * Created by bluejoe on 2018/2/5.
  */

class Servlet extends HttpServlet {
  var _beans: WebApplicationContext = null;
  var _commandRegistry: CommandRegistry = null;

  override def init(servletConfig: ServletConfig) = {
    _beans = WebApplicationContextUtils.getRequiredWebApplicationContext(servletConfig.getServletContext());
    _commandRegistry = _beans.getBean(classOf[CommandRegistry]);
  }

  //Get the content of the request body
  protected  def  readRequest(req:HttpServletRequest): String={
  var reqReader = req.getReader;
  val jsonStrBuilder = new StringBuilder;
  var inputStr = "";
  while ({inputStr = reqReader.readLine(); inputStr!=null})  {
    jsonStrBuilder.append(inputStr);
  }
  val jsonStr = jsonStrBuilder.toString();
  return jsonStr;
}

override protected def doPost(req: HttpServletRequest, resp: HttpServletResponse) = {
  val command = req.getParameter("command");
  val jsonStr = readRequest(req).toString;
  val jsonObj = JSONObject.fromObject(jsonStr);
  val jsoncallback = req.getParameter("jsoncallback");

  def sendError(msg: String): Unit = {
    resp.sendError(500, msg);
  }

  if (command == null) {
    sendError("no command");
  }
  else {
    try {
      val out = resp.getOutputStream;
      if (!_commandRegistry.knows(command))
        throw new UnrecognizedCommandException(command);

      val params: Params = new Params {
        override def getInt(name: String): Int = jsonObj.get(name).toString.toInt;

        override def getString(name: String): String = jsonObj.get(name).toString;

        override def contains(name: String): Boolean = jsonObj.get(name) != null;

        override def getStringArray(name: String): Array[String] = {
          val jsonArray = jsonObj.getJSONArray(name);
          val strArray = ArrayBuffer[String]();
          if(jsonArray.size()>0){
            var i = 0;
            while(i<jsonArray.size()){
                strArray += jsonArray.get(i).toString;
                i+=1;
            }
          }
          return strArray.toArray;
        };
      };

      val ct: ContentTag = new ContentTag {
        def setContentType(ct: String) = resp.setContentType(ct);

        def setContentLength(len: Int) = resp.setContentLength(len);

        def setCharacterEncoding(en: String) = resp.setCharacterEncoding(en);
      };

      //enable jsonp
      if (jsoncallback != null)
        out.print(jsoncallback + "(");

      _commandRegistry.from(command).execute(params, ct, out);
      if (jsoncallback != null)
        out.print(")");

      out.close();
    }
    catch {
      case e: Exception => sendError(e.getMessage);
    }
  }
}



}