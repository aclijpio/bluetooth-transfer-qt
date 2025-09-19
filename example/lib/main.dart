import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:bluetooth_transfer_qt/bluetooth_transfer_qt.dart';
import 'universal_example.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  bool _bluetoothEnabled = false;
  final BluetoothTransferQt _bluetooth = BluetoothTransferQt.instance;

  @override
  void initState() {
    super.initState();
    initPlatformState();
  }

  Future<void> initPlatformState() async {
    String platformVersion;
    bool bluetoothEnabled;

    try {
      platformVersion =
          await _bluetooth.getPlatformVersion() ?? 'Unknown platform version';
      bluetoothEnabled = await _bluetooth.isBluetoothEnabled();
    } on PlatformException {
      platformVersion = 'Failed to get platform version.';
      bluetoothEnabled = false;
    }

    if (!mounted) return;

    setState(() {
      _platformVersion = platformVersion;
      _bluetoothEnabled = bluetoothEnabled;
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Universal Bluetooth Transfer',
      theme: ThemeData(primarySwatch: Colors.blue, useMaterial3: true),
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Universal Bluetooth Transfer'),
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              const Icon(Icons.bluetooth, size: 64, color: Colors.blue),
              const SizedBox(height: 24),
              Text(
                'Universal Bluetooth Transfer Plugin',
                style: Theme.of(context).textTheme.headlineSmall,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Card(
                margin: const EdgeInsets.all(16),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.phone_android),
                          const SizedBox(width: 8),
                          const Text('Platform: '),
                          Expanded(child: Text(_platformVersion)),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Row(
                        children: [
                          Icon(
                            Icons.bluetooth,
                            color: _bluetoothEnabled
                                ? Colors.green
                                : Colors.red,
                          ),
                          const SizedBox(width: 8),
                          const Text('Bluetooth: '),
                          Text(
                            _bluetoothEnabled ? 'Enabled' : 'Disabled',
                            style: TextStyle(
                              color: _bluetoothEnabled
                                  ? Colors.green
                                  : Colors.red,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 24),
              const Text(
                'Features:',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 32),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('• Universal client/server design'),
                    Text('• Scalable message protocol'),
                    Text('• Filter chain pattern support'),
                    Text('• Device information exchange'),
                    Text('• File transfer with progress'),
                    Text('• SOLID principle compliance'),
                  ],
                ),
              ),
              const SizedBox(height: 32),
              ElevatedButton.icon(
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(
                      builder: (context) => const UniversalBluetoothExample(),
                    ),
                  );
                },
                icon: const Icon(Icons.play_arrow),
                label: const Text('Open Demo'),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 24,
                    vertical: 12,
                  ),
                ),
              ),
              const SizedBox(height: 16),
              if (!_bluetoothEnabled)
                ElevatedButton.icon(
                  onPressed: () async {
                    final success = await _bluetooth.enableBluetooth();
                    if (success) {
                      setState(() {
                        _bluetoothEnabled = true;
                      });
                    }
                  },
                  icon: const Icon(Icons.bluetooth),
                  label: const Text('Enable Bluetooth'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    foregroundColor: Colors.white,
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
