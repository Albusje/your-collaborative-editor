package com.sasut.editor.backend.actor

import java.time.Duration

import akka.actor.ActorRef
import akka.actor.ActorSystem

import akka.testkit.javadsl.TestKit

import com.typesafe.config.ConfigFactory

import org.slf4j.LoggerFactory

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

import com.sasut.editor.backend.command.ClientOperation
import com.sasut.editor.backend.command.DocumentStateResponse
import com.sasut.editor.backend.command.GetDocumentState
import com.sasut.editor.backend.notification.DocumentUpdate
import com.sasut.editor.core.model.Delete
import com.sasut.editor.core.model.Insert

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DocumentActor Persistence and Transformation Tests")
class DocumentActorTest {

    private lateinit var system: ActorSystem
    private lateinit var testKit: TestKit
    private val log = LoggerFactory.getLogger(javaClass)

    @BeforeAll
    fun setup() {
        val testConfig = ConfigFactory.parseString("""
    akka {
      actor {
        allow-java-serialization = on
        warn-about-java-serializer-usage = off
      }
      persistence {
        journal {
          plugin = "akka.persistence.journal.inmem"
          inmem {
            test-serialization = on
          }
        }
        snapshot-store {
          plugin = "akka.persistence.snapshot-store.local"
          local.dir = "target/snapshots"
        }
      }
      loglevel = "INFO"
      loggers = ["akka.event.slf4j.Slf4jLogger"]
      logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
    }
""".trimIndent()).withFallback(ConfigFactory.load())

        system = ActorSystem.create("DocumentActorTestSystem", testConfig)
        testKit = TestKit(system)
        log.info("Akka Test System initialized for DocumentActor tests with In-Memory journal.")

        system.eventStream().subscribe(testKit.ref, DocumentUpdate::class.java as Class<*>)
    }


    @AfterAll
    fun teardown() {
        TestKit.shutdownActorSystem(system)
        log.info("Akka Test System shut down after tests.")
    }

    private fun ActorRef.send(msg: Any, sender: ActorRef = testKit.ref) {
        this.tell(msg, sender)
    }

    @Test
    @DisplayName("Should persist and recover document state after operations")
    fun `should_persist_and_recover_document_state_after_operations`() {
        val documentId = "doc-recover-1"
        val actor1 = system.actorOf(DocumentActor.props(documentId), "docActorRecover1")
        testKit.watch(actor1)

        log.info("Test: Sending initial operations to actor1 for docId: {}", documentId)

        actor1.send(ClientOperation(Insert(0, "Hello"), "clientA", 0, "req1"))
        val update1 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("Hello", update1.updatedContent)
        assertEquals(1, update1.newVersion)

        actor1.send(ClientOperation(Insert(5, " World"), "clientB", 1, "req2"))
        val update2 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("Hello World", update2.updatedContent)
        assertEquals(2, update2.newVersion)

        actor1.send(ClientOperation(Insert(11, "!"), "clientA", 2, "req3"))
        val update3 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("Hello World!", update3.updatedContent)
        assertEquals(3, update3.newVersion)

        actor1.send(GetDocumentState("getState1"))
        val response1 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentStateResponse::class.java) as DocumentStateResponse
        assertEquals("Hello World!", response1.content)
        assertEquals(3, response1.version)

        system.stop(actor1)
        testKit.expectTerminated(Duration.ofSeconds(10), actor1)

        val actor2 = system.actorOf(DocumentActor.props(documentId), "docActorRecover2")
        testKit.watch(actor2)

        actor2.send(GetDocumentState("getState2"))
        val response2 = testKit.expectMsgClass(Duration.ofSeconds(10), DocumentStateResponse::class.java) as DocumentStateResponse
        assertEquals("Hello World!", response2.content)
        assertEquals(3, response2.version)

        system.stop(actor2)
        testKit.expectTerminated( Duration.ofSeconds(5), actor2)
    }

    @Test
    @DisplayName("Should transform concurrent operations correctly")
    fun `should_transform_concurrent_operations_correctly`() {
        val documentId = "doc-concurrent-ot-1"
        val actor = system.actorOf(DocumentActor.props(documentId), "docActorConcurrentOt")
        testKit.watch(actor)

        actor.send(ClientOperation(Insert(0, "Hello"), "clientA", 0, "reqA1"))
        val update1 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("Hello", update1.updatedContent)
        assertEquals(1, update1.newVersion)

        actor.send(ClientOperation(Insert(0, "World"), "clientB", 0, "reqB1"))
        val update2 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("WorldHello", update2.updatedContent)
        assertEquals(2, update2.newVersion)

        actor.send(GetDocumentState("getState"))
        val response = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentStateResponse::class.java) as DocumentStateResponse
        assertEquals("WorldHello", response.content)
        assertEquals(2, response.version)

        system.stop(actor)
        testKit.expectTerminated(Duration.ofSeconds(5), actor)
    }

    @Test
    @DisplayName("Should transform inserts against a concurrent delete")
    fun `should_transform_inserts_against_concurrent_delete`() {
        val documentId = "doc-transform-insert-delete"
        val initialContent = "abcdefg"
        val actor = system.actorOf(DocumentActor.props(documentId), "docActorInsertDelete")
        testKit.watch(actor)

        actor.send(ClientOperation(Insert(0, initialContent), "clientInit", 0, "reqInit"))
        val update1 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals(initialContent, update1.updatedContent)
        assertEquals(1, update1.newVersion)

        actor.send(ClientOperation(Delete(2, 3), "clientA", 1, "reqA1"))
        val update2 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("abfg", update2.updatedContent)
        assertEquals(2, update2.newVersion)

        actor.send(ClientOperation(Insert(3, "X"), "clientB", 1, "reqB1"))
        val update3 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("abXfg", update3.updatedContent)
        assertEquals(3, update3.newVersion)

        actor.send(GetDocumentState("getState"))
        val response = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentStateResponse::class.java) as DocumentStateResponse
        assertEquals("abXfg", response.content)
        assertEquals(3, response.version)

        system.stop(actor)
        testKit.expectTerminated(Duration.ofSeconds(5), actor)
    }

    @Test
    @DisplayName("Should transform deletes against a concurrent insert")
    fun `should_transform_deletes_against_concurrent_insert`() {
        val documentId = "doc-transform-delete-insert"
        val initialContent = "abcdefg"
        val actor = system.actorOf(DocumentActor.props(documentId), "docActorDeleteInsert")
        testKit.watch(actor)

        actor.send(ClientOperation(Insert(0, initialContent), "clientInit", 0, "reqInit"))
        val update1 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals(initialContent, update1.updatedContent)
        assertEquals(1, update1.newVersion)

        actor.send(ClientOperation(Insert(2, "XYZ"), "clientA", 1, "reqA1"))
        val update2 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("abXYZcdefg", update2.updatedContent)
        assertEquals(2, update2.newVersion)

        actor.send(ClientOperation(Delete(2, 3), "clientB", 1, "reqB1"))
        val update3 = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentUpdate::class.java) as DocumentUpdate
        assertEquals("abXYZfg", update3.updatedContent)
        assertEquals(3, update3.newVersion)

        actor.send(GetDocumentState("getState"))
        val response = testKit.expectMsgClass(Duration.ofSeconds(5), DocumentStateResponse::class.java) as DocumentStateResponse
        assertEquals("abXYZfg", response.content)
        assertEquals(3, response.version)

        system.stop(actor)
        testKit.expectTerminated(Duration.ofSeconds(5), actor)
    }
}