import java.lang.reflect.Type
import java.util.{Map => JMap}

import com.google.gson.{GsonBuilder, JsonElement, JsonSerializationContext, JsonSerializer}
import org.interactivegraph.server.util.JsonUtils

import scala.collection.JavaConversions

/**
  * Created by bluejoe on 2018/2/8.
  */
object MapTest {
  def main(args: Array[String]): Unit = {
    val map = Map("a" -> 1, "b" -> Map("x" -> 100, "y" -> 200));
    //println(map2JMap(map));
        println(JsonUtils.map2JSON(map));
  }
}
