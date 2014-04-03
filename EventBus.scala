package com.tridion.actor

import akka.actor._

case class Subscribing(messageType: String)
case class Unsubscribing(messageType: String)


case class RequestInitialized(description: String)
case class ExpressionTreeGenerationDone(description: String)
case class GenerateExpressionTree(description: String)
case class ProcessRequest(description: String)
case class RequestProcessed(description: String)



object ActorRegistry {

  def createRequestDispatcher(actorSystem: ActorSystem, name:String):ActorRef = {
    val props:Props = Props[ODataDynamicDispatcher]
    actorSystem.actorOf(props, name)
  }

  def createODataUriParserProvider(context: ActorContext, eventBus:ActorRef, name:String):ActorRef = {
    val props:Props = Props(new ODataExpressionTreeProvider(eventBus))
    context.actorOf(props, name)
  }

  def createUndeliverableEventProcessor(context: ActorContext, eventBus:ActorRef, name:String):ActorRef={
    val props:Props = Props(new UndeliverableEventProcessor(eventBus))
    context.actorOf(props, name)
  }
}

class ODataExpressionTreeProvider(eventBus: ActorRef) extends Actor{
  eventBus ! Subscribing(GenerateExpressionTree.getClass.getSimpleName)

  override def receive: Receive = {

    case msg@GenerateExpressionTree(description) =>
      println(s"ODataExpressionTreeProvider: received: $msg")

      //process request
      eventBus ! ExpressionTreeGenerationDone(s"Done generating expression tree for $description")

    case msg:Any =>
      println(s"ODataExpressionTreeProvider: unexpeced message received: $msg")
  }
}

class ODataDynamicDispatcher extends Actor{
  import ActorRegistry._

  val expressionTreeGenerator = createODataUriParserProvider(context,self,"parser")
  val nullEventProcessor = createUndeliverableEventProcessor(context,self,"nullProcessor")
  val eventRegistry = scala.collection.mutable.Map[String, Set[ActorRef]]()

  override def receive: Receive = {
    case r@RequestInitialized(description) =>
      println(s"Processing $r")
      self ! GenerateExpressionTree(description)
    case msg@Subscribing(msgType) =>
      registerSubscription(msgType)
    case msg@Subscribing(msgType) =>
      unregisterSubscription(msgType)
    case msg: Any =>
      processEvent(msg)
  }


  def registerSubscription(rawEventType: String) = {
    val eType = eventType(rawEventType)

    println(s"registering subscription for $eType")

    if(!eventRegistry.contains(eType)){
      eventRegistry(eType) = Set[ActorRef]()
    }
    eventRegistry(eType)  = eventRegistry(eType) +  sender
  }

  def unregisterSubscription(rawEventType: String) = {
    val eType = eventType(rawEventType)

    if(eventRegistry.contains(eType)){
      eventRegistry(eType)  = eventRegistry(eType) -  sender
    }
  }

  def processEvent(event:Any) = {
    val eType = eventType(event.getClass.getName)

    println(s"Processing event $eType")

    if(eventRegistry.contains(eType)){
      eventRegistry(eType).foreach(actorRef => actorRef forward event)
    }else{
      nullEventProcessor ! event
    }
  }

  def eventType(rawEventType: String): String = {
    rawEventType.replace('$', ' ').replace('.', ' ').split(' ').last.trim
  }
}


class UndeliverableEventProcessor(eventBus: ActorRef) extends Actor{
  override def receive: Receive = {
    case msg:Any =>
      println(s"NullEventProcessor: received message  that can not be delivered. No Actor is subscribed to this event: $msg")
  }
}