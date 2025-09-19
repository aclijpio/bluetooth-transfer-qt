import '../bluetooth_transfer_qt_platform_interface.dart';

abstract class MessageFilter {
  final String id;
  final int priority;

  const MessageFilter({required this.id, this.priority = 0});

  Future<BluetoothMessage> processIncoming(BluetoothMessage message);

  Future<BluetoothMessage> processOutgoing(BluetoothMessage message);

  Map<String, dynamic> toConfig();

  static MessageFilter fromConfig(String id, Map<String, dynamic> config) {
    final type = config['type'] as String;

    switch (type) {
      case 'logging':
        return LoggingFilter(
          id: id,
          priority: config['priority'] as int? ?? 0,
          logIncoming: config['logIncoming'] as bool? ?? true,
          logOutgoing: config['logOutgoing'] as bool? ?? true,
        );
      case 'encryption':
        return EncryptionFilter(
          id: id,
          priority: config['priority'] as int? ?? 0,
          key: config['key'] as String,
        );
      case 'compression':
        return CompressionFilter(
          id: id,
          priority: config['priority'] as int? ?? 0,
          threshold: config['threshold'] as int? ?? 1024,
        );
      case 'validation':
        return ValidationFilter(
          id: id,
          priority: config['priority'] as int? ?? 0,
          requiredFields: List<String>.from(config['requiredFields'] ?? []),
        );
      default:
        throw ArgumentError('Unknown filter type: $type');
    }
  }
}

class LoggingFilter extends MessageFilter {
  final bool logIncoming;
  final bool logOutgoing;

  const LoggingFilter({
    required super.id,
    super.priority,
    this.logIncoming = true,
    this.logOutgoing = true,
  });

  @override
  Future<BluetoothMessage> processIncoming(BluetoothMessage message) async {
    if (logIncoming) {
      print(
        '[BluetoothFilter:$id] Incoming: ${message.type} - ${message.content}',
      );
    }
    return message;
  }

  @override
  Future<BluetoothMessage> processOutgoing(BluetoothMessage message) async {
    if (logOutgoing) {
      print(
        '[BluetoothFilter:$id] Outgoing: ${message.type} - ${message.content}',
      );
    }
    return message;
  }

  @override
  Map<String, dynamic> toConfig() {
    return {
      'type': 'logging',
      'priority': priority,
      'logIncoming': logIncoming,
      'logOutgoing': logOutgoing,
    };
  }
}

class EncryptionFilter extends MessageFilter {
  final String key;

  const EncryptionFilter({
    required super.id,
    super.priority,
    required this.key,
  });

  @override
  Future<BluetoothMessage> processIncoming(BluetoothMessage message) async {
    return BluetoothMessage(
      type: message.type,
      content: message.content,
      data: message.data,
      metadata: {
        ...?message.metadata,
        'decrypted': true,
        'encryptionFilter': id,
      },
    );
  }

  @override
  Future<BluetoothMessage> processOutgoing(BluetoothMessage message) async {
    return BluetoothMessage(
      type: message.type,
      content: message.content,
      data: message.data,
      metadata: {
        ...?message.metadata,
        'encrypted': true,
        'encryptionFilter': id,
      },
    );
  }

  @override
  Map<String, dynamic> toConfig() {
    return {'type': 'encryption', 'priority': priority, 'key': key};
  }
}

class CompressionFilter extends MessageFilter {
  final int threshold;

  const CompressionFilter({
    required super.id,
    super.priority,
    this.threshold = 1024,
  });

  @override
  Future<BluetoothMessage> processIncoming(BluetoothMessage message) async {
    final wasCompressed = message.metadata?['compressed'] == true;

    if (wasCompressed) {
      return BluetoothMessage(
        type: message.type,
        content: message.content,
        data: message.data,
        metadata: {
          ...?message.metadata,
          'decompressed': true,
          'compressionFilter': id,
        }..remove('compressed'),
      );
    }

    return message;
  }

  @override
  Future<BluetoothMessage> processOutgoing(BluetoothMessage message) async {
    final contentLength =
        (message.content?.length ?? 0) + (message.data?.length ?? 0);

    if (contentLength >= threshold) {
      return BluetoothMessage(
        type: message.type,
        content: message.content,
        data: message.data,
        metadata: {
          ...?message.metadata,
          'compressed': true,
          'compressionFilter': id,
          'originalSize': contentLength,
        },
      );
    }

    return message;
  }

  @override
  Map<String, dynamic> toConfig() {
    return {
      'type': 'compression',
      'priority': priority,
      'threshold': threshold,
    };
  }
}

class ValidationFilter extends MessageFilter {
  final List<String> requiredFields;

  const ValidationFilter({
    required super.id,
    super.priority,
    this.requiredFields = const [],
  });

  @override
  Future<BluetoothMessage> processIncoming(BluetoothMessage message) async {
    _validateMessage(message);
    return message;
  }

  @override
  Future<BluetoothMessage> processOutgoing(BluetoothMessage message) async {
    _validateMessage(message);
    return message;
  }

  void _validateMessage(BluetoothMessage message) {
    if (message.type.isEmpty) {
      throw ArgumentError('Message type cannot be empty');
    }

    for (final field in requiredFields) {
      if (message.metadata?[field] == null) {
        throw ArgumentError('Required field missing: $field');
      }
    }
  }

  @override
  Map<String, dynamic> toConfig() {
    return {
      'type': 'validation',
      'priority': priority,
      'requiredFields': requiredFields,
    };
  }
}

class CustomFilter extends MessageFilter {
  final Future<BluetoothMessage> Function(BluetoothMessage) incomingProcessor;
  final Future<BluetoothMessage> Function(BluetoothMessage) outgoingProcessor;
  final Map<String, dynamic> customConfig;

  const CustomFilter({
    required super.id,
    super.priority,
    required this.incomingProcessor,
    required this.outgoingProcessor,
    this.customConfig = const {},
  });

  @override
  Future<BluetoothMessage> processIncoming(BluetoothMessage message) {
    return incomingProcessor(message);
  }

  @override
  Future<BluetoothMessage> processOutgoing(BluetoothMessage message) {
    return outgoingProcessor(message);
  }

  @override
  Map<String, dynamic> toConfig() {
    return {'type': 'custom', 'priority': priority, ...customConfig};
  }
}

class RoutingFilter extends MessageFilter {
  final Map<String, String> routingRules;

  const RoutingFilter({
    required super.id,
    super.priority,
    this.routingRules = const {},
  });

  @override
  Future<BluetoothMessage> processIncoming(BluetoothMessage message) async {
    final targetDevice = routingRules[message.type];
    if (targetDevice != null) {
      return BluetoothMessage(
        type: message.type,
        content: message.content,
        data: message.data,
        metadata: {
          ...?message.metadata,
          'routedTo': targetDevice,
          'routingFilter': id,
        },
      );
    }
    return message;
  }

  @override
  Future<BluetoothMessage> processOutgoing(BluetoothMessage message) async {
    return processIncoming(message);
  }

  @override
  Map<String, dynamic> toConfig() {
    return {
      'type': 'routing',
      'priority': priority,
      'routingRules': routingRules,
    };
  }
}