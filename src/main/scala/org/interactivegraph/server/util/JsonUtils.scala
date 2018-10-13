package org.interactivegraph.server.util

import com.google.gson._

import scala.collection.Map

/**
  * Created by bluejoe on 2018/10/8.
  */
object JsonUtils {
  def getPrimitiveValue(value: JsonPrimitive): Any = {
    (value.isBoolean, value.isNumber, value.isString) match {
      case (true, false, false) => value.getAsBoolean;
      case (false, true, false) => Some(value.getAsNumber).map(num =>
        if (num.toString.contains(".")) {
          num.doubleValue()
        }
        else {
          num.intValue()
        }
      ).get;
      case (false, false, true) => value.getAsString;
    }
  }

  val gson = new GsonBuilder()
    .setPrettyPrinting()
    .create();

  def parse(json: String): JsonElement = {
    new JsonParser().parse(json);
  }

  def stringify(e: JsonElement): String = {
    gson.toJson(e);
  }

  def stringify(e: Map[String, _]): String = {
    gson.toJson(asJsonObject(e));
  }

  def asJsonArray(arr: Array[_]) = {
    val ja = new JsonArray();
    arr.foreach(x => ja.add(asJsonElement(x)));
    ja;
  }

  def asJsonElement(v: Any): JsonElement = {
    if (v.isInstanceOf[Map[_, _]]) {
      asJsonObject(v.asInstanceOf[Map[String, _]]);
    }
    else if (v.isInstanceOf[Array[_]]) {
      asJsonArray(v.asInstanceOf[Array[_]]);
    }
    else {
      v match {
        case x: String =>
          new JsonPrimitive(x);
        case x: Number =>
          new JsonPrimitive(x);
        case x: Boolean =>
          new JsonPrimitive(x);
      }
    }
  }

  def asJsonObject(map: Map[String, _]) = {
    val jo = new JsonObject();
    map.foreach(en => {
      jo.add(en._1, asJsonElement(en._2));
    })
    jo;
  }
}