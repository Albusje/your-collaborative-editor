# API Documentation

## WebSocket Protocol

### Connection

**Endpoint**: `ws://localhost:8080/ws/document/{documentId}`

**Example**:
```javascript
const ws = new WebSocket('ws://localhost:8080/ws/document/my-document');
```

### Message Types

All messages are JSON-encoded. The protocol supports bi-directional communication with distinct message types for client-to-server and server-to-client operations.

## Client → Server Messages

### Insert Operation

Inserts text at a specific position in the document.

```json
{
  "type": "insert",
  "position": 5,
  "text": "Hello World",
  "clientVersion": 1,
  "clientId": "user-123",
  "requestId": "req-456"
}
```

**Fields**:
- `type`: `"insert"` (required)
- `position`: Integer position where text should be inserted (required)
- `text`: String content to insert (required)
- `clientVersion`: Client's current document version (required)
- `clientId`: Unique identifier for the client (required)  
- `requestId`: Unique identifier for this request (required)

### Delete Operation

Removes text from a specific range in the document.

```json
{
  "type": "delete", 
  "position": 5,
  "length": 11,
  "clientVersion": 2,
  "clientId": "user-123", 
  "requestId": "req-789"
}
```

**Fields**:
- `type`: `"delete"` (required)
- `position`: Start position of text to delete (required)
- `length`: Number of characters to delete (required)
- `clientVersion`: Client's current document version (required)
- `clientId`: Unique identifier for the client (required)
- `requestId`: Unique identifier for this request (required)

## Server → Client Messages

### Document Update

Notifies clients of document changes, including the new content and version.

```json
{
  "type": "documentUpdate",
  "documentId": "my-document",
  "transformedOperation": {
    "type": "insert",
    "position": 5, 
    "text": "Hello World"
  },
  "newContent": "Hello Hello World",
  "newVersion": 2
}
```

**Fields**:
- `type`: `"documentUpdate"` (required)
- `documentId`: ID of the document that was updated (required)
- `transformedOperation`: The operation after OT transformation (optional, null for initial state)
- `newContent`: Complete updated document content (required)
- `newVersion`: New document version number (required)

## Operation Transformation

The server applies **Operational Transformation (OT)** to handle concurrent operations:

### Transformation Rules

1. **Insert vs Insert**: Adjust position based on operation order
2. **Insert vs Delete**: Handle overlap and position adjustment  
3. **Delete vs Insert**: Adjust delete range for inserted content
4. **Delete vs Delete**: Complex overlap resolution

### Example OT Scenario

**Initial State**: `"Hello World"` (version 1)

**Concurrent Operations**:
- Client A: Insert "Beautiful " at position 6
- Client B: Insert "Nice " at position 6  

**After OT**:
- Client A operation: Insert "Beautiful " at position 6 → `"Hello Beautiful World"`
- Client B operation (transformed): Insert "Nice " at position 16 → `"Hello Beautiful Nice World"`

## Client Implementation

### Basic Connection

```javascript
const documentId = 'my-document';
const clientId = 'user-' + Math.random().toString(36).substr(2, 9);
let currentVersion = 0;
let requestIdCounter = 0;
let pendingOperation = false;

const ws = new WebSocket(`ws://localhost:8080/ws/document/${documentId}`);
```

### Handling Messages

```javascript
ws.onmessage = (event) => {
  const message = JSON.parse(event.data);
  
  if (message.type === 'documentUpdate') {
    // Update local state
    currentVersion = message.newVersion;
    document.getElementById('editor').value = message.newContent;
    pendingOperation = false;
    
    console.log(`Document updated to version ${currentVersion}`);
  }
};
```

### Sending Operations

```javascript
function sendInsert(position, text) {
  if (pendingOperation) return; // Prevent concurrent operations
  
  const operation = {
    type: 'insert',
    position: position,
    text: text,
    clientVersion: currentVersion,
    clientId: clientId,
    requestId: `${clientId}-${++requestIdCounter}`
  };
  
  pendingOperation = true;
  ws.send(JSON.stringify(operation));
}

function sendDelete(position, length) {
  if (pendingOperation) return;
  
  const operation = {
    type: 'delete', 
    position: position,
    length: length,
    clientVersion: currentVersion,
    clientId: clientId,
    requestId: `${clientId}-${++requestIdCounter}`
  };
  
  pendingOperation = true;
  ws.send(JSON.stringify(operation));
}
```

### Real-time Typing Support

```javascript
let lastContent = '';

document.getElementById('editor').addEventListener('input', (event) => {
  if (isUpdatingFromServer) return; // Avoid loops
  
  const currentContent = event.target.value;
  const operation = detectOperation(lastContent, currentContent);
  
  if (operation) {
    if (operation.type === 'insert') {
      sendInsert(operation.position, operation.text);
    } else if (operation.type === 'delete') {
      sendDelete(operation.position, operation.length);
    }
    lastContent = currentContent;
  }
});

function detectOperation(oldText, newText) {
  // Simple diff algorithm to detect insert/delete operations
  // Returns { type: 'insert'|'delete', position: number, text?: string, length?: number }
  
  if (newText.length > oldText.length) {
    // Insert operation
    for (let i = 0; i < newText.length; i++) {
      if (i >= oldText.length || newText[i] !== oldText[i]) {
        return {
          type: 'insert',
          position: i,
          text: newText.substring(i, i + (newText.length - oldText.length))
        };
      }
    }
  } else if (newText.length < oldText.length) {
    // Delete operation  
    for (let i = 0; i < oldText.length; i++) {
      if (i >= newText.length || oldText[i] !== newText[i]) {
        return {
          type: 'delete',
          position: i,
          length: oldText.length - newText.length
        };
      }
    }
  }
  return null;
}
```

## Error Handling

### Connection Errors

```javascript
ws.onerror = (error) => {
  console.error('WebSocket error:', error);
  // Implement reconnection logic
};

ws.onclose = (event) => {
  console.log('WebSocket closed:', event.code, event.reason);
  // Implement reconnection logic
};
```

### Operational Errors

The server will close the connection if:
- Invalid JSON is sent
- Required fields are missing
- Invalid operation types are specified
- Version conflicts cannot be resolved

### Recommended Error Handling

```javascript
function sendOperationWithRetry(operation, maxRetries = 3) {
  let retries = 0;
  
  function attempt() {
    if (retries >= maxRetries) {
      console.error('Max retries reached for operation:', operation);
      return;
    }
    
    try {
      ws.send(JSON.stringify(operation));
    } catch (error) {
      console.error('Failed to send operation:', error);
      retries++;
      setTimeout(attempt, 1000 * retries); // Exponential backoff
    }
  }
  
  attempt();
}
```

## Performance Considerations

### Rate Limiting

The client should implement rate limiting to avoid overwhelming the server:

```javascript
const OPERATION_RATE_LIMIT = 10; // operations per second
let operationQueue = [];
let lastOperationTime = 0;

function throttledSendOperation(operation) {
  const now = Date.now();
  const timeSinceLastOp = now - lastOperationTime;
  const minInterval = 1000 / OPERATION_RATE_LIMIT;
  
  if (timeSinceLastOp >= minInterval) {
    ws.send(JSON.stringify(operation));
    lastOperationTime = now;
  } else {
    // Queue operation for later
    setTimeout(() => {
      ws.send(JSON.stringify(operation));
    }, minInterval - timeSinceLastOp);
  }
}
```

### Batching Operations

For rapid typing, consider batching multiple character insertions:

```javascript
let operationBuffer = [];
let bufferTimeout;

function bufferOperation(operation) {
  operationBuffer.push(operation);
  
  clearTimeout(bufferTimeout);
  bufferTimeout = setTimeout(() => {
    if (operationBuffer.length > 0) {
      // Merge operations if possible
      const mergedOp = mergeOperations(operationBuffer);
      ws.send(JSON.stringify(mergedOp));
      operationBuffer = [];
    }
  }, 100); // 100ms debounce
}
```

## Testing

### Manual Testing

1. **Basic Operations**: Test insert/delete via buttons
2. **Real-time Typing**: Type directly in textarea
3. **Concurrent Editing**: Open multiple tabs, edit simultaneously
4. **Version Sync**: Verify version numbers increment correctly
5. **Conflict Resolution**: Create overlapping edits, verify OT behavior

### Automated Testing

```javascript
// Example test for WebSocket communication
async function testBasicOperations() {
  const ws = new WebSocket('ws://localhost:8080/ws/document/test-doc');
  
  return new Promise((resolve, reject) => {
    ws.onopen = () => {
      // Send insert operation
      ws.send(JSON.stringify({
        type: 'insert',
        position: 0,
        text: 'Hello',
        clientVersion: 0,
        clientId: 'test-client',
        requestId: 'test-1'
      }));
    };
    
    ws.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.type === 'documentUpdate' && message.newContent === 'Hello') {
        resolve('Test passed');
      } else {
        reject('Unexpected message: ' + JSON.stringify(message));
      }
    };
    
    setTimeout(() => reject('Test timeout'), 5000);
  });
}
```

This API provides a robust foundation for real-time collaborative editing with proper conflict resolution and synchronization guarantees. 