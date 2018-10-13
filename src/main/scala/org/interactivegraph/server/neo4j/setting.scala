package org.interactivegraph.server.neo4j

import java.util.{Map => JMap}

import org.interactivegraph.server.Setting
import org.interactivegraph.server.util.VelocityUtils
import org.neo4j.driver.v1.types.Node
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._

class Neo4jSetting extends Setting {
  @Autowired
  var _cypherService: CypherService = _;
  var _backendType = "";
  var _categories: Map[String, String] = _;

  def setBackendType(s: String) = _backendType = s;

  def setNodeCategories(s: String) = _categories =
    s.split(",").
      filter(_.contains(":")).
      map(_.split(":")).
      map(x => x(0) -> x(1)).
      toMap;

  var _regexpSearchFields: Array[String] = Array();
  var _strictSearchFields: Map[String, String] = Map[String, String]();

  def setRegexpSearchFields(s: String) = _regexpSearchFields = s.split(",");

  def setStrictSearchFields(s: String) = _strictSearchFields =
    s.split(",").
      filter(_.contains(":")).
      map(_.split(":")).
      map(x => x(0) -> x(1)).
      toMap;

  def setCypherService(value: CypherService) = _cypherService = value;
  var _graphMetaDB: GraphMetaDB = _;

  def setGraphMetaDB(value: GraphMetaDB) = _graphMetaDB = value;
}

trait GraphMetaDB {
  def getNodesCount(): Option[Int];

  def getEdgesCount(): Option[Int];

  def getNodeMeta(node: Node): Map[String, _];
}

class Neo4jGraphMetaDBInMemory extends GraphMetaDB with InitializingBean {
  @Autowired
  var _cypherService: CypherService = _;
  var _nodesDegreeMap = collection.mutable.Map[String, Int]();
  var _nodesCount: Option[Int] = None;
  var _edgesCount: Option[Int] = None;
  var _graphNodeProperties = collection.mutable.Map[String, String]();

  def setVisNodeProperties(v: JMap[String, String]) = {
    _graphNodeProperties ++= v;
  }

  def updateMeta() = {
    //count numbers of nodes
    _nodesCount = Some(_cypherService.querySingleObject("match (n) return count(n)", _.get(0).asInt()));
    //count numbers of nodes
    _edgesCount = Some(_cypherService.querySingleObject("match ()-[p]->() return count(p)", _.get(0).asInt()));

    //calculates relations of each node
    _nodesDegreeMap.clear();
    _cypherService.queryObjects("MATCH (c)-[]-() WITH c, count(*) AS degree return id(c), degree",
      (node) => (node.get(0).toString() -> node.get(1).asInt())).foreach(x => _nodesDegreeMap.update(x._1, x._2));
  }

  def afterPropertiesSet = updateMeta;

  override def getNodesCount(): Option[Int] = _nodesCount;
  override def getEdgesCount(): Option[Int] = _edgesCount;

  override def getNodeMeta(node: Node): Map[String, _] = {
    val ctx = Map[String, Any]("node" -> node,
      "prop" -> (node.asMap().toMap
        + ("id" -> node.id())
        + ("degree" -> _nodesDegreeMap.getOrDefault("" + node.id(), 0))
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