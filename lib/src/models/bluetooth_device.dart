import '../bluetooth_transfer_qt.dart';
import 'bluetooth_connection.dart';
import 'transfer_progress.dart';
import '../bluetooth_transfer_qt_platform_interface.dart';

class BluetoothDevice {
  final String address;
  final String? name;
  final String? alias;
  final int? rssi;
  final BluetoothDeviceType type;
  final BluetoothBondState bondState;

  BluetoothDevice._({
    required this.address,
    this.name,
    this.alias,
    this.rssi,
    this.type = BluetoothDeviceType.unknown,
    this.bondState = BluetoothBondState.none,
  });

  factory BluetoothDevice.fromMap(Map<String, dynamic> map) =>
      BluetoothDevice._(
        address: map['address'] as String,
        name: map['name'] as String?,
        alias: map['alias'] as String?,
        rssi: map['rssi'] as int?,
        type: BluetoothDeviceType.values.firstWhere(
          (e) => e.name == map['type'],
          orElse: () => BluetoothDeviceType.unknown,
        ),
        bondState: BluetoothBondState.values.firstWhere(
          (e) => e.name == map['bondState'],
          orElse: () => BluetoothBondState.none,
        ),
      );

  /// Connect to this device
  Future<BluetoothConnection?> connect({String? uuid}) async {
    final success = await BluetoothTransferQtPlatform.instance
        .connectToDevice(address, uuid: uuid);
    return success
        ? BluetoothConnection(address, BluetoothTransferQtPlatform.instance)
        : null;
  }

  /// Send file to this device (requires connection)
  Future<bool> sendFile(
    String filePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) async {
    // Check filters first
    final bt = BluetoothTransferQt.instance;
    if (!bt.isFileAllowed(filePath)) {
      onError?.call('File type not allowed by filters');
      return false;
    }

    return BluetoothTransferQtPlatform.instance.sendFile(
      address,
      filePath,
      onProgress: onProgress,
      onError: onError,
    );
  }

  /// Download file from this device
  Future<bool> downloadFile(
    String fileName,
    String savePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) async {
    return BluetoothTransferQtPlatform.instance.downloadFile(
      address,
      fileName,
      savePath,
      onProgress: onProgress,
      onError: onError,
    );
  }

  /// Send command to this device
  Future<bool> sendCommand(String command) async {
    return BluetoothTransferQtPlatform.instance.sendCommand(address, command);
  }

  /// Disconnect from this device
  Future<bool> disconnect() async {
    return BluetoothTransferQtPlatform.instance.disconnectFromDevice(address);
  }

  @override
  bool operator ==(Object other) {
    return other is BluetoothDevice && other.address == address;
  }

  @override
  int get hashCode => address.hashCode;

  @override
  String toString() => 'BluetoothDevice(address: $address, name: $name)';
}

enum BluetoothDeviceType { classic, dual, unknown }
