package org.grapheco.server.hbase

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hbase.{HBaseConfiguration, HColumnDescriptor, HTableDescriptor, TableName}
import org.apache.hadoop.hbase.client.{Connection, ConnectionFactory, Table}
import org.apache.hadoop.hbase.util.Bytes

object HBaseService {

  private var _connection:Connection = null
  private var conf:Configuration = null
  def getConf() = {
    if (conf == null){
      conf = HBaseConfiguration.create()
      //      conf.set("hbase.zookeeper.quorum","159.226.193.183")
      conf.addResource(new Path(System.getenv("HBASE_HOME")+"/conf", "hbase-site.xml"))
      conf.addResource(new Path(System.getenv("HADOOP_HOME")+"/etc/hadoop", "core-site.xml"))
    }
    conf
  }

  def connection:Connection = {
    if (_connection==null) {
      _connection = ConnectionFactory.createConnection(getConf())
    }
    _connection
  }

  def getTable(name:String, con: Connection):Table = {
    val admin = con.getAdmin()
    if (!admin.tableExists(TableName.valueOf(name))) {
      val tableDescriptor = new HTableDescriptor(TableName.valueOf(name))
      tableDescriptor.addFamily(new HColumnDescriptor(Bytes.toBytes("coordinate")))
      admin.createTable(tableDescriptor)
    }
    con.getTable(TableName.valueOf(name))
  }

  def newConnection:Connection = ConnectionFactory.createConnection(getConf())

}

