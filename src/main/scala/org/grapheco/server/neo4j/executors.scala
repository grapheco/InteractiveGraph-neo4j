package org.grapheco.server.neo4j

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import com.google.gson.JsonObject
import org.grapheco.server.{CommandExecutor, JsonCommandExecutor, Setting}

import org.neo4j.driver.v1.types.{Node, Path, Relationship}
import org.neo4j.driver.v1.{Session, StatementResult}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

trait Neo4jCommandExecutor extends CommandExecutor {
  var _setting: Neo4jSetting = _;

  override def initialize(setting: Setting): Unit = {
    _setting = setting.asInstanceOf[Neo4jSetting];
  }

  def setting() = _setting;

  def wrapNode(node: Node): Map[String, _] = {
    val id = node.id();
    val nodeMap = collection.mutable.Map[String, Any]();
    nodeMap ++= setting()._graphMetaDB.getNodeMeta(node);
    nodeMap.toMap + ("id" -> node.id())
  }

  def wrapRelationship(rel: Relationship): Map[String, _] = {
    val from = rel.startNodeId();
    val to = rel.endNodeId();
    val id = rel.id();
    val label = rel.`type`();
    Map[String, Any]("id" -> id, "label" -> label, "from" -> from, "to" -> to);
  }

  def wrapPath(path: Path): Map[_, _] = {
    Map("nodes" -> path.nodes().map(wrapNode(_)).toArray,
      "edges" -> path.relationships().map(wrapRelationship(_)).toArray
    );
  }
}

class Init extends JsonCommandExecutor with Neo4jCommandExecutor {
  def execute(request: JsonObject): Map[String, _] = {

    Map("product" -> "neo4j",
      "backendType" -> setting()._backendType,
      "categories" -> setting()._categories
    ) ++
      setting()._graphMetaDB.getNodesCount().map("nodesCount" -> _) ++
      setting()._graphMetaDB.getEdgesCount().map("edgesCount" -> _) ++
      Map("autoLayout" -> !(
        setting()._cypherService.querySingleObject("match (n) return n limit 1", rsl=>{
          rsl.get("n").asNode().containsKey("x")
        })
      ))
  }
}

class GetNeighbours extends JsonCommandExecutor with Neo4jCommandExecutor {

  def execute(request: JsonObject): Map[String, _] = {
    val id = request.get("nodeId").getAsString;
    val query1 = s"start n=node($id) match (n)-[p]-(x) return distinct p";
    val query2 = s"start n=node($id) match (n)-[p]-(x) return distinct x";
    Map("neighbourEdges" ->
      setting()._cypherService.queryObjects(query1, {
        record =>
          wrapRelationship(record.get(0).asRelationship());
      }),
      "neighbourNodes" ->
        setting()._cypherService.queryObjects(query2, {
          record =>
            wrapNode(record.get(0).asNode());
        }));
  }
}

class GetNodesInfo extends JsonCommandExecutor with Neo4jCommandExecutor {

  def execute(request: JsonObject): Map[String, _] = {

    val ids = request.getAsJsonArray("nodeIds").map(_.getAsString).reduce(_ + "," + _);
    val query = s"match (n) where id(n) in [$ids] return n";
    Map("infos" ->
      setting()._cypherService.queryObjects(query, {
        record =>
          val node = record.get(0).asNode();
          wrapNode(node).getOrElse("info", null);
      }));
  }
}

//TODO dynamic
class FilterNodesByCategory extends JsonCommandExecutor with Neo4jCommandExecutor {

  def execute(request: JsonObject): Map[String, _] = {
    val category = request.get("catagory").getAsString;
    val ids = request.getAsJsonArray("nodeIds").map(_.getAsString).reduce(_ + "," + _);
    val query = s"match (n:$category) where id(n) in [$ids] return n limit 10000";
    val filteredNodeIds = setting()._cypherService.queryObjects(query, {
      record =>
        val node = record.get(0).asNode();
        node.id();
    }).toSeq.toArray;

    Map("filteredNodeIds" -> filteredNodeIds);
  }
}

class Search extends JsonCommandExecutor with Neo4jCommandExecutor {

  def execute(request: JsonObject): Map[String, _] = {
    val jexpr = request.get("expr");
    val limit = request.getAsJsonPrimitive("limit").getAsNumber;
    val nodes =
      if (jexpr.isJsonArray) {
        strictSearch(jexpr.getAsJsonArray.map(_.getAsJsonObject).toArray, limit);
      }
      else {
        regexpSearch(jexpr.getAsString, limit);
      }

    Map("nodes" -> nodes);
  }

  def getNodePropertyName(searchField: String): String = {
    val ssf = setting()._strictSearchFields;
    ssf.getOrDefault(searchField, searchField);
  }

  def strictSearch(filters: Array[JsonObject], limit: Number): Array[_] = {
    filters.map { filter =>
      filter.entrySet().map { en =>
        val key = getNodePropertyName(en.getKey);
        val value = en.getValue.getAsString;
        s"n.$key='$value'"
      }.reduce(_ + " and " + _);
    }.map { filter =>
      val query = s"match (n) where ${filter} return n limit ${limit}";
      setting()._cypherService.queryObjects(query, {
        record =>
          val node = record.get(0).asNode();
          wrapNode(node);
      })
    }.reduce { (x, y) =>
      x ++ y
    };
  }

  def regexpSearch(exp: String, limit: Number): Array[_] = {
    var expr: String = exp;
    var label: String = null;
    if(expr.contains(':')&&expr.split(':').length==2) {
      label = expr.split(':')(0);
      expr = expr.split(':')(1);
//      if(label.length==0||expr.length==0){return null}
    }else{return Array.emptyIntArray}
//    val filter = setting()._regexpSearchFields.map(x => s"n.${x} starts with '${expr}'").reduce(_ + " and " + _);
    val prop = setting()._regexpSearchFields.getOrDefault(label, setting()._regexpSearchFields.getOrDefault("*", "name"))
    val filter = s"n.${prop} starts with '${expr}'"
    val query = s"match (n${ if(label!=null && !label.equals("")){":"+label}}) where ${filter} return n limit ${limit}";

    setting()._cypherService.queryObjects(query, {
      record =>
        val node = record.get(0).asNode();
        wrapNode(node);
    });
  }
}

class LoadGraph extends JsonCommandExecutor with Neo4jCommandExecutor {

  final val WINDOWWIDTH = 2000;
  final val WINDOWHEIGHT = 1200;

  def execute(request: JsonObject): Map[String, _] = {
    val cypher = setting()._loadCypher;
    if (cypher!=null&&cypher.length>0){
      setting()._cypherService.execute{ session:Session=>
        val nodes = session.run(cypher).map{ result =>
          val n = result.get("p").asPath().nodes().iterator().next();
          wrapNode(n);
        }.toArray
        val edges = session.run(cypher).map{ result =>
          val rel = result.get("p").asPath().relationships().iterator().next();
          wrapRelationship(rel);
        }.toArray
        Map[String, Any]("nodes" -> nodes, "edges" -> edges, "width" -> 0, "height" -> 0);
      };
    }else {
    if (request.get("dynamic").getAsBoolean) {
      // dynamic mode
      setting()._cypherService.aliveExecute { (session: Session) =>
        val scale = if(request.has("scale"))
          request.get("scale").getAsDouble
        else 1
        val nodes = queryBatchNodes(session, request);
        val edges = queryBatchEdges(session, request);
        Map[String, Any]("nodes" -> nodes, "edges" -> edges, "width" -> (2 * this.WINDOWWIDTH/scale).toInt, "height" -> (2 * this.WINDOWHEIGHT/scale).toInt);
      }
    } else {
      // Read all at once


        setting()._cypherService.execute { (session: Session) =>
          val nodes = queryNodes(session);
          val edges = queryEdges(session);
          Map[String, Any]("nodes" -> nodes, "edges" -> edges, "width" -> 0, "height" -> 0);
        };
      }
    }
  }

  private def queryEdges(session: Session): Array[Map[String, Any]] = {
    session.run("MATCH p=()-->() RETURN p limit 1000").map { result =>
      val rel = result.get("p").asPath().relationships().iterator().next();
      wrapRelationship(rel);
    }.toArray
  }

  private def queryNodes(session: Session): Array[Map[String, Any]] = {
    session.run("MATCH (n) RETURN n limit 1000").map { result =>
      val node = result.get("n").asNode();
      wrapNode(node);
    }.toArray
  }

  private def queryBatchNodes(session: Session, request: JsonObject): Array[Map[String, Any]] = {

    val scale = if(request.has("scale"))
                request.get("scale").getAsDouble
                else 1

    val t = ( if(request.has("centerPointY"))
            request.get("centerPointY").getAsDouble.toInt
            else 0 ) + (this.WINDOWHEIGHT/scale).toInt

    val b = ( if(request.has("centerPointY"))
            request.get("centerPointY").getAsDouble.toInt
            else 0 ) - (this.WINDOWHEIGHT/scale).toInt

    val l = ( if(request.has("centerPointX"))
            request.get("centerPointX").getAsDouble.toInt
            else 0 ) - (this.WINDOWWIDTH/scale).toInt

    val r = ( if(request.has("centerPointX"))
            request.get("centerPointX").getAsDouble.toInt
            else 0 ) + (this.WINDOWWIDTH/scale).toInt

    session.run(
      s"MATCH (n) WHERE n.x > $l AND n.x < $r AND n.y > $b AND n.y < $t RETURN n LIMIT 1000")
      .map { result =>
        val node = result.get("n").asNode();
        wrapNode(node);
    }.toArray
  }

  private def queryBatchEdges(session: Session, request: JsonObject): Array[Map[String, _]] = {
    val scale = if(request.has("scale"))
      request.get("scale").getAsDouble
    else 1

    val t = ( if(request.has("centerPointY"))
      request.get("centerPointY").getAsDouble.toInt
    else 0 ) + (this.WINDOWHEIGHT/scale).toInt

    val b = ( if(request.has("centerPointY"))
      request.get("centerPointY").getAsDouble.toInt
    else 0 ) - (this.WINDOWHEIGHT/scale).toInt

    val l = ( if(request.has("centerPointX"))
      request.get("centerPointX").getAsDouble.toInt
    else 0 ) - (this.WINDOWWIDTH/scale).toInt

    val r = ( if(request.has("centerPointX"))
      request.get("centerPointX").getAsDouble.toInt
    else 0 ) + (this.WINDOWWIDTH/scale).toInt

    session.run(
      s"MATCH (n)-[r]-() WHERE n.x > $l AND n.x < $r AND n.y > $b AND n.y < $t RETURN r LIMIT 1000")
      .map { result =>
        val rel = result.get("r").asRelationship();
        wrapRelationship(rel);
    }.toArray
  }

}

object FindRelationsTaskManager {
  val seq = new AtomicInteger(1224);
  val allTasks = mutable.Map[String, FindRelationsTask]();


  class FindRelationsTask(executor: Neo4jCommandExecutor, query: String) {
    var _isCompleted = false;
    val lock = new CountDownLatch(1);
    val taskId = seq.incrementAndGet();
    allTasks("" + taskId) = this;
    var flagStop = false;
    val paths = ArrayBuffer[Map[_, _]]();
    val thread = new Thread(new Runnable() {
      override def run(): Unit = {
        executor.setting()._cypherService.executeQuery(query, {
          result: StatementResult =>
            lock.countDown();
            while (result.hasNext && !flagStop) {
              val record = result.next();
              val path = executor.wrapPath(record.get(0).asPath());
              paths.append(path);
            }

            _isCompleted = true;
        });
      }
    });

    thread.start();

    def isCompleted() = _isCompleted;

    def readMore(limit: Int): (Array[_], Boolean) = {
      val token = paths.take(limit);
      paths.remove(0, token.size);
      (token.toArray, !paths.isEmpty);
    }

    def waitForExecution(): Unit = {
      lock.await();
    }

    def stop(): Unit = {
      flagStop = true;
      thread.interrupt();
    }
  }

  def createTask(executor: Neo4jCommandExecutor, query: String): FindRelationsTask = {
    new FindRelationsTask(executor, query);
  }

  def getTask(taskId: String) = allTasks(taskId);
}

class FindRelations extends JsonCommandExecutor with Neo4jCommandExecutor {
  def execute(request: JsonObject): Map[String, _] = {
    val startNodeId = request.get("startNodeId").getAsString;
    val endNodeId = request.get("endNodeId").getAsString;
    val maxDepth = request.get("maxDepth").getAsNumber;

    val query = s"match p=(m)-[*1..$maxDepth]-(n) where id(m)=${startNodeId} and id(n) = ${endNodeId} RETURN p";
    val task = FindRelationsTaskManager.createTask(this, query);
    Thread.sleep(1);
    task.waitForExecution();

    Map("queryId" -> task.taskId);
  }
}

class GetMoreRelations extends JsonCommandExecutor with Neo4jCommandExecutor {
  def execute(request: JsonObject): Map[String, _] = {
    val queryId = request.get("queryId").getAsString;

    val task = FindRelationsTaskManager.getTask(queryId);
    val (paths, hasMore) = task.readMore(10);

    Map("completed" -> task.isCompleted,
      "queryId" -> task.taskId,
      "paths" -> paths);
  }
}

class StopFindRelations extends JsonCommandExecutor with Neo4jCommandExecutor {
  def execute(request: JsonObject): Map[String, _] = {
    val queryId = request.get("queryId").getAsString;

    val task = FindRelationsTaskManager.getTask(queryId);
    task.stop();

    Map(
      "queryId" -> task.taskId,
      "stopped" -> true
    );
  }
}

class searchImage extends JsonCommandExecutor with Neo4jCommandExecutor {
  override def execute(request: JsonObject): Map[String, _] = {
    println(request)
    return null
  }
}
