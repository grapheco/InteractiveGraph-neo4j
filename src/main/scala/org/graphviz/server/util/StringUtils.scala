package org.graphviz.server.util

import java.util.regex.Pattern

/**
  * Created by bluejoe on 2018/2/7.
  */
object StringUtils {
  def render(expr: String, map: Map[String, Any]):String= {
    var content = expr;
    for ((k, v) <- map) {
      val regex = "\\$\\{" + k + "\\}";
      val pattern = Pattern.compile(regex);
      val matcher = pattern.matcher(content);
      content = matcher.replaceAll(v.toString);
    }

    content;
  }
}
