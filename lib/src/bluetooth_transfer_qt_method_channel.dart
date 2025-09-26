import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'bluetooth_transfer_qt_platform_interface.dart';
import 'models/bluetooth_device.dart';
import 'models/transfer_progress.dart';

class MethodChannelBluetoothTransferQt extends BluetoothTransferQtPlatform {
  @visibleForTesting
  final methodChannel = const MethodChannel('bluetooth_transfer_qt');

  @visibleForTesting
  final eventChannel = const EventChannel('bluetooth_transfer_qt/events');

  StreamSubscription<dynamic>? _eventSubscription;
  final StreamController<BluetoothDevice> _scanResultsController =
      StreamController.broadcast();
  final Map<String, StreamController<List<int>>> _rawDataControllers = {};
  final Map<String, StreamController<String>> _archiveUpdateControllers = {};
  final Map<String, int> _lastCommandTimestamps = {};
  final Map<String, String> _lastCommandContents = {};

  // Server callbacks
  void Function(String clientAddress)? _onClientConnected;
  void Function(String clientAddress)? _onClientDisconnected;
  void Function(String command, String clientAddress)? _onCommand;
  void Function(String error)? _onError;

  // Transfer callbacks
  final Map<String, void Function(TransferProgress)> _progressCallbacks = {};
  final Map<String, void Function(String?)> _errorCallbacks = {};

  MethodChannelBluetoothTransferQt() {
    _setupEventChannel();
  }

  void _setupEventChannel() {
    _eventSubscription = eventChannel.receiveBroadcastStream().listen(
          _handleEvent,
          onError: (error) => _onError?.call(error.toString()),
        );
  }

  void _handleEvent(dynamic event) {
    if (event is! Map) return;

    final eventMap = Map<String, dynamic>.from(event);
    final eventType = eventMap['type'] as String?;

    print(
        '[BluetoothTransferQt] Received event: $eventType, data: ${eventMap['data']}');

    switch (eventType) {
      case 'deviceDiscovered':
        final rawDeviceData = eventMap['data'];
        if (rawDeviceData != null && rawDeviceData is Map) {
          final deviceData = Map<String, dynamic>.from(rawDeviceData);
          if (deviceData.containsKey('address')) {
            _scanResultsController.add(BluetoothDevice.fromMap(deviceData));
          }
        }
        break;

      case 'clientConnected':
        final address = eventMap['clientAddress'] as String?;
        if (address != null) {
          _onClientConnected?.call(address);
        }
        break;

      case 'clientDisconnected':
        final address = eventMap['clientAddress'] as String?;
        if (address != null) {
          _onClientDisconnected?.call(address);
        }
        break;

      case 'commandReceived':
        final command = eventMap['command'] as String?;
        final clientAddress = eventMap['clientAddress'] as String?;
        if (command != null && clientAddress != null) {
          _onCommand?.call(command, clientAddress);
        }
        break;

      case 'transferProgress':
        final transferId = eventMap['transferId'] as String?;
        final rawProgressData = eventMap['progress'];
        if (transferId != null &&
            rawProgressData != null &&
            rawProgressData is Map) {
          final progressData = Map<String, dynamic>.from(rawProgressData);
          final progress = TransferProgress.fromJson(progressData);
          _progressCallbacks[transferId]?.call(progress);
        }
        break;

      case 'transferError':
        final transferId = eventMap['transferId'] as String?;
        final error = eventMap['error'] as String?;
        if (transferId != null) {
          _errorCallbacks[transferId]?.call(error);
          _progressCallbacks.remove(transferId);
          _errorCallbacks.remove(transferId);
        }
        break;

      case 'messageReceived':
        final raw = eventMap['data'];
        if (raw != null && raw is Map) {
          final data = Map<String, dynamic>.from(raw);
          final content = (data['content'] as String?)?.trim();
          final metadata = data['metadata'];
          String? senderAddress;
          if (metadata != null && metadata is Map) {
            final md = Map<String, dynamic>.from(metadata);
            senderAddress = (md['senderAddress'] as String?) ??
                (md['deviceAddress'] as String?);
          }
          if (content != null && senderAddress != null) {
            final now = DateTime.now().millisecondsSinceEpoch;
            final lastTs = _lastCommandTimestamps[senderAddress] ?? 0;
            final lastContent = _lastCommandContents[senderAddress];
            final isDup = lastContent == content && (now - lastTs) < 300;
            if (!isDup) {
              _lastCommandTimestamps[senderAddress] = now;
              _lastCommandContents[senderAddress] = content;
              _onCommand?.call(content, senderAddress);
            }
          }
        }
        break;

      case 'rawDataReceived':
        // Android now sends raw data payload under 'data': { deviceAddress, data }
        final container = eventMap['data'];
        String? deviceAddress;
        dynamic rawDataObj;
        if (container is Map) {
          final m = Map<String, dynamic>.from(container);
          deviceAddress = m['deviceAddress'] as String?;
          rawDataObj = m['data'];
        } else {
          deviceAddress = eventMap['deviceAddress'] as String?;
          rawDataObj = eventMap['data'];
        }
        if (deviceAddress != null && rawDataObj != null && rawDataObj is List) {
          final rawData = List<int>.from(rawDataObj);
          if (_rawDataControllers.containsKey(deviceAddress)) {
            _rawDataControllers[deviceAddress]?.add(rawData);
            print('[BluetoothTransferQt] Raw data sent to controller for ' +
                deviceAddress);
          } else {
            print('[BluetoothTransferQt] No controller found for device ' +
                deviceAddress +
                ', creating one');
            _rawDataControllers[deviceAddress] =
                StreamController<List<int>>.broadcast();
            _rawDataControllers[deviceAddress]?.add(rawData);
          }
        }
        break;

      case 'archiveUpdate':
        final deviceAddress = eventMap['deviceAddress'] as String?;
        final status = eventMap['status'] as String?;
        if (deviceAddress != null && status != null) {
          _archiveUpdateControllers[deviceAddress]?.add(status);
        }
        break;
    }
  }

  @override
  Future<bool> isBluetoothSupported() async {
    final result =
        await methodChannel.invokeMethod<bool>('isBluetoothSupported');
    return result ?? false;
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
    final result =
        await methodChannel.invokeMethod<bool>('setDeviceName', {'name': name});
    return result ?? false;
  }

  @override
  Future<bool> requestPermissions() async {
    final result = await methodChannel.invokeMethod<bool>('requestPermissions');
    return result ?? false;
  }

  @override
  Future<void> startScan({Duration? timeout}) async {
    await methodChannel.invokeMethod('startScan', {
      'timeoutMs': timeout?.inMilliseconds,
    });
  }

  @override
  void stopScan() {
    methodChannel.invokeMethod('stopScan');
  }

  @override
  Stream<BluetoothDevice> get scanResults => _scanResultsController.stream;

  @override
  Future<List<BluetoothDevice>> bondedDevices() async {
    final result =
        await methodChannel.invokeMethod<List<dynamic>>('getBondedDevices');
    return result
            ?.map((data) =>
                BluetoothDevice.fromMap(Map<String, dynamic>.from(data)))
            .toList() ??
        [];
  }

  @override
  Future<bool> connectToDevice(String address, {String? uuid}) async {
    final result = await methodChannel.invokeMethod<bool>('connectToDevice', {
      'address': address,
      'uuid': uuid,
    });
    return result ?? false;
  }

  @override
  Future<bool> disconnectFromDevice(String address) async {
    final result =
        await methodChannel.invokeMethod<bool>('disconnectFromDevice', {
      'address': address,
    });
    return result ?? false;
  }

  @override
  Future<bool> isDeviceConnected(String address) async {
    final result = await methodChannel.invokeMethod<bool>('isDeviceConnected', {
      'address': address,
    });
    return result ?? false;
  }

  @override
  Future<bool> startServer({
    String? serviceName,
    String? serviceUuid,
    void Function(String clientAddress)? onClientConnected,
    void Function(String clientAddress)? onClientDisconnected,
    void Function(String command, String clientAddress)? onCommand,
    void Function(String error)? onError,
  }) async {
    _onClientConnected = onClientConnected;
    _onClientDisconnected = onClientDisconnected;
    _onCommand = onCommand;
    _onError = onError;

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
  Future<bool> sendFile(
    String deviceAddress,
    String filePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) async {
    final transferId = '${DateTime.now().millisecondsSinceEpoch}';

    if (onProgress != null) {
      _progressCallbacks[transferId] = onProgress;
    }
    if (onError != null) {
      _errorCallbacks[transferId] = onError;
    }

    final result = await methodChannel.invokeMethod<bool>('sendFile', {
      'deviceAddress': deviceAddress,
      'filePath': filePath,
      'transferId': transferId,
    });

    if (result != true) {
      _progressCallbacks.remove(transferId);
      _errorCallbacks.remove(transferId);
    }

    return result ?? false;
  }

  @override
  Future<bool> downloadFile(
    String deviceAddress,
    String fileName,
    String savePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) async {
    final transferId = '${DateTime.now().millisecondsSinceEpoch}';

    if (onProgress != null) {
      _progressCallbacks[transferId] = onProgress;
    }
    if (onError != null) {
      _errorCallbacks[transferId] = onError;
    }

    final result = await methodChannel.invokeMethod<bool>('downloadFile', {
      'deviceAddress': deviceAddress,
      'fileName': fileName,
      'savePath': savePath,
      'transferId': transferId,
    });

    if (result != true) {
      _progressCallbacks.remove(transferId);
      _errorCallbacks.remove(transferId);
    }

    return result ?? false;
  }

  @override
  Future<bool> sendCommand(String deviceAddress, String command) async {
    final result = await methodChannel.invokeMethod<bool>('sendCommand', {
      'address': deviceAddress,
      'command': command,
    });
    return result ?? false;
  }

  @override
  Future<bool> sendRawData(String deviceAddress, Uint8List data) async {
    final result = await methodChannel.invokeMethod<bool>('sendRawData', {
      'deviceAddress': deviceAddress,
      'data': data,
    });
    return result ?? false;
  }

  @override
  Stream<List<int>> listenToRawData(String deviceAddress) {
    if (!_rawDataControllers.containsKey(deviceAddress)) {
      _rawDataControllers[deviceAddress] =
          StreamController<List<int>>.broadcast();
    }
    return _rawDataControllers[deviceAddress]!.stream;
  }

  void dispose() {
    _eventSubscription?.cancel();
    _scanResultsController.close();
    _rawDataControllers.values.forEach((controller) => controller.close());
    _rawDataControllers.clear();
    _archiveUpdateControllers.values
        .forEach((controller) => controller.close());
    _archiveUpdateControllers.clear();
    _progressCallbacks.clear();
    _errorCallbacks.clear();
  }
}
