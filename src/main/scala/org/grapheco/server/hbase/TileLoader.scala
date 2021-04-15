package org.grapheco.server.hbase

import java.util

import org.apache.hadoop.hbase.TableName
import org.apache.hadoop.hbase.client.Scan
import org.apache.hadoop.hbase.util.Bytes
import com.redis.serialization.Parse.Implicits._
/**
 * @ClassName LayoutLoader
 * @Description TODO
 * @Author huchuan
 * @Date 2020/11/4
 * @Version 0.1
 */
object TileLoader {

  def loadTile(tableName: String, level:Int, column: Int, row: Int) ={
    val start = rowKey(0, level.toByte, row, column, Math.pow(2, level).toInt)
    val end   = rowKey(-1, level.toByte, row, column, Math.pow(2, level).toInt)
    val table = HBaseService.connection.getTable(TableName.valueOf(tableName))
    val s = new Scan(start, end)
    val rs = table.getScanner(s).iterator()

    val res = new util.ArrayList[(Long, Int, Int)]()
    while (rs.hasNext) {
      val r = rs.next()
      val id = Bytes.toLong(r.getRow.takeRight(8))
      val x = Bytes.toInt(r.getValue(Bytes.toBytes("coordinate"), Bytes.toBytes("x")))
      val y = Bytes.toInt(r.getValue(Bytes.toBytes("coordinate"), Bytes.toBytes("y")))
      res.add((id,x,y))
    }
    table.close()
    res
  }

  def loadTileFromRedis(level: Int, column: Int, row: Int) = {
    RedisService.execute{ rc =>
      rc.lrange[Array[Byte]](rdsKey(level.toByte,row,column,Math.pow(2, level).toInt),0,-1)
        .get
        .map(v => rdsValueParse(v.get))
        .map(v => (v._1,(v._2, v._3, v._4)))
    }
  }

  def rowKey(id: Long, level: Byte, row: Int, column: Int, column_num: Int): Array[Byte] ={
    val tile_id:Long = column + row.toLong * column_num
    println(s"row:${row}, column:${column}, tile_id:${tile_id}")
    Array[Byte](level) ++
      Bytes.toBytes(tile_id).takeRight(7)++
      Bytes.toBytes(id)
  }

  def rdsKey(level: Byte, row: Int, column: Int, column_num: Int): Array[Byte] ={
    val tile_id:Long = column + row.toLong * column_num
    Array[Byte](level) ++
      Bytes.toBytes(tile_id).takeRight(7)
  }

  def rdsValue(id: Long, x: Int, y: Int, w: Int): Array[Byte] = {
    Bytes.toBytes(id) ++ Bytes.toBytes(x) ++ Bytes.toBytes(y) ++ Bytes.toBytes(w)
  }

  def rdsValueParse(arr: Array[Byte]): (Long, Int, Int, Int) = {
    require(arr.length==20)
    (Bytes.toLong(arr.take(8)),Bytes.toInt(arr.takeRight(12).take(4)),
      Bytes.toInt(arr.takeRight(8).take(4)),Bytes.toInt(arr.takeRight(4)))
  }

  def node2edge(node:(Long, (Int, Int, Int))) ={
    (node._1, Bytes.toLong(Bytes.toBytes(node._2._1) ++ Bytes.toBytes(node._2._2)), -node._2._3)
  }
}
