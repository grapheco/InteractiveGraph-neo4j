package org.graphviz.server.commands

import org.neo4j.driver.v1._

import scala.collection.JavaConversions._
import scala.reflect.ClassTag

/**
  * Created by bluejoe on 2018/2/7.
  */
class Neo4jConnector {
  var _url = "";
  var _user = "";
  var _pass = "";

  def setBoltUrl(value: String) = _url = value;

  def setBoltUser(value: String) = _user = value;

  def setBoltPassword(value: String) = _pass = value;

  def execute[T](f: (Session) => T): T = {
    val driver = GraphDatabase.driver(_url, AuthTokens.basic(_user, _pass));
    val session = driver.session(AccessMode.READ);
    val result = f(session);
    session.close();
    driver.close();
    result;
  }

  def queryObjects[T: ClassTag](queryString: String, fnMap: (Record => T)): Array[T] = {
    execute((session: Session) => {
      session.run(queryString).map(fnMap).toArray
    });
  }

  def querySingleObject[T](queryString: String, fnMap: (Record => T)): T = {
    execute((session: Session) => {
      fnMap(session.run(queryString).next())
    });
  }
}
