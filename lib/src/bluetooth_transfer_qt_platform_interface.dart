import 'dart:typed_data';

import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'bluetooth_transfer_qt_method_channel.dart';
import 'models/bluetooth_device.dart';
import 'models/transfer_progress.dart';

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

  // Basic Bluetooth operations
  Future<bool> isBluetoothSupported();
  Future<bool> isBluetoothEnabled();
  Future<bool> enableBluetooth();
  Future<bool> setDeviceName(String name);
  Future<bool> requestPermissions();

  // Device scanning and connection
  Future<void> startScan({Duration? timeout});
  void stopScan();
  Stream<BluetoothDevice> get scanResults;
  Future<List<BluetoothDevice>> bondedDevices();
  Future<bool> connectToDevice(String address, {String? uuid});
  Future<bool> disconnectFromDevice(String address);
  Future<bool> isDeviceConnected(String address);

  // Server operations
  Future<bool> startServer({
    String? serviceName,
    String? serviceUuid,
    void Function(String clientAddress)? onClientConnected,
    void Function(String clientAddress)? onClientDisconnected,
    void Function(String command, String clientAddress)? onCommand,
    void Function(String error)? onError,
  });
  Future<bool> stopServer();

  // File transfer operations
  Future<bool> sendFile(
    String deviceAddress,
    String filePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  });

  Future<bool> downloadFile(
    String deviceAddress,
    String fileName,
    String savePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  });

  // Command/message operations
  Future<bool> sendCommand(String deviceAddress, String command);
  Future<bool> sendRawData(String deviceAddress, Uint8List data);
  Stream<List<int>> listenToRawData(String deviceAddress);

  // Default implementations that subclasses can override
  Future<String?> getPlatformVersion() async => '1.0.0';
}
