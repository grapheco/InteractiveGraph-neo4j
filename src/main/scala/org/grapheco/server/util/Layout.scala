package org.grapheco.server.util

import scala.collection.mutable
import scala.math.sqrt
import scala.math.random
import scala.math.floor

class Node(id:Int, label:String){
  val _id = id
  val _label = label
  var _x:Double = 0
  var _y:Double = 0
}

class Edge(from:Int, to:Int){
  val _from = from
  val _to   = to
  var _fromNode:Node = null;
  var _toNode:Node = null;
}

class Graph(nodes: List[Node], edges: List[Edge]){
  val _nodes = nodes
  val _edges = edges
  val _id2node:Map[Int, Node] = nodes.map(node=>(node._id->node)).toMap
  _edges.foreach(edge => {
    edge._fromNode = _id2node.apply(edge._from)
    edge._toNode   = _id2node.apply(edge._to)
  })

}

object Layout {

  val L_BASE = 36 * 5
  val W_BASE = 64 * 5
  var ejectfactor = 3
  val condensefactor = 1
  val near_ejectfactor = 5

  var sum = 0

  def layout(graph: Graph, _step: Int): Graph = {

    val nodes = graph._nodes
    val edges = graph._edges
    // init setting
    val initSize = 3 * sqrt(nodes.size).round
    val area = L_BASE * W_BASE * nodes.size

    var xt = (W_BASE * sqrt(nodes.size) * 0.1).round.toInt
    var yt = (L_BASE * sqrt(nodes.size) * 0.1).round.toInt
    val k = sqrt(area / nodes.size.toDouble).round.toInt

    var diffx = .0
    var diffy = .0
    var diff = .0
    var dispx:mutable.Map[Int,Double] = mutable.Map[Int,Double]()
    var dispy:mutable.Map[Int,Double] = mutable.Map[Int,Double]()

    // random init
    println("layout: init")
    val start_x = .0
    val start_y = .0
    for (node <- nodes) {
      node._x = start_x + initSize * (random - .5)
      node._y = start_y + initSize * (random - .5)
    }

    var step:Int = _step
    // update position
    println("layout: start")
    while ( {
      step > 0 && xt > 0 && yt > 0
    }) {
      println(s"layout: unfinished step: ${step}/${_step}")
      // Repulsion
      for (node <- nodes) {
        dispx += (node._id -> 0.0)
        dispy += (node._id -> 0.0)
        for (node2 <- nodes){
          if (node2 != node) {
            diffx = node._x - node2._x
            diffy = node._y - node2._y
            diff = sqrt(diffx * diffx + diffy * diffy)
            ejectfactor = if (diff < 30) near_ejectfactor else ejectfactor;
            dispx += (node._id -> (dispx.get(node._id).get + diffx / diff * k * k / diff * ejectfactor))
            dispy += (node._id -> (dispy.get(node._id).get + diffy / diff * k * k / diff * ejectfactor))
          }
        }
      }
      // Gravitation
      for (edge <- edges) {
        if (edge._toNode!=null && edge._fromNode!=null) {
          diffx = edge._fromNode._x - edge._toNode._x
          diffy = edge._fromNode._y - edge._toNode._y
          diff  = sqrt(diffx * diffx + diffy * diffy)

          dispx +=(edge._fromNode._id -> (dispx.get(edge._fromNode._id).get - diffx * diff / k * condensefactor))
          dispy +=(edge._fromNode._id -> (dispy.get(edge._fromNode._id).get - diffy * diff / k * condensefactor))
          dispx +=(edge._toNode._id -> (dispx.get(edge._toNode._id).get + diffx * diff / k * condensefactor))
          dispy +=(edge._toNode._id -> (dispy.get(edge._toNode._id).get + diffy * diff / k * condensefactor))
        }
      }
      //set x,y
      sum = 0
      for (node <- nodes) {
        var disppx = floor(dispx.get(node._id).get).toInt
        var disppy = floor(dispy.get(node._id).get).toInt
        sum += sqrt(disppx * disppx + disppy * disppy).round.toInt
        if (disppx < -xt) disppx = -xt
        if (disppx > xt) disppx = xt
        if (disppy < -yt) disppy = -yt
        if (disppy > yt) disppy = yt
        node._x += disppx
        node._y += disppy
      }
      xt = cool(xt)
      yt = cool(yt)
      step -= 1
    }
    println("layout: finished")
    return graph
  }

  def cool(a: Int): Int = Math.floor(0.95 * a).toInt

}
