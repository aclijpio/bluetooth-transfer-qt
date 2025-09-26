import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import '../bluetooth_transfer_qt_platform_interface.dart';
import 'transfer_progress.dart';

/// Represents an active Bluetooth connection to a remote device
class BluetoothConnection {
  final String address;
  final BluetoothTransferQtPlatform _platform;

  StreamController<Uint8List>? _inputController;
  Stream<Uint8List>? _input;
  BluetoothStreamSink? _output;

  BluetoothConnection(this.address, this._platform) {
    _inputController = StreamController<Uint8List>.broadcast();
    _input = _inputController!.stream;
    _output = BluetoothStreamSink._(address, _platform);

    // Listen to raw data from platform
    _platform.listenToRawData(address).listen(
      (data) {
        _inputController?.add(Uint8List.fromList(data));
      },
      onError: (error) {
        _inputController?.addError(error);
      },
      onDone: () {
        _inputController?.close();
      },
    );
  }

  /// Stream for reading data from the remote device
  Stream<Uint8List>? get input => _input;

  /// Sink for writing data to the remote device
  BluetoothStreamSink? get output => _output;

  /// Check if connection is active
  Future<bool> get isConnected => _platform.isDeviceConnected(address);

  /// Send text string (UTF-8 encoded)
  void writeString(String text) {
    output?.add(utf8.encode(text));
  }

  /// Send file with progress tracking
  Future<bool> sendFile(
    String filePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) {
    return _platform.sendFile(
      address,
      filePath,
      onProgress: onProgress,
      onError: onError,
    );
  }

  /// Download file with progress tracking
  Future<bool> downloadFile(
    String fileName,
    String savePath, {
    void Function(TransferProgress progress)? onProgress,
    void Function(String? error)? onError,
  }) {
    return _platform.downloadFile(
      address,
      fileName,
      savePath,
      onProgress: onProgress,
      onError: onError,
    );
  }

  /// Send command
  Future<bool> sendCommand(String command) {
    return _platform.sendCommand(address, command);
  }

  /// Close connection immediately
  Future<void> close() async {
    await _output?.close();
    await _inputController?.close();
    _inputController = null;
    _input = null;
    _output = null;
  }

  /// Close connection gracefully (wait for pending writes)
  Future<void> finish() async {
    await _output?.allSent;
    await close();
  }

  /// Dispose resources
  void dispose() => close();
}

/// Stream sink for writing data to Bluetooth device
class BluetoothStreamSink implements StreamSink<Uint8List> {
  final String _address;
  final BluetoothTransferQtPlatform _platform;
  bool _isConnected = true;
  Future<void> _chainedFutures = Future.value();

  BluetoothStreamSink._(this._address, this._platform);

  bool get isConnected => _isConnected;

  @override
  void add(Uint8List data) {
    if (!_isConnected) {
      throw StateError('Not connected!');
    }

    _chainedFutures = _chainedFutures.then((_) async {
      if (!_isConnected) {
        throw StateError('Not connected!');
      }
      await _platform.sendRawData(_address, data);
    }).catchError((e) {
      close();
      throw e;
    });
  }

  @override
  void addError(Object error, [StackTrace? stackTrace]) {
    throw UnsupportedError(
        'BluetoothConnection output sink cannot receive errors!');
  }

  @override
  Future addStream(Stream<Uint8List> stream) async {
    final completer = Completer<void>();
    stream.listen(add,
        onDone: completer.complete, onError: completer.completeError);
    await completer.future;
    await _chainedFutures;
  }

  @override
  Future close() async {
    _isConnected = false;
    await _platform.disconnectFromDevice(_address);
  }

  @override
  Future get done => _chainedFutures;

  /// Wait for all pending writes to complete
  Future get allSent async {
    Future lastFuture;
    do {
      lastFuture = _chainedFutures;
      await lastFuture;
    } while (lastFuture != _chainedFutures);

    _chainedFutures = Future.value();
  }
}
