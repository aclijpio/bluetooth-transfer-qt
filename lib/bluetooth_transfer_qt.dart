import 'dart:async';

import 'bluetooth_transfer_qt_platform_interface.dart';
import 'models/bluetooth_device.dart';
import 'models/device_info.dart';
import 'models/transfer_progress.dart';
import 'protocol/message_filter.dart';

class BluetoothTransferQt {
  static BluetoothTransferQt? _instance;

  static BluetoothTransferQt get instance =>
      _instance ??= BluetoothTransferQt._internal();

  BluetoothTransferQt._internal();

  final List<MessageFilter> _messageFilters = [];

  Future<String?> getPlatformVersion() {
    return BluetoothTransferQtPlatform.instance.getPlatformVersion();
  }

  Future<bool> isBluetoothEnabled() {
    return BluetoothTransferQtPlatform.instance.isBluetoothEnabled();
  }

  Future<bool> enableBluetooth() {
    return BluetoothTransferQtPlatform.instance.enableBluetooth();
  }

  Future<bool> setDeviceName(String name) {
    return BluetoothTransferQtPlatform.instance.setDeviceName(name);
  }

  Future<bool> startServer({
    String? serviceName,
    String? serviceUuid,
    ConnectionStateCallback? onConnectionChanged,
    MessageReceivedCallback? onMessageReceived,
    ErrorCallback? onError,
  }) {
    return BluetoothTransferQtPlatform.instance.startServer(
      serviceName: serviceName,
      serviceUuid: serviceUuid,
      onConnectionChanged: onConnectionChanged,
      onMessageReceived: _processIncomingMessage(onMessageReceived),
      onError: onError,
    );
  }

  Future<bool> stopServer() {
    return BluetoothTransferQtPlatform.instance.stopServer();
  }

  Future<List<BluetoothDevice>> scanForDevices({
    Duration? timeout,
    DeviceDiscoveredCallback? onDeviceFound,
  }) {
    return BluetoothTransferQtPlatform.instance.scanForDevices(
      timeout: timeout,
      onDeviceFound: onDeviceFound,
    );
  }

  Future<bool> connectToDevice(String deviceAddress) {
    return BluetoothTransferQtPlatform.instance.connectToDevice(deviceAddress);
  }

  Future<bool> disconnectFromDevice(String deviceAddress) {
    return BluetoothTransferQtPlatform.instance.disconnectFromDevice(
      deviceAddress,
    );
  }

  void stopScan() {
    BluetoothTransferQtPlatform.instance.stopScan();
  }

  Future<bool> sendMessage(String deviceAddress, BluetoothMessage message,) async {
    final processedMessage = await _processOutgoingMessage(message);
    return BluetoothTransferQtPlatform.instance.sendMessage(
      deviceAddress,
      processedMessage,
    );
  }

  Future<bool> sendFile(
    String deviceAddress,
    String filePath, {
    TransferProgressCallback? onProgress,
  }) {
    return BluetoothTransferQtPlatform.instance.sendFile(
      deviceAddress,
      filePath,
      onProgress: onProgress,
    );
  }

  Future<bool> downloadFile(
    String deviceAddress,
    String fileName,
    String savePath, {
    TransferProgressCallback? onProgress,
  }) {
    return BluetoothTransferQtPlatform.instance.downloadFile(
      deviceAddress,
      fileName,
      savePath,
      onProgress: onProgress,
    );
  }

  Future<bool> sendDeviceInfo(String deviceAddress, DeviceInfo deviceInfo) {
    return BluetoothTransferQtPlatform.instance.sendDeviceInfo(
      deviceAddress,
      deviceInfo,
    );
  }

  Future<DeviceInfo?> requestDeviceInfo(String deviceAddress) {
    return BluetoothTransferQtPlatform.instance.requestDeviceInfo(
      deviceAddress,
    );
  }

  void addMessageFilter(MessageFilter filter) {
    _messageFilters.add(filter);
    BluetoothTransferQtPlatform.instance.addMessageFilter(
      filter.id,
      filter.toConfig(),
    );
  }

  void removeMessageFilter(String filterId) {
    _messageFilters.removeWhere((filter) => filter.id == filterId);
    BluetoothTransferQtPlatform.instance.removeMessageFilter(filterId);
  }

  void clearMessageFilters() {
    _messageFilters.clear();
    BluetoothTransferQtPlatform.instance.clearMessageFilters();
  }

  List<MessageFilter> get messageFilters => List.unmodifiable(_messageFilters);

  Future<List<String>> getConnectedDevices() {
    return BluetoothTransferQtPlatform.instance.getConnectedDevices();
  }

  Future<bool> isDeviceConnected(String deviceAddress) {
    return BluetoothTransferQtPlatform.instance.isDeviceConnected(
      deviceAddress,
    );
  }

  Future<bool> cancelTransfer(String transferId) {
    return BluetoothTransferQtPlatform.instance.cancelTransfer(transferId);
  }

  Future<List<String>> getActiveTransfers() {
    return BluetoothTransferQtPlatform.instance.getActiveTransfers();
  }

  Future<bool> requestPermissions() {
    return BluetoothTransferQtPlatform.instance.requestPermissions();
  }

  Future<bool> checkPermissions() {
    return BluetoothTransferQtPlatform.instance.checkPermissions();
  }

  MessageReceivedCallback? _processIncomingMessage(
    MessageReceivedCallback? originalCallback,
  ) {
    if (originalCallback == null) return null;

    return (BluetoothMessage message) async {
      BluetoothMessage processedMessage = message;

      for (final filter in _messageFilters) {
        processedMessage = await filter.processIncoming(processedMessage);
      }

      originalCallback(processedMessage);
    };
  }

  Future<BluetoothMessage> _processOutgoingMessage(
    BluetoothMessage message,
  ) async {
    BluetoothMessage processedMessage = message;

    for (final filter in _messageFilters.reversed) {
      processedMessage = await filter.processOutgoing(processedMessage);
    }

    return processedMessage;
  }

  DeviceInfo createBasicDeviceInfo({
    required String deviceId,
    String? deviceName,
    String? appVersion,
    Map<String, dynamic>? customFields,
  }) {
    return DeviceInfo(
      deviceId: deviceId,
      deviceName: deviceName,
      appVersion: appVersion,
      customFields: customFields,
    );
  }

  BluetoothMessage createTextMessage(
    String content, {
    Map<String, dynamic>? metadata,
  }) {
    return BluetoothMessage(type: 'text', content: content, metadata: metadata);
  }

  BluetoothMessage createCommandMessage(
    String command, {
    Map<String, dynamic>? metadata,
  }) {
    return BluetoothMessage(
      type: 'command',
      content: command,
      metadata: metadata,
    );
  }

  BluetoothMessage createFileRequestMessage(
    String fileName, {
    Map<String, dynamic>? metadata,
  }) {
    return BluetoothMessage(
      type: 'file_request',
      content: fileName,
      metadata: metadata,
    );
  }
}