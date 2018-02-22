package org.interactivegraph.server

import org.apache.log4j.Logger

/**
  * Created by bluejoe on 2018/2/12.
  */
trait Logging {
  val logger = Logger.getLogger(this.getClass);
}
