import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/features/apps/app_detail_page.dart';
import 'package:shieldblock/presentation/widgets/shield_widgets.dart';

class AppsPage extends StatefulWidget {
  const AppsPage({super.key, required this.controller});

  final AppController controller;

  @override
  State<AppsPage> createState() => _AppsPageState();
}

class _AppsPageState extends State<AppsPage> {
  String query = '';

  @override
  Widget build(BuildContext context) {
    final apps = widget.controller.apps.where((app) {
      final needle = query.toLowerCase();
      return app.appName.toLowerCase().contains(needle) ||
          (app.packageName?.toLowerCase().contains(needle) ?? false);
    }).toList();
    return Column(
      children: [
        const PageHeading(
          'Apps',
          subtitle: 'Launchable apps and their observed DNS activity.',
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20),
          child: TextField(
            decoration: const InputDecoration(
              hintText: 'Search apps or packages',
              prefixIcon: Icon(Icons.search),
            ),
            onChanged: (value) => setState(() => query = value),
          ),
        ),
        const SizedBox(height: 8),
        Expanded(
          child: apps.isEmpty
              ? const EmptyState(
                  icon: Icons.apps_outlined,
                  title: 'No matching apps',
                  message: 'Try a different app or package name.',
                )
              : RefreshIndicator(
                  onRefresh: widget.controller.reloadData,
                  child: ListView.separated(
                    physics: const AlwaysScrollableScrollPhysics(),
                    itemCount: apps.length,
                    separatorBuilder: (_, _) =>
                        const Divider(height: 1, indent: 80),
                    itemBuilder: (context, index) {
                      final app = apps[index];
                      return ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 20,
                          vertical: 6,
                        ),
                        leading: AppAvatar(bytes: app.icon, name: app.appName),
                        title: Text(app.appName),
                        subtitle: Text(
                          '${app.blockedConnections} blocked · ${app.totalConnections} connections',
                        ),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () => Navigator.of(context).push(
                          MaterialPageRoute<void>(
                            builder: (_) => AppDetailPage(
                              controller: widget.controller,
                              packageName: app.packageName,
                            ),
                          ),
                        ),
                        titleTextStyle: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(color: ShieldColors.ink),
                      );
                    },
                  ),
                ),
        ),
      ],
    );
  }
}
