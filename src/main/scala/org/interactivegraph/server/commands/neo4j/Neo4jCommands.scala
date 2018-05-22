package org.interactivegraph.server.commands.neo4j

import java.io.PrintWriter

import org.interactivegraph.server.{JsonOutput, Params}
import org.neo4j.driver.v1.Session
import org.springframework.beans.factory.annotation.Autowired

import scala.collection.JavaConversions._

/**
  * Created by bluejoe on 2018/2/23.
  */

class GetNodesInfo extends JsonOutput with WithNeo4jServer {
  override def execute(params: Params): Map[String, Any] = {
//    val ids = params.getStringArray("nodes").reduce(_ + "," + _);
    val nodeIds = params.getStringArray("nodeIds").reduce(_ + "," + _);
    val query = s"match (n) where id(n) in [$nodeIds] return n";
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

class GetNodesCategories extends JsonOutput with WithNeo4jServer {
      override def execute(params: Params): Map[String, Any] = {
        val query = s"match(n) return distinct labels(n)";
        Map("labels and categories" ->
        _neo4jConnector.queryObjects(query,{
          result =>val label = result.fields().get(0).value().get(0).asString();
            Map[String, Any]("label" -> label);
        }));
    }
}

class GetNeighbours extends  JsonOutput with WithNeo4jServer{
  override def execute(params: Params): Map[String, Any] = {
    val id = params.getStringArray("id").reduce(_ + "," + _);
    val queryNodes = s"match (n)-[r]-(m) where id(n) in [$id] return m";
    val queryRels = s"match(n)-[r]-(m) where id(n) in [$id] return r";
    val neighbourNodes = Map("nodes" -> _neo4jConnector.queryObjects(queryNodes, { record =>
          val node = record.get("m").asNode();
          node.asMap() + ("id" -> node.id());
      }));

    val neighbourRels = Map("realtions" -> _neo4jConnector.queryObjects(queryRels, { record =>
          val rel = record.get("r").asRelationship();
          rel.asMap() + ("id" -> rel.id());
    }));

    var neighbours = neighbourNodes ++ neighbourRels;
    return neighbours;

  }
}

class FindRelations extends JsonOutput with WithNeo4jServer{
  override def execute(params: Params): Map[String, Any] = {
    val startId = params.getInt("startNodeId");
    val endId = params.getInt("endNodeId");
    val limit = params.getInt("limit");

    val queryNodes = s"match p=(n)-[*1..$limit]-(m) where id(n)=$startId and id(m)=$endId return nodes(p)";
    val queryRels = s"match p=(n)-[*1..$limit]-(m) where id(n)=$startId and id(m)=$endId return relationships(p)";
    val nodes = Map("nodes" -> _neo4jConnector.queryObjects(queryNodes,{ record =>
      val node = record.get("nodes(p)");
      Map[String, Any]("node" -> node);
      }));
    val relationships = Map("relationships" -> _neo4jConnector.queryObjects(queryRels,{ record =>
      val relationoship = record.get("relationships(p)");
      Map[String, Any]("relationship" -> relationoship);
    }));
    var result = nodes ++ relationships;
    return result
  }
}

class Search extends  JsonOutput with WithNeo4jServer{
  override def execute(params: Params): Map[String, Any] = {
    val limit = params.getInt("limit");
    val keyword = params.getString("keyword");
    val query = s"match(n) where n.name contains '$keyword' return n limit $limit";
    val nodes = Map("nodes" -> _neo4jConnector.queryObjects(query,{ record =>
      val node = record.get("n");
      Map[String, Any]("node" -> node);
    }));

    return nodes;

  }
}

class FilterNodesByCategories extends JsonOutput with WithNeo4jServer {
  override def execute(params: Params): Map[String, Any] = {
    val category = params.getString("category");
    val nodeIds = params.getStringArray("nodeIds").reduce(_ + "," + _);;
    val showOrNot = params.getInt("showOrNot");
    var query = new String;
    if (showOrNot == 0) {
      query = s"match(n) where id(n) in [$nodeIds] and labels(n)<>[$category] return n";
    }
    if (showOrNot == 1) {
      query = s"match(n) where id(n) in [$nodeIds] and labels(n)=[$category] return n";

    }
    val filteredIds = Map("nodes" -> _neo4jConnector.queryObjects(query, { record =>
      val node = record.get("n").asNode();
      Map[String, Any]("id" -> node.id());
    }))
    return filteredIds;
  }
}











