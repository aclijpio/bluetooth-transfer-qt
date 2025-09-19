import 'package:flutter_test/flutter_test.dart';
import 'package:bluetooth_transfer_qt/bluetooth_transfer_qt.dart';
import 'package:bluetooth_transfer_qt/bluetooth_transfer_qt_platform_interface.dart';
import 'package:bluetooth_transfer_qt/bluetooth_transfer_qt_method_channel.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class MockBluetoothTransferQtPlatform
    with MockPlatformInterfaceMixin
    implements BluetoothTransferQtPlatform {

  @override
  Future<String?> getPlatformVersion() => Future.value('42');
}

void main() {
  final BluetoothTransferQtPlatform initialPlatform = BluetoothTransferQtPlatform.instance;

  test('$MethodChannelBluetoothTransferQt is the default instance', () {
    expect(initialPlatform, isInstanceOf<MethodChannelBluetoothTransferQt>());
  });

  test('getPlatformVersion', () async {
    BluetoothTransferQt bluetoothTransferQtPlugin = BluetoothTransferQt();
    MockBluetoothTransferQtPlatform fakePlatform = MockBluetoothTransferQtPlatform();
    BluetoothTransferQtPlatform.instance = fakePlatform;

    expect(await bluetoothTransferQtPlugin.getPlatformVersion(), '42');
  });
}
