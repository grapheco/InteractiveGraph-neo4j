import org.grapheco.server.hbase.TileLoader
import org.junit.jupiter.api.Test

/**
 * @ClassName TileLoadTest
 * @Description TODO
 * @Author huchuan
 * @Date 2020/11/10
 * @Version 0.1
 */
@Test
class TileLoadTest {

  @Test
  def loadFromRedis(): Unit ={
    TileLoader.loadTileFromRedis(2,1,1).foreach(println(_))
  }
}
