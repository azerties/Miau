package myau.clientanticheat.player.scaffold.ml;

import java.util.Random;

/**
 * Stacked Bidirectional LSTM encoder. Stacks multiple LSTM layers, each optionally bidirectional.
 * Ported from MX Project's kireiko.dev.millennium.ml.logic.rnn.layers.StackedBiLSTM
 */
final class StackedBiLSTM {
  final int inputSize;
  final int hiddenSize;
  final int numLayers;
  final boolean bidirectional;

  final LSTMLayer[] fwd;
  final LSTMLayer[] bwd;

  static final class Cache {
    LSTMLayer.Cache[] fwdCache;
    LSTMLayer.Cache[] bwdCache;
    double[][][] layerOutputs;
    double[] dropoutMask;
  }

  StackedBiLSTM(int inputSize, int hiddenSize, int numLayers, boolean bidirectional, Random rng) {
    this.inputSize = inputSize;
    this.hiddenSize = hiddenSize;
    this.numLayers = numLayers;
    this.bidirectional = bidirectional;

    fwd = new LSTMLayer[numLayers];
    bwd = bidirectional ? new LSTMLayer[numLayers] : null;

    for (int l = 0; l < numLayers; l++) {
      int in = (l == 0) ? inputSize : (bidirectional ? hiddenSize * 2 : hiddenSize);
      fwd[l] = new LSTMLayer(in, hiddenSize, rng);
      if (bidirectional) bwd[l] = new LSTMLayer(in, hiddenSize, rng);
    }
  }

  int outputSize() {
    return bidirectional ? hiddenSize * 2 : hiddenSize;
  }

  /**
   * Forward pass through all stacked layers.
   *
   * @param x input [time][features]
   * @param training whether to apply dropout
   * @param dropoutRate dropout between layers (0 = no dropout)
   * @param recDropRate recurrent dropout
   * @param rng RNG for dropout
   * @param cache activation cache (null for inference)
   * @return output [time][outputSize]
   */
  double[][] forward(
      double[][] x,
      boolean training,
      double dropoutRate,
      double recDropRate,
      Random rng,
      Cache cache) {
    double[][] cur = x;

    double[] dropMask = null;
    if (training && dropoutRate > 0) {
      int os = outputSize();
      dropMask = new double[os];
      double keep = 1.0 - dropoutRate;
      for (int i = 0; i < os; i++) dropMask[i] = rng.nextDouble() < keep ? (1.0 / keep) : 0.0;
    }

    LSTMLayer.Cache[] fCaches = cache != null ? new LSTMLayer.Cache[numLayers] : null;
    LSTMLayer.Cache[] bCaches =
        cache != null && bidirectional ? new LSTMLayer.Cache[numLayers] : null;
    double[][][] layerOut = cache != null ? new double[numLayers][][] : null;

    for (int l = 0; l < numLayers; l++) {
      LSTMLayer.Cache fc = cache != null ? (fCaches[l] = new LSTMLayer.Cache()) : null;
      double[][] fo = fwd[l].forward(cur, false, training, recDropRate, rng, fc);

      double[][] out;
      if (bidirectional) {
        LSTMLayer.Cache bc = cache != null ? (bCaches[l] = new LSTMLayer.Cache()) : null;
        double[][] bo = bwd[l].forward(cur, true, training, recDropRate, rng, bc);
        int hs = hiddenSize;
        out = new double[fo.length][hs * 2];
        for (int t = 0; t < fo.length; t++) {
          System.arraycopy(fo[t], 0, out[t], 0, hs);
          System.arraycopy(bo[t], 0, out[t], hs, hs);
        }
      } else {
        out = fo;
      }

      // Apply dropout between layers (except last)
      if (training && dropMask != null && l < numLayers - 1) {
        for (int t = 0; t < out.length; t++)
          for (int i = 0; i < out[t].length; i++) out[t][i] *= dropMask[i % dropMask.length];
      }

      if (cache != null) layerOut[l] = out;
      cur = out;
    }

    if (cache != null) {
      cache.fwdCache = fCaches;
      cache.bwdCache = bCaches;
      cache.layerOutputs = layerOut;
      cache.dropoutMask = dropMask;
    }

    return cur;
  }

  static final class Grad {
    final LSTMLayer.Grad[] fwd;
    final LSTMLayer.Grad[] bwd;

    Grad(StackedBiLSTM m) {
      fwd = new LSTMLayer.Grad[m.numLayers];
      bwd = m.bidirectional ? new LSTMLayer.Grad[m.numLayers] : null;
      for (int l = 0; l < m.numLayers; l++) {
        int in = (l == 0) ? m.inputSize : (m.bidirectional ? m.hiddenSize * 2 : m.hiddenSize);
        fwd[l] = new LSTMLayer.Grad(in, m.hiddenSize);
        if (m.bidirectional) bwd[l] = new LSTMLayer.Grad(in, m.hiddenSize);
      }
    }
  }

  double[][] backward(Cache cache, double[][] dOut_time, Grad gAcc, double clip) {
    double[][] dCur = dOut_time;

    for (int l = numLayers - 1; l >= 0; l--) {
      if (cache.dropoutMask != null && l < numLayers - 1) {
        for (int t = 0; t < dCur.length; t++)
          for (int i = 0; i < dCur[t].length; i++)
            dCur[t][i] *= cache.dropoutMask[i % cache.dropoutMask.length];
      }

      if (bidirectional) {
        int hs = hiddenSize;
        double[][] dF = new double[dCur.length][hs];
        double[][] dB = new double[dCur.length][hs];
        for (int t = 0; t < dCur.length; t++) {
          System.arraycopy(dCur[t], 0, dF[t], 0, hs);
          System.arraycopy(dCur[t], hs, dB[t], 0, hs);
        }

        double[][] dXF = fwd[l].backward(cache.fwdCache[l], dF, gAcc.fwd[l], clip);
        double[][] dXB = bwd[l].backward(cache.bwdCache[l], dB, gAcc.bwd[l], clip);

        int in = (l == 0) ? inputSize : hs * 2;
        double[][] dNext = new double[dCur.length][in];
        for (int t = 0; t < dCur.length; t++)
          for (int i = 0; i < in; i++) dNext[t][i] = dXF[t][i] + dXB[t][i];
        dCur = dNext;
      } else {
        dCur = fwd[l].backward(cache.fwdCache[l], dCur, gAcc.fwd[l], clip);
      }
    }
    return dCur;
  }
}
