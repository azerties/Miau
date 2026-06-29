package myau.clientanticheat.player.scaffold.ml;

import java.util.Random;

/**
 * Binary classification head: linear layer + sigmoid. Ported from MX Project's
 * kireiko.dev.millennium.ml.logic.rnn.layers.BinaryHead
 */
final class BinaryClassifier {
  final int in;
  final double[] V; // weight vector
  double bias;

  static final class Cache {
    double[] pooled;
    double logit;
    double y;
  }

  BinaryClassifier(int in, Random rng) {
    this.in = in;
    this.V = MathOps.xavierUniform(rng, in, in, 1);
    this.bias = 0.0;
  }

  /** Forward: score = sigmoid(dot(pooled, V) + bias) */
  double forward(double[] pooled, Cache c) {
    double logit = bias;
    for (int i = 0; i < in; i++) logit += pooled[i] * V[i];
    double y = MathOps.sigmoid(logit);
    if (c != null) {
      c.pooled = pooled;
      c.logit = logit;
      c.y = y;
    }
    return y;
  }

  static final class Grad {
    final double[] dV;
    double dBias;

    Grad(int in) {
      dV = new double[in];
    }
  }

  double[] backward(Cache c, double dLogit, Grad g) {
    g.dBias += dLogit;
    double[] dPooled = new double[in];
    for (int i = 0; i < in; i++) {
      g.dV[i] += dLogit * c.pooled[i];
      dPooled[i] = dLogit * V[i];
    }
    return dPooled;
  }
}
