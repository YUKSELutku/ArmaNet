import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:shieldblock/core/app_theme.dart';

class PageHeading extends StatelessWidget {
  const PageHeading(this.title, {super.key, this.subtitle, this.trailing});

  final String title;
  final String? subtitle;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(title, style: Theme.of(context).textTheme.displaySmall),
              if (subtitle != null) ...[
                const SizedBox(height: 4),
                Text(
                  subtitle!,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: ShieldColors.ink.withValues(alpha: 0.64),
                  ),
                ),
              ],
            ],
          ),
        ),
        ?trailing,
      ],
    ),
  );
}

class SectionLabel extends StatelessWidget {
  const SectionLabel(this.label, {super.key, this.trailing});

  final String label;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(20, 20, 20, 8),
    child: Row(
      children: [
        Expanded(
          child: Text(
            label.toUpperCase(),
            style: Theme.of(
              context,
            ).textTheme.labelSmall?.copyWith(color: ShieldColors.signal),
          ),
        ),
        ?trailing,
      ],
    ),
  );
}

class SignalAperture extends StatelessWidget {
  const SignalAperture({
    super.key,
    required this.enabled,
    required this.busy,
    required this.onChanged,
  });

  final bool enabled;
  final bool busy;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    toggled: enabled,
    label: enabled ? 'Protection on' : 'Protection off',
    child: InkWell(
      customBorder: const CircleBorder(),
      onTap: busy ? null : () => onChanged(!enabled),
      child: SizedBox.square(
        dimension: 206,
        child: CustomPaint(
          painter: _AperturePainter(enabled: enabled),
          child: Center(
            child: busy
                ? const CircularProgressIndicator()
                : Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        enabled ? Icons.shield_rounded : Icons.shield_outlined,
                        size: 44,
                        color: enabled
                            ? ShieldColors.signal
                            : ShieldColors.ink.withValues(alpha: 0.5),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        enabled ? 'ON' : 'OFF',
                        style: Theme.of(context).textTheme.headlineMedium
                            ?.copyWith(
                              color: enabled
                                  ? ShieldColors.signal
                                  : ShieldColors.ink,
                            ),
                      ),
                      Text(
                        enabled ? 'DNS FILTERING' : 'TAP TO PROTECT',
                        style: Theme.of(context).textTheme.labelSmall,
                      ),
                    ],
                  ),
          ),
        ),
      ),
    ),
  );
}

class _AperturePainter extends CustomPainter {
  _AperturePainter({required this.enabled});

  final bool enabled;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final radius = size.shortestSide / 2 - 10;
    final muted = Paint()
      ..color = ShieldColors.ink.withValues(alpha: 0.1)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 8
      ..strokeCap = StrokeCap.round;
    final active = Paint()
      ..color = enabled ? ShieldColors.signal : ShieldColors.blocked
      ..style = PaintingStyle.stroke
      ..strokeWidth = 8
      ..strokeCap = StrokeCap.round;
    for (var index = 0; index < 12; index++) {
      final start = -math.pi / 2 + index * math.pi * 2 / 12;
      canvas.drawArc(
        Rect.fromCircle(center: center, radius: radius),
        start,
        math.pi * 0.115,
        false,
        enabled || index < 3 ? active : muted,
      );
    }
    canvas.drawCircle(
      center,
      radius - 22,
      Paint()
        ..color = enabled
            ? ShieldColors.glacier.withValues(alpha: 0.7)
            : Colors.white,
    );
    canvas.drawLine(
      Offset(center.dx, 12),
      Offset(center.dx, 25),
      muted..strokeWidth = 1,
    );
  }

  @override
  bool shouldRepaint(_AperturePainter oldDelegate) =>
      enabled != oldDelegate.enabled;
}

class StatCard extends StatelessWidget {
  const StatCard({
    super.key,
    required this.label,
    required this.value,
    required this.icon,
    this.accent = ShieldColors.signal,
  });

  final String label;
  final int value;
  final IconData icon;
  final Color accent;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: accent, size: 20),
          const Spacer(),
          Text('$value', style: Theme.of(context).textTheme.headlineMedium),
          Text(label, style: Theme.of(context).textTheme.bodySmall),
        ],
      ),
    ),
  );
}

class AppAvatar extends StatelessWidget {
  const AppAvatar({super.key, this.bytes, required this.name, this.size = 44});

  final Uint8List? bytes;
  final String name;
  final double size;

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(size * 0.25),
    child: SizedBox.square(
      dimension: size,
      child: bytes == null
          ? ColoredBox(
              color: ShieldColors.glacier,
              child: Center(
                child: Text(
                  name.isEmpty ? '?' : name.characters.first.toUpperCase(),
                  style: Theme.of(
                    context,
                  ).textTheme.titleLarge?.copyWith(color: ShieldColors.signal),
                ),
              ),
            )
          : Image.memory(bytes!, fit: BoxFit.cover, gaplessPlayback: true),
    ),
  );
}

class EmptyState extends StatelessWidget {
  const EmptyState({
    super.key,
    required this.icon,
    required this.title,
    required this.message,
  });

  final IconData icon;
  final String title;
  final String message;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.all(32),
    child: Column(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(icon, size: 48, color: ShieldColors.signal),
        const SizedBox(height: 16),
        Text(title, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: 6),
        Text(message, textAlign: TextAlign.center),
      ],
    ),
  );
}
