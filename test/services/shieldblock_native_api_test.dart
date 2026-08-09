import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shieldblock/services/shieldblock_native_api.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('shieldblock.test/native');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
  late ShieldBlockNativeApi api;

  setUp(() {
    api = ShieldBlockNativeApi(methodChannel: channel);
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  test('parses versioned filter information from native', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getFilterInfo');
      return <String, Object?>{
        'version': 2,
        'updatedAt': '2026-08-09',
        'advertisementRules': 48,
        'trackerRules': 53,
        'totalRules': 101,
      };
    });

    final info = await api.getFilterInfo();

    expect(info.version, 2);
    expect(info.updatedAt, '2026-08-09');
    expect(info.advertisementRules, 48);
    expect(info.trackerRules, 53);
    expect(info.totalRules, 101);
  });

  test('parses the embedded ruleset ledger from native', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getFilterRules');
      return <Object?>[
        <String, Object?>{
          'domain': 'doubleclick.net',
          'network': 'Google Ads',
          'category': 'advertisement',
        },
        <String, Object?>{
          'domain': 'app-measurement.com',
          'network': 'Google Analytics',
          'category': 'tracker',
        },
      ];
    });

    final rules = await api.getFilterRules();

    expect(rules, hasLength(2));
    expect(rules.first.domain, 'doubleclick.net');
    expect(rules.first.network, 'Google Ads');
    expect(rules.last.category, 'tracker');
  });

  test('passes domain and allowlist to native evaluation', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'evaluateDomain');
      expect(call.arguments, <String, Object?>{
        'domain': 'ads.example.com',
        'allowlist': <String>['safe.example.com'],
      });
      return <String, Object?>{
        'action': 'block',
        'blocked': true,
        'normalizedDomain': 'ads.example.com',
        'matchedDomain': 'example.com',
        'network': 'Test Network',
        'category': 'advertisement',
        'allowlistOverride': false,
      };
    });

    final decision = await api.evaluateDomain(
      'ads.example.com',
      allowlist: const ['safe.example.com'],
    );

    expect(decision.blocked, isTrue);
    expect(decision.action, 'block');
    expect(decision.normalizedDomain, 'ads.example.com');
    expect(decision.matchedDomain, 'example.com');
    expect(decision.network, 'Test Network');
    expect(decision.category, 'advertisement');
    expect(decision.allowlistOverride, isFalse);
  });

  test(
    'persists and retrieves custom blocklist domains through native',
    () async {
      final calls = <MethodCall>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call);
        return switch (call.method) {
          'getCustomBlocklist' => <Object?>['functional.example.com'],
          'addCustomBlocklistDomain' || 'removeCustomBlocklistDomain' => null,
          _ => throw PlatformException(code: 'UNEXPECTED_METHOD'),
        };
      });

      await api.addCustomBlocklistDomain('functional.example.com');
      final domains = await api.getCustomBlocklist();
      await api.removeCustomBlocklistDomain('functional.example.com');

      expect(domains, ['functional.example.com']);
      expect(calls.map((call) => call.method), [
        'addCustomBlocklistDomain',
        'getCustomBlocklist',
        'removeCustomBlocklistDomain',
      ]);
      expect(calls.first.arguments, {'domain': 'functional.example.com'});
      expect(calls.last.arguments, {'domain': 'functional.example.com'});
    },
  );

  test('parses a running VPN status', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getVpnStatus');
      return <String, Object?>{
        'state': 'running',
        'running': true,
        'detail': 'DNS protection is active.',
        'dnsQueries': 42,
        'blockedQueries': 9,
        'packetErrors': 0,
        'lastDnsAt': 1786260000000,
        'privateDnsActive': false,
      };
    });

    final status = await api.getVpnStatus();

    expect(status.state, 'running');
    expect(status.running, isTrue);
    expect(status.detail, isNotEmpty);
    expect(status.dnsQueries, 42);
    expect(status.blockedQueries, 9);
    expect(status.packetErrors, 0);
    expect(status.lastDnsAt, isNotNull);
    expect(status.privateDnsActive, isFalse);
  });

  test('does not hide native validation errors', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      throw PlatformException(
        code: 'INVALID_ARGUMENT',
        message: '${call.method} received invalid arguments.',
      );
    });

    await expectLater(
      api.getStatistics(),
      throwsA(
        isA<PlatformException>().having(
          (error) => error.code,
          'code',
          'INVALID_ARGUMENT',
        ),
      ),
    );
  });

  test('requests real aggregated details for Unknown App', () async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'getAppStats');
      expect(call.arguments, <String, Object?>{
        'packageName': null,
        'unknownApp': true,
      });
      return <String, Object?>{
        'packageName': null,
        'appName': 'Unknown App',
        'totalConnections': 3,
        'blockedConnections': 2,
        'allowedConnections': 1,
        'advertisements': 2,
        'trackers': 0,
        'domains': <Object?>[
          <String, Object?>{
            'domain': 'doubleclick.net',
            'networkName': 'Google Ads',
            'category': 'advertisement',
            'totalConnections': 2,
            'blockedConnections': 2,
            'allowedConnections': 0,
          },
        ],
      };
    });

    final detail = await api.getAppStats(null);

    expect(detail.appName, 'Unknown App');
    expect(detail.packageName, isNull);
    expect(detail.totalConnections, 3);
    expect(detail.domains.single.domain, 'doubleclick.net');
  });
}
