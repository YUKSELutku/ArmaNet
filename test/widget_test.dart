import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/presentation/shield_shell.dart';

void main() {
  testWidgets('renders four-section ArmaNet shell', (tester) async {
    final controller = AppController();
    addTearDown(controller.dispose);

    await tester.pumpWidget(
      MaterialApp(
        theme: buildShieldTheme(),
        home: ShieldShell(controller: controller),
      ),
    );

    expect(find.text('ArmaNet'), findsOneWidget);
    expect(find.text('Home'), findsOneWidget);
    expect(find.text('Apps'), findsOneWidget);
    expect(find.text('Traffic'), findsOneWidget);
    expect(find.text('Settings'), findsOneWidget);
    expect(find.text('OFF'), findsOneWidget);

    await tester.tap(find.text('Traffic'));
    await tester.pumpAndSettle();
    expect(
      find.text('Recent DNS metadata. No HTTPS content is inspected.'),
      findsOneWidget,
    );
  });
}
