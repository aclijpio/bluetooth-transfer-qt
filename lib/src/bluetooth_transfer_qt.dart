import 'bluetooth_transfer_qt_platform_interface.dart';
import 'models/bluetooth_device.dart';
import 'models/bluetooth_connection.dart';
import 'models/file_filter.dart';
import 'models/transfer_progress.dart';

class BluetoothTransferQt {
  static BluetoothTransferQt? _instance;
  static BluetoothTransferQt get instance =>
      _instance ??= BluetoothTransferQt._();

  BluetoothTransferQt._();

  final _platform = BluetoothTransferQtPlatform.instance;
  final List<FileFilter> _fileFilters = [];

  /// Check if Bluetooth is supported
  Future<bool> get isSupported => _platform.isBluetoothSupported();

  /// Check if Bluetooth is enabled
  Future<bool> get isEnabled => _platform.isBluetoothEnabled();

  /// Enable Bluetooth
  Future<bool> enableBluetooth() => _platform.enableBluetooth();

  /// Set device name
  Future<bool> setDeviceName(String name) => _platform.setDeviceName(name);

  /// Start scanning for devices
  Future<void> startScan({Duration? timeout}) =>
      _platform.startScan(timeout: timeout);

  /// Stop scanning
  void stopScan() => _platform.stopScan();

  /// Stream of discovered devices
  Stream<BluetoothDevice> get scanResults => _platform.scanResults;

  /// Get bonded devices
  Future<List<BluetoothDevice>> get bondedDevices => _platform.bondedDevices();

  /// Connect to device
  Future<BluetoothConnection?> connect(String address, {String? uuid}) async {
    final success = await _platform.connectToDevice(address, uuid: uuid);
    return success ? BluetoothConnection(address, _platform) : null;
  }

  /// Start server
  Future<bool> startServer({
    String? serviceName,
    String? serviceUuid,
    void Function(String clientAddress)? onClientConnected,
    void Function(String clientAddress)? onClientDisconnected,
    void Function(String command, String clientAddress)? onCommand,
    void Function(String error)? onError,
  }) =>
      _platform.startServer(
        serviceName: serviceName,
        serviceUuid: serviceUuid,
        onClientConnected: onClientConnected,
        onClientDisconnected: onClientDisconnected,
        onCommand: onCommand,
        onError: onError,
      );

  /// Stop server
  Future<bool> stopServer() => _platform.stopServer();

  /// Add file filter (e.g., exclude .apk files)
  void addFileFilter(FileFilter filter) {
    _fileFilters.add(filter);
  }

  /// Remove file filter
  void removeFileFilter(String filterId) {
    _fileFilters.removeWhere((f) => f.id == filterId);
  }

  /// Get all file filters
  List<FileFilter> get fileFilters => List.unmodifiable(_fileFilters);

  /// Check if file is allowed by filters
  bool isFileAllowed(String filePath) {
    for (final filter in _fileFilters) {
      if (!filter.allows(filePath)) return false;
    }
    return true;
  }

  /// Request permissions
  Future<bool> requestPermissions() => _platform.requestPermissions();

  Future<bool> sendCommand(String deviceAddress, String command) =>
      _platform.sendCommand(deviceAddress, command);

  Stream<List<int>> listenToRawData(String deviceAddress) =>
      _platform.listenToRawData(deviceAddress);

  Future<bool> sendFile(
    String deviceAddress,
    String filePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) =>
      _platform.sendFile(
        deviceAddress,
        filePath,
        onProgress: onProgress,
        onError: onError,
      );

  Future<bool> downloadFile(
    String deviceAddress,
    String fileName,
    String savePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) =>
      _platform.downloadFile(
        deviceAddress,
        fileName,
        savePath,
        onProgress: onProgress,
        onError: onError,
      );
}

/// Bluetooth adapter states
enum BluetoothAdapterState { unknown, turningOn, on, turningOff, off }

/// Device bonding states
enum BluetoothBondState { none, bonding, bonded }
