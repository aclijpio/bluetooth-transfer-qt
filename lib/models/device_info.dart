class DeviceInfo {
  final String deviceId;
  final String? deviceName;
  final String? bluetoothName;
  final String? appVersion;
  final String? osVersion;
  final String? model;
  final String? manufacturer;
  final Map<String, dynamic>? customFields;

  const DeviceInfo({
    required this.deviceId,
    this.deviceName,
    this.bluetoothName,
    this.appVersion,
    this.osVersion,
    this.model,
    this.manufacturer,
    this.customFields,
  });

  Map<String, dynamic> toJson() {
    return {
      'deviceId': deviceId,
      'deviceName': deviceName,
      'bluetoothName': bluetoothName,
      'appVersion': appVersion,
      'osVersion': osVersion,
      'model': model,
      'manufacturer': manufacturer,
      'customFields': customFields,
    };
  }

  factory DeviceInfo.fromJson(Map<String, dynamic> json) {
    return DeviceInfo(
      deviceId: json['deviceId'] as String,
      deviceName: json['deviceName'] as String?,
      bluetoothName: json['bluetoothName'] as String?,
      appVersion: json['appVersion'] as String?,
      osVersion: json['osVersion'] as String?,
      model: json['model'] as String?,
      manufacturer: json['manufacturer'] as String?,
      customFields: json['customFields'] as Map<String, dynamic>?,
    );
  }

  @override
  String toString() {
    return 'DeviceInfo(deviceId: $deviceId, deviceName: $deviceName, bluetoothName: $bluetoothName, appVersion: $appVersion)';
  }

  DeviceInfo copyWith({
    String? deviceId,
    String? deviceName,
    String? bluetoothName,
    String? appVersion,
    String? osVersion,
    String? model,
    String? manufacturer,
    Map<String, dynamic>? customFields,
  }) {
    return DeviceInfo(
      deviceId: deviceId ?? this.deviceId,
      deviceName: deviceName ?? this.deviceName,
      bluetoothName: bluetoothName ?? this.bluetoothName,
      appVersion: appVersion ?? this.appVersion,
      osVersion: osVersion ?? this.osVersion,
      model: model ?? this.model,
      manufacturer: manufacturer ?? this.manufacturer,
      customFields: customFields ?? this.customFields,
    );
  }

  factory DeviceInfo.basic({
    required String deviceId,
    String? deviceName,
    String? appVersion,
  }) {
    return DeviceInfo(
      deviceId: deviceId,
      deviceName: deviceName,
      appVersion: appVersion,
    );
  }

  factory DeviceInfo.withCustomFields({
    required String deviceId,
    required Map<String, dynamic> customFields,
    String? deviceName,
    String? appVersion,
  }) {
    return DeviceInfo(
      deviceId: deviceId,
      deviceName: deviceName,
      appVersion: appVersion,
      customFields: customFields,
    );
  }
}