package org.interactivegraph.server.util

import java.lang.reflect.Type

import com.google.gson.{GsonBuilder, JsonElement, JsonSerializationContext, JsonSerializer}

import scala.collection.{JavaConversions, Map}

/**
  * Created by bluejoe on 2018/2/9.
  */
object JsonUtils {
  val gson = new GsonBuilder().
    registerTypeHierarchyAdapter(classOf[Map[_, _]], new ScalaMapSerializer()).
    create();

  class ScalaMapSerializer extends JsonSerializer[Map[_, _]] {
    def serialize(src: Map[_, _], typeOfSrc: Type, context: JsonSerializationContext): JsonElement = {
      gson.toJsonTree(JavaConversions.mapAsJavaMap(src));
    }
  }

  def toJSONString(o: Any): String = {
    gson.toJson(o);
  }
}
