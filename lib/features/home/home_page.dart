import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/presentation/widgets/shield_widgets.dart';
import 'package:shieldblock/services/shieldblock_native_api.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key, required this.controller});

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    final stats = controller.statistics;
    final vpn = controller.vpnStatus;
    final topApps = controller.apps
        .where((app) => app.blockedConnections > 0)
        .take(3)
        .toList();
    return RefreshIndicator(
      onRefresh: controller.reloadAll,
      child: CustomScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        slivers: [
          const SliverToBoxAdapter(
            child: PageHeading(
              'ArmaNet',
              subtitle: 'On-device DNS privacy, without traffic decryption.',
            ),
          ),
          SliverToBoxAdapter(
            child: Center(
              child: SignalAperture(
                enabled: controller.vpnStatus.running,
                busy: controller.protectionBusy,
                onChanged: controller.toggleProtection,
              ),
            ),
          ),
          SliverToBoxAdapter(
            child: Center(
              child: Padding(
                padding: const EdgeInsets.only(top: 6, bottom: 6),
                child: Text(
                  _protectionDetail(vpn),
                  style: Theme.of(context).textTheme.bodySmall,
                  textAlign: TextAlign.center,
                ),
              ),
            ),
          ),
          SliverPadding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            sliver: SliverGrid.count(
              crossAxisCount: 2,
              childAspectRatio: 1.55,
              mainAxisSpacing: 10,
              crossAxisSpacing: 10,
              children: [
                StatCard(
                  label: 'Today blocked',
                  value: stats.todayBlocked,
                  icon: Icons.today_rounded,
                  accent: ShieldColors.blocked,
                ),
                StatCard(
                  label: 'Total blocked',
                  value: stats.blockedConnections,
                  icon: Icons.block_rounded,
                  accent: ShieldColors.blocked,
                ),
                StatCard(
                  label: 'Advertisements',
                  value: stats.advertisements,
                  icon: Icons.campaign_outlined,
                ),
                StatCard(
                  label: 'Trackers',
                  value: stats.trackers,
                  icon: Icons.fingerprint_rounded,
                  accent: ShieldColors.allowed,
                ),
              ],
            ),
          ),
          const SliverToBoxAdapter(child: SectionLabel('Top blocked apps')),
          if (topApps.isEmpty)
            const SliverToBoxAdapter(
              child: EmptyState(
                icon: Icons.shield_outlined,
                title: 'No blocked requests yet',
                message: 'Start protection, then use your other apps normally.',
              ),
            )
          else
            SliverList.builder(
              itemCount: topApps.length,
              itemBuilder: (context, index) {
                final app = topApps[index];
                return ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20),
                  leading: AppAvatar(bytes: app.icon, name: app.appName),
                  title: Text(app.appName),
                  subtitle: Text(app.packageName ?? 'Unknown package'),
                  trailing: Text(
                    '${app.blockedConnections}',
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: ShieldColors.blocked,
                    ),
                  ),
                );
              },
            ),
          const SliverToBoxAdapter(child: SectionLabel('Recent activity')),
          if (controller.traffic.isEmpty)
            const SliverToBoxAdapter(
              child: Padding(
                padding: EdgeInsets.fromLTRB(20, 0, 20, 30),
                child: Text('DNS activity will appear here in real time.'),
              ),
            )
          else
            SliverList.builder(
              itemCount: controller.traffic.take(5).length,
              itemBuilder: (context, index) {
                final event = controller.traffic[index];
                return ListTile(
                  contentPadding: const EdgeInsets.symmetric(horizontal: 20),
                  leading: Icon(
                    event.blocked ? Icons.block : Icons.check_circle_outline,
                    color: event.blocked
                        ? ShieldColors.blocked
                        : ShieldColors.allowed,
                  ),
                  title: Text(
                    event.domain,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  subtitle: Text(
                    '${event.appName ?? 'Unknown App'} · ${event.networkName ?? 'Unknown'}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: Text(
                    event.blocked ? 'BLOCKED' : 'ALLOWED',
                    style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: event.blocked
                          ? ShieldColors.blocked
                          : ShieldColors.allowed,
                    ),
                  ),
                );
              },
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 24)),
        ],
      ),
    );
  }

  String _protectionDetail(VpnStatus vpn) {
    if (!vpn.running) return vpn.detail ?? 'Protection is off';
    if (vpn.dnsQueries > 0) {
      return '${vpn.dnsQueries} DNS requests inspected · ${vpn.blockedQueries} blocked';
    }
    if (vpn.privateDnsActive) {
      return 'Protection is active, but no DNS is visible yet. Private DNS may bypass filtering.';
    }
    return 'Protection is active and waiting for DNS requests.';
  }
}
