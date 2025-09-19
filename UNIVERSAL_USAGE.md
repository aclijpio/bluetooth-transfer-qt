# Universal Bluetooth Transfer Plugin

A scalable, universal Bluetooth transfer plugin that works as both client and server with the same codebase. Built following SOLID principles with extensible message protocols.

## Key Features

- ✅ **Universal Design**: Single plugin works as both client and server
- ✅ **Scalable Protocol**: Extensible message system with filter chains
- ✅ **Device Information Exchange**: Built-in device metadata sharing
- ✅ **File Transfer**: Unified `sendFile`/`downloadFile` methods (renamed from `sendArchive`/`downloadArchive`)
- ✅ **Filter Chain Pattern**: Customizable message processing pipeline
- ✅ **SOLID Principles**: Clean, maintainable architecture
- ✅ **Progress Tracking**: Real-time transfer progress monitoring

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  bluetooth_transfer_qt: ^0.0.1
```

## Basic Usage

### 1. Initialize the Plugin

```dart
import 'package:bluetooth_transfer_qt/bluetooth_transfer_qt.dart';

final bluetooth = BluetoothTransferQt.instance;
```

### 2. Server Mode

```dart
// Start server
await bluetooth.startServer(
  serviceName: 'MyBluetoothService',
  onConnectionChanged: (deviceAddress, isConnected) {
    print('Device $deviceAddress ${isConnected ? 'connected' : 'disconnected'}');
  },
  onMessageReceived: (message) {
    print('Received: ${message.type} - ${message.content}');
  },
  onError: (error) {
    print('Server error: $error');
  },
);

// Stop server
await bluetooth.stopServer();
```

### 3. Client Mode

```dart
// Scan for devices
final devices = await bluetooth.scanForDevices(
  timeout: Duration(seconds: 15),
  onDeviceFound: (device) {
    print('Found: ${device.name} (${device.address})');
  },
);

// Connect to device
await bluetooth.connectToDevice(deviceAddress);

// Send message
final message = bluetooth.createTextMessage('Hello!');
await bluetooth.sendMessage(deviceAddress, message);
```

### 4. File Transfer (Renamed Methods)

```dart
// Send file (previously sendArchive)
await bluetooth.sendFile(
  deviceAddress,
  '/path/to/file.txt',
  onProgress: (progress) {
    print('Sending: ${progress.percentage}%');
  },
);

// Download file (previously downloadArchive) 
await bluetooth.downloadFile(
  deviceAddress,
  'filename.txt',
  '/path/to/save/filename.txt',
  onProgress: (progress) {
    print('Downloading: ${progress.percentage}%');
  },
);
```

### 5. Device Information Exchange

```dart
// Create device info
final deviceInfo = bluetooth.createBasicDeviceInfo(
  deviceId: 'my_device_123',
  deviceName: 'My Flutter App',
  appVersion: '1.0.0',
  customFields: {
    'feature_flags': ['bluetooth', 'file_transfer'],
    'last_update': DateTime.now().toIso8601String(),
  },
);

// Send device info
await bluetooth.sendDeviceInfo(deviceAddress, deviceInfo);

// Request device info
final remoteDeviceInfo = await bluetooth.requestDeviceInfo(deviceAddress);
print('Remote device: ${remoteDeviceInfo?.deviceName}');
```

## Advanced Features

### Message Filter Chain

Create custom protocol layers using the filter chain pattern:

```dart
// Add logging filter
bluetooth.addMessageFilter(LoggingFilter(
  id: 'logger',
  priority: 0,
  logIncoming: true,
  logOutgoing: true,
));

// Add compression filter
bluetooth.addMessageFilter(CompressionFilter(
  id: 'compressor',
  priority: 1,
  threshold: 1024, // Compress messages > 1KB
));

// Add encryption filter
bluetooth.addMessageFilter(EncryptionFilter(
  id: 'encryptor',
  priority: 2,
  key: 'your-encryption-key',
));

// Add validation filter
bluetooth.addMessageFilter(ValidationFilter(
  id: 'validator',
  priority: 3,
  requiredFields: ['timestamp', 'sender'],
));
```

### Custom Message Filters

```dart
// Create custom filter
final customFilter = CustomFilter(
  id: 'custom_protocol',
  priority: 10,
  incomingProcessor: (message) async {
    // Process incoming messages
    return message.copyWith(
      metadata: {
        ...?message.metadata,
        'processed_by': 'custom_filter',
        'received_at': DateTime.now().toIso8601String(),
      },
    );
  },
  outgoingProcessor: (message) async {
    // Process outgoing messages
    return message.copyWith(
      metadata: {
        ...?message.metadata,
        'sent_by': 'custom_filter',
        'sent_at': DateTime.now().toIso8601String(),
      },
    );
  },
);

bluetooth.addMessageFilter(customFilter);
```

### Message Types

```dart
// Text message
final textMessage = bluetooth.createTextMessage(
  'Hello, World!',
  metadata: {'priority': 'high'},
);

// Command message
final commandMessage = bluetooth.createCommandMessage(
  'restart_service',
  metadata: {'timeout': 30000},
);

// File request message
final fileRequestMessage = bluetooth.createFileRequestMessage(
  'data/export.db',
  metadata: {'compression': 'gzip'},
);

// Custom message
final customMessage = BluetoothMessage(
  type: 'custom_protocol',
  content: jsonEncode({'action': 'sync', 'version': '1.0'}),
  metadata: {'encrypted': true},
);
```

## Protocol Customization

The plugin allows you to build your own communication protocol:

### 1. Define Message Types

```dart
enum MessageType {
  heartbeat,
  dataSync,
  fileTransfer,
  command,
  response,
}
```

### 2. Create Protocol Handler

```dart
class MyProtocolHandler {
  final BluetoothTransferQt bluetooth;
  
  MyProtocolHandler(this.bluetooth) {
    // Add protocol-specific filters
    bluetooth.addMessageFilter(ProtocolFilter());
  }
  
  Future<void> sendHeartbeat(String deviceAddress) async {
    final message = BluetoothMessage(
      type: 'heartbeat',
      metadata: {
        'timestamp': DateTime.now().toIso8601String(),
        'version': '1.0',
      },
    );
    await bluetooth.sendMessage(deviceAddress, message);
  }
  
  Future<void> syncData(String deviceAddress, Map<String, dynamic> data) async {
    final message = BluetoothMessage(
      type: 'data_sync',
      content: jsonEncode(data),
      metadata: {
        'checksum': calculateChecksum(data),
        'timestamp': DateTime.now().toIso8601String(),
      },
    );
    await bluetooth.sendMessage(deviceAddress, message);
  }
}
```

### 3. Handle Protocol Messages

```dart
void handleProtocolMessage(BluetoothMessage message) {
  switch (message.type) {
    case 'heartbeat':
      handleHeartbeat(message);
      break;
    case 'data_sync':
      handleDataSync(message);
      break;
    case 'file_transfer':
      handleFileTransfer(message);
      break;
  }
}
```

## Migration from Old API

### Method Renaming

The plugin uses unified naming for better consistency:

| Old Method | New Method | Purpose |
|------------|------------|---------|
| `sendArchive()` | `sendFile()` | Send any file type |
| `downloadArchive()` | `downloadFile()` | Download any file type |

### Universal Usage

Instead of separate client/server plugins:

```dart
// Old way (separate plugins)
// final clientPlugin = BluetoothClient();
// final serverPlugin = BluetoothServer();

// New way (universal plugin)
final bluetooth = BluetoothTransferQt.instance;

// Works as both client and server
await bluetooth.startServer(); // Server mode
await bluetooth.scanForDevices(); // Client mode
```

## Example Applications

### 1. File Synchronization App

```dart
class FileSyncApp {
  final BluetoothTransferQt bluetooth = BluetoothTransferQt.instance;
  
  Future<void> startAsServer() async {
    await bluetooth.startServer(
      onMessageReceived: (message) {
        if (message.type == 'file_request') {
          handleFileRequest(message.content!);
        }
      },
    );
  }
  
  Future<void> syncWithServer(String serverAddress) async {
    await bluetooth.connectToDevice(serverAddress);
    
    // Request file list
    final request = bluetooth.createFileRequestMessage('list_files');
    await bluetooth.sendMessage(serverAddress, request);
    
    // Download missing files
    // Implementation details...
  }
}
```

### 2. Device Monitoring System

```dart
class DeviceMonitor {
  final BluetoothTransferQt bluetooth = BluetoothTransferQt.instance;
  
  Future<void> startMonitoring() async {
    // Add custom monitoring filter
    bluetooth.addMessageFilter(MonitoringFilter(
      id: 'monitor',
      onMessageProcessed: (message) {
        logMessage(message);
        updateStatistics(message);
      },
    ));
    
    await bluetooth.startServer(
      onConnectionChanged: (address, connected) {
        if (connected) {
          requestDeviceInfo(address);
        }
      },
    );
  }
}
```

## Best Practices

### 1. Error Handling

```dart
try {
  final success = await bluetooth.sendFile(deviceAddress, filePath);
  if (!success) {
    // Handle failure
    showError('Failed to send file');
  }
} catch (e) {
  // Handle exception
  showError('Error: $e');
}
```

### 2. Connection Management

```dart
// Check connection before operations
if (await bluetooth.isDeviceConnected(deviceAddress)) {
  await bluetooth.sendMessage(deviceAddress, message);
} else {
  // Reconnect or show error
  await bluetooth.connectToDevice(deviceAddress);
}
```

### 3. Resource Cleanup

```dart
@override
void dispose() {
  bluetooth.stopServer();
  bluetooth.stopScan();
  // Disconnect from all devices
  for (final address in connectedDevices) {
    bluetooth.disconnectFromDevice(address);
  }
  super.dispose();
}
```

### 4. Filter Management

```dart
// Remove filters when not needed
bluetooth.removeMessageFilter('temporary_filter');

// Clear all filters
bluetooth.clearMessageFilters();
```

## Permissions

Add these permissions to your Android manifest:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- For Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

## Architecture Benefits

### SOLID Principles

1. **Single Responsibility**: Each class has one clear purpose
2. **Open/Closed**: Extensible via filters without modifying core code
3. **Liskov Substitution**: Filters can be substituted without breaking functionality
4. **Interface Segregation**: Clean, focused interfaces
5. **Dependency Inversion**: Depends on abstractions, not concretions

### Scalability

- **Filter Chain**: Add new protocol layers without changing existing code
- **Message Types**: Extensible message system
- **Universal Design**: One plugin for all use cases
- **Custom Fields**: Extensible device information

### Maintainability

- **Clean API**: Intuitive method names and structure
- **Type Safety**: Strong typing throughout
- **Documentation**: Comprehensive inline documentation
- **Examples**: Real-world usage examples

This universal design makes the plugin suitable for any Bluetooth communication scenario while maintaining clean, scalable code.

