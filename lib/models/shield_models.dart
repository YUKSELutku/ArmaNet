import 'dart:typed_data';

int _integer(Object? value) => value is num ? value.toInt() : 0;

class ShieldStatistics {
  const ShieldStatistics({
    this.totalConnections = 0,
    this.blockedConnections = 0,
    this.allowedConnections = 0,
    this.advertisements = 0,
    this.trackers = 0,
    this.todayBlocked = 0,
  });

  factory ShieldStatistics.fromMap(Map<String, Object?> map) =>
      ShieldStatistics(
        totalConnections: _integer(map['totalConnections']),
        blockedConnections: _integer(map['blockedConnections']),
        allowedConnections: _integer(map['allowedConnections']),
        advertisements: _integer(map['advertisements']),
        trackers: _integer(map['trackers']),
        todayBlocked: _integer(map['todayBlocked']),
      );

  final int totalConnections;
  final int blockedConnections;
  final int allowedConnections;
  final int advertisements;
  final int trackers;
  final int todayBlocked;
}

class AppSummary {
  const AppSummary({
    required this.appName,
    required this.totalConnections,
    required this.blockedConnections,
    required this.allowedConnections,
    required this.advertisements,
    required this.trackers,
    this.packageName,
    this.uid,
    this.icon,
  });

  factory AppSummary.fromMap(Map<String, Object?> map) => AppSummary(
    packageName: map['packageName'] as String?,
    appName: map['appName'] as String? ?? 'Unknown App',
    uid: map['uid'] is num ? (map['uid'] as num).toInt() : null,
    icon: map['icon'] as Uint8List?,
    totalConnections: _integer(map['totalConnections']),
    blockedConnections: _integer(map['blockedConnections']),
    allowedConnections: _integer(map['allowedConnections']),
    advertisements: _integer(map['advertisements']),
    trackers: _integer(map['trackers']),
  );

  final String? packageName;
  final String appName;
  final int? uid;
  final Uint8List? icon;
  final int totalConnections;
  final int blockedConnections;
  final int allowedConnections;
  final int advertisements;
  final int trackers;
}

class DomainSummary {
  const DomainSummary({
    required this.domain,
    required this.category,
    required this.totalConnections,
    required this.blockedConnections,
    required this.allowedConnections,
    this.networkName,
  });

  factory DomainSummary.fromMap(Map<String, Object?> map) => DomainSummary(
    domain: map['domain'] as String? ?? 'Unknown domain',
    networkName: map['networkName'] as String?,
    category: map['category'] as String? ?? 'unknown',
    totalConnections: _integer(map['totalConnections']),
    blockedConnections: _integer(map['blockedConnections']),
    allowedConnections: _integer(map['allowedConnections']),
  );

  final String domain;
  final String? networkName;
  final String category;
  final int totalConnections;
  final int blockedConnections;
  final int allowedConnections;
}

class AppDetail extends AppSummary {
  const AppDetail({
    required super.appName,
    required super.totalConnections,
    required super.blockedConnections,
    required super.allowedConnections,
    required super.advertisements,
    required super.trackers,
    required this.domains,
    super.packageName,
    super.uid,
    super.icon,
  });

  factory AppDetail.fromMap(Map<String, Object?> map) {
    final summary = AppSummary.fromMap(map);
    final domains = (map['domains'] as List<Object?>? ?? const [])
        .whereType<Map>()
        .map(
          (item) => DomainSummary.fromMap(
            item.map((key, value) => MapEntry(key.toString(), value)),
          ),
        )
        .toList(growable: false);
    return AppDetail(
      packageName: summary.packageName,
      appName: summary.appName,
      uid: summary.uid,
      icon: summary.icon,
      totalConnections: summary.totalConnections,
      blockedConnections: summary.blockedConnections,
      allowedConnections: summary.allowedConnections,
      advertisements: summary.advertisements,
      trackers: summary.trackers,
      domains: domains,
    );
  }

  final List<DomainSummary> domains;
}

class TrafficRecord {
  const TrafficRecord({
    required this.id,
    required this.domain,
    required this.protocol,
    required this.category,
    required this.blocked,
    required this.timestamp,
    required this.attributionConfidence,
    this.uid,
    this.packageName,
    this.appName,
    this.destinationIp,
    this.destinationPort,
    this.networkName,
  });

  factory TrafficRecord.fromMap(Map<String, Object?> map) => TrafficRecord(
    id: _integer(map['id']),
    uid: map['uid'] is num ? (map['uid'] as num).toInt() : null,
    packageName: map['packageName'] as String?,
    appName: map['appName'] as String?,
    domain: map['domain'] as String? ?? 'Unknown domain',
    destinationIp: map['destinationIp'] as String?,
    destinationPort: map['destinationPort'] is num
        ? (map['destinationPort'] as num).toInt()
        : null,
    protocol: map['protocol'] as String? ?? 'Unknown',
    networkName: map['networkName'] as String?,
    category: map['category'] as String? ?? 'unknown',
    blocked: map['blocked'] as bool? ?? false,
    timestamp: DateTime.fromMillisecondsSinceEpoch(_integer(map['timestamp'])),
    attributionConfidence:
        map['attributionConfidence'] as String? ?? 'unavailable',
  );

  final int id;
  final int? uid;
  final String? packageName;
  final String? appName;
  final String domain;
  final String? destinationIp;
  final int? destinationPort;
  final String protocol;
  final String? networkName;
  final String category;
  final bool blocked;
  final DateTime timestamp;
  final String attributionConfidence;
}

class ShieldSettings {
  const ShieldSettings({this.historyLimit = 5000, this.debugMode = false});

  factory ShieldSettings.fromMap(Map<String, Object?> map) => ShieldSettings(
    historyLimit: _integer(map['historyLimit']),
    debugMode: map['debugMode'] as bool? ?? false,
  );

  final int historyLimit;
  final bool debugMode;
}

class TrafficQuery {
  const TrafficQuery({
    this.packageName,
    this.unknownApp = false,
    this.domain,
    this.blocked,
    this.category,
    this.sinceTimestamp,
    this.limit = 200,
  });

  final String? packageName;
  final bool unknownApp;
  final String? domain;
  final bool? blocked;
  final String? category;
  final int? sinceTimestamp;
  final int limit;

  Map<String, Object?> toMap() => {
    'packageName': packageName,
    'unknownApp': unknownApp,
    'domain': domain,
    'blocked': blocked,
    'category': category,
    'sinceTimestamp': sinceTimestamp,
    'limit': limit,
  };
}
