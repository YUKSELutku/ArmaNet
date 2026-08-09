import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/features/settings/filter_rules_page.dart';
import 'package:shieldblock/presentation/widgets/shield_widgets.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) => ListView(
    children: [
      const PageHeading(
        'Settings',
        subtitle: 'Local controls. Nothing is synced to an ArmaNet server.',
      ),
      SwitchListTile(
        title: const Text('Protection'),
        subtitle: Text(
          controller.vpnStatus.running ? 'VPN active' : 'VPN stopped',
        ),
        value: controller.vpnStatus.running,
        onChanged: controller.protectionBusy
            ? null
            : controller.toggleProtection,
      ),
      const Divider(indent: 20, endIndent: 20),
      const SectionLabel('Allowlist'),
      ...controller.allowlist.map(
        (domain) => ListTile(
          leading: const Icon(Icons.check_circle_outline),
          title: Text(domain),
          subtitle: Text('$domain and all subdomains are allowed'),
          trailing: IconButton(
            tooltip: 'Remove from allowlist',
            onPressed: () => controller.removeAllowlist(domain),
            icon: const Icon(Icons.delete_outline),
          ),
        ),
      ),
      ListTile(
        leading: const Icon(Icons.add_circle_outline),
        title: const Text('Add allowlist domain'),
        onTap: () => _showAllowlistDialog(context),
      ),
      const SectionLabel('Custom blocklist'),
      if (controller.customBlocklist.isEmpty)
        const ListTile(
          leading: Icon(Icons.info_outline),
          title: Text('No custom blocks'),
          subtitle: Text(
            'Allowed domains can be blocked from an app activity screen.',
          ),
        ),
      ...controller.customBlocklist.map(
        (domain) => ListTile(
          leading: const Icon(Icons.block_outlined),
          title: Text(domain),
          subtitle: Text(
            '$domain and all subdomains are blocked for every app',
          ),
          trailing: IconButton(
            tooltip: 'Remove custom block',
            onPressed: () => controller.removeCustomBlocklist(domain),
            icon: const Icon(Icons.delete_outline),
          ),
        ),
      ),
      const SectionLabel('Data'),
      ListTile(
        leading: const Icon(Icons.delete_sweep_outlined),
        title: const Text('Clear traffic history'),
        subtitle: Text(
          'Up to ${controller.settings.historyLimit} recent events are kept',
        ),
        onTap: () => _confirmClear(context, statisticsOnly: false),
      ),
      ListTile(
        leading: const Icon(Icons.query_stats_outlined),
        title: const Text('Clear statistics'),
        subtitle: const Text('Reset counters derived from retained traffic'),
        onTap: () => _confirmClear(context, statisticsOnly: true),
      ),
      ListTile(
        leading: const Icon(Icons.storage_outlined),
        title: const Text('History limit'),
        subtitle: const Text('Older records are removed automatically'),
        trailing: DropdownButton<int>(
          value: controller.settings.historyLimit,
          items: const [1000, 5000, 10000, 25000, 50000]
              .map(
                (value) =>
                    DropdownMenuItem<int>(value: value, child: Text('$value')),
              )
              .toList(),
          onChanged: (value) {
            if (value != null) controller.updateHistoryLimit(value);
          },
        ),
      ),
      SwitchListTile(
        secondary: const Icon(Icons.bug_report_outlined),
        title: const Text('Debug mode'),
        subtitle: const Text(
          'Show UID, protocol, IP, and attribution confidence',
        ),
        value: controller.settings.debugMode,
        onChanged: controller.updateDebugMode,
      ),
      if (controller.settings.debugMode)
        ListTile(
          leading: const Icon(Icons.monitor_heart_outlined),
          title: const Text('VPN diagnostics'),
          subtitle: Text(
            '${controller.vpnStatus.dnsQueries} DNS inspected · '
            '${controller.vpnStatus.blockedQueries} blocked · '
            '${controller.vpnStatus.packetErrors} packet errors\n'
            'Last DNS: ${controller.vpnStatus.lastDnsAt?.toIso8601String() ?? 'None'}'
            '${controller.vpnStatus.lastError == null ? '' : '\nLast error: ${controller.vpnStatus.lastError}'}',
          ),
          isThreeLine: true,
        ),
      const SectionLabel('Filter information'),
      ListTile(
        leading: const Icon(Icons.rule_folder_outlined),
        title: Text('Local ruleset v${controller.filterInfo?.version ?? '—'}'),
        subtitle: Text(
          '${controller.filterInfo?.advertisementRules ?? 0} ad rules · '
          '${controller.filterInfo?.trackerRules ?? 0} tracker rules\n'
          'Updated ${controller.filterInfo?.updatedAt ?? 'Unknown'}',
        ),
        isThreeLine: true,
        trailing: const Icon(Icons.chevron_right),
        onTap: () => Navigator.of(context).push(
          MaterialPageRoute<void>(
            builder: (_) => FilterRulesPage(controller: controller),
          ),
        ),
      ),
      const SectionLabel('About'),
      const ListTile(
        leading: Icon(Icons.lock_outline),
        title: Text('Private by design'),
        subtitle: Text(
          'ArmaNet does not decrypt HTTPS, install certificates, inspect content, or send traffic to a remote ArmaNet server.',
        ),
      ),
      const SizedBox(height: 32),
    ],
  );

  Future<void> _showAllowlistDialog(BuildContext context) async {
    final textController = TextEditingController();
    final domain = await showDialog<String>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Allow a domain'),
        content: TextField(
          controller: textController,
          autofocus: true,
          keyboardType: TextInputType.url,
          decoration: const InputDecoration(hintText: 'example.com'),
          onSubmitted: (value) => Navigator.pop(context, value),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, textController.text),
            child: const Text('Allow domain'),
          ),
        ],
      ),
    );
    textController.dispose();
    if (domain != null && domain.trim().isNotEmpty) {
      try {
        await controller.addAllowlist(domain.trim());
      } catch (error) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Could not add domain: $error')),
          );
        }
      }
    }
  }

  Future<void> _confirmClear(
    BuildContext context, {
    required bool statisticsOnly,
  }) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(
          statisticsOnly ? 'Clear statistics?' : 'Clear traffic history?',
        ),
        content: Text(
          statisticsOnly
              ? 'Statistics are derived from retained traffic. Resetting them also removes the retained traffic records.'
              : 'This removes all locally stored traffic events and derived statistics.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Clear history'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      if (statisticsOnly) {
        await controller.clearStatistics();
      } else {
        await controller.clearHistory();
      }
    }
  }
}
