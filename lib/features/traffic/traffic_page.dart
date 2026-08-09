import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/models/shield_models.dart';
import 'package:shieldblock/presentation/widgets/shield_widgets.dart';

class TrafficPage extends StatefulWidget {
  const TrafficPage({super.key, required this.controller});

  final AppController controller;

  @override
  State<TrafficPage> createState() => _TrafficPageState();
}

class _TrafficPageState extends State<TrafficPage> {
  static const _unknownApp = '__shieldblock_unknown_app__';

  String filter = 'all';
  String period = 'all';
  String? packageName;
  String domain = '';

  Future<void> applyFilter() {
    final blocked = switch (filter) {
      'blocked' => true,
      'allowed' => false,
      _ => null,
    };
    final category = switch (filter) {
      'advertisement' => 'advertisement',
      'tracker' => 'tracker',
      _ => null,
    };
    final now = DateTime.now();
    final sinceTimestamp = switch (period) {
      'hour' => now.subtract(const Duration(hours: 1)).millisecondsSinceEpoch,
      'today' => DateTime(now.year, now.month, now.day).millisecondsSinceEpoch,
      _ => null,
    };
    return widget.controller.updateTrafficQuery(
      TrafficQuery(
        packageName: packageName == _unknownApp ? null : packageName,
        unknownApp: packageName == _unknownApp,
        domain: domain.trim().isEmpty ? null : domain.trim(),
        blocked: blocked,
        category: category,
        sinceTimestamp: sinceTimestamp,
      ),
    );
  }

  @override
  Widget build(BuildContext context) => Column(
    children: [
      const PageHeading(
        'Traffic',
        subtitle: 'Recent DNS metadata. No HTTPS content is inspected.',
      ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: TextField(
          decoration: const InputDecoration(
            hintText: 'Filter by domain',
            prefixIcon: Icon(Icons.travel_explore),
          ),
          onChanged: (value) => domain = value,
          onSubmitted: (_) => applyFilter(),
        ),
      ),
      SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
        child: SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'all', label: Text('All')),
            ButtonSegment(value: 'blocked', label: Text('Blocked')),
            ButtonSegment(value: 'allowed', label: Text('Allowed')),
            ButtonSegment(value: 'advertisement', label: Text('Ads')),
            ButtonSegment(value: 'tracker', label: Text('Trackers')),
          ],
          selected: {filter},
          onSelectionChanged: (value) {
            setState(() => filter = value.first);
            applyFilter();
          },
        ),
      ),
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 20),
        child: DropdownButtonFormField<String?>(
          initialValue: packageName,
          decoration: const InputDecoration(
            labelText: 'Application',
            prefixIcon: Icon(Icons.apps),
          ),
          items: [
            const DropdownMenuItem<String?>(
              value: null,
              child: Text('All apps'),
            ),
            if (widget.controller.apps.any(
              (app) => app.packageName == null && app.totalConnections > 0,
            ))
              const DropdownMenuItem<String?>(
                value: _unknownApp,
                child: Text('Unknown App'),
              ),
            ...widget.controller.apps
                .where(
                  (app) => app.packageName != null && app.totalConnections > 0,
                )
                .map(
                  (app) => DropdownMenuItem<String?>(
                    value: app.packageName,
                    child: Text(app.appName, overflow: TextOverflow.ellipsis),
                  ),
                ),
          ],
          onChanged: (value) {
            setState(() => packageName = value);
            applyFilter();
          },
        ),
      ),
      SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
        child: SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'all', label: Text('All time')),
            ButtonSegment(value: 'today', label: Text('Today')),
            ButtonSegment(value: 'hour', label: Text('Last hour')),
          ],
          selected: {period},
          onSelectionChanged: (value) {
            setState(() => period = value.first);
            applyFilter();
          },
        ),
      ),
      const SizedBox(height: 8),
      Expanded(
        child: widget.controller.traffic.isEmpty
            ? const EmptyState(
                icon: Icons.route_outlined,
                title: 'No traffic in this view',
                message: 'Start protection or adjust the active filters.',
              )
            : RefreshIndicator(
                onRefresh: applyFilter,
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  itemCount: widget.controller.traffic.length,
                  separatorBuilder: (_, _) =>
                      const Divider(height: 1, indent: 54),
                  itemBuilder: (context, index) {
                    final event = widget.controller.traffic[index];
                    return _TrafficTile(
                      event: event,
                      debugMode: widget.controller.settings.debugMode,
                    );
                  },
                ),
              ),
      ),
    ],
  );
}

class _TrafficTile extends StatelessWidget {
  const _TrafficTile({required this.event, required this.debugMode});

  final TrafficRecord event;
  final bool debugMode;

  @override
  Widget build(BuildContext context) {
    final time = [
      event.timestamp.hour,
      event.timestamp.minute,
      event.timestamp.second,
    ].map((part) => part.toString().padLeft(2, '0')).join(':');
    final technical = [
      if (event.uid != null) 'UID ${event.uid}' else 'UID Unknown',
      event.packageName ?? 'Package Unknown',
      event.protocol,
      event.destinationIp ?? 'IP Unknown',
      event.destinationPort != null
          ? 'Port ${event.destinationPort}'
          : 'Port Unknown',
      event.timestamp.toIso8601String(),
      '${event.category} / ${event.blocked ? 'BLOCKED' : 'ALLOWED'}',
      'Attribution ${event.attributionConfidence}',
    ].join(' · ');
    return ListTile(
      isThreeLine: debugMode,
      contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 4),
      leading: SizedBox(
        width: 34,
        child: Text(time, style: Theme.of(context).textTheme.labelSmall),
      ),
      title: Text(event.domain, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        debugMode
            ? '${event.appName ?? 'Unknown App'} · ${event.networkName ?? 'Unknown'}\n$technical'
            : '${event.appName ?? 'Unknown App'} · ${event.networkName ?? 'Unknown'}',
        maxLines: debugMode ? 4 : 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: Icon(
        event.blocked ? Icons.block : Icons.check_circle_outline,
        color: event.blocked ? ShieldColors.blocked : ShieldColors.allowed,
      ),
    );
  }
}
