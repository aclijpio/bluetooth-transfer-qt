import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'bluetooth_transfer_qt_platform_interface.dart';
import 'models/bluetooth_device.dart';
import 'models/device_info.dart';
import 'models/transfer_progress.dart';

class MethodChannelBluetoothTransferQt extends BluetoothTransferQtPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('bluetooth_transfer_qt');

  @visibleForTesting
  final eventChannel = const EventChannel('bluetooth_transfer_qt/events');

  DeviceDiscoveredCallback? _deviceDiscoveredCallback;
  TransferProgressCallback? _transferProgressCallback;
  MessageReceivedCallback? _messageReceivedCallback;
  ConnectionStateCallback? _connectionStateCallback;
  ErrorCallback? _errorCallback;

  StreamSubscription<dynamic>? _eventSubscription;
  final Map<String, StreamController<List<int>>> _rawDataStreams = {};

  static MessageReceivedCallback? _staticClientMessageCallback;
  static ConnectionStateCallback? _staticClientConnectionCallback;
  static ErrorCallback? _staticClientErrorCallback;

  MethodChannelBluetoothTransferQt() {
    _setupEventChannel();
  }

  void _setupEventChannel() {
    _eventSubscription = eventChannel.receiveBroadcastStream().listen(
      (dynamic event) {
        _handleEvent(event);
      },
      onError: (dynamic error) {
        _errorCallback?.call(error.toString());
      },
    );
  }

  Map<String, dynamic>? _toStringKeyedMap(dynamic raw) {
    if (raw is Map) {
      final Map<String, dynamic> converted = {};
      raw.forEach((key, value) {
        final String skey = key?.toString() ?? '';
        if (value is Map) {
          converted[skey] = _toStringKeyedMap(value);
        } else if (value is List) {
          converted[skey] =
              value.map((e) => e is Map ? _toStringKeyedMap(e) : e).toList();
        } else {
          converted[skey] = value;
        }
      });
      return converted;
    }
    return null;
  }

  void _handleEvent(dynamic event) {
    if (event is! Map) return;

    final eventMap = Map<String, dynamic>.from(event);
    final eventType = eventMap['type'] as String?;

    switch (eventType) {
      case 'deviceDiscovered':
        if (_deviceDiscoveredCallback != null) {
          final data = _toStringKeyedMap(eventMap['data']);
          if (data != null) {
            final device = BluetoothDevice.fromJson(data);
            _deviceDiscoveredCallback!(device);
          }
        }
        break;
      case 'transferProgress':
        if (_transferProgressCallback != null) {
          final data = _toStringKeyedMap(eventMap['data']);
          if (data != null) {
            final progress = TransferProgress.fromJson(data);
            _transferProgressCallback!(progress);
          }
        }
        break;
      case 'messageReceived':
        if (_messageReceivedCallback != null) {
          final data = _toStringKeyedMap(eventMap['data']);
          if (data != null) {
            final message = BluetoothMessage.fromJson(data);
            _messageReceivedCallback!(message);
          }
        }
        if (_staticClientMessageCallback != null) {
          final data = _toStringKeyedMap(eventMap['data']);
          if (data != null) {
            final message = BluetoothMessage.fromJson(data);
            _staticClientMessageCallback!(message);
          }
        }
        break;
      case 'rawDataReceived':
        final deviceAddress = eventMap['deviceAddress'] as String?;
        final data = eventMap['data'];

        List<dynamic>? rawData;
        if (data is List<dynamic>) {
          rawData = data;
        } else if (data is Map) {
          rawData = data['rawData'] as List<dynamic>? ??
              data['data'] as List<dynamic>? ??
              data['bytes'] as List<dynamic>?;
        }

        if (deviceAddress != null &&
            rawData != null &&
            _rawDataStreams.containsKey(deviceAddress)) {
          final bytes = rawData.cast<int>();
          _rawDataStreams[deviceAddress]!.add(bytes);
        }
        break;
      case 'connectionStateChanged':
        final deviceAddress = eventMap['deviceAddress'] as String? ?? '';
        final isConnected = eventMap['isConnected'] as bool? ?? false;

        if (_connectionStateCallback != null) {
          _connectionStateCallback!(deviceAddress, isConnected);
        }
        if (_staticClientConnectionCallback != null) {
          _staticClientConnectionCallback!(deviceAddress, isConnected);
        }
        break;
      case 'error':
        final error = eventMap['error'] as String? ?? 'Unknown error';

        if (_errorCallback != null) {
          _errorCallback!(error);
        }
        if (_staticClientErrorCallback != null) {
          _staticClientErrorCallback!(error);
        }
        break;
    }
  }

  @override
  Future<String?> getPlatformVersion() async {
    final version = await methodChannel.invokeMethod<String>(
      'getPlatformVersion',
    );
    return version;
  }

  @override
  Future<bool> isBluetoothEnabled() async {
    final result = await methodChannel.invokeMethod<bool>('isBluetoothEnabled');
    return result ?? false;
  }

  @override
  Future<bool> enableBluetooth() async {
    final result = await methodChannel.invokeMethod<bool>('enableBluetooth');
    return result ?? false;
  }

  @override
  Future<bool> setDeviceName(String name) async {
    final result = await methodChannel.invokeMethod<bool>('setDeviceName', {
      'name': name,
    });
    return result ?? false;
  }

  @override
  Future<bool> startServer({
    String? serviceName,
    String? serviceUuid,
    ConnectionStateCallback? onConnectionChanged,
    MessageReceivedCallback? onMessageReceived,
    ErrorCallback? onError,
  }) async {
    _connectionStateCallback = onConnectionChanged;
    _messageReceivedCallback = onMessageReceived;
    _errorCallback = onError;

    final result = await methodChannel.invokeMethod<bool>('startServer', {
      'serviceName': serviceName,
      'serviceUuid': serviceUuid,
    });
    return result ?? false;
  }

  @override
  Future<bool> stopServer() async {
    final result = await methodChannel.invokeMethod<bool>('stopServer');
    return result ?? false;
  }

  @override
  Future<List<BluetoothDevice>> scanForDevices({
    Duration? timeout,
    DeviceDiscoveredCallback? onDeviceFound,
  }) async {
    _deviceDiscoveredCallback = onDeviceFound;

    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'scanForDevices',
      {'timeout': timeout?.inMilliseconds},
    );

    if (result == null) return [];

    return result
        .map(
          (deviceMap) =>
              BluetoothDevice.fromJson(Map<String, dynamic>.from(deviceMap)),
        )
        .toList();
  }

  @override
  Future<bool> connectToDevice(String deviceAddress) async {
    final result = await methodChannel.invokeMethod<bool>('connectToDevice', {
      'deviceAddress': deviceAddress,
    });
    return result ?? false;
  }

  @override
  Future<bool> disconnectFromDevice(String deviceAddress) async {
    final result = await methodChannel.invokeMethod<bool>(
      'disconnectFromDevice',
      {'deviceAddress': deviceAddress},
    );
    return result ?? false;
  }

  @override
  void stopScan() {
    methodChannel.invokeMethod('stopScan');
  }

  @override
  Future<bool> sendMessage(
    String deviceAddress,
    BluetoothMessage message,
  ) async {
    final result = await methodChannel.invokeMethod<bool>('sendMessage', {
      'deviceAddress': deviceAddress,
      'message': message.toJson(),
    });
    return result ?? false;
  }

  @override
  Future<bool> sendFile(
    String deviceAddress,
    String filePath, {
    TransferProgressCallback? onProgress,
  }) async {
    _transferProgressCallback = onProgress;

    final result = await methodChannel.invokeMethod<bool>('sendFile', {
      'deviceAddress': deviceAddress,
      'filePath': filePath,
    });
    return result ?? false;
  }

  @override
  Future<bool> downloadFile(
    String deviceAddress,
    String fileName,
    String savePath, {
    TransferProgressCallback? onProgress,
  }) async {
    _transferProgressCallback = onProgress;

    final result = await methodChannel.invokeMethod<bool>('downloadFile', {
      'deviceAddress': deviceAddress,
      'fileName': fileName,
      'savePath': savePath,
    });
    return result ?? false;
  }

  @override
  Future<bool> sendDeviceInfo(
    String deviceAddress,
    DeviceInfo deviceInfo,
  ) async {
    final result = await methodChannel.invokeMethod<bool>('sendDeviceInfo', {
      'deviceAddress': deviceAddress,
      'deviceInfo': deviceInfo.toJson(),
    });
    return result ?? false;
  }

  @override
  Future<DeviceInfo?> requestDeviceInfo(String deviceAddress) async {
    final result = await methodChannel.invokeMethod<Map<dynamic, dynamic>>(
      'requestDeviceInfo',
      {'deviceAddress': deviceAddress},
    );

    if (result == null) return null;

    return DeviceInfo.fromJson(Map<String, dynamic>.from(result));
  }

  @override
  Future<bool> addMessageFilter(
    String filterId,
    Map<String, dynamic> filterConfig,
  ) async {
    final result = await methodChannel.invokeMethod<bool>('addMessageFilter', {
      'filterId': filterId,
      'filterConfig': filterConfig,
    });
    return result ?? false;
  }

  @override
  Future<bool> removeMessageFilter(String filterId) async {
    final result = await methodChannel.invokeMethod<bool>(
      'removeMessageFilter',
      {'filterId': filterId},
    );
    return result ?? false;
  }

  @override
  Future<bool> clearMessageFilters() async {
    final result = await methodChannel.invokeMethod<bool>(
      'clearMessageFilters',
    );
    return result ?? false;
  }

  @override
  Future<List<String>> getConnectedDevices() async {
    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'getConnectedDevices',
    );
    return result?.cast<String>() ?? [];
  }

  @override
  Future<bool> isDeviceConnected(String deviceAddress) async {
    final result = await methodChannel.invokeMethod<bool>('isDeviceConnected', {
      'deviceAddress': deviceAddress,
    });
    return result ?? false;
  }

  @override
  Future<bool> cancelTransfer(String transferId) async {
    final result = await methodChannel.invokeMethod<bool>('cancelTransfer', {
      'transferId': transferId,
    });
    return result ?? false;
  }

  @override
  Future<List<String>> getActiveTransfers() async {
    final result = await methodChannel.invokeMethod<List<dynamic>>(
      'getActiveTransfers',
    );
    return result?.cast<String>() ?? [];
  }

  @override
  Future<bool> requestPermissions() async {
    final result = await methodChannel.invokeMethod<bool>('requestPermissions');
    return result ?? false;
  }

  @override
  Future<bool> checkPermissions() async {
    final result = await methodChannel.invokeMethod<bool>('checkPermissions');
    return result ?? false;
  }

  @override
  Stream<List<int>> listenToRawData(String deviceAddress) {
    if (!_rawDataStreams.containsKey(deviceAddress)) {
      _rawDataStreams[deviceAddress] = StreamController<List<int>>.broadcast();
    }
    return _rawDataStreams[deviceAddress]!.stream;
  }

  void dispose() {
    _eventSubscription?.cancel();
    _eventSubscription = null;

    for (final controller in _rawDataStreams.values) {
      controller.close();
    }
    _rawDataStreams.clear();
  }

  static void setStaticClientCallbacks({
    MessageReceivedCallback? onMessageReceived,
    ConnectionStateCallback? onConnectionChanged,
    ErrorCallback? onError,
  }) {
    _staticClientMessageCallback = onMessageReceived;
    _staticClientConnectionCallback = onConnectionChanged;
    _staticClientErrorCallback = onError;
  }

  static void clearStaticClientCallbacks() {
    _staticClientMessageCallback = null;
    _staticClientConnectionCallback = null;
    _staticClientErrorCallback = null;
  }

  @override
  void setClientCallbacks({
    MessageReceivedCallback? onMessageReceived,
    ConnectionStateCallback? onConnectionChanged,
    ErrorCallback? onError,
  }) {
    setStaticClientCallbacks(
      onMessageReceived: onMessageReceived,
      onConnectionChanged: onConnectionChanged,
      onError: onError,
    );
  }

  @override
  void clearClientCallbacks() {
    clearStaticClientCallbacks();
  }
}
