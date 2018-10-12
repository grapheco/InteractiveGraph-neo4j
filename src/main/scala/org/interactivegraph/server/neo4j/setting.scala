package org.interactivegraph.server.neo4j

import java.util.{Map => JMap}

import org.interactivegraph.server.Setting
import org.interactivegraph.server.util.{Logging, VelocityUtils}
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.types.Node
import org.springframework.beans.factory.InitializingBean

import scala.collection.JavaConversions._
import scala.reflect.ClassTag

class Neo4jSetting extends Setting {
  var _categories: Map[String, String] = _;

  def setCategories(s: String) = _categories = s.split(",").map(_.split(":")).map(x => x(0) -> x(1)).toMap;
  var _regexpSearchFields: Array[String] = Array();
  var _strictSearchFields: Map[String, String] = Map[String, String]();

  def setRegexpSearchFields(s: String) = _regexpSearchFields = s.split(",");

  def setStrictSearchFields(s: String) = _strictSearchFields = s.split(",").map(_.split(":")).map(x => x(0) -> x(1)).toMap;
  var _neo4jConnector: Neo4jConnector = _;

  def setNeo4jConnector(value: Neo4jConnector) = _neo4jConnector = value;
  var _graphMetaDB: GraphMetaDB = null;

  def setGraphMetaDB(value: GraphMetaDB) = _graphMetaDB = value;
}

trait GraphMetaDB {
  def getNodesCount(): Option[Int];

  def getNodeMeta(node: Node): Map[String, _];
}

class Neo4jGraphMetaDBInMemory extends GraphMetaDB with InitializingBean {
  var _neo4jConnector: Neo4jConnector = null;
  var _nodesDegreeMap = collection.mutable.Map[String, Int]();
  var _nodesCount: Option[Int] = None;
  var _graphNodeProperties = collection.mutable.Map[String, String]();

  def setNeo4jConnector(value: Neo4jConnector) = _neo4jConnector = value;

  def setVisNodeProperties(v: JMap[String, String]) = {
    _graphNodeProperties ++= v;
  }

  def updateMeta() = {
    //count numbers of nodes
    _nodesCount = Some(_neo4jConnector.querySingleObject("match (n) return count(n)", _.get(0).asInt()));
    //calculates relations of each node
    _nodesDegreeMap.clear();
    _neo4jConnector.queryObjects("MATCH (c)-[]-() WITH c, count(*) AS degree return id(c), degree",
      (node) => (node.get(0).toString() -> node.get(1).asInt())).foreach(x => _nodesDegreeMap.update(x._1, x._2));
  }

  @throws(classOf[Exception])
  def afterPropertiesSet = updateMeta;

  override def getNodesCount(): Option[Int] = _nodesCount;

  override def getNodeMeta(node: Node): Map[String, _] = {
    val ctx = Map[String, Any]("node" -> node,
      "prop" -> (node.asMap().toMap
        + ("id" -> node.id())
        + ("degree" -> _nodesDegreeMap("" + node.id()))
        + ("labels" -> node.labels.toArray)));

    Map("id" -> node.id(), "categories" -> node.labels.toArray) ++
      (node.labels.headOption.map("group" -> _).toMap) ++
      _graphNodeProperties.filter(!_._1.startsWith("flag:")).map {
        en => {
          (en._1 -> VelocityUtils.parse(en._2, ctx))
        }
      }.filter(_._2 != null).toMap
  }
}

class Neo4jConnector extends Logging {
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
      logger.debug(s"cypher: $queryString");
      session.run(queryString).map(fnMap).toArray
    });
  }

  def executeQuery[T: ClassTag](queryString: String, fn: (StatementResult => T)): T = {
    execute((session: Session) => {
      logger.debug(s"cypher: $queryString");
      val result = session.run(queryString);
      fn(result);
    });
  }

  def querySingleObject[T](queryString: String, fnMap: (Record => T)): T = {
    execute((session: Session) => {
      logger.debug(s"cypher: $queryString");
      fnMap(session.run(queryString).next())
    });
  }
}
