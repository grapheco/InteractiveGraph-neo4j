package org.interactivegraph.server.neo4j

import java.io.{File, FileInputStream}

import org.apache.commons.io.{FileUtils, IOUtils}
import org.interactivegraph.server.util.{JsonUtils, Logging, ServletContextUtils}
import org.neo4j.driver.v1._
import org.neo4j.graphdb.factory.{GraphDatabaseFactory, GraphDatabaseSettings}
import org.neo4j.graphdb.{GraphDatabaseService, Label, RelationshipType}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.{DisposableBean, InitializingBean}

import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.reflect.ClassTag

/**
  * Created by bluejoe on 2018/10/12.
  */
trait CypherService extends Logging {
  def queryObjects[T: ClassTag](queryString: String, fnMap: (Record => T)): Array[T];

  def execute[T](f: (Session) => T): T;

  def executeQuery[T](queryString: String, fn: (StatementResult => T)): T;

  final def querySingleObject[T](queryString: String, fnMap: (Record => T)): T = {
    executeQuery(queryString, (rs: StatementResult) => {
      fnMap(rs.next());
    });
  }
}

class BoltService extends Logging with CypherService {
  var _url = "";
  var _user = "";
  var _pass = "";

  def setBoltUrl(value: String) = _url = value;

  def setBoltUser(value: String) = _user = value;

  def setBoltPassword(value: String) = _pass = value;

  override def execute[T](f: (Session) => T): T = {
    val driver = GraphDatabase.driver(_url, AuthTokens.basic(_user, _pass));
    val session = driver.session(AccessMode.READ);
    val result = f(session);
    session.close();
    driver.close();
    result;
  }

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

  override def queryObjects[T: ClassTag](queryString: String, fnMap: (Record => T)): Array[T] = {
    execute((session: Session) => {
      logger.debug(s"cypher: $queryString");
      session.run(queryString).map(fnMap).toArray
    });
  }

  override def executeQuery[T](queryString: String, fn: (StatementResult => T)): T = {
    execute((session: Session) => {
      logger.debug(s"cypher: $queryString");
      val result = session.run(queryString);
      fn(result);
    });
  }
}

class Neo4jDatabaseService extends BoltService with InitializingBean with DisposableBean {
  var _dataDir = "";
  var _boltPort = 7687;
  var _db: GraphDatabaseService = _;

  def setBoltPort(port: Int) = _boltPort = port;

  def setDataDir(value: String) = _dataDir = value;

  protected def openDatabase(dbFile: File): Unit = {
    val bolt = GraphDatabaseSettings.boltConnector("0");
    super.setBoltUrl(s"bolt://localhost:${_boltPort}");

    _db = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dbFile).setConfig(bolt.`type`, "BOLT")
      .setConfig(bolt.enabled, "true")
      .setConfig(bolt.address, s"localhost:${_boltPort}")
      .newGraphDatabase();

    logger.info(s"neo4j database loaded: ${dbFile}");
  }

  def afterPropertiesSet(): Unit = {
    openDatabase(getRelativeOrAbsoluteFile(_dataDir));
  }

  override def destroy(): Unit = {
    _db.shutdown();
  }
}

class GsonService extends Neo4jDatabaseService with InitializingBean with DisposableBean {
  @Autowired
  var _setting: Neo4jSetting = _;
  var _gsonPath: String = _;
  var _dbFile: File = _;
  var _tempDir: String = _;

  def setGsonPath(value: String) = _gsonPath = value;

  def setTempDir(value: String) = _tempDir = value;

  override def afterPropertiesSet(): Unit = {
    val gsonFile = getRelativeOrAbsoluteFile(_gsonPath);
    _dbFile = new File(_tempDir, gsonFile.getName);
    if (_dbFile.exists()) {
      FileUtils.deleteDirectory(_dbFile);
    }
    else {
      _dbFile.mkdir();
    }

    super.openDatabase(_dbFile);
    loadGson(gsonFile);
  }

  def loadGson(gsonFile: File): Unit = {
    val root = JsonUtils.parse(IOUtils.toString(new FileInputStream(gsonFile)));
    val tx = _db.beginTx();
    val nodeIdMapGson2Neo4j = mutable.Map[Long, Long]();

    //create nodes
    root.getAsJsonObject.getAsJsonObject("data").getAsJsonArray("nodes").map(_.getAsJsonObject).foreach { jsonNode =>
      val node = _db.createNode();

      //auto generated node id
      nodeIdMapGson2Neo4j(jsonNode.getAsJsonPrimitive("id").getAsLong) = node.getId;
      jsonNode.getAsJsonArray("categories").
        map(_.getAsJsonPrimitive.getAsString).
        map(Label.label(_)).
        foreach(node.addLabel(_));
      jsonNode.entrySet().filter(en =>
        !"categories".equals(en.getKey)
          && !"id".equals(en.getKey)
      ).map { en =>
        node.setProperty(en.getKey,
          JsonUtils.getPrimitiveValue(en.getValue.getAsJsonPrimitive));
      }
    }

    //create edges
    root.getAsJsonObject.getAsJsonObject("data").getAsJsonArray("edges").map(_.getAsJsonObject).foreach { jsonEdge =>
      val from = jsonEdge.getAsJsonPrimitive("from").getAsLong;
      val to = jsonEdge.getAsJsonPrimitive("to").getAsLong;
      val typeName = jsonEdge.getAsJsonPrimitive("name").getAsString;

      _db.getNodeById(nodeIdMapGson2Neo4j(from)).
        createRelationshipTo(_db.getNodeById(nodeIdMapGson2Neo4j(to)),
          RelationshipType.withName(typeName));
    }

    tx.success();
    tx.close();

    logger.info(s"loaded graph data from: ${_gsonPath}");
  }

  override def destroy(): Unit = {
    super.destroy();
    FileUtils.deleteDirectory(_dbFile);
  }
}
