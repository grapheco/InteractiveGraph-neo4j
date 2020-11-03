import org.grapheco.server.pandadb.PandaDBDatabaseService
import org.grapheco.server.util.VelocityUtils.{PATH, fileSystemTool}
import org.neo4j.driver.internal.value.RemoteBlob

object DriverTest {

  def main(args: Array[String]): Unit = {
    val driver = new PandaDBDatabaseService("bolt://10.0.90.173:7687","","")
    driver.queryObjects("match(n) where n.aminer_id = '562d1ff545cedb3398d5cd54' return n", r =>{
      val node = r.get("n").asNode()
      val blob = node.get("image").asBlob()
      if (blob.isInstanceOf[RemoteBlob]) {
        val b = blob.toBytes()
        fileSystemTool.filesave(b,PATH,"test.jpg")
      }
//      val image = node.get()
    })
  }
}
