package com.sasut.editor.backend.common

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object ActorSystemProvider {
    val system: ActorSystem by lazy {
        // Load configuration from application.conf (and potentially other files)
        val config = ConfigFactory.load()
        ActorSystem.create("DocumentEditorSystem", config)
    }

    fun shutdown() {
        system.terminate()
        system.whenTerminated() // Block until terminated, for graceful shutdown
    }
}