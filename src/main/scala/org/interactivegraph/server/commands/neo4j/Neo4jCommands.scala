package org.interactivegraph.server.commands.neo4j

import org.interactivegraph.server.{JsonOutput, Params}
import org.neo4j.driver.v1.Session
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._

/**
  * Created by bluejoe on 2018/2/23.
  */

class GetNodesInfo extends JsonOutput with WithNeo4jServer {
  override def execute(params: Params): Map[String, Any] = {
    val ids = params.getStringArray("nodes").reduce(_ + "," + _);
    val query = s"match (n) where id(n) in [$ids] return n";
    Map("infos" ->
      _neo4jConnector.queryObjects(query, {
        record =>
          val node = record.get(0).asNode();
          node.asMap() + ("id" -> node.id());
      }));
  }
}

class LoadGraph extends JsonOutput with WithNeo4jServer {
  @Autowired
  var _graphMetaDB: GraphMetaDB = null;

  override def execute(params: Params): Map[String, Any] = {
    _neo4jConnector.execute { (session: Session) =>
      val nodes = queryNodes(session);
      val edges = queryEdges(session);
      Map[String, Any]("data" -> Map[String, Any]("nodes" -> nodes, "edges" -> edges));
    };
  }

  private def queryEdges(session: Session): Array[Map[String, Any]] = {
    session.run("MATCH p=()-->() RETURN p").map { result =>
      val rel = result.get("p").asPath().relationships().iterator().next();

      val from = rel.startNodeId();
      val to = rel.endNodeId();
      val id = rel.id();
      val label = rel.`type`();
      Map[String, Any]("id" -> id, "label" -> label, "from" -> from, "to" -> to);
    }.toArray
  }

  private def queryNodes(session: Session): Array[Map[String, Any]] = {
    session.run("MATCH (n) RETURN n").map { result =>
      val node = result.get("n").asNode();
      val id = node.id();
      val map = node.asMap();

      val labels = node.labels().toArray;
      val metaMap = collection.mutable.Map[String, Any]();
      val nodes = collection.mutable.Map[String, Any]() ++ map + ("id" -> id) + ("labels" -> labels);
      val meta = _graphMetaDB.getNodeMeta(node);
      meta.getGroupName().foreach { x => metaMap += ("group" -> x); }
      meta.getCaption().foreach { x => nodes += ("label" -> x); }
      meta.getSize().foreach { x => metaMap += ("degree" -> x); }
      meta.getPhotoURL().foreach { x => metaMap += ("image" -> x); }
      meta.getInfo().foreach { x => metaMap += ("info" -> x); }
      //nodes += ("_meta" -> metaMap.toMap);
      nodes ++= metaMap
      nodes.toMap
    }.toArray
  }
}