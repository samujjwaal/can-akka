package com.group11.can

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.group11.can.CanMessageTypes._
import com.typesafe.config.Config

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext

object CanNode {
  def props(id:BigInt):Props= {
    Props(new CanNode(id:BigInt))
  }

}

class CanNode(myId:BigInt) extends Actor with ActorLogging {
  implicit val ec: ExecutionContext = context.dispatcher

  val nodeConf: Config = context.system.settings.config.getConfig("CANnetworkConstants")
  val xMax: Double = nodeConf.getDouble("xMax")
  val yMax: Double = nodeConf.getDouble("yMax")
  var myCoord: Coordinate = null
  val myNeighbors = new mutable.HashMap[BigInt,Neighbor]()
//  val myNeighbors = new ListBuffer[Neighbor]()

  def nbrsAsString(): String = {
    var str = ""
    for (n <- myNeighbors) {
      str += n._1.toString() + n._2.getAsString()
    }
    str
  }

  def splitMyZone(newNode: ActorRef): Unit = {

    var incomingUpperX=Double.MinValue
    var incomingLowerX=Double.MinValue
    var incomingUpperY=Double.MinValue
    var incomingLowerY=Double.MinValue
    if(myCoord.canSplitVertically)
    {
      log.info("Splitting vertically for "+BigInt(newNode.path.name.toInt))
      val oldUpperX=myCoord.upperX
      myCoord.splitVertically()

      newNode ! SetCoord(myCoord.upperX, myCoord.lowerY, oldUpperX, myCoord.upperY)

      incomingLowerX=myCoord.upperX
      incomingLowerY=myCoord.lowerY
      incomingUpperX=oldUpperX
      incomingUpperY=myCoord.upperY
    }
    else
    {
      log.info("Splitting horizontally for "+BigInt(newNode.path.name.toInt))
      val oldUpperY=myCoord.upperY
      myCoord.splitHorizontally()

      newNode ! SetCoord(myCoord.lowerX, myCoord.upperY, myCoord.upperX, oldUpperY)

      incomingLowerX=myCoord.lowerX
      incomingLowerY=myCoord.upperY
      incomingUpperX=myCoord.upperX
      incomingUpperY=oldUpperY
    }

    addNbr(newNode,incomingLowerX,incomingLowerY,incomingUpperX,incomingUpperY,BigInt(newNode.path.name.toInt))
    newNode ! AddNeighbor(self,myCoord.lowerX:Double, myCoord.lowerY:Double, myCoord.upperX:Double, myCoord.upperY:Double, myId)

    val incomingCoord= new Coordinate(incomingLowerX,incomingLowerY,incomingUpperX,incomingUpperY)

    var nbrToRemove = new ListBuffer[BigInt]()
    for( n <- myNeighbors)
    {
      val nbrCoord = n._2.nodeCoord
      if((incomingCoord.isAdjacentX(nbrCoord) && incomingCoord.isSubsetY(nbrCoord)) ||
          (incomingCoord.isAdjacentY(nbrCoord) && incomingCoord.isSubsetX(nbrCoord)))
      {
         n._2.nodeRef ! AddNeighbor(newNode,incomingLowerX,incomingLowerY,incomingUpperX,incomingUpperY,BigInt(newNode.path.name.toInt))
         newNode ! AddNeighbor(n._2.nodeRef,nbrCoord.lowerX,nbrCoord.lowerY,nbrCoord.upperX,nbrCoord.upperY,n._1)
      }

      if((myCoord.isAdjacentX(nbrCoord) && myCoord.isSubsetY(nbrCoord)) ||
          (myCoord.isAdjacentY(nbrCoord) && myCoord.isSubsetX(nbrCoord))) {
        n._2.nodeRef ! UpdateNeighbor(myId: BigInt,
                                      myCoord.lowerX:Double, myCoord.lowerY:Double,
                                      myCoord.upperX:Double, myCoord.upperY:Double)
      }
      else {
        n._2.nodeRef ! RemoveNeighbor(myId)
        nbrToRemove.addOne(n._1)
      }
    }
    myNeighbors --= nbrToRemove


  }

  def addNbr(nbrRef: ActorRef,lx:Double,ly:Double,ux:Double,uy:Double, nbrID: BigInt): Unit = {
    val nbrCoord = new Coordinate(lx:Double,ly:Double,ux:Double,uy:Double)
    val nbr = new Neighbor(nbrRef,nbrCoord,nbrID)
    myNeighbors(nbrID) = nbr
  }

  def findClosestNeighbor(p_x: Double, p_y: Double): Neighbor = {
    var closestNbr: Neighbor = null
    var dist = Double.MaxValue
    for (nbr <- myNeighbors) {
      val nbrDist = nbr._2.nodeCoord.dist(p_x,p_y)
      if (nbrDist < dist) {
        dist = nbrDist
        closestNbr = nbr._2
      }
    }
    closestNbr
  }

  override def receive: Receive = {

    case JoinCan(peer: ActorRef) => {
      println("Join called for node:"+ myId)
//      log.info("Join called for node:"+ myId)
      if (peer == self) {
        myCoord = new Coordinate(0,0,xMax,yMax)
      }
      else {
        val p_x = scala.util.Random.nextDouble() * xMax
        val p_y = scala.util.Random.nextDouble() * yMax
        println(p_x,p_y)
        peer ! RouteNewNode(p_x, p_y, self)
      }
      Thread.sleep(1000)
//      log.info("Joined node "+myId)
//      log.info("node "+myId+" neighbors : "+nbrsAsString())
//      log.info("node "+myId+" coords :"+myCoord.getAsString())
      println("Joined node "+myId)
      println("node "+myId+" neighbors : "+nbrsAsString())
      println("node "+myId+" coords :"+myCoord.getAsString())
    }

    case RouteNewNode(p_x, p_y, newNode) => {
      println("Route request received at "+myId)
//      log.info("Route request received at "+myId)
      if (myCoord.hasPoint(p_x,p_y)) {
        log.info("Point lies in my zone, splitting my zone.")
        splitMyZone(newNode)
      }
      else {
        log.info("Not in my zone. Finding my closest neighbor to forward request.")
        val closestNeighbor = findClosestNeighbor(p_x,p_y)
        closestNeighbor.nodeRef ! RouteNewNode(p_x, p_y, newNode)
      }
    }

    case GetNodeId() => {
      sender() ! myId
    }

    case GetNeighbors() => {
      sender() ! myNeighbors
    }

    case GetCoord() => {
      sender() ! myCoord
    }

    case SetCoord(l_X: Double, l_Y: Double, u_X:Double, u_Y:Double) => {
      if (myCoord == null) {
        println("node "+myId+" my coord is null")
        myCoord = new Coordinate(l_X, l_Y, u_X, u_Y)
//        log.info("node "+myId+" coords :"+myCoord.getAsString())
        println("------node "+myId+" coords :"+myCoord.getAsString())
      }
      else {
        myCoord.setCoord(l_X, l_Y, u_X, u_Y)
      }
    }

    case AddNeighbor(nbrRef: ActorRef,lx:Double,ly:Double,ux:Double,uy:Double, nbrID: BigInt) => {
      addNbr(nbrRef,lx,ly,ux,uy,nbrID)
    }

    case RemoveNeighbor(nbrID: BigInt) => {
        myNeighbors.remove(nbrID)
    }

    case UpdateNeighbor(nbrID: BigInt,lx:Double,ly:Double,ux:Double,uy:Double) => {
      myNeighbors(nbrID).nodeCoord.setCoord(lx:Double,ly:Double,ux:Double,uy:Double)
    }

  }
}
