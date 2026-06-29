package myau.clientanticheat.player.scaffold.ml;

/**
 * Wraps a sequence of input vectors with a mask array. Ported from MX Project's
 * kireiko.dev.millennium.ml.logic.rnn.data.SequenceData
 */
final class SequenceData {
  final double[][] x; // [time][features]
  final double[] mask; // [time] 1.0 = valid, 0.0 = padding

  SequenceData(double[][] x, double[] mask) {
    this.x = x;
    this.mask = mask;
  }

  int length() {
    return x.length;
  }
}
