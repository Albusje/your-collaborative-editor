# Architecture Overview

## Core Concepts

### Actor Model
- Each document managed by a dedicated `DocumentActor`
- Actors process messages sequentially (no race conditions)
- Built-in fault tolerance and supervision

### Event Sourcing
- All changes stored as immutable events
- Document state rebuilt by replaying events
- Perfect audit trail and debugging capability

### Operational Transformation (OT)
- Transforms concurrent operations to avoid conflicts
- Ensures all clients converge to the same final state
- Handles insert/delete operations intelligently

## System Flow

```
WebSocket Client → DocumentActor → Event Store
                      ↓
                 Broadcast Actor → All Connected Clients
```

## Key Components

### DocumentActor
- Manages document state and version
- Applies operational transformations
- Persists events and publishes updates

### WebSocketBroadcastActor  
- Receives document updates
- Broadcasts to all connected clients
- Manages client registrations

### Operational Transformation
Handles concurrent operations:
- **Insert vs Insert**: Adjust positions based on order
- **Insert vs Delete**: Handle position overlaps
- **Delete vs Delete**: Resolve range conflicts

## Message Flow

1. Client sends operation via WebSocket
2. Parse to `ClientOperation` message
3. Send to `DocumentActor`
4. Apply OT transformation and persist event
5. Publish `DocumentUpdate` to event stream
6. Broadcast to all connected clients

## Persistence

Currently uses in-memory persistence for demonstration:
```hocon
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
```

---

*This architecture demonstrates enterprise patterns: Actor Model, Event Sourcing, and Operational Transformation.* 