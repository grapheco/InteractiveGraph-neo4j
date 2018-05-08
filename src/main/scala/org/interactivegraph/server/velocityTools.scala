package org.interactivegraph.server

import java.io.File

import org.apache.velocity.tools.config.DefaultKey

/**
  * Created by bluejoe on 2018/3/1.
  */
@DefaultKey("fileTool")
class FileSystemTool {
  def exists(path: String) = new File(path).exists();
}
