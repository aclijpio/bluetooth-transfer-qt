class TransferProgress {
  final String transferId;
  final String fileName;
  final int totalBytes;
  final int transferredBytes;
  final double percentage;
  final TransferStatus status;
  final String? error;
  final DateTime timestamp;

  const TransferProgress({
    required this.transferId,
    required this.fileName,
    required this.totalBytes,
    required this.transferredBytes,
    required this.percentage,
    required this.status,
    this.error,
    required this.timestamp,
  });

  Map<String, dynamic> toJson() {
    return {
      'transferId': transferId,
      'fileName': fileName,
      'totalBytes': totalBytes,
      'transferredBytes': transferredBytes,
      'percentage': percentage,
      'status': status.name,
      'error': error,
      'timestamp': timestamp.toIso8601String(),
    };
  }

  factory TransferProgress.fromJson(Map<String, dynamic> json) {
    final num? totalBytesNum = json['totalBytes'] as num?;
    final num? transferredBytesNum = json['transferredBytes'] as num?;

    TransferStatus parseStatus(dynamic raw) {
      if (raw is String) {
        return TransferStatus.values.firstWhere(
          (e) => e.name == raw,
          orElse: () => TransferStatus.unknown,
        );
      }
      if (raw is int) {
        return (raw >= 0 && raw < TransferStatus.values.length)
            ? TransferStatus.values[raw]
            : TransferStatus.unknown;
      }
      return TransferStatus.unknown;
    }

    DateTime parseTimestamp(dynamic raw) {
      if (raw is String) {
        try {
          return DateTime.parse(raw);
        } catch (_) {}
      } else if (raw is int) {
        return DateTime.fromMillisecondsSinceEpoch(raw);
      } else if (raw is double) {
        return DateTime.fromMillisecondsSinceEpoch(raw.toInt());
      }
      return DateTime.now();
    }

    return TransferProgress(
      transferId: (json['transferId'] ?? '') as String,
      fileName: (json['fileName'] ?? '') as String,
      totalBytes: (totalBytesNum ?? 0).toInt(),
      transferredBytes: (transferredBytesNum ?? 0).toInt(),
      percentage: (json['percentage'] is num)
          ? (json['percentage'] as num).toDouble()
          : 0.0,
      status: parseStatus(json['status']),
      error: json['error'] as String?,
      timestamp: parseTimestamp(json['timestamp']),
    );
  }

  @override
  String toString() {
    return 'TransferProgress(transferId: $transferId, fileName: $fileName, percentage: ${percentage.toStringAsFixed(1)}%, status: $status)';
  }

  TransferProgress copyWith({
    String? transferId,
    String? fileName,
    int? totalBytes,
    int? transferredBytes,
    double? percentage,
    TransferStatus? status,
    String? error,
    DateTime? timestamp,
  }) {
    return TransferProgress(
      transferId: transferId ?? this.transferId,
      fileName: fileName ?? this.fileName,
      totalBytes: totalBytes ?? this.totalBytes,
      transferredBytes: transferredBytes ?? this.transferredBytes,
      percentage: percentage ?? this.percentage,
      status: status ?? this.status,
      error: error ?? this.error,
      timestamp: timestamp ?? this.timestamp,
    );
  }

  factory TransferProgress.started({
    required String transferId,
    required String fileName,
    required int totalBytes,
  }) {
    return TransferProgress(
      transferId: transferId,
      fileName: fileName,
      totalBytes: totalBytes,
      transferredBytes: 0,
      percentage: 0.0,
      status: TransferStatus.started,
      timestamp: DateTime.now(),
    );
  }

  TransferProgress updateProgress(int newTransferredBytes) {
    final newPercentage = totalBytes > 0
        ? (newTransferredBytes / totalBytes) * 100
        : 0.0;
    return copyWith(
      transferredBytes: newTransferredBytes,
      percentage: newPercentage.clamp(0.0, 100.0),
      status: newTransferredBytes >= totalBytes
          ? TransferStatus.completed
          : TransferStatus.inProgress,
      timestamp: DateTime.now(),
    );
  }

  TransferProgress completed() {
    return copyWith(
      transferredBytes: totalBytes,
      percentage: 100.0,
      status: TransferStatus.completed,
      timestamp: DateTime.now(),
    );
  }

  TransferProgress failed(String errorMessage) {
    return copyWith(
      status: TransferStatus.failed,
      error: errorMessage,
      timestamp: DateTime.now(),
    );
  }

  TransferProgress cancelled() {
    return copyWith(
      status: TransferStatus.cancelled,
      timestamp: DateTime.now(),
    );
  }
}

enum TransferStatus {
  started,
  inProgress,
  completed,
  failed,
  cancelled,
  paused,
  unknown,
}