import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_controller.dart';
import 'package:shieldblock/core/app_theme.dart';
import 'package:shieldblock/presentation/widgets/shield_widgets.dart';
import 'package:shieldblock/services/shieldblock_native_api.dart';

class FilterRulesPage extends StatefulWidget {
  const FilterRulesPage({super.key, required this.controller});

  final AppController controller;

  @override
  State<FilterRulesPage> createState() => _FilterRulesPageState();
}

class _FilterRulesPageState extends State<FilterRulesPage> {
  late Future<List<FilterRuleEntry>> _future;
  String _query = '';
  String _category = 'all';

  @override
  void initState() {
    super.initState();
    _future = widget.controller.loadFilterRules();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Filter rules')),
    body: FutureBuilder<List<FilterRuleEntry>>(
      future: _future,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Center(child: CircularProgressIndicator());
        }
        if (snapshot.hasError) {
          return EmptyState(
            icon: Icons.rule_folder_outlined,
            title: 'Rules could not be loaded',
            message: snapshot.error.toString(),
          );
        }
        final rules = snapshot.data ?? const [];
        final needle = _query.trim().toLowerCase();
        final visible = rules
            .where((rule) {
              final categoryMatches =
                  _category == 'all' || rule.category == _category;
              final queryMatches =
                  needle.isEmpty ||
                  rule.domain.toLowerCase().contains(needle) ||
                  rule.network.toLowerCase().contains(needle);
              return categoryMatches && queryMatches;
            })
            .toList(growable: false);
        return Column(
          children: [
            _RulesetLedgerHeader(
              controller: widget.controller,
              visibleCount: visible.length,
              totalCount: rules.length,
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 4, 20, 10),
              child: TextField(
                decoration: const InputDecoration(
                  hintText: 'Search domain or provider',
                  prefixIcon: Icon(Icons.manage_search_rounded),
                ),
                textInputAction: TextInputAction.search,
                onChanged: (value) => setState(() => _query = value),
              ),
            ),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
              child: SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'all', label: Text('All')),
                  ButtonSegment(
                    value: 'advertisement',
                    label: Text('Advertisements'),
                    icon: Icon(Icons.campaign_outlined),
                  ),
                  ButtonSegment(
                    value: 'tracker',
                    label: Text('Trackers'),
                    icon: Icon(Icons.fingerprint_rounded),
                  ),
                ],
                selected: {_category},
                onSelectionChanged: (value) =>
                    setState(() => _category = value.first),
              ),
            ),
            Expanded(
              child: visible.isEmpty
                  ? const EmptyState(
                      icon: Icons.search_off_rounded,
                      title: 'No matching rules',
                      message: 'Try another domain, provider, or category.',
                    )
                  : ListView.separated(
                      keyboardDismissBehavior:
                          ScrollViewKeyboardDismissBehavior.onDrag,
                      itemCount: visible.length,
                      separatorBuilder: (_, _) =>
                          const Divider(height: 1, indent: 28, endIndent: 20),
                      itemBuilder: (context, index) =>
                          _RuleLedgerRow(rule: visible[index]),
                    ),
            ),
          ],
        );
      },
    ),
  );
}

class _RulesetLedgerHeader extends StatelessWidget {
  const _RulesetLedgerHeader({
    required this.controller,
    required this.visibleCount,
    required this.totalCount,
  });

  final AppController controller;
  final int visibleCount;
  final int totalCount;

  @override
  Widget build(BuildContext context) {
    final info = controller.filterInfo;
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 12, 20, 12),
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: ShieldColors.ink,
          borderRadius: BorderRadius.circular(20),
        ),
        child: Padding(
          padding: const EdgeInsets.all(18),
          child: Row(
            children: [
              Container(
                width: 54,
                height: 54,
                alignment: Alignment.center,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  border: Border.all(color: ShieldColors.glacier, width: 2),
                ),
                child: Text(
                  'v${info?.version ?? '—'}',
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    color: Colors.white,
                    fontFamily: 'monospace',
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '$visibleCount of $totalCount local rules',
                      style: Theme.of(
                        context,
                      ).textTheme.titleLarge?.copyWith(color: Colors.white),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      'Loaded on-device · updated ${info?.updatedAt ?? 'Unknown'}',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: ShieldColors.glacier,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _RuleLedgerRow extends StatelessWidget {
  const _RuleLedgerRow({required this.rule});

  final FilterRuleEntry rule;

  @override
  Widget build(BuildContext context) {
    final isAd = rule.category == 'advertisement';
    final accent = isAd ? ShieldColors.blocked : ShieldColors.signal;
    return Semantics(
      label: '${rule.domain}, ${rule.network}, ${rule.category}',
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 7, 20, 7),
        child: Row(
          children: [
            Container(
              width: 4,
              height: 44,
              decoration: BoxDecoration(
                color: accent,
                borderRadius: BorderRadius.circular(4),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    rule.domain,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      fontFamily: 'monospace',
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 3),
                  Text(
                    rule.network,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
            const SizedBox(width: 8),
            DecoratedBox(
              decoration: BoxDecoration(
                color: accent.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(999),
              ),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 5),
                child: Text(
                  isAd ? 'AD' : 'TRACKER',
                  style: Theme.of(
                    context,
                  ).textTheme.labelSmall?.copyWith(color: accent),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
