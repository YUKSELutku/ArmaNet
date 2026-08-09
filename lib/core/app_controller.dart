import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:shieldblock/models/shield_models.dart';
import 'package:shieldblock/services/shieldblock_native_api.dart';

class AppController extends ChangeNotifier {
  AppController({ShieldBlockNativeApi? api})
    : api = api ?? ShieldBlockNativeApi();

  final ShieldBlockNativeApi api;
  VpnStatus vpnStatus = const VpnStatus(state: 'stopped', running: false);
  ShieldStatistics statistics = const ShieldStatistics();
  List<AppSummary> apps = const [];
  List<TrafficRecord> traffic = const [];
  List<String> allowlist = const [];
  List<String> customBlocklist = const [];
  ShieldSettings settings = const ShieldSettings();
  FilterInfo? filterInfo;
  bool loading = true;
  bool protectionBusy = false;
  String? errorMessage;

  StreamSubscription<NativeBridgeEvent>? _eventSubscription;
  Timer? _refreshDebounce;
  bool _refreshInFlight = false;
  bool _refreshPending = false;
  bool _disposed = false;
  TrafficQuery _trafficQuery = const TrafficQuery();
  DateTime _lastAppsRefresh = DateTime.fromMillisecondsSinceEpoch(0);

  Future<void> initialize() async {
    _eventSubscription = api.events.listen(
      _onNativeEvent,
      onError: (Object error) => _setError(_friendlyError(error)),
    );
    await reloadAll();
  }

  Future<void> reloadAll() async {
    loading = true;
    errorMessage = null;
    notifyListeners();
    try {
      final values = await Future.wait<Object>([
        api.getVpnStatus(),
        api.getStatistics(),
        api.getApps(),
        api.getRecentTraffic(query: _trafficQuery),
        api.getAllowlist(),
        api.getCustomBlocklist(),
        api.getSettings(),
        api.getFilterInfo(),
      ]);
      vpnStatus = values[0] as VpnStatus;
      statistics = values[1] as ShieldStatistics;
      apps = values[2] as List<AppSummary>;
      _lastAppsRefresh = DateTime.now();
      traffic = values[3] as List<TrafficRecord>;
      allowlist = values[4] as List<String>;
      customBlocklist = values[5] as List<String>;
      settings = values[6] as ShieldSettings;
      filterInfo = values[7] as FilterInfo;
    } catch (error) {
      errorMessage = _friendlyError(error);
    } finally {
      loading = false;
      notifyListeners();
    }
  }

  Future<void> toggleProtection(bool enabled) async {
    if (protectionBusy) return;
    protectionBusy = true;
    errorMessage = null;
    notifyListeners();
    try {
      if (enabled) {
        final granted = await api.requestVpnPermission();
        if (!granted) {
          throw StateError(
            'VPN and notification permissions are required to start protection.',
          );
        }
        await api.startVpn();
        vpnStatus = await _waitForProtection(expectedRunning: true);
      } else {
        await api.stopVpn();
        vpnStatus = await _waitForProtection(expectedRunning: false);
      }
    } catch (error) {
      errorMessage = _friendlyError(error);
    } finally {
      protectionBusy = false;
      notifyListeners();
    }
  }

  Future<VpnStatus> _waitForProtection({required bool expectedRunning}) async {
    final deadline = DateTime.now().add(const Duration(seconds: 10));
    VpnStatus status = await api.getVpnStatus();
    while (DateTime.now().isBefore(deadline)) {
      if (status.running == expectedRunning &&
          (expectedRunning || status.state == 'stopped')) {
        return status;
      }
      if (status.state == 'error') {
        throw StateError(status.detail ?? 'The VPN service reported an error.');
      }
      await Future<void>.delayed(const Duration(milliseconds: 250));
      status = await api.getVpnStatus();
    }
    throw StateError(
      expectedRunning
          ? 'Protection did not start within 10 seconds.'
          : 'Protection did not stop within 10 seconds.',
    );
  }

  Future<void> updateTrafficQuery(TrafficQuery query) async {
    _trafficQuery = query;
    try {
      traffic = await api.getRecentTraffic(query: query);
      errorMessage = null;
    } catch (error) {
      errorMessage = _friendlyError(error);
    }
    notifyListeners();
  }

  Future<AppDetail> loadAppDetail(String? packageName) =>
      api.getAppStats(packageName);

  Future<List<FilterRuleEntry>> loadFilterRules() => api.getFilterRules();

  Future<void> addAllowlist(String domain) async {
    await api.addAllowlistDomain(domain);
    allowlist = await api.getAllowlist();
    notifyListeners();
  }

  Future<void> removeAllowlist(String domain) async {
    await api.removeAllowlistDomain(domain);
    allowlist = await api.getAllowlist();
    notifyListeners();
  }

  Future<void> addCustomBlocklist(String domain) async {
    await api.addCustomBlocklistDomain(domain);
    customBlocklist = await api.getCustomBlocklist();
    notifyListeners();
  }

  Future<void> removeCustomBlocklist(String domain) async {
    await api.removeCustomBlocklistDomain(domain);
    customBlocklist = await api.getCustomBlocklist();
    notifyListeners();
  }

  String? matchingAllowlistRule(String domain) =>
      _matchingDomainRule(domain, allowlist);

  List<String> matchingAllowlistRules(String domain) =>
      _matchingDomainRules(domain, allowlist);

  String? matchingCustomBlocklistRule(String domain) =>
      _matchingDomainRule(domain, customBlocklist);

  String? _matchingDomainRule(String rawDomain, Iterable<String> rules) {
    final matches = _matchingDomainRules(rawDomain, rules);
    return matches.isEmpty ? null : matches.first;
  }

  List<String> _matchingDomainRules(String rawDomain, Iterable<String> rules) {
    final domain = rawDomain.trim().toLowerCase().replaceFirst(
      RegExp(r'\.$'),
      '',
    );
    final matches = rules.where((rule) {
      final normalizedRule = rule.toLowerCase();
      return domain == normalizedRule || domain.endsWith('.$normalizedRule');
    }).toList();
    matches.sort((left, right) => right.length.compareTo(left.length));
    return matches;
  }

  Future<void> clearHistory() async {
    await api.clearHistory();
    await reloadData();
  }

  Future<void> clearStatistics() async {
    await api.clearStatistics();
    await reloadData();
  }

  Future<void> updateHistoryLimit(int value) async {
    settings = await api.updateSettings(historyLimit: value);
    notifyListeners();
  }

  Future<void> updateDebugMode(bool value) async {
    settings = await api.updateSettings(debugMode: value);
    notifyListeners();
  }

  Future<void> reloadData() async {
    try {
      final values = await Future.wait<Object>([
        api.getVpnStatus(),
        api.getStatistics(),
        api.getApps(),
        api.getRecentTraffic(query: _trafficQuery),
      ]);
      vpnStatus = values[0] as VpnStatus;
      statistics = values[1] as ShieldStatistics;
      apps = values[2] as List<AppSummary>;
      _lastAppsRefresh = DateTime.now();
      traffic = values[3] as List<TrafficRecord>;
      errorMessage = null;
    } catch (error) {
      errorMessage = _friendlyError(error);
    }
    notifyListeners();
  }

  void clearError() {
    errorMessage = null;
    notifyListeners();
  }

  void _onNativeEvent(NativeBridgeEvent event) {
    if (_disposed) return;
    final statusPayload = event.payload['status'];
    if (statusPayload is Map) {
      vpnStatus = VpnStatus.fromMap(
        statusPayload.map((key, value) => MapEntry(key.toString(), value)),
      );
      notifyListeners();
    }
    if (event.type == 'VPN_RUNTIME_WARNING') {
      errorMessage =
          'VPN warning: ${event.payload['message'] ?? 'A DNS packet could not be processed.'}';
      notifyListeners();
    }
    _scheduleLiveRefresh();
  }

  void _scheduleLiveRefresh() {
    _refreshPending = true;
    if (_disposed ||
        _refreshInFlight ||
        (_refreshDebounce?.isActive ?? false)) {
      return;
    }
    _refreshDebounce = Timer(const Duration(milliseconds: 400), () async {
      if (_disposed) return;
      _refreshPending = false;
      _refreshInFlight = true;
      await _reloadLiveActivity();
      _refreshInFlight = false;
      if (_refreshPending) _scheduleLiveRefresh();
    });
  }

  Future<void> _reloadLiveActivity() async {
    try {
      final values = await Future.wait<Object>([
        api.getVpnStatus(),
        api.getStatistics(),
        api.getRecentTraffic(query: _trafficQuery),
      ]);
      vpnStatus = values[0] as VpnStatus;
      statistics = values[1] as ShieldStatistics;
      traffic = values[2] as List<TrafficRecord>;
      if (DateTime.now().difference(_lastAppsRefresh) >
          const Duration(seconds: 30)) {
        apps = await api.getApps();
        _lastAppsRefresh = DateTime.now();
      }
      errorMessage = null;
    } catch (error) {
      errorMessage = _friendlyError(error);
    }
    if (!_disposed) notifyListeners();
  }

  void _setError(String error) {
    if (_disposed) return;
    errorMessage = error;
    notifyListeners();
  }

  String _friendlyError(Object error) {
    if (error is PlatformException) {
      return error.message ?? error.code;
    }
    return error.toString().replaceFirst('Bad state: ', '');
  }

  @override
  void dispose() {
    _disposed = true;
    _refreshDebounce?.cancel();
    _eventSubscription?.cancel();
    super.dispose();
  }
}
