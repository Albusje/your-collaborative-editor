# Real-time Collaborative Text Editor

A demonstration of real-time collaborative editing built with **Kotlin + Akka + WebSockets**. Multiple users can edit the same document simultaneously with instant synchronization.

## Features

- ✅ **Real-time collaboration** - Multiple users edit simultaneously
- ✅ **Conflict resolution** - Using Operational Transformation (OT)
- ✅ **WebSocket communication** - Instant bi-directional updates
- ✅ **Event sourcing** - All changes persisted as events

## Quick Start

```bash
# Start the server
./gradlew :api-server:run

# Open client/client.html in multiple browser tabs
# Connect to same document and start editing!
```

## How it Works

```
Client A ←─┐
           ├→ WebSocket Server → DocumentActor → Event Store
Client B ←─┘                      ↓
                               Broadcast to all clients
```

## Testing

1. Open multiple browser tabs with `client/client.html`
2. Type in one tab, see updates in others instantly
3. Try concurrent edits to see conflict resolution

## Tech Stack

- **Backend**: Kotlin + Akka + Ktor + WebSockets
- **Frontend**: HTML5 + JavaScript
- **Architecture**: Actor Model + Event Sourcing + OT

---

*This is a demonstration project showcasing distributed systems and real-time collaboration.* 