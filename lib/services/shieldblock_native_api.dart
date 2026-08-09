import 'package:flutter/services.dart';
import 'package:shieldblock/models/shield_models.dart';

class ShieldBlockNativeApi {
  ShieldBlockNativeApi({
    MethodChannel? methodChannel,
    EventChannel? eventChannel,
  }) : _methodChannel =
           methodChannel ?? const MethodChannel('com.shieldblock/native'),
       _eventChannel =
           eventChannel ?? const EventChannel('com.shieldblock/events');

  final MethodChannel _methodChannel;
  final EventChannel _eventChannel;

  Stream<NativeBridgeEvent> get events => _eventChannel
      .receiveBroadcastStream()
      .map((event) => NativeBridgeEvent.fromMap(_asMap(event)));

  Future<bool> requestVpnPermission() async =>
      await _methodChannel.invokeMethod<bool>('requestVpnPermission') ?? false;

  Future<bool> startVpn() async =>
      await _methodChannel.invokeMethod<bool>('startVpn') ?? false;

  Future<bool> stopVpn() async =>
      await _methodChannel.invokeMethod<bool>('stopVpn') ?? false;

  Future<bool> isVpnRunning() async =>
      await _methodChannel.invokeMethod<bool>('isVpnRunning') ?? false;

  Future<VpnStatus> getVpnStatus() async => VpnStatus.fromMap(
    _asMap(await _methodChannel.invokeMethod<Object?>('getVpnStatus')),
  );

  Future<FilterInfo> getFilterInfo() async => FilterInfo.fromMap(
    _asMap(await _methodChannel.invokeMethod<Object?>('getFilterInfo')),
  );

  Future<List<FilterRuleEntry>> getFilterRules() async => (await _invokeMapList(
    'getFilterRules',
  )).map(FilterRuleEntry.fromMap).toList(growable: false);

  Future<NativeFilterDecision> evaluateDomain(
    String domain, {
    Iterable<String> allowlist = const [],
  }) async => NativeFilterDecision.fromMap(
    _asMap(
      await _methodChannel.invokeMethod<Object?>('evaluateDomain', {
        'domain': domain,
        'allowlist': allowlist.toList(growable: false),
      }),
    ),
  );

  Future<ShieldStatistics> getStatistics() async =>
      ShieldStatistics.fromMap(await _invokeMap('getStatistics'));

  Future<List<AppSummary>> getApps() async =>
      (await _invokeMapList('getApps')).map(AppSummary.fromMap).toList();

  Future<AppDetail> getAppStats(String? packageName) async => AppDetail.fromMap(
    await _invokeMap('getAppStats', {
      'packageName': packageName,
      'unknownApp': packageName == null,
    }),
  );

  Future<List<TrafficRecord>> getRecentTraffic({
    TrafficQuery query = const TrafficQuery(),
  }) async => (await _invokeMapList(
    'getRecentTraffic',
    query.toMap(),
  )).map(TrafficRecord.fromMap).toList();

  Future<List<TrafficRecord>> getTrafficForApp(String packageName) async =>
      (await _invokeMapList('getTrafficForApp', {
        'packageName': packageName,
      })).map(TrafficRecord.fromMap).toList();

  Future<void> addAllowlistDomain(String domain) => _methodChannel
      .invokeMethod<void>('addAllowlistDomain', {'domain': domain});

  Future<void> removeAllowlistDomain(String domain) => _methodChannel
      .invokeMethod<void>('removeAllowlistDomain', {'domain': domain});

  Future<void> addCustomBlocklistDomain(String domain) => _methodChannel
      .invokeMethod<void>('addCustomBlocklistDomain', {'domain': domain});

  Future<void> removeCustomBlocklistDomain(String domain) => _methodChannel
      .invokeMethod<void>('removeCustomBlocklistDomain', {'domain': domain});

  Future<void> clearHistory() =>
      _methodChannel.invokeMethod<void>('clearHistory');

  Future<void> clearStatistics() =>
      _methodChannel.invokeMethod<void>('clearStatistics');

  Future<List<String>> getAllowlist() async =>
      (await _methodChannel.invokeMethod<List<Object?>>(
        'getAllowlist',
      ))?.whereType<String>().toList(growable: false) ??
      const [];

  Future<List<String>> getCustomBlocklist() async =>
      (await _methodChannel.invokeMethod<List<Object?>>(
        'getCustomBlocklist',
      ))?.whereType<String>().toList(growable: false) ??
      const [];

  Future<ShieldSettings> getSettings() async =>
      ShieldSettings.fromMap(await _invokeMap('getSettings'));

  Future<ShieldSettings> updateSettings({
    int? historyLimit,
    bool? debugMode,
  }) async => ShieldSettings.fromMap(
    await _invokeMap('updateSettings', {
      'historyLimit': historyLimit,
      'debugMode': debugMode,
    }),
  );

  Future<Map<String, Object?>> _invokeMap(
    String method, [
    Map<String, Object?>? arguments,
  ]) async =>
      _asMap(await _methodChannel.invokeMethod<Object?>(method, arguments));

  Future<List<Map<String, Object?>>> _invokeMapList(
    String method, [
    Map<String, Object?>? arguments,
  ]) async {
    final value = await _methodChannel.invokeMethod<List<Object?>>(
      method,
      arguments,
    );
    return value?.map(_asMap).toList(growable: false) ?? const [];
  }

  static Map<String, Object?> _asMap(Object? value) {
    if (value is! Map) {
      throw const FormatException('Expected a platform map response.');
    }
    return value.map((key, item) => MapEntry(key.toString(), item));
  }
}

class VpnStatus {
  const VpnStatus({
    required this.state,
    required this.running,
    this.detail,
    this.dnsQueries = 0,
    this.blockedQueries = 0,
    this.packetErrors = 0,
    this.lastDnsAt,
    this.lastError,
    this.privateDnsActive = false,
  });

  factory VpnStatus.fromMap(Map<String, Object?> map) => VpnStatus(
    state: map['state'] as String? ?? 'unknown',
    running: map['running'] as bool? ?? false,
    detail: map['detail'] as String?,
    dnsQueries: (map['dnsQueries'] as num?)?.toInt() ?? 0,
    blockedQueries: (map['blockedQueries'] as num?)?.toInt() ?? 0,
    packetErrors: (map['packetErrors'] as num?)?.toInt() ?? 0,
    lastDnsAt: map['lastDnsAt'] is num
        ? DateTime.fromMillisecondsSinceEpoch((map['lastDnsAt'] as num).toInt())
        : null,
    lastError: map['lastError'] as String?,
    privateDnsActive: map['privateDnsActive'] as bool? ?? false,
  );

  final String state;
  final bool running;
  final String? detail;
  final int dnsQueries;
  final int blockedQueries;
  final int packetErrors;
  final DateTime? lastDnsAt;
  final String? lastError;
  final bool privateDnsActive;
}

class FilterInfo {
  const FilterInfo({
    required this.version,
    required this.updatedAt,
    required this.advertisementRules,
    required this.trackerRules,
    required this.totalRules,
  });

  factory FilterInfo.fromMap(Map<String, Object?> map) => FilterInfo(
    version: (map['version'] as num).toInt(),
    updatedAt: map['updatedAt'] as String,
    advertisementRules: (map['advertisementRules'] as num).toInt(),
    trackerRules: (map['trackerRules'] as num).toInt(),
    totalRules: (map['totalRules'] as num).toInt(),
  );

  final int version;
  final String updatedAt;
  final int advertisementRules;
  final int trackerRules;
  final int totalRules;
}

class FilterRuleEntry {
  const FilterRuleEntry({
    required this.domain,
    required this.network,
    required this.category,
  });

  factory FilterRuleEntry.fromMap(Map<String, Object?> map) => FilterRuleEntry(
    domain: map['domain'] as String? ?? '',
    network: map['network'] as String? ?? 'Unknown network',
    category: map['category'] as String? ?? 'unknown',
  );

  final String domain;
  final String network;
  final String category;
}

class NativeFilterDecision {
  const NativeFilterDecision({
    required this.action,
    required this.blocked,
    required this.category,
    required this.allowlistOverride,
    this.normalizedDomain,
    this.matchedDomain,
    this.network,
  });

  factory NativeFilterDecision.fromMap(Map<String, Object?> map) =>
      NativeFilterDecision(
        action: map['action'] as String? ?? 'allow',
        blocked: map['blocked'] as bool? ?? false,
        normalizedDomain: map['normalizedDomain'] as String?,
        matchedDomain: map['matchedDomain'] as String?,
        network: map['network'] as String?,
        category: map['category'] as String? ?? 'unknown',
        allowlistOverride: map['allowlistOverride'] as bool? ?? false,
      );

  final String action;
  final bool blocked;
  final String? normalizedDomain;
  final String? matchedDomain;
  final String? network;
  final String category;
  final bool allowlistOverride;
}

class NativeBridgeEvent {
  const NativeBridgeEvent({required this.type, required this.payload});

  factory NativeBridgeEvent.fromMap(Map<String, Object?> map) =>
      NativeBridgeEvent(
        type: map['type'] as String? ?? 'unknown',
        payload: map,
      );

  final String type;
  final Map<String, Object?> payload;
}
