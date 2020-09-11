package org.grapheco.server.pidb

import java.io.{File, FileInputStream}

import org.apache.commons.io.{FileUtils, IOUtils}
import org.grapheco.server.util.{JsonUtils, Logging, ServletContextUtils}
import org.neo4j.driver.v1._
import org.neo4j.graphdb.factory.{GraphDatabaseFactory, GraphDatabaseSettings}
import org.neo4j.graphdb.{GraphDatabaseService, Label, RelationshipType}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.{DisposableBean, InitializingBean}
import cn.pidb.engine.{BoltService, CypherService, PidbConnector}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Created by huchuan on 2019/4/10.
  */

class PidbService(boltUrl:String, boltUser:String, boltPassword:String) extends BoltService(boltUrl, boltUser, boltPassword){


  def getRelativeOrAbsoluteFile(path: String) = {
    Some(new File(path)).map { file =>
      if (file.isAbsolute) {
        file
      }
      else {
        new File(ServletContextUtils.getServletContext.getRealPath(s"/${path}"))
      }
    }.get
  }
}


