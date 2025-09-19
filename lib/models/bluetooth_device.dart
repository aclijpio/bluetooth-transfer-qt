class BluetoothDevice {
  final String address;
  final String? name;
  final int? rssi;
  final bool? isConnected;
  final Map<String, dynamic>? metadata;

  const BluetoothDevice({
    required this.address,
    this.name,
    this.rssi,
    this.isConnected,
    this.metadata,
  });

  Map<String, dynamic> toJson() {
    return {
      'address': address,
      'name': name,
      'rssi': rssi,
      'isConnected': isConnected,
      'metadata': metadata,
    };
  }

  factory BluetoothDevice.fromJson(Map<String, dynamic> json) {
    return BluetoothDevice(
      address: json['address'] as String,
      name: json['name'] as String?,
      rssi: json['rssi'] as int?,
      isConnected: json['isConnected'] as bool?,
      metadata: json['metadata'] as Map<String, dynamic>?,
    );
  }

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    return other is BluetoothDevice && other.address == address;
  }

  @override
  int get hashCode => address.hashCode;

  @override
  String toString() {
    return 'BluetoothDevice(address: $address, name: $name, rssi: $rssi, isConnected: $isConnected)';
  }

  BluetoothDevice copyWith({
    String? address,
    String? name,
    int? rssi,
    bool? isConnected,
    Map<String, dynamic>? metadata,
  }) {
    return BluetoothDevice(
      address: address ?? this.address,
      name: name ?? this.name,
      rssi: rssi ?? this.rssi,
      isConnected: isConnected ?? this.isConnected,
      metadata: metadata ?? this.metadata,
    );
  }
}