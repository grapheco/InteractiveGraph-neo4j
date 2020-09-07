import java.io.File
import java.util.function.Consumer

import com.google.gson.JsonElement
import org.apache.commons.io.FileUtils
import org.grapheco.server.util.{Edge, Graph, JsonUtils, Layout, Node}


object LayoutTest {
  def main(args: Array[String]): Unit = {

    val data = JsonUtils.parse(
      FileUtils.readFileToString(new File("/Users/huchuan/Documents/InteractiveGraph-neo4j/src/test/test.json"), "UTF-8")).getAsJsonObject.get("data").getAsJsonObject
    val node = data.get("nodes").getAsJsonArray
    val edge = data.get("edges").getAsJsonArray
    var nodes: List[Node] = List()
    var edges: List[Edge] = List()
    val node_it = node.iterator()
    val edge_it = edge.iterator()
    var nodes_set: Set[Int] = Set()
    while (node_it.hasNext){
      val n = node_it.next().getAsJsonObject
      nodes = nodes :+ new Node(n.get("id").getAsInt, "")
      nodes_set = nodes_set.+(n.get("id").getAsInt)
    }
    while (edge_it.hasNext){
      val e = edge_it.next().getAsJsonObject
      if (nodes_set.contains(e.get("from").getAsInt)&&nodes_set.contains(e.get("to").getAsInt)) {
        edges = edges :+ new Edge(e.get("from").getAsInt, e.get("to").getAsInt)
      }
    }
    print(nodes_set.size)
    val time1=System.currentTimeMillis()
    Layout.layout(new Graph(nodes, edges), 100)
    val time2=System.currentTimeMillis()
    print(time2 - time1)
  }
}
