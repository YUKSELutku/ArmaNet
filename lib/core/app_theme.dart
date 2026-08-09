import 'package:flutter/material.dart';

abstract final class ShieldColors {
  static const fog = Color(0xFFF3F7F8);
  static const ink = Color(0xFF132A33);
  static const glacier = Color(0xFFD7ECF1);
  static const signal = Color(0xFF1E6F78);
  static const blocked = Color(0xFFC44F5E);
  static const allowed = Color(0xFF3F7D62);
}

ThemeData buildShieldTheme() {
  final scheme =
      ColorScheme.fromSeed(
        seedColor: ShieldColors.signal,
        brightness: Brightness.light,
        surface: ShieldColors.fog,
      ).copyWith(
        primary: ShieldColors.signal,
        onPrimary: Colors.white,
        secondary: ShieldColors.allowed,
        error: ShieldColors.blocked,
        onSurface: ShieldColors.ink,
        surfaceContainer: Colors.white,
        surfaceContainerHigh: ShieldColors.glacier,
      );
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: ShieldColors.fog,
    fontFamily: 'sans-serif',
    textTheme: const TextTheme(
      displaySmall: TextStyle(
        fontFamily: 'sans-serif-condensed',
        fontWeight: FontWeight.w700,
        letterSpacing: -1.2,
      ),
      headlineMedium: TextStyle(
        fontFamily: 'sans-serif-condensed',
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
      ),
      titleLarge: TextStyle(fontWeight: FontWeight.w700),
      labelSmall: TextStyle(
        fontFamily: 'monospace',
        fontWeight: FontWeight.w600,
        letterSpacing: 0.5,
      ),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
    ),
    navigationBarTheme: const NavigationBarThemeData(
      height: 72,
      backgroundColor: Colors.white,
      indicatorColor: ShieldColors.glacier,
      labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(14),
        borderSide: BorderSide.none,
      ),
    ),
  );
}
