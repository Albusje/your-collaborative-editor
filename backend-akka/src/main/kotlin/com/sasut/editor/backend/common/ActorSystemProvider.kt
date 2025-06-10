package com.sasut.editor.backend.common

import akka.actor.ActorSystem
import akka.actor.ActorRef
import com.typesafe.config.ConfigFactory
import com.sasut.editor.backend.actor.DocumentManagerActor
import com.sasut.editor.backend.actor.WebSocketBroadcastActor

object ActorSystemProvider {
    val system: ActorSystem by lazy {
        val config = ConfigFactory.load()
        ActorSystem.create("DocumentEditorSystem", config)
    }

    val documentManager: ActorRef by lazy {
        system.actorOf(DocumentManagerActor.props(), "document-manager")
    }
    val webSocketBroadcaster: ActorRef by lazy {
        system.actorOf(WebSocketBroadcastActor.props(), "websocket-broadcaster")
    }

    fun shutdown() {
        system.terminate()
        system.whenTerminated() // Block until terminated, for graceful shutdown
    }
}