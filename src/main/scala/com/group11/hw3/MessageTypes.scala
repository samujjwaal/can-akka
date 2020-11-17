package com.group11.hw3

import akka.actor.typed.ActorRef

trait DataRequest
case class ReadKey(key: String) extends DataRequest
case class WriteValue(key: String, value: String) extends DataRequest

trait NodeRequest
case class FindNode(node: ActorRef[Nothing]) extends NodeRequest
case class getKeyValue(key: String) extends NodeRequest
case class writeKeyValue(key: String, value: String) extends  NodeRequest





//case class FindNode(node: ActorRef)

trait NodeCommand
case class GetNodeIndex() extends NodeCommand
case class DisplayNodeInfo() extends NodeCommand
case class FindPredecessor(key: String) extends NodeCommand
case class FindSuccessor(key: String) extends NodeCommand