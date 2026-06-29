package myau.clientanticheat.player.scaffold.ml;

import java.util.Random;

/**
 * LSTM layer with layer normalization, peephole connections, and optional recurrent dropout.
 * Supports forward and backward directions.
 *
 * <p>Ported from MX Project's kireiko.dev.millennium.ml.logic.rnn.layers.LSTMLayer
 */
final class LSTMLayer {
  final int inputSize;
  final int hiddenSize;

  final double[] Wf, Wi, Wc, Wo; // input-to-hidden weights
  final double[] Uf, Ui, Uc, Uo; // hidden-to-hidden weights
  final double[] bf, bi, bc, bo; // biases
  final double[] lnGamma, lnBeta; // layer norm params

  static final class Cache {
    int T;
    int[] stepToTime;
    double[][] xByStep;
    double[][] h;
    double[][] c;
    double[][] f, i, g, o;
    double[][] tanhC;
    double[][] preLN;
    LayerNorm.Cache[] lnCache;
    double[] recDropMask;
  }

  LSTMLayer(int inputSize, int hiddenSize, Random rng) {
    this.inputSize = inputSize;
    this.hiddenSize = hiddenSize;

    Wf = MathOps.xavierUniform(rng, hiddenSize * inputSize, inputSize, hiddenSize);
    Wi = MathOps.xavierUniform(rng, hiddenSize * inputSize, inputSize, hiddenSize);
    Wc = MathOps.xavierUniform(rng, hiddenSize * inputSize, inputSize, hiddenSize);
    Wo = MathOps.xavierUniform(rng, hiddenSize * inputSize, inputSize, hiddenSize);

    Uf = MathOps.orthogonal(rng, hiddenSize);
    Ui = MathOps.orthogonal(rng, hiddenSize);
    Uc = MathOps.orthogonal(rng, hiddenSize);
    Uo = MathOps.orthogonal(rng, hiddenSize);

    bf = new double[hiddenSize];
    bi = new double[hiddenSize];
    bc = new double[hiddenSize];
    bo = new double[hiddenSize];

    for (int k = 0; k < hiddenSize; k++) bf[k] = 1.0; // forget gate bias = 1

    lnGamma = new double[hiddenSize];
    lnBeta = new double[hiddenSize];
    for (int k = 0; k < hiddenSize; k++) lnGamma[k] = 1.0;
  }

  /**
   * Forward pass through LSTM over a time sequence.
   *
   * @param xTime input [time][features]
   * @param reverse if true, process sequence in reverse
   * @param training if true, apply dropout and cache activations
   * @param recurrentDropoutRate dropout probability on recurrent connections
   * @param rng RNG for dropout masks
   * @param cache if non-null, stores activations for backward pass
   * @return output [time][hiddenSize]
   */
  double[][] forward(
      double[][] xTime,
      boolean reverse,
      boolean training,
      double recurrentDropoutRate,
      Random rng,
      Cache cache) {
    int T = xTime.length;
    double[][] out = new double[T][hiddenSize];

    int[] stepToTime = new int[T];
    if (!reverse) {
      for (int s = 0; s < T; s++) stepToTime[s] = s;
    } else {
      for (int s = 0; s < T; s++) stepToTime[s] = T - 1 - s;
    }

    double[] hPrev = new double[hiddenSize];
    double[] cPrev = new double[hiddenSize];

    // Recurrent dropout mask
    double[] recMask = null;
    if (training && recurrentDropoutRate > 0) {
      recMask = new double[hiddenSize];
      double keep = 1.0 - recurrentDropoutRate;
      for (int i = 0; i < hiddenSize; i++)
        recMask[i] = rng.nextDouble() < keep ? (1.0 / keep) : 0.0;
    }

    // Cache structures
    double[][] h = cache != null ? new double[T + 1][hiddenSize] : null;
    double[][] c = cache != null ? new double[T + 1][hiddenSize] : null;
    double[][] f = cache != null ? new double[T][hiddenSize] : null;
    double[][] it = cache != null ? new double[T][hiddenSize] : null;
    double[][] g = cache != null ? new double[T][hiddenSize] : null;
    double[][] o = cache != null ? new double[T][hiddenSize] : null;
    double[][] tanhC = cache != null ? new double[T][hiddenSize] : null;
    double[][] preLN = cache != null ? new double[T][hiddenSize] : null;
    LayerNorm.Cache[] lnCache = cache != null ? new LayerNorm.Cache[T] : null;
    double[][] xByStep = cache != null ? new double[T][inputSize] : null;

    if (cache != null) {
      System.arraycopy(hPrev, 0, h[0], 0, hiddenSize);
      System.arraycopy(cPrev, 0, c[0], 0, hiddenSize);
    }

    // Reusable buffers
    double[] hInBuf = new double[hiddenSize];
    double[] ftBuf = new double[hiddenSize];
    double[] iiBuf = new double[hiddenSize];
    double[] ggBuf = new double[hiddenSize];
    double[] ooBuf = new double[hiddenSize];
    double[] cNowBuf = new double[hiddenSize];
    double[] tanhNowBuf = new double[hiddenSize];
    double[] preBuf = new double[hiddenSize];

    for (int s = 0; s < T; s++) {
      int t = stepToTime[s];
      double[] x = xTime[t];

      // Apply recurrent dropout
      if (training && recMask != null) {
        for (int i = 0; i < hiddenSize; i++) hInBuf[i] = hPrev[i] * recMask[i];
      } else {
        System.arraycopy(hPrev, 0, hInBuf, 0, hiddenSize);
      }

      // Gates: forget, input, candidate, output
      gateSigmoid(x, hInBuf, Wf, Uf, bf, ftBuf);
      gateSigmoid(x, hInBuf, Wi, Ui, bi, iiBuf);
      gateTanh(x, hInBuf, Wc, Uc, bc, ggBuf);
      gateSigmoid(x, hInBuf, Wo, Uo, bo, ooBuf);

      // Cell state update
      for (int k = 0; k < hiddenSize; k++) {
        cNowBuf[k] = ftBuf[k] * cPrev[k] + iiBuf[k] * ggBuf[k];
        tanhNowBuf[k] = Math.tanh(cNowBuf[k]);
        preBuf[k] = ooBuf[k] * tanhNowBuf[k];
      }

      // Layer norm
      LayerNorm.Cache lnc = cache != null ? (lnCache[s] = new LayerNorm.Cache()) : null;
      double[] hNow = LayerNorm.forward(preBuf, lnGamma, lnBeta, lnc);

      setOutput(out[t], hNow);

      // State carry-over
      System.arraycopy(hNow, 0, hPrev, 0, hiddenSize);
      System.arraycopy(cNowBuf, 0, cPrev, 0, hiddenSize);

      // Cache activations
      if (cache != null) {
        System.arraycopy(hNow, 0, h[s + 1], 0, hiddenSize);
        System.arraycopy(cNowBuf, 0, c[s + 1], 0, hiddenSize);
        cacheSlice(f, s, ftBuf);
        cacheSlice(it, s, iiBuf);
        cacheSlice(g, s, ggBuf);
        cacheSlice(o, s, ooBuf);
        cacheSlice(tanhC, s, tanhNowBuf);
        cacheSlice(preLN, s, preBuf);
        System.arraycopy(x, 0, xByStep[s] = new double[inputSize], 0, inputSize);
      }
    }

    if (cache != null) {
      cache.T = T;
      cache.stepToTime = stepToTime;
      cache.xByStep = xByStep;
      cache.h = h;
      cache.c = c;
      cache.f = f;
      cache.i = it;
      cache.g = g;
      cache.o = o;
      cache.tanhC = tanhC;
      cache.preLN = preLN;
      cache.lnCache = lnCache;
      cache.recDropMask = recMask;
    }

    return out;
  }

  /** Gradient holder for this LSTM layer. */
  static final class Grad {
    final double[] dWf, dWi, dWc, dWo;
    final double[] dUf, dUi, dUc, dUo;
    final double[] dbf, dbi, dbc, dbo;
    final double[] dLnGamma, dLnBeta;

    Grad(int inputSize, int hiddenSize) {
      int wSize = hiddenSize * inputSize;
      int uSize = hiddenSize * hiddenSize;
      dWf = new double[wSize];
      dWi = new double[wSize];
      dWc = new double[wSize];
      dWo = new double[wSize];
      dUf = new double[uSize];
      dUi = new double[uSize];
      dUc = new double[uSize];
      dUo = new double[uSize];
      dbf = new double[hiddenSize];
      dbi = new double[hiddenSize];
      dbc = new double[hiddenSize];
      dbo = new double[hiddenSize];
      dLnGamma = new double[hiddenSize];
      dLnBeta = new double[hiddenSize];
    }

    void zero() {
      java.util.Arrays.fill(dWf, 0.0);
      java.util.Arrays.fill(dWi, 0.0);
      java.util.Arrays.fill(dWc, 0.0);
      java.util.Arrays.fill(dWo, 0.0);
      java.util.Arrays.fill(dUf, 0.0);
      java.util.Arrays.fill(dUi, 0.0);
      java.util.Arrays.fill(dUc, 0.0);
      java.util.Arrays.fill(dUo, 0.0);
      java.util.Arrays.fill(dbf, 0.0);
      java.util.Arrays.fill(dbi, 0.0);
      java.util.Arrays.fill(dbc, 0.0);
      java.util.Arrays.fill(dbo, 0.0);
      java.util.Arrays.fill(dLnGamma, 0.0);
      java.util.Arrays.fill(dLnBeta, 0.0);
    }
  }

  /** Backward pass through time (BPTT). */
  double[][] backward(Cache cch, double[][] dH_time, Grad gAcc, double clip) {
    int T = cch.T;
    double[][] dX_time = new double[T][inputSize];

    double[] dhNext = new double[hiddenSize];
    double[] dcNext = new double[hiddenSize];
    double[] dOo = new double[hiddenSize];
    double[] dFt = new double[hiddenSize];
    double[] dIt = new double[hiddenSize];
    double[] dGg = new double[hiddenSize];
    double[] dc = new double[hiddenSize];
    double[] dhPrev = new double[hiddenSize];
    double[] dh = new double[hiddenSize];

    for (int s = T - 1; s >= 0; s--) {
      int t = cch.stepToTime[s];

      for (int k = 0; k < hiddenSize; k++) {
        dh[k] = dH_time[t][k] + dhNext[k];
        if (dh[k] > clip) dh[k] = clip;
        else if (dh[k] < -clip) dh[k] = -clip;
      }

      double[] dPre = LayerNorm.backward(dh, lnGamma, cch.lnCache[s], gAcc.dLnGamma, gAcc.dLnBeta);

      double[] ft = cch.f[s];
      double[] it = cch.i[s];
      double[] gg = cch.g[s];
      double[] oo = cch.o[s];
      double[] tanhC = cch.tanhC[s];
      double[] cPrev = cch.c[s];
      double[] hPrev = cch.h[s];

      for (int k = 0; k < hiddenSize; k++) {
        dOo[k] = dPre[k] * tanhC[k];
        dc[k] = dcNext[k] + dPre[k] * oo[k] * (1.0 - tanhC[k] * tanhC[k]);
        dFt[k] = dc[k] * cPrev[k];
        dIt[k] = dc[k] * gg[k];
        dGg[k] = dc[k] * it[k];
      }

      // Gate derivative scaling
      for (int k = 0; k < hiddenSize; k++) {
        dOo[k] = dOo[k] * oo[k] * (1.0 - oo[k]);
        dFt[k] = dFt[k] * ft[k] * (1.0 - ft[k]);
        dIt[k] = dIt[k] * it[k] * (1.0 - it[k]);
        dGg[k] = dGg[k] * (1.0 - gg[k] * gg[k]);
      }

      double[] x = cch.xByStep[s];

      // Accumulate gradients
      for (int n = 0; n < hiddenSize; n++) {
        gAcc.dbf[n] += dFt[n];
        gAcc.dbi[n] += dIt[n];
        gAcc.dbc[n] += dGg[n];
        gAcc.dbo[n] += dOo[n];

        int wOff = n * inputSize;
        for (int k = 0; k < inputSize; k++) {
          gAcc.dWf[wOff + k] += dFt[n] * x[k];
          gAcc.dWi[wOff + k] += dIt[n] * x[k];
          gAcc.dWc[wOff + k] += dGg[n] * x[k];
          gAcc.dWo[wOff + k] += dOo[n] * x[k];
        }

        int uOff = n * hiddenSize;
        for (int k = 0; k < hiddenSize; k++) {
          double hpk = hPrev[k];
          if (cch.recDropMask != null) hpk *= cch.recDropMask[k];
          gAcc.dUf[uOff + k] += dFt[n] * hpk;
          gAcc.dUi[uOff + k] += dIt[n] * hpk;
          gAcc.dUc[uOff + k] += dGg[n] * hpk;
          gAcc.dUo[uOff + k] += dOo[n] * hpk;
        }
      }

      // Input gradient
      for (int k = 0; k < inputSize; k++) {
        double sdx = 0.0;
        for (int n = 0; n < hiddenSize; n++) {
          int wOff = n * inputSize + k;
          sdx += Wf[wOff] * dFt[n];
          sdx += Wi[wOff] * dIt[n];
          sdx += Wc[wOff] * dGg[n];
          sdx += Wo[wOff] * dOo[n];
        }
        if (sdx > clip) sdx = clip;
        else if (sdx < -clip) sdx = -clip;
        dX_time[t][k] += sdx;
      }

      // Hidden gradient to previous time step
      for (int j = 0; j < hiddenSize; j++) {
        double sdh = 0.0;
        for (int n = 0; n < hiddenSize; n++) {
          int idx = n * hiddenSize + j;
          sdh += Uf[idx] * dFt[n];
          sdh += Ui[idx] * dIt[n];
          sdh += Uc[idx] * dGg[n];
          sdh += Uo[idx] * dOo[n];
        }
        if (cch.recDropMask != null) sdh *= cch.recDropMask[j];
        dhPrev[j] = sdh;
      }

      for (int k = 0; k < hiddenSize; k++) {
        dhNext[k] = dhPrev[k];
        dcNext[k] = dc[k] * ft[k];
      }
    }

    return dX_time;
  }

  // ── Gate helpers ──
  private void gateSigmoid(
      double[] x, double[] h, double[] W, double[] U, double[] b, double[] out) {
    for (int n = 0; n < hiddenSize; n++) {
      double sum = b[n];
      int wOff = n * inputSize;
      for (int k = 0; k < inputSize; k++) sum += W[wOff + k] * x[k];
      int uOff = n * hiddenSize;
      for (int k = 0; k < hiddenSize; k++) sum += U[uOff + k] * h[k];
      if (sum > 50.0) sum = 50.0;
      else if (sum < -50.0) sum = -50.0;
      out[n] = 1.0 / (1.0 + Math.exp(-sum));
    }
  }

  private void gateTanh(double[] x, double[] h, double[] W, double[] U, double[] b, double[] out) {
    for (int n = 0; n < hiddenSize; n++) {
      double sum = b[n];
      int wOff = n * inputSize;
      for (int k = 0; k < inputSize; k++) sum += W[wOff + k] * x[k];
      int uOff = n * hiddenSize;
      for (int k = 0; k < hiddenSize; k++) sum += U[uOff + k] * h[k];
      if (sum > 50.0) sum = 50.0;
      else if (sum < -50.0) sum = -50.0;
      out[n] = Math.tanh(sum);
    }
  }

  private static void setOutput(double[] out, double[] src) {
    System.arraycopy(src, 0, out, 0, src.length);
  }

  private static void cacheSlice(double[][] cache, int t, double[] buf) {
    System.arraycopy(buf, 0, cache[t] = new double[buf.length], 0, buf.length);
  }
}
