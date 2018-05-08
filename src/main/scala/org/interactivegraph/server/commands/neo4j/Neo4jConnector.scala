package org.interactivegraph.server.commands.neo4j

import org.interactivegraph.server.util.VelocityUtils
import org.neo4j.driver.v1._
import org.neo4j.driver.v1.types.Node
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._
import scala.reflect.ClassTag

trait GraphMetaDB {
  def getNodesCount(): Option[Int];

  def getNodeMeta(node: Node): NodeMeta;
}

trait NodeMeta {
  def getCaption(): Option[String];

  def getInfo(): Option[String];

  def getTitle(): Option[String];

  def getSize(): Option[Int];

  def getGroupName(): Option[String];

  def getXY(): Option[(Double, Double)];

  def getColor(): Option[String];

  def getPhotoURL(): Option[String];
}

class Neo4jGraphMetaDBInMemory extends GraphMetaDB with InitializingBean {
  @Autowired
  var _neo4jConnector: Neo4jConnector = null;
  var _nodesDegreeMap = collection.mutable.Map[String, Int]();
  var _photoURLExpr: Option[String] = None;
  var _infoExpr: Option[String] = None;
  var _localPhotoFilePathExpr: Option[String] = None;
  var _nodesCount: Option[Int] = None;
  var _captionExpr: Option[String] = None;
  var _titleExpr: Option[String] = None;

  def setCaptionExpr(value: String) = _captionExpr = Some(value);

  def setInfoExpr(value: String) = _infoExpr = Some(value);

  def setTitleExpr(value: String) = _titleExpr = Some(value);

  def setPhotoURLExpr(value: String) = _photoURLExpr = Some(value);

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

  override def getNodeMeta(node: Node): NodeMeta = {
    val ctx = Map[String, Any]("node" -> node,
      "prop" -> (node.asMap().toMap + ("_id" -> node.id())));

    new NodeMeta() {
      override def getCaption(): Option[String] = _captionExpr.map(VelocityUtils.parse(_, ctx));

      override def getTitle(): Option[String] = _titleExpr.map(VelocityUtils.parse(_, ctx));

      override def getSize(): Option[Int] = _nodesDegreeMap.get(node.id().toString);

      override def getGroupName(): Option[String] = node.labels().headOption;

      override def getXY(): Option[(Double, Double)] = None;

      override def getColor(): Option[String] = None;

      override def getPhotoURL(): Option[String] = _photoURLExpr.map(x => VelocityUtils.parse(x.trim(), ctx).trim);

      override def getInfo(): Option[String] = _infoExpr.map(x => VelocityUtils.parse(x.trim(), ctx).trim);
    };
  }
}

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

trait WithNeo4jServer {
  @Autowired
  var _neo4jConnector: Neo4jConnector = null;
}
