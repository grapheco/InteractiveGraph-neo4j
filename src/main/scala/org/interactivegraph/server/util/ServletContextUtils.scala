package org.interactivegraph.server.util

import javax.servlet.ServletContext

/**
  * Created by bluejoe on 2018/10/13.
  */
object ServletContextUtils {
  private var _servletContext: ServletContext = _;

  def setServletContext(value: ServletContext) = _servletContext = value;

  def getServletContext = _servletContext;
}
