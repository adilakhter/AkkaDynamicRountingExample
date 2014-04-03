package com.tridion.actor

import akka.actor.ActorSystem

object Main extends App{

  println("Initializing Actor System")

  val actorSystem = ActorSystem.create("odatasystem")
  val eventBus = ActorRegistry.createRequestDispatcher(actorSystem, "eventbus");

  eventBus ! RequestInitialized("query!")
}
