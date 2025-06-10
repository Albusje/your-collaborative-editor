# Real-time Collaborative Text Editor

A production-ready real-time collaborative text editor built with **Kotlin**, **Akka**, and **WebSockets**. Multiple users can edit the same document simultaneously with instant synchronization and conflict resolution.

![Collaborative Editor](https://img.shields.io/badge/Status-Working-brightgreen)
![Technology](https://img.shields.io/badge/Tech-Kotlin%20%2B%20Akka%20%2B%20WebSockets-blue)
![Real-time](https://img.shields.io/badge/Real--time-Yes-success)

## ✨ Features

- 🚀 **Real-time Collaboration** - Multiple users edit simultaneously with instant sync
- 🔄 **Operational Transformation** - Conflict-free collaborative editing using OT algorithms
- 💾 **Event Sourcing** - All operations stored as immutable events for auditability
- 🎯 **Actor-Based Architecture** - Scalable, fault-tolerant using Akka actors
- 🌐 **WebSocket Communication** - Bi-directional real-time updates
- 📱 **Multi-Client Support** - Connect from multiple browser tabs/windows
- 🔧 **Version Management** - Proper client/server state synchronization

## 🏗️ Architecture

This project implements **enterprise-grade collaborative editing** using:

- **Backend**: Kotlin + Akka + Ktor + Akka Persistence
- **Frontend**: HTML5 + JavaScript + WebSockets  
- **Architecture**: Event Sourcing + CQRS + Actor Model
- **Real-time**: WebSocket broadcasting + Event streaming

```
┌─────────────────┐    WebSocket    ┌─────────────────┐
│   Client A      │◄──────────────►│   Ktor Server   │
│   (Browser)     │                 │                 │
└─────────────────┘                 │                 │
                                    │  ┌─────────────┐ │
┌─────────────────┐    WebSocket    │  │ DocumentActor│ │
│   Client B      │◄──────────────►│  │ (Persistent) │ │
│   (Browser)     │                 │  └─────────────┘ │
└─────────────────┘                 │                 │
                                    │  ┌─────────────┐ │
┌─────────────────┐    WebSocket    │  │ Broadcast   │ │
│   Client C      │◄──────────────►│  │ Actor       │ │
│   (Browser)     │                 │  └─────────────┘ │
└─────────────────┘                 └─────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- **Java 17+**
- **Gradle 7+**

### Running the Server
```bash
# Clone the repository
git clone <your-repo-url>
cd realtime-editor-backend

# Start the server
./gradlew :api-server:run
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

## 🔧 Configuration

### Persistence
Currently configured for **in-memory persistence** (perfect for testing):
```hocon
akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
```

For production, switch to **Cassandra** or **PostgreSQL**:
```hocon
akka.persistence.journal.plugin = "akka.persistence.cassandra.journal"
```

### WebSocket Endpoint
```
ws://localhost:8080/ws/document/{documentId}
```

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Manual Testing
1. **Basic Collaboration**: Open 2+ tabs, edit in one, see updates in others
2. **Concurrent Operations**: Type in multiple tabs simultaneously  
3. **OT Verification**: Insert/delete at same positions, verify conflict resolution
4. **Persistence**: Make edits, restart server, verify state recovery (if configured)

## 🎯 Usage Example

### Connect to Document
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/document/my-doc');
```

### Send Operation
```javascript
ws.send(JSON.stringify({
  type: "insert",
  position: 0,
  text: "Hello World",
  clientVersion: 1,
  clientId: "user-123",
  requestId: "req-456"
}));
```

### Receive Updates
```javascript
ws.onmessage = (event) => {
  const update = JSON.parse(event.data);
  if (update.type === 'documentUpdate') {
    editor.value = update.newContent;
    currentVersion = update.newVersion;
  }
};
```

## 🏆 Key Achievements

This project demonstrates:

- **Distributed Systems** - Multi-node coordination and consistency
- **Real-time Systems** - Sub-second latency for collaborative operations  
- **Conflict Resolution** - Operational Transformation algorithms
- **Event Sourcing** - Audit trail and state reconstruction
- **Actor Concurrency** - Fault-tolerant concurrent programming
- **WebSocket Technology** - Full-duplex real-time communication

## 📚 Learn More

- [Architecture Documentation](ARCHITECTURE.md)
- [API Reference](API.md) 
- [Setup Guide](SETUP.md)
- [Feature Details](FEATURES.md)

## 🤝 Contributing

This is a demonstration project showcasing collaborative editing technology. Feel free to:

- Add new features (user cursors, authentication, etc.)
- Implement different OT algorithms
- Add persistence backends
- Improve the client UI

## 📄 License

This project is for educational and demonstration purposes.

---

**Built with ❤️ using Kotlin, Akka, and WebSockets** 