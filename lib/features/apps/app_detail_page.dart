import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/models/shield_models.dart';
import 'package:shieldblock/presentation/widgets/shield_widgets.dart';

class AppDetailPage extends StatefulWidget {
  const AppDetailPage({
    super.key,
    required this.controller,
    required this.packageName,
  });

  final AppController controller;
  final String? packageName;

  @override
  State<AppDetailPage> createState() => _AppDetailPageState();
}

class _AppDetailPageState extends State<AppDetailPage> {
  late Future<AppDetail> future;
  String filter = 'all';

  @override
  void initState() {
    super.initState();
    future = widget.controller.loadAppDetail(widget.packageName);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('App activity')),
    body: FutureBuilder<AppDetail>(
      future: future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return EmptyState(
            icon: Icons.error_outline,
            title: 'Could not load app activity',
            message: snapshot.error.toString(),
          );
        }
        final app = snapshot.requireData;
        final domains = app.domains.where((domain) {
          return switch (filter) {
            'blocked' => domain.blockedConnections > 0,
            'allowed' => domain.allowedConnections > 0,
            'advertisement' => domain.category == 'advertisement',
            'tracker' => domain.category == 'tracker',
            _ => true,
          };
        }).toList();
        return CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Padding(
                padding: const EdgeInsets.all(20),
                child: Row(
                  children: [
                    AppAvatar(bytes: app.icon, name: app.appName, size: 64),
                    const SizedBox(width: 16),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            app.appName,
                            style: Theme.of(context).textTheme.headlineMedium,
                          ),
                          Text(app.packageName ?? 'Unknown package'),
                          if (app.uid != null) Text('UID ${app.uid}'),
                          if (app.packageName == null)
                            const Padding(
                              padding: EdgeInsets.only(top: 6),
                              child: Text(
                                'Android did not expose a reliable originating app. The domains and block decisions below are real, but are not assigned to a guessed package.',
                              ),
                            ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              sliver: SliverGrid.count(
                crossAxisCount: 3,
                childAspectRatio: 1.1,
                mainAxisSpacing: 8,
                crossAxisSpacing: 8,
                children: [
                  StatCard(
                    label: 'Total',
                    value: app.totalConnections,
                    icon: Icons.route,
                  ),
                  StatCard(
                    label: 'Blocked',
                    value: app.blockedConnections,
                    icon: Icons.block,
                    accent: ShieldColors.blocked,
                  ),
                  StatCard(
                    label: 'Allowed',
                    value: app.allowedConnections,
                    icon: Icons.check,
                    accent: ShieldColors.allowed,
                  ),
                ],
              ),
            ),
            SliverToBoxAdapter(
              child: SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                child: SegmentedButton<String>(
                  segments: const [
                    ButtonSegment(value: 'all', label: Text('All')),
                    ButtonSegment(value: 'blocked', label: Text('Blocked')),
                    ButtonSegment(value: 'allowed', label: Text('Allowed')),
                    ButtonSegment(value: 'advertisement', label: Text('Ads')),
                    ButtonSegment(value: 'tracker', label: Text('Trackers')),
                  ],
                  selected: {filter},
                  onSelectionChanged: (value) =>
                      setState(() => filter = value.first),
                ),
              ),
            ),
            if (domains.isEmpty)
              const SliverFillRemaining(
                hasScrollBody: false,
                child: EmptyState(
                  icon: Icons.language,
                  title: 'No domains in this view',
                  message: 'Choose another filter or generate app traffic.',
                ),
              )
            else
              SliverList.builder(
                itemCount: domains.length,
                itemBuilder: (context, index) {
                  final domain = domains[index];
                  final customRule = widget.controller
                      .matchingCustomBlocklistRule(domain.domain);
                  final exactCustomRule = widget.controller.customBlocklist.any(
                    (rule) => rule.toLowerCase() == domain.domain.toLowerCase(),
                  );
                  return ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 20),
                    title: Text(domain.domain),
                    subtitle: Text(
                      customRule == null
                          ? '${domain.networkName ?? 'Unknown'} · ${domain.category}'
                          : '${domain.networkName ?? 'Unknown'} · ${domain.category} · custom block',
                    ),
                    trailing: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          crossAxisAlignment: CrossAxisAlignment.end,
                          children: [
                            Text('${domain.totalConnections} requests'),
                            Text(
                              '${domain.blockedConnections} blocked',
                              style: const TextStyle(
                                color: ShieldColors.blocked,
                              ),
                            ),
                          ],
                        ),
                        if (domain.allowedConnections > 0)
                          IconButton(
                            tooltip: exactCustomRule
                                ? 'Remove custom block'
                                : customRule == null
                                ? 'Block this domain'
                                : 'Blocked by $customRule',
                            color: customRule == null
                                ? ShieldColors.blocked
                                : ShieldColors.allowed,
                            onPressed: exactCustomRule
                                ? () => _removeCustomBlock(domain.domain)
                                : customRule == null
                                ? () => _confirmCustomBlock(domain.domain)
                                : null,
                            icon: Icon(
                              customRule == null
                                  ? Icons.block_outlined
                                  : Icons.undo,
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
          ],
        );
      },
    ),
  );

  Future<void> _confirmCustomBlock(String domain) async {
    final allowlistRules = widget.controller.matchingAllowlistRules(domain);
    final allowlistWarning = allowlistRules.isEmpty
        ? ''
        : '\n\nThe following allowlist rule${allowlistRules.length == 1 ? '' : 's'} currently override blocking and will be removed if you continue: ${allowlistRules.join(', ')}.';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        icon: const Icon(Icons.warning_amber_rounded),
        title: const Text('Block this domain?'),
        content: Text(
          '$domain and its subdomains will be blocked for every app on this device. '
          'This may break sign-in, media, notifications, configuration, or other app features.'
          '$allowlistWarning',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(
              backgroundColor: ShieldColors.blocked,
            ),
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Block anyway'),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    final removedAllowlistRules = <String>[];
    try {
      for (final rule in allowlistRules) {
        await widget.controller.removeAllowlist(rule);
        removedAllowlistRules.add(rule);
      }
      await widget.controller.addCustomBlocklist(domain);
      if (!mounted) return;
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Future requests to $domain will be blocked.')),
      );
    } catch (error) {
      for (final rule in removedAllowlistRules) {
        try {
          await widget.controller.addAllowlist(rule);
        } catch (_) {
          // The original error remains the most useful message to the user.
        }
      }
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not block $domain: $error')),
      );
    }
  }

  Future<void> _removeCustomBlock(String domain) async {
    try {
      await widget.controller.removeCustomBlocklist(domain);
      if (!mounted) return;
      setState(() {});
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Custom block removed for $domain.')),
      );
    } catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Could not remove custom block: $error')),
      );
    }
  }
}
