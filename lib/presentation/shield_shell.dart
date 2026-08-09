import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/features/apps/apps_page.dart';
import 'package:shieldblock/features/home/home_page.dart';
import 'package:shieldblock/features/settings/settings_page.dart';
import 'package:shieldblock/features/traffic/traffic_page.dart';

class ShieldShell extends StatefulWidget {
  const ShieldShell({super.key, required this.controller});

  final AppController controller;

  @override
  State<ShieldShell> createState() => _ShieldShellState();
}

class _ShieldShellState extends State<ShieldShell> {
  int index = 0;

  @override
  Widget build(BuildContext context) => AnimatedBuilder(
    animation: widget.controller,
    builder: (context, _) => Scaffold(
      body: SafeArea(
        child: Stack(
          children: [
            IndexedStack(
              index: index,
              children: [
                HomePage(controller: widget.controller),
                AppsPage(controller: widget.controller),
                TrafficPage(controller: widget.controller),
                SettingsPage(controller: widget.controller),
              ],
            ),
            if (widget.controller.errorMessage != null)
              Positioned(
                left: 12,
                right: 12,
                bottom: 8,
                child: MaterialBanner(
                  content: Text(widget.controller.errorMessage!),
                  backgroundColor: Theme.of(context).colorScheme.errorContainer,
                  actions: [
                    TextButton(
                      onPressed: widget.controller.clearError,
                      child: const Text('Dismiss'),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (value) => setState(() => index = value),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.radar), label: 'Home'),
          NavigationDestination(icon: Icon(Icons.apps_rounded), label: 'Apps'),
          NavigationDestination(
            icon: Icon(Icons.route_rounded),
            label: 'Traffic',
          ),
          NavigationDestination(
            icon: Icon(Icons.tune_rounded),
            label: 'Settings',
          ),
        ],
      ),
    ),
  );
}
