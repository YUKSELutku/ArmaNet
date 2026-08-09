import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/presentation/shield_shell.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ShieldBlockApp());
}

class ShieldBlockApp extends StatefulWidget {
  const ShieldBlockApp({super.key});

  @override
  State<ShieldBlockApp> createState() => _ShieldBlockAppState();
}

class _ShieldBlockAppState extends State<ShieldBlockApp> {
  late final AppController controller;

  @override
  void initState() {
    super.initState();
    controller = AppController()..initialize();
  }

  @override
  void dispose() {
    controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'ArmaNet',
    debugShowCheckedModeBanner: false,
    theme: buildShieldTheme(),
    home: ShieldShell(controller: controller),
  );
}
