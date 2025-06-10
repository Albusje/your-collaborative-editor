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
┌─────────────────┐    WebSocket    ┌─────────────────┐
│   Client A      │◄──────────────► │   Ktor Server   │
│   (Browser)     │                 │                 │
└─────────────────┘                 │                 │
                                    │ ┌─────────────┐ │
┌─────────────────┐    WebSocket    │ │DocumentActor│ │
│   Client B      │◄──────────────► │ │ (Persistent)│ │
│   (Browser)     │                 │ └─────────────┘ │
└─────────────────┘                 │                 │
                                    │ ┌─────────────┐ │
┌─────────────────┐    WebSocket    │ │ Broadcast   │ │
│   Client C      │◄──────────────► │ │ Actor       │ │
│   (Browser)     │                 │ └─────────────┘ │
└─────────────────┘                 └─────────────────┘
```

### Accessing the Editor
1. Open multiple browser tabs
2. Navigate to `client/client.html`
3. Connect to the same document ID
4. Start typing and see real-time collaboration in action!

## 📊 Project Structure

```
realtime-editor-backend/
├── api-server/                 # Ktor WebSocket server
│   ├── src/main/kotlin/
│   │   ├── routes/            # WebSocket routing
│   │   └── actor/             # WebSocket client handlers
│   └── build.gradle.kts
├── backend-akka/              # Core Akka backend
│   ├── src/main/kotlin/
│   │   ├── actor/            # Document & Manager actors
│   │   ├── command/          # Command messages
│   │   ├── event/            # Event sourcing events
│   │   └── state/            # Document state management
│   └── build.gradle.kts
├── core-ot/                  # Operational Transformation engine
│   ├── src/main/kotlin/
│   │   ├── model/           # Operation models (Insert, Delete)
│   │   └── ot/              # OT transformation algorithms
│   └── build.gradle.kts
├── client/                   # Frontend client
│   └── client.html          # HTML5 + JavaScript client
└── build.gradle.kts         # Root build configuration
```

## Tech Stack

- **Backend**: Kotlin + Akka + Ktor + WebSockets
- **Frontend**: HTML5 + JavaScript
- **Architecture**: Actor Model + Event Sourcing + OT

---

*This is a demonstration project showcasing distributed systems and real-time collaboration.* 