package org.grapheco.server.neo4j

import java.util.{Map => JMap}

import org.grapheco.server.Setting
import org.grapheco.server.util.{Edge, Layout, VelocityUtils}
import org.neo4j.driver.v1.types.Node
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._
import org.grapheco.server.util.Graph

class Neo4jSetting extends Setting {
  @Autowired
  var _cypherService: CypherService = _;
  var _backendType = "";
  var _categories: Map[String, String] = _;
  var _loadCypher: String = _;


  def setBackendType(s: String) = _backendType = s;

  def setNodeCategories(s: String) = _categories =
    s.split(",").
      filter(_.contains(":")).
      map(_.split(":")).
      map(x => x(0) -> x(1)).
      toMap;

  var _regexpSearchFields: Map[String, String] = Map[String, String]();
  var _strictSearchFields: Map[String, String] = Map[String, String]();

  def setRegexpSearchFields(s: String) = _regexpSearchFields =
    s.split(",")
      .filter(_.contains("."))
      .map(_.split("\\."))
      .map(x => x(0) -> x(1))
      .toMap

  def setStrictSearchFields(s: String) = _strictSearchFields =
    s.split(",").
      filter(_.contains(":")).
      map(_.split(":")).
      map(x => x(0) -> x(1)).
      toMap;

  def setCypherService(value: CypherService) = _cypherService = value;
  var _graphMetaDB: GraphMetaDB = _;

  def setGraphMetaDB(value: GraphMetaDB) = _graphMetaDB = value;

  def setLoadCypher(s:String) = _loadCypher = s;


  def setLayout(value: Boolean) = {
    if (value) {
      Layout.layout(new org.grapheco.server.util.Graph(
        _cypherService.queryObjects("match (n) return id(n),labels(n)",
          r => new org.grapheco.server.util.Node(r.get(0).asInt(), r.get(1).asList().get(0).toString)).toList,
        _cypherService.queryObjects("match (n)-[r]->(n2) return id(n), id(n2)",
          r => new Edge(r.get(0).asInt(), r.get(1).asInt())).toList
      ), 100)._nodes.foreach(node =>
        _cypherService.aliveExecute(s =>
          s.run("match (n:" + node._label + ") where id(n) = " + node._id + " set n.x = " + node._x + " , n.y = " + node._y)
        )
      )
      for (c<-_categories){
        _cypherService.execute(s=>s.run(s"CREATE INDEX ON :${c._1}(x)"))
        _cypherService.execute(s=>s.run(s"CREATE INDEX ON :${c._1}(y)"))
      }

    }
  }
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
//    _cypherService.queryObjects("MATCH (c)-[]-() WITH c, count(*) AS degree return id(c), degree",
//      (node) => (node.get(0).toString() -> node.get(1).asInt())).foreach(x => _nodesDegreeMap.update(x._1, x._2));
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
          var exp = en._2.split(",")
            .filter(s => !s.contains(":")||node.labels.toArray.contains(s.split(":")(0)))
          if(exp.length>1) exp = exp.filter(s=>s.contains(":")).map(s=>s.split(":")(1))
          if(en._1 == "info") exp = Array(en._2)
          (en._1 -> VelocityUtils.parse(exp.last, ctx))

        }
      }.filter(_._2 != null).toMap
  }
}