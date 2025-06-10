# Architecture Documentation

## Overview

This collaborative text editor implements a **distributed, event-sourced architecture** using the **Actor Model** for concurrent operations and **Operational Transformation** for conflict resolution.

## Core Principles

### 1. Event Sourcing
- All document changes stored as immutable events
- State derived by replaying events from the beginning
- Enables audit trails, debugging, and state reconstruction

### 2. Actor Model (Akka)
- Each document managed by a dedicated persistent actor
- Actors process messages sequentially (no race conditions)
- Location transparency and fault tolerance built-in

### 3. Operational Transformation (OT)
- Transforms concurrent operations to maintain consistency
- Handles conflicting edits from multiple clients
- Preserves user intent while ensuring convergence

## System Components

### Backend Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Ktor WebSocket Server                    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ DocumentWebSocket│  │WebSocketClientH │  │ WebSocket    │ │
│  │ Routes          │  │ andlerActor     │  │ Broadcast    │ │
│  │                 │  │                 │  │ Actor        │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Akka Actor System                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │ DocumentManager │  │ DocumentActor   │  │ Event Stream │ │
│  │ Actor           │  │ (Persistent)    │  │              │ │
│  │                 │  │                 │  │              │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Persistence Layer                        │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────┐  ┌─────────────────┐                   │
│  │ Event Journal   │  │ Snapshot Store  │                   │
│  │ (Events)        │  │ (State)         │                   │
│  │                 │  │                 │                   │
│  └─────────────────┘  └─────────────────┘                   │
└─────────────────────────────────────────────────────────────┘
```

## Actor Responsibilities

### DocumentManagerActor
**Purpose**: Factory and registry for DocumentActor instances

**Responsibilities**:
- Create DocumentActor per document ID
- Maintain registry of active document actors
- Handle actor lifecycle (creation, termination)
- Route requests to appropriate DocumentActor

**Messages**:
- `GetDocumentActor(documentId)` → `DocumentActorRefResponse`

### DocumentActor (Persistent)
**Purpose**: Manages single document state and operations

**Responsibilities**:
- Store document content and version
- Apply operational transformations
- Persist events to journal
- Publish updates to event stream
- Handle recovery from events

**State**:
```kotlin
data class DocumentState(
    val content: String = "",
    val version: Int = 0
)
```

**Messages**:
- `ClientOperation` - Process client edit operations
- `GetDocumentState` - Return current document state

**Events**:
- `OperationAppliedEvent` - Records successful operation application

### WebSocketClientHandlerActor
**Purpose**: Bridge between WebSocket sessions and actor system

**Responsibilities**:
- Hold reference to Ktor WebSocketSession
- Forward DocumentUpdate events to client
- Handle client disconnections
- Convert between WebSocket DTOs and actor messages

### WebSocketBroadcastActor
**Purpose**: Fan-out DocumentUpdate events to all connected clients

**Responsibilities**:
- Subscribe to document update events
- Maintain registry of client session actors per document
- Broadcast updates to all relevant clients
- Handle client registration/unregistration

## Data Flow

### Client Operation Flow
```
1. Client sends operation via WebSocket
   ↓
2. DocumentWebSocketRoutes receives JSON
   ↓
3. Parse to ClientOperation message
   ↓
4. Send to DocumentActor
   ↓
5. DocumentActor applies OT transformation
   ↓
6. Persist OperationAppliedEvent
   ↓
7. Update internal state
   ↓
8. Publish DocumentUpdate to EventStream
   ↓
9. WebSocketBroadcastActor receives update
   ↓
10. Broadcast to all connected clients
```

### State Recovery Flow
```
1. DocumentActor starts/restarts
   ↓
2. Akka Persistence replays events
   ↓
3. Apply each OperationAppliedEvent
   ↓
4. Rebuild DocumentState
   ↓
5. Ready to process new operations
```

## Operational Transformation

### Transform Function
```kotlin
fun transform(op1: Operation, op2: Operation): Operation
```

### Transformation Rules

#### Insert vs Insert
```kotlin
// If op1 position <= op2 position: no change
// If op1 position > op2 position: adjust op1 position by op2 text length
```

#### Insert vs Delete
```kotlin
// If insert position <= delete position: no change  
// If insert position > delete end: adjust position by delete length
// If insert position within delete range: adjust to delete start
```

#### Delete vs Insert
```kotlin
// If delete position <= insert position: no change
// If delete position > insert position: adjust position by insert length
```

#### Delete vs Delete
```kotlin
// Complex overlap detection and range adjustment
```

## Message Protocols

### WebSocket Protocol

#### Client → Server
```json
{
  "type": "insert|delete",
  "position": 0,
  "text": "hello",
  "length": 5,
  "clientVersion": 1,
  "clientId": "client-123",
  "requestId": "req-456"
}
```

#### Server → Client
```json
{
  "type": "documentUpdate",
  "documentId": "doc-123", 
  "transformedOperation": null,
  "newContent": "hello world",
  "newVersion": 2
}
```

### Actor Messages

#### Commands
```kotlin
data class ClientOperation(
    val operation: Operation,
    val clientId: String,
    val clientVersion: Int,
    val requestId: String
)

data class GetDocumentState(val requestId: String)
```

#### Events
```kotlin
data class OperationAppliedEvent(
    val transformedOperation: Operation,
    val newVersion: Int,
    val originalClientId: String,
    val originalClientVersion: Int,
    val originalRequestId: String
)
```

#### Notifications
```kotlin
data class DocumentUpdate(
    val documentId: String,
    val transformedOperation: Operation,
    val newVersion: Int,
    val updatedContent: String
)
```

## Scalability Considerations

### Horizontal Scaling
- Each DocumentActor can run on different nodes
- Akka Cluster can distribute actors across machines
- WebSocket connections can be load balanced

### Performance Optimizations
- Actor mailbox prioritization for user operations
- Snapshot creation every N operations
- Bounded operation history for OT transformations

### Fault Tolerance
- Actor supervision strategies for error recovery
- Persistent actors survive process restarts
- Circuit breakers for external dependencies

## Persistence Strategies

### Development (Current)
```hocon
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
```

### Production Options
```hocon
# Cassandra (Distributed)
akka.persistence.journal.plugin = "akka.persistence.cassandra.journal"

# PostgreSQL (SQL)  
akka.persistence.journal.plugin = "akka.persistence.jdbc.journal"

# LevelDB (Local)
akka.persistence.journal.plugin = "akka.persistence.journal.leveldb"
```

## Security Considerations

### Current State
- No authentication (demo purposes)
- No document access control
- No rate limiting

### Production Enhancements
- JWT-based authentication
- Document-level permissions
- Rate limiting per client
- Input validation and sanitization
- WebSocket origin validation

## Monitoring & Observability

### Metrics to Track
- Active document count
- Connected client count per document
- Operation processing latency
- Event persistence rate
- WebSocket connection health

### Logging Strategy
- Structured logging with correlation IDs
- Operation tracing across actors
- Error aggregation and alerting
- Performance monitoring

This architecture provides a solid foundation for real-time collaborative editing that can scale to handle thousands of concurrent users across multiple documents. 