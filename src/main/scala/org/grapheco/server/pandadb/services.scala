package org.grapheco.server.pandadb

import java.io.{File, FileInputStream}

import cn.pandadb.connector.BoltService
import org.apache.commons.io.{FileUtils, IOUtils}
import org.grapheco.server.util.{JsonUtils, Logging, ServletContextUtils}
import org.neo4j.driver.{Session, StatementResult}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.{DisposableBean, InitializingBean}
import cn.pandadb.database.PandaDB.logger

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Created by huchuan on 2019/4/10.
  */

//
class PandaDBDatabaseService(url: String, user: String = "", pass: String = "") extends BoltService(url, user, pass) with InitializingBean with DisposableBean {

  override def executeQuery[T](queryString: String, fn: StatementResult => T): T = {
    val logger = cn.pandadb.database.PandaDB.logger
    if (!queryString.contains("<http")){
      logger.info(queryString)
    }
    super.executeQuery(queryString, fn)
  }


  def afterPropertiesSet(): Unit = {

  }

  override def destroy(): Unit = {

  }
}
