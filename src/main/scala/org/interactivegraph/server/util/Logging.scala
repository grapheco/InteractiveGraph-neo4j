package org.interactivegraph.server.util

/**
  * Created by bluejoe on 2018/10/10.
  */
import org.apache.log4j.Logger

trait Logging {
  protected lazy val logger = Logger.getLogger(this.getClass);
}