import 'dart:io';

import 'package:image/image.dart' as image;

/// Produces a launcher-only derivative while preserving the supplied logo.
///
/// The artwork bounds in logo.png are centered around (537, 485) on a
/// 1024-square canvas. Moving the canvas 25 px left and 27 px down aligns the
/// visible snake/globe mark with the adaptive-icon mask center. Edge pixels are
/// extended instead of introducing a new background color or seam.
void main() {
  const sourcePath = 'logo.png';
  const outputPath = 'assets/branding/logo_launcher.png';
  const offsetX = -25;
  const offsetY = 27;

  final source = image.decodePng(File(sourcePath).readAsBytesSync());
  if (source == null) {
    throw StateError('$sourcePath is not a valid PNG image.');
  }
  if (source.width != source.height) {
    throw StateError('$sourcePath must use a square canvas.');
  }

  final output = image.Image(
    width: source.width,
    height: source.height,
    numChannels: source.numChannels,
  );
  for (var y = 0; y < output.height; y++) {
    final sourceY = (y - offsetY).clamp(0, source.height - 1);
    for (var x = 0; x < output.width; x++) {
      final sourceX = (x - offsetX).clamp(0, source.width - 1);
      output.setPixel(x, y, source.getPixel(sourceX, sourceY));
    }
  }

  final target = File(outputPath)..parent.createSync(recursive: true);
  target.writeAsBytesSync(image.encodePng(output));
  stdout.writeln('Prepared centered launcher artwork: $outputPath');
}
