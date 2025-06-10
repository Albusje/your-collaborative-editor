# API Reference

## WebSocket Connection

**Endpoint**: `ws://localhost:8080/ws/document/{documentId}`

```javascript
const ws = new WebSocket('ws://localhost:8080/ws/document/my-doc');
```

## Message Types

### Client → Server

#### Insert Operation
```json
{
  "type": "insert",
  "position": 5,
  "text": "Hello",
  "clientVersion": 1,
  "clientId": "user-123",
  "requestId": "req-456"
}
```

#### Delete Operation
```json
{
  "type": "delete",
  "position": 5,
  "length": 5,
  "clientVersion": 2,
  "clientId": "user-123",
  "requestId": "req-789"
}
```

### Server → Client

#### Document Update
```json
{
  "type": "documentUpdate",
  "documentId": "my-doc",
  "transformedOperation": {
    "type": "insert",
    "position": 5,
    "text": "Hello"
  },
  "newContent": "Hello World",
  "newVersion": 2
}
```

## Client Implementation

### Basic Setup
```javascript
const documentId = 'my-document';
const clientId = 'user-' + Math.random().toString(36).substr(2, 9);
let currentVersion = 0;
let requestIdCounter = 0;

const ws = new WebSocket(`ws://localhost:8080/ws/document/${documentId}`);
```

### Handling Updates
```javascript
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  if (message.type === 'documentUpdate') {
    currentVersion = message.newVersion;
    document.getElementById('editor').value = message.newContent;
  }
};
```

### Sending Operations
```javascript
function sendInsert(position, text) {
  const operation = {
    type: 'insert',
    position: position,
    text: text,
    clientVersion: currentVersion,
    clientId: clientId,
    requestId: `${clientId}-${++requestIdCounter}`
  };
  
  ws.send(JSON.stringify(operation));
}
```

## Operational Transformation

The server automatically handles concurrent operations:

- **Insert vs Insert**: Adjusts positions to maintain order
- **Insert vs Delete**: Handles overlapping ranges intelligently  
- **Delete vs Delete**: Resolves conflicting deletions

**Example**: Two users insert text at position 5 simultaneously → server ensures proper ordering and both changes are preserved.

---

*For a working example, see `client/client.html`* 