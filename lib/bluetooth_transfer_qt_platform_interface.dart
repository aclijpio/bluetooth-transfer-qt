import 'dart:typed_data';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'bluetooth_transfer_qt_method_channel.dart';
import 'models/bluetooth_device.dart';
import 'models/device_info.dart';
import 'models/transfer_progress.dart';

typedef DeviceDiscoveredCallback = void Function(BluetoothDevice device);
typedef TransferProgressCallback = void Function(TransferProgress progress);
typedef MessageReceivedCallback = void Function(BluetoothMessage message);
typedef ConnectionStateCallback =
    void Function(String deviceAddress, bool isConnected);
typedef ErrorCallback = void Function(String error);

abstract class BluetoothTransferQtPlatform extends PlatformInterface {
  BluetoothTransferQtPlatform() : super(token: _token);

  static final Object _token = Object();

  static BluetoothTransferQtPlatform _instance =
      MethodChannelBluetoothTransferQt();

  static BluetoothTransferQtPlatform get instance => _instance;

  static set instance(BluetoothTransferQtPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }

  Future<bool> isBluetoothEnabled() {
    throw UnimplementedError('isBluetoothEnabled() has not been implemented.');
  }

  Future<bool> enableBluetooth() {
    throw UnimplementedError('enableBluetooth() has not been implemented.');
  }

  Future<bool> setDeviceName(String name) {
    throw UnimplementedError('setDeviceName() has not been implemented.');
  }

  Future<bool> startServer({
    String? serviceName,
    String? serviceUuid,
    ConnectionStateCallback? onConnectionChanged,
    MessageReceivedCallback? onMessageReceived,
    ErrorCallback? onError,
  }) {
    throw UnimplementedError('startServer() has not been implemented.');
  }

  Future<bool> stopServer() {
    throw UnimplementedError('stopServer() has not been implemented.');
  }

  Future<List<BluetoothDevice>> scanForDevices({
    Duration? timeout,
    DeviceDiscoveredCallback? onDeviceFound,
  }) {
    throw UnimplementedError('scanForDevices() has not been implemented.');
  }

  Future<bool> connectToDevice(String deviceAddress) {
    throw UnimplementedError('connectToDevice() has not been implemented.');
  }

  Future<bool> disconnectFromDevice(String deviceAddress) {
    throw UnimplementedError(
      'disconnectFromDevice() has not been implemented.',
    );
  }

  void stopScan() {
    throw UnimplementedError('stopScan() has not been implemented.');
  }

  Future<bool> sendMessage(String deviceAddress, BluetoothMessage message) {
    throw UnimplementedError('sendMessage() has not been implemented.');
  }

  Future<bool> sendFile(
    String deviceAddress,
    String filePath, {
    TransferProgressCallback? onProgress,
  }) {
    throw UnimplementedError('sendFile() has not been implemented.');
  }

  Future<bool> downloadFile(
    String deviceAddress,
    String fileName,
    String savePath, {
    TransferProgressCallback? onProgress,
  }) {
    throw UnimplementedError('downloadFile() has not been implemented.');
  }

  Future<bool> sendDeviceInfo(String deviceAddress, DeviceInfo deviceInfo) {
    throw UnimplementedError('sendDeviceInfo() has not been implemented.');
  }

  Future<DeviceInfo?> requestDeviceInfo(String deviceAddress) {
    throw UnimplementedError('requestDeviceInfo() has not been implemented.');
  }

  Future<bool> addMessageFilter(
    String filterId,
    Map<String, dynamic> filterConfig,
  ) {
    throw UnimplementedError('addMessageFilter() has not been implemented.');
  }

  Future<bool> removeMessageFilter(String filterId) {
    throw UnimplementedError('removeMessageFilter() has not been implemented.');
  }

  Future<bool> clearMessageFilters() {
    throw UnimplementedError('clearMessageFilters() has not been implemented.');
  }

  Future<List<String>> getConnectedDevices() {
    throw UnimplementedError('getConnectedDevices() has not been implemented.');
  }

  Future<bool> isDeviceConnected(String deviceAddress) {
    throw UnimplementedError('isDeviceConnected() has not been implemented.');
  }

  Future<bool> cancelTransfer(String transferId) {
    throw UnimplementedError('cancelTransfer() has not been implemented.');
  }

  Future<List<String>> getActiveTransfers() {
    throw UnimplementedError('getActiveTransfers() has not been implemented.');
  }

  Future<bool> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  Future<bool> checkPermissions() {
    throw UnimplementedError('checkPermissions() has not been implemented.');
  }
}

class BluetoothMessage {
  final String type;
  final String? content;
  final Uint8List? data;
  final Map<String, dynamic>? metadata;

  const BluetoothMessage({
    required this.type,
    this.content,
    this.data,
    this.metadata,
  });

  Map<String, dynamic> toJson() {
    return {
      'type': type,
      'content': content,
      'data': data?.toList(),
      'metadata': metadata,
    };
  }

  factory BluetoothMessage.fromJson(Map<String, dynamic> json) {
    return BluetoothMessage(
      type: json['type'] as String,
      content: json['content'] as String?,
      data: json['data'] != null
          ? Uint8List.fromList(List<int>.from(json['data']))
          : null,
      metadata: json['metadata'] as Map<String, dynamic>?,
    );
  }
}