# Setup Guide

## Prerequisites

### System Requirements
- **Java 17 or higher** (JDK 17+ recommended)
- **Gradle 7.0+** (wrapper included)
- **Modern web browser** with WebSocket support

### Verify Prerequisites

#### Check Java Version
```bash
java -version
```
Expected output: `java version "17.0.x"` or higher

#### Check Gradle (Optional)
```bash
./gradlew --version
```
The project includes Gradle wrapper, so local Gradle installation is not required.

## Project Structure

```
realtime-editor-backend/
├── api-server/             # Ktor WebSocket server
├── backend-akka/           # Akka actor system
├── core-ot/               # Operational Transformation engine
├── client/                # HTML/JS client
├── gradle/                # Gradle wrapper
├── gradlew               # Gradle wrapper (Unix)
├── gradlew.bat          # Gradle wrapper (Windows)
└── settings.gradle.kts   # Multi-module configuration
```

## Quick Start

### 1. Clone the Repository
```bash
git clone <repository-url>
cd realtime-editor-backend
```

### 2. Build the Project
```bash
# Windows
.\gradlew build

# Unix/Linux/macOS
./gradlew build
```

### 3. Start the Server
```bash
# Windows
.\gradlew :api-server:run

# Unix/Linux/macOS
./gradlew :api-server:run
```

### 4. Open the Client
1. Navigate to the `client/` directory
2. Open `client.html` in your web browser
3. Multiple tabs can connect to the same document

### 5. Test Collaboration
1. Open multiple browser tabs with `client.html`
2. Type in one tab and see updates in others
3. Try the Insert/Delete buttons for testing

## Configuration

### Server Configuration

#### Port Configuration
The server runs on port **8080** by default. To change:

**Option 1**: Environment Variable
```bash
export PORT=9000
./gradlew :api-server:run
```

**Option 2**: Edit `api-server/src/main/kotlin/main.kt`
```kotlin
embeddedServer(Netty, port = 9000) {
    // ... existing configuration
}
```

#### WebSocket Path
Default path: `/ws/document/{documentId}`

To modify, edit `api-server/src/main/kotlin/routes/DocumentWebSocketRoutes.kt`:
```kotlin
routing {
    webSocket("/ws/document/{documentId}") {
        // ... existing implementation
    }
}
```

### Persistence Configuration

#### Current Setup (In-Memory)
File: `backend-akka/src/main/resources/application.conf`
```hocon
akka {
  persistence {
    journal.plugin = "akka.persistence.journal.inmem"
    snapshot-store.plugin = "akka.persistence.snapshot-store.local"
  }
}
```

#### Switch to LevelDB (Local Persistent Storage)
1. Add LevelDB dependency to `backend-akka/build.gradle.kts`:
```kotlin
dependencies {
    implementation("org.iq80.leveldb:leveldb:0.12")
    implementation("org.fusesource.leveldbjni:leveldbjni-all:1.8")
    implementation("com.typesafe.akka:akka-persistence-query_2.13:2.8.5")
}
```

2. Update `application.conf`:
```hocon
akka {
  persistence {
    journal {
      plugin = "akka.persistence.journal.leveldb"
      leveldb {
        dir = "target/persistence/journal"
        native = false
      }
    }
    snapshot-store {
      plugin = "akka.persistence.snapshot-store.local"
      local.dir = "target/persistence/snapshots"
    }
  }
}
```

#### Switch to Cassandra (Production)
1. Add Cassandra dependency:
```kotlin
dependencies {
    implementation("com.typesafe.akka:akka-persistence-cassandra_2.13:1.1.1")
}
```

2. Update `application.conf`:
```hocon
akka {
  persistence {
    journal.plugin = "akka.persistence.cassandra.journal"
    snapshot-store.plugin = "akka.persistence.cassandra.snapshot"
  }
}

cassandra-journal {
  contact-points = ["127.0.0.1"]
  port = 9042
  keyspace = "akka"
}
```

3. Start Cassandra:
```bash
# Docker
docker run --name cassandra -p 9042:9042 -d cassandra:3.11

# Or install locally and start
cassandra -f
```

### Client Configuration

#### WebSocket Endpoint
Edit `client/client.html` to change the WebSocket endpoint:
```javascript
const ws = new WebSocket(`ws://localhost:8080/ws/document/${documentId}`);
```

For different host/port:
```javascript
const ws = new WebSocket(`ws://your-server:9000/ws/document/${documentId}`);
```

#### Document ID
Default document ID is `"document1"`. To change:
```javascript
const documentId = 'my-custom-document';
```

## Development Setup

### IDE Configuration

#### IntelliJ IDEA
1. Open project root directory
2. IDEA should auto-detect the Gradle project
3. If not, manually import `build.gradle.kts`
4. Enable Kotlin plugin
5. Set Project SDK to Java 17+

#### VS Code
1. Install extensions:
   - Kotlin Language Support
   - Gradle for Java
2. Open project folder
3. Use `Ctrl+Shift+P` → "Java: Reload Projects"

### Running Tests

#### All Tests
```bash
./gradlew test
```

#### Specific Module Tests
```bash
# Test only OT engine
./gradlew :core-ot:test

# Test only backend actors
./gradlew :backend-akka:test

# Test only API server
./gradlew :api-server:test
```

#### Test Reports
After running tests, reports are available at:
- `core-ot/build/reports/tests/test/index.html`
- `backend-akka/build/reports/tests/test/index.html`
- `api-server/build/reports/tests/test/index.html`

### Hot Reload Development

#### Server Hot Reload
Use Gradle continuous build:
```bash
./gradlew :api-server:run --continuous
```

The server will restart automatically when files change.

#### Client Development
For client development:
1. Serve `client.html` via a local HTTP server (to avoid CORS issues)
2. Use browser dev tools for debugging
3. WebSocket messages are logged to browser console

Example with Python:
```bash
cd client
python -m http.server 3000
# Open http://localhost:3000/client.html
```

## Troubleshooting

### Common Issues

#### Port Already in Use
```
Exception: Address already in use
```
**Solution**: Kill the process using port 8080 or change the port
```bash
# Find process using port 8080
lsof -i :8080  # macOS/Linux
netstat -ano | findstr :8080  # Windows

# Kill the process
kill -9 <PID>  # macOS/Linux
taskkill /PID <PID> /F  # Windows
```

#### Java Version Issues
```
Exception: Unsupported class file major version
```
**Solution**: Ensure Java 17+ is installed and set as JAVA_HOME
```bash
export JAVA_HOME=/path/to/java17
```

#### WebSocket Connection Failed
```
WebSocket connection to 'ws://localhost:8080/...' failed
```
**Solutions**:
1. Ensure server is running: `./gradlew :api-server:run`
2. Check server logs for errors
3. Verify correct WebSocket URL in client
4. Check firewall settings

#### Actor System Not Starting
```
Exception in thread "main" akka.actor.ActorSystemImpl
```
**Solutions**:
1. Check `application.conf` syntax
2. Verify persistence plugin dependencies
3. Ensure database (if using external persistence) is running
4. Check file permissions for local storage

#### Build Failures
```
Could not resolve dependencies
```
**Solutions**:
1. Check internet connection
2. Clear Gradle cache: `./gradlew clean`
3. Refresh dependencies: `./gradlew --refresh-dependencies`
4. Check proxy settings if behind corporate firewall

### Debugging

#### Enable Debug Logging
Add to `backend-akka/src/main/resources/logback.xml`:
```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
    
    <logger name="akka" level="DEBUG" />
    <logger name="com.realtimeeditor" level="DEBUG" />
    
    <root level="INFO">
        <appender-ref ref="STDOUT" />
    </root>
</configuration>
```

#### WebSocket Message Debugging
Add to client JavaScript:
```javascript
ws.onmessage = (event) => {
    console.log('Received:', event.data);
    // ... existing handling
};

// Before sending
console.log('Sending:', JSON.stringify(operation));
ws.send(JSON.stringify(operation));
```

#### Actor Message Debugging
Add logging to actors:
```kotlin
override fun createReceive(): Receive {
    return receiveBuilder()
        .match(ClientOperation::class.java) { operation ->
            log().info("Received operation: $operation")
            // ... existing handling
        }
        .build()
}
```

## Production Deployment

### Environment Variables
Set these environment variables for production:
```bash
export ENV=production
export PORT=8080
export AKKA_PERSISTENCE_JOURNAL_PLUGIN=akka.persistence.cassandra.journal
export CASSANDRA_CONTACT_POINTS=cassandra1,cassandra2,cassandra3
export LOG_LEVEL=INFO
```

### Systemd Service (Linux)
Create `/etc/systemd/system/collaborative-editor.service`:
```ini
[Unit]
Description=Collaborative Editor Backend
After=network.target

[Service]
Type=forking
User=editor
WorkingDirectory=/opt/collaborative-editor
ExecStart=/opt/collaborative-editor/gradlew :api-server:run
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable collaborative-editor
sudo systemctl start collaborative-editor
```

### Docker Deployment
Create `Dockerfile`:
```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app
COPY . .
RUN ./gradlew build

EXPOSE 8080
CMD ["./gradlew", ":api-server:run"]
```

Build and run:
```bash
docker build -t collaborative-editor .
docker run -p 8080:8080 collaborative-editor
```

## Performance Tuning

### JVM Settings
For production, add JVM tuning flags:
```bash
export JAVA_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
./gradlew :api-server:run
```

### Akka Configuration
Tune actor system for high load in `application.conf`:
```hocon
akka {
  actor {
    default-dispatcher {
      throughput = 5
      throughput-deadline-time = 10ms
    }
  }
  
  http {
    server {
      websocket {
        periodic-keep-alive-max-idle = 30s
      }
    }
  }
}
```

This setup guide should get you up and running with the collaborative editor in any environment! 