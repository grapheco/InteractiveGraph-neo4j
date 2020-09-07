package org.grapheco.server

import java.io.{File, FileInputStream, FileOutputStream, InputStreamReader}
import java.util.Properties

import com.google.gson.JsonObject
import javax.servlet.ServletConfig
import javax.servlet.http.{HttpServlet, HttpServletRequest, HttpServletResponse}
import org.apache.commons.fileupload.{FileItem, FileUploadException}
import org.apache.commons.fileupload.disk.DiskFileItemFactory
import org.apache.commons.fileupload.servlet.ServletFileUpload
import org.apache.commons.io.IOUtils
import org.grapheco.server.util.{JsonUtils, Logging, ServletContextUtils}
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer
import org.springframework.context.support.FileSystemXmlApplicationContext
import scala.collection.JavaConverters._

import scala.collection.{JavaConversions, mutable}

/**
  * Created by bluejoe on 2018/2/5.
  */

class ConnectorServlet extends HttpServlet with Logging {
  var _setting: Setting = _;
  var _commandRegistry: CommandExecutorRegistry = _;
  var _allowOrigin: Option[String] = None;

  override def init(servletConfig: ServletConfig) = {
    ServletContextUtils.setServletContext(servletConfig.getServletContext);

    //load conf file
    val path: String = servletConfig.getInitParameter("configFile")
    val configFile = Some(new File(path)).map { file =>
      if (file.isAbsolute) {
        file;
      }
      else {
        new File(servletConfig.getServletContext.getRealPath("/" + path));
      }
    }.get

    val is = new InputStreamReader(new FileInputStream(configFile), "utf-8");
    val ps = new Properties();
    ps.load(is);
    is.close();

    logger.info(s"using configuration: $configFile");
    val map = JavaConversions.propertiesAsScalaMap(ps);

    _allowOrigin = map.get("allowOrigin");
    val backendType = map("backendType").toLowerCase();

    val appctx = new FileSystemXmlApplicationContext();

    //enable expr like ${neo4j.boltUser}
    val placeholder = new PropertyPlaceholderConfigurer();
    placeholder.setProperties(ps);
    placeholder.setIgnoreUnresolvablePlaceholders(true);
    appctx.addBeanFactoryPostProcessor(placeholder);

    appctx.setConfigLocation(s"classpath:${backendType}.xml");
    appctx.refresh();

    _setting = appctx.getBean(classOf[Setting]);
    logger.info(s"loaded setting: ${_setting}");
    _commandRegistry = _setting._executors;
  }

  override def service(req: HttpServletRequest, resp: HttpServletResponse) = {
    _allowOrigin.foreach(resp.setHeader("Access-Control-Allow-Origin", _));
    super.service(req, resp);
  }

  override def doOptions(req: HttpServletRequest, resp: HttpServletResponse) = {
    super.doOptions(req, resp);
    resp.setHeader("Access-Control-Allow-Headers", "Content-Type");
  }

  val _executorCache = mutable.Map[String, CommandExecutor]();

  def getCachedExecutor(command: String): CommandExecutor = {
    _executorCache.getOrElseUpdate(command, {
      val executor =
        try {
          val opt = _commandRegistry.executorOf(command);
          if (!opt.isDefined)
            throw new UnrecognizedCommandException(command);

          opt.get
        }
        catch {
          case e: Throwable => {
            e.printStackTrace();
            throw new UnrecognizedCommandException(command);
          }
        }

      executor.initialize(this._setting);
      executor;
    });
  }

  override def doPost(req: HttpServletRequest, resp: HttpServletResponse) = {
    val command = req.getParameter("command");

    var requestBody:JsonObject = new JsonObject
    if (command == "searchImage") {
      requestBody = saveFile(req)
    }else{
      val is = req.getInputStream;
      val body = IOUtils.toString(is, "utf-8");
      requestBody = JsonUtils.parse(body).getAsJsonObject;
    }


    def sendError(msg: String): Unit = {
      resp.sendError(500, msg);
    }

    if (command == null) {
      sendError("no command in request");
    }
    else {
      try {
        val out = resp.getOutputStream;

        val ct: ContentTag = new ContentTag {
          def setContentType(ct: String) = resp.setContentType(ct);

          def setContentLength(len: Int) = resp.setContentLength(len);

          def setCharacterEncoding(en: String) = resp.setCharacterEncoding(en);
        };

        getCachedExecutor(command).execute(requestBody, ct, out);

        out.close();
      }
      catch {
        case e: Throwable => {
          e.printStackTrace();
          sendError(e.getMessage)
        };
      }
    }
  }

  def saveFile(request:HttpServletRequest): JsonObject ={
    val res = new JsonObject
    try {
      val realPath = ServletContextUtils.getServletContext.getRealPath("/upload");//  /WEB-INF/files
      //判断存放上传文件的目录是否存在（不存在则创建）
      val f = new File(realPath);
      if(!f.exists() && !f.isDirectory()){
        System.out.println("目录或文件不存在! 创建目标目录。");
        f.mkdir();
      }
      //1、设置环境:创建一个DiskFileItemFactory工厂
      val factory = new DiskFileItemFactory();

      //2、核心操作类:创建一个文件上传解析器。
      val upload = new ServletFileUpload(factory);
      //解决上传"文件名"的中文乱码
      upload.setHeaderEncoding("UTF-8");

      //3、判断enctype:判断提交上来的数据是否是上传表单的数据
      if(!ServletFileUpload.isMultipartContent(request)){
        System.out.println("不是上传文件，终止");
        //按照传统方式获取数据
        return res;
      }

      //限制单个上传文件大小(5M)
      upload.setFileSizeMax(1024*1024*5);
      //限制总上传文件大小(50M)
      upload.setSizeMax(1024*1024*50);

      //4、使用ServletFileUpload解析器解析上传数据，解析结果返回的是一个List<FileItem>集合，每一个FileItem对应一个Form表单的输入项
      val items =upload.parseRequest(request).asScala.toList;

      items.foreach(i=>{
        val item = i.asInstanceOf[FileItem]
        //如果fileitem中封装的是普通输入项的数据（输出名、值）
        if(item.isFormField()){
          val filedName = item.getFieldName();//普通输入项数据的名
          //解决普通输入项的数据的中文乱码问题
          val filedValue = item.getString("UTF-8");//普通输入项的值
          res.addProperty(filedName, filedValue)
//          System.out.println("普通字段:"+filedName+"=="+filedValue);
        }else{
          //如果fileitem中封装的是上传文件，得到上传的文件名称，
          val fileName = "123"
          //多个文件上传输入框有空 的 异常处理
          if(fileName!=null && !("".equals(fileName.trim()))) { //去空格是否为空
            //拼接上传路径。存放路径+上传的文件名
            val filePath = realPath + "/" + fileName;
            //构建输入输出流
            val in = item.getInputStream(); //获取item中的上传文件的输入流
            val out = new FileOutputStream(filePath); //创建一个文件输出流

            //创建一个缓冲区
            val b = new Array[Byte](1024*1024*5)
            //判断输入流中的数据是否已经读完的标识
            var len = -1;
            //循环将输入流读入到缓冲区当中，(len=in.read(buffer))！=-1就表示in里面还有数据
            while ((len = in.read(b)) != -1) { //没数据了返回-1
              //使用FileOutputStream输出流将缓冲区的数据写入到指定的目录(savePath+"\\"+filename)当中
              out.write(b, 0, len);
            }
            //关闭流
            out.close();
            in.close();
            //删除临时文件
            item.delete(); //删除处理文件上传时生成的临时文件
            System.out.println("文件上传成功");
          }
        }
      })
    }
    catch {
      case e: FileUploadException=>
      throw new RuntimeException("服务器繁忙，文件上传失败");
    }
    return res
  }
}


class UnknownBackendTypeException(typeName: String) extends
  RuntimeException(s"unknown backend type: $typeName") {

}