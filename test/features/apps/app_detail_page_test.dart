import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/features/apps/app_detail_page.dart';
import 'package:shieldblock/services/shieldblock_native_api.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const channel = MethodChannel('shieldblock.test/app-detail');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() {
    messenger.setMockMethodCallHandler(channel, null);
  });

  testWidgets('warns before an allowed domain is custom blocked', (
    tester,
  ) async {
    final calls = <MethodCall>[];
    messenger.setMockMethodCallHandler(channel, (call) async {
      calls.add(call);
      return switch (call.method) {
        'getAppStats' => <String, Object?>{
          'packageName': 'com.example.game',
          'appName': 'Example Game',
          'totalConnections': 1,
          'blockedConnections': 0,
          'allowedConnections': 1,
          'advertisements': 0,
          'trackers': 0,
          'domains': <Object?>[
            <String, Object?>{
              'domain': 'functional.example.com',
              'category': 'unknown',
              'totalConnections': 1,
              'blockedConnections': 0,
              'allowedConnections': 1,
            },
          ],
        },
        'addCustomBlocklistDomain' => null,
        'getCustomBlocklist' => <Object?>['functional.example.com'],
        _ => throw PlatformException(code: 'UNEXPECTED_METHOD'),
      };
    });
    final controller = AppController(
      api: ShieldBlockNativeApi(methodChannel: channel),
    );
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        home: AppDetailPage(
          controller: controller,
          packageName: 'com.example.game',
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Block this domain'));
    await tester.pumpAndSettle();

    expect(find.text('Block this domain?'), findsOneWidget);
    expect(
      find.textContaining('may break sign-in, media, notifications'),
      findsOneWidget,
    );
    expect(find.textContaining('for every app on this device'), findsOneWidget);

    await tester.tap(find.text('Block anyway'));
    await tester.pumpAndSettle();

    expect(
      calls.map((call) => call.method),
      containsAllInOrder([
        'getAppStats',
        'addCustomBlocklistDomain',
        'getCustomBlocklist',
      ]),
    );
    expect(controller.customBlocklist, ['functional.example.com']);
  });
}
