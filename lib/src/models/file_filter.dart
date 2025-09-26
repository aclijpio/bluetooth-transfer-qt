import 'dart:io';

/// File filter for excluding certain file types from transfer
abstract class FileFilter {
  String get id;
  bool allows(String filePath);
}

/// Extension-based file filter
class ExtensionFileFilter extends FileFilter {
  @override
  final String id;
  final Set<String> _allowedExtensions;
  final Set<String> _blockedExtensions;
  final bool _isWhitelist;

  ExtensionFileFilter.allow({
    required this.id,
    required List<String> extensions,
  })  : _allowedExtensions = extensions.map((e) => e.toLowerCase()).toSet(),
        _blockedExtensions = {},
        _isWhitelist = true;

  ExtensionFileFilter.block({
    required this.id,
    required List<String> extensions,
  })  : _allowedExtensions = {},
        _blockedExtensions = extensions.map((e) => e.toLowerCase()).toSet(),
        _isWhitelist = false;

  @override
  bool allows(String filePath) {
    final extension = _getExtension(filePath);

    if (_isWhitelist) {
      return _allowedExtensions.contains(extension);
    } else {
      return !_blockedExtensions.contains(extension);
    }
  }

  String _getExtension(String filePath) {
    final file = File(filePath);
    final name = file.path.split('/').last;
    final dotIndex = name.lastIndexOf('.');
    if (dotIndex == -1) return '';
    return name.substring(dotIndex).toLowerCase();
  }
}

/// Size-based file filter
class SizeFileFilter extends FileFilter {
  @override
  final String id;
  final int? maxSizeBytes;
  final int? minSizeBytes;

  SizeFileFilter({
    required this.id,
    this.maxSizeBytes,
    this.minSizeBytes,
  });

  @override
  bool allows(String filePath) {
    try {
      final file = File(filePath);
      if (!file.existsSync()) return false;

      final size = file.lengthSync();

      if (minSizeBytes != null && size < minSizeBytes!) return false;
      if (maxSizeBytes != null && size > maxSizeBytes!) return false;

      return true;
    } catch (e) {
      return false;
    }
  }
}

/// Pattern-based file filter
class PatternFileFilter extends FileFilter {
  @override
  final String id;
  final RegExp _pattern;
  final bool _shouldMatch;

  PatternFileFilter.allow({
    required this.id,
    required String pattern,
  })  : _pattern = RegExp(pattern, caseSensitive: false),
        _shouldMatch = true;

  PatternFileFilter.block({
    required this.id,
    required String pattern,
  })  : _pattern = RegExp(pattern, caseSensitive: false),
        _shouldMatch = false;

  @override
  bool allows(String filePath) {
    final fileName = File(filePath).path.split('/').last;
    final matches = _pattern.hasMatch(fileName);
    return _shouldMatch ? matches : !matches;
  }
}

/// Composite file filter (combines multiple filters with AND/OR logic)
class CompositeFileFilter extends FileFilter {
  @override
  final String id;
  final List<FileFilter> _filters;
  final bool _requireAll; // true = AND, false = OR

  CompositeFileFilter.all({
    required this.id,
    required List<FileFilter> filters,
  })  : _filters = filters,
        _requireAll = true;

  CompositeFileFilter.any({
    required this.id,
    required List<FileFilter> filters,
  })  : _filters = filters,
        _requireAll = false;

  @override
  bool allows(String filePath) {
    if (_filters.isEmpty) return true;

    if (_requireAll) {
      return _filters.every((filter) => filter.allows(filePath));
    } else {
      return _filters.any((filter) => filter.allows(filePath));
    }
  }
}

/// Predefined common filters
class CommonFileFilters {
  /// Block APK files
  static FileFilter get blockApk => ExtensionFileFilter.block(
        id: 'block_apk',
        extensions: ['.apk'],
      );

  /// Block executables
  static FileFilter get blockExecutables => ExtensionFileFilter.block(
        id: 'block_executables',
        extensions: ['.exe', '.msi', '.bat', '.cmd', '.com', '.scr'],
      );

  /// Block large files (>100MB)
  static FileFilter get blockLargeFiles => SizeFileFilter(
        id: 'block_large_files',
        maxSizeBytes: 100 * 1024 * 1024,
      );

  /// Allow only images
  static FileFilter get allowOnlyImages => ExtensionFileFilter.allow(
        id: 'allow_only_images',
        extensions: ['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp'],
      );

  /// Allow only documents
  static FileFilter get allowOnlyDocuments => ExtensionFileFilter.allow(
        id: 'allow_only_documents',
        extensions: ['.pdf', '.doc', '.docx', '.txt', '.rtf', '.odt'],
      );

  /// Safe files (no executables, no APK, reasonable size)
  static FileFilter get safeFiles => CompositeFileFilter.all(
        id: 'safe_files',
        filters: [blockExecutables, blockApk, blockLargeFiles],
      );
}
