package myau.clientanticheat.player.scaffold.ml;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Scaffold RNN Model: Stacked BiLSTM with Attention Pooling.
 *
 * <p>Architecture (matching MX Project's RNNModelML v5): - Input: 16-dim feature vector (rotation
 * deltas + timing) - Preprocessing: Learned embedding (tanh-scaled input) - Encoder: 2-layer
 * Stacked BiLSTM, hidden=32 (lighter than MX's 48) - Pooling: Attention-based weighted aggregation
 * over time - Output: Binary sigmoid classifier (cheat probability)
 *
 * <p>Total parameters: ~46K (lightweight enough for client-side) ≈ 2 × (4 × 16 × 32 + 4 × 32 × 32 +
 * 4 × 32) × 2 layers + attention(32) + binary(32) ≈ 46K
 *
 * <p>Model file magic: 0x5343524E ("SCRN")
 */
public final class ScaffoldRNNModel {

  // ── Constants ──
  private static final int MAGIC = 0x5343524E;
  private static final int VERSION = 1;
  public static final int INPUT_SIZE = 16;
  public static final int HIDDEN_SIZE = 32;
  public static final int NUM_LAYERS = 2;
  public static final boolean BIDIRECTIONAL = true;
  private static final double DECISION_THRESHOLD = 0.55;

  // ── Components ──
  private final Random rng;
  private final StackedBiLSTM encoder;
  private final AttentionPooling attnPooling;
  private final PoolingStrategy lastPooling;
  private final BinaryClassifier classifier;

  // Input embedding weights
  private final double[] embWy;
  private final double[] embWp;
  private final double[] embB;

  private int totalSamplesProcessed = 0;

  /**
   * Create a new RNN model with randomly initialized weights. Use load() to restore a pre-trained
   * model.
   */
  public ScaffoldRNNModel() {
    this.rng = new Random(42); // fixed seed for reproducibility
    this.encoder = new StackedBiLSTM(INPUT_SIZE, HIDDEN_SIZE, NUM_LAYERS, BIDIRECTIONAL, rng);
    this.attnPooling = new AttentionPooling(encoder.outputSize(), rng);
    this.lastPooling = new LastHiddenPooling(encoder.outputSize());
    this.classifier = new BinaryClassifier(encoder.outputSize(), rng);

    // Input embedding: yaw weight, pitch weight, bias
    this.embWy = new double[INPUT_SIZE];
    this.embWp = new double[INPUT_SIZE];
    this.embB = new double[INPUT_SIZE];
    for (int i = 0; i < INPUT_SIZE; i++) {
      embWy[i] = (rng.nextDouble() - 0.5) * 0.1;
      embWp[i] = (rng.nextDouble() - 0.5) * 0.1;
      embB[i] = (rng.nextDouble() - 0.5) * 0.01;
    }
  }

  /**
   * Prepare input sequence from raw rotation + timing data. Allocates and returns a SequenceData
   * with proper masking.
   *
   * <p>Input format: double[T][2] where [0]=yawDelta, [1]=pitchDelta Output: SequenceData with
   * embedded 16-dim vectors
   */
  SequenceData preprocess(double[][] rawVecs) {
    if (rawVecs == null || rawVecs.length == 0) return null;

    int T = rawVecs.length;
    double[][] x = new double[T][INPUT_SIZE];
    double[] mask = new double[T];

    for (int t = 0; t < T; t++) {
      double yaw = rawVecs[t][0];
      double pitch = rawVecs[t][1];

      // Skip padding/zero sequences
      if (Math.abs(yaw) < 1e-10 && Math.abs(pitch) < 1e-10) {
        mask[t] = 0.0;
        continue;
      }
      mask[t] = 1.0;

      // Learnable embedding: tanh-scaled input features
      double vY = Math.tanh(yaw / 100.0);
      double vP = Math.tanh(pitch / 100.0);

      for (int i = 0; i < INPUT_SIZE; i++) {
        x[t][i] = embWy[i] * vY + embWp[i] * vP + embB[i];
      }
    }

    return new SequenceData(x, mask);
  }

  /**
   * Run inference: compute probability that this sequence is a cheater.
   *
   * @param rawVecs array of [yawDelta, pitchDelta] pairs, length 2-150
   * @return probability in [0, 1] (higher = more likely cheating)
   */
  public double predict(double[][] rawVecs) {
    SequenceData seq = preprocess(rawVecs);
    if (seq == null || seq.length() < 2) return 0.5;

    // Encode with Stacked BiLSTM
    double[][] hTime = encoder.forward(seq.x, false, 0.0, 0.0, rng, null);

    // Attention pooling
    double[] pooled = attnPooling.forward(hTime, seq.mask, null);

    // Binary classification
    double prob = classifier.forward(pooled, null);

    return prob;
  }

  /** Quick check: returns true if the probability exceeds the decision threshold. */
  public boolean isCheating(double[][] rawVecs) {
    return predict(rawVecs) >= DECISION_THRESHOLD;
  }

  /** Get the raw probability with a detail string. */
  public RNNResult analyze(double[][] rawVecs) {
    SequenceData seq = preprocess(rawVecs);
    if (seq == null || seq.length() < 2) {
      return new RNNResult(0.5, "insufficient_data");
    }

    double[][] hTime = encoder.forward(seq.x, false, 0.0, 0.0, rng, null);
    double[] pooled = attnPooling.forward(hTime, seq.mask, null);
    double prob = classifier.forward(pooled, null);
    totalSamplesProcessed++;

    String level;
    if (prob > 0.90) level = "insane";
    else if (prob > 0.80) level = "suspiciously_high";
    else if (prob > 0.70) level = "suspicious";
    else if (prob > 0.60) level = "unusual";
    else level = "normal";

    return new RNNResult(prob, level);
  }

  /** Result of RNN analysis. */
  public static final class RNNResult {
    public final double probability;
    public final String level;
    public final boolean flagged;

    RNNResult(double probability, String level) {
      this.probability = probability;
      this.level = level;
      this.flagged = probability >= DECISION_THRESHOLD;
    }

    public double getProbability() {
      return probability;
    }

    public String getLevel() {
      return level;
    }

    public boolean isFlagged() {
      return flagged;
    }
  }

  // ── Model persistence ──

  /** Save model to a file */
  public void saveToFile(Path path) {
    try (DataOutputStream out =
        new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
      out.writeInt(MAGIC);
      out.writeInt(VERSION);
      out.writeInt(INPUT_SIZE);
      out.writeInt(HIDDEN_SIZE);
      out.writeInt(NUM_LAYERS);
      out.writeInt(totalSamplesProcessed);

      // Embedding weights
      ModelIO.writeArr(out, embWy);
      ModelIO.writeArr(out, embWp);
      ModelIO.writeArr(out, embB);

      // Encoder layers
      out.writeInt(NUM_LAYERS);
      for (int l = 0; l < NUM_LAYERS; l++) {
        writeLayer(out, encoder.fwd[l]);
        if (BIDIRECTIONAL) writeLayer(out, encoder.bwd[l]);
      }

      // Attention
      ModelIO.writeArr(out, attnPooling.W);
      out.writeDouble(attnPooling.b);

      // Classifier
      ModelIO.writeArr(out, classifier.V);
      out.writeDouble(classifier.bias);
    } catch (Exception e) {
      // silent
    }
  }

  /** Load model from an InputStream */
  public void load(InputStream in) {
    try (DataInputStream dis = new DataInputStream(new BufferedInputStream(in))) {
      int magic = dis.readInt();
      int ver = dis.readInt();
      if (magic != MAGIC) return;

      int inSize = dis.readInt();
      int hid = dis.readInt();
      int layers = dis.readInt();
      totalSamplesProcessed = dis.readInt();

      // Architecture validation
      if (inSize != INPUT_SIZE || hid != HIDDEN_SIZE || layers != NUM_LAYERS) return;

      // Embedding
      readInto(dis, embWy);
      readInto(dis, embWp);
      readInto(dis, embB);

      // Encoder
      int nLayers = dis.readInt();
      for (int l = 0; l < nLayers; l++) {
        readLayer(dis, encoder.fwd[l]);
        if (BIDIRECTIONAL) readLayer(dis, encoder.bwd[l]);
      }

      // Attention
      readInto(dis, attnPooling.W);
      attnPooling.b = dis.readDouble();

      // Classifier
      readInto(dis, classifier.V);
      classifier.bias = dis.readDouble();
    } catch (Exception e) {
      // silent
    }
  }

  /** Load model from file path */
  public void loadFromFile(Path path) {
    if (Files.exists(path)) {
      try (InputStream in = Files.newInputStream(path)) {
        load(in);
      } catch (Exception ignored) {
      }
    }
  }

  /** Count total trainable parameters */
  public int countParameters() {
    long p = 0;
    p += embWy.length + embWp.length + embB.length;
    for (int l = 0; l < NUM_LAYERS; l++) {
      p += countLayer(encoder.fwd[l]);
      if (BIDIRECTIONAL) p += countLayer(encoder.bwd[l]);
    }
    p += attnPooling.W.length + 1;
    p += classifier.V.length + 1;
    return (int) p;
  }

  public int getTotalSamplesProcessed() {
    return totalSamplesProcessed;
  }

  // ── Layer serialization helpers ──

  private static void writeLayer(DataOutputStream out, LSTMLayer l) throws IOException {
    ModelIO.writeArr(out, l.Wf);
    ModelIO.writeArr(out, l.Wi);
    ModelIO.writeArr(out, l.Wc);
    ModelIO.writeArr(out, l.Wo);
    ModelIO.writeArr(out, l.Uf);
    ModelIO.writeArr(out, l.Ui);
    ModelIO.writeArr(out, l.Uc);
    ModelIO.writeArr(out, l.Uo);
    ModelIO.writeArr(out, l.bf);
    ModelIO.writeArr(out, l.bi);
    ModelIO.writeArr(out, l.bc);
    ModelIO.writeArr(out, l.bo);
    ModelIO.writeArr(out, l.lnGamma);
    ModelIO.writeArr(out, l.lnBeta);
  }

  private static void readLayer(DataInputStream in, LSTMLayer l) throws IOException {
    readInto(in, l.Wf);
    readInto(in, l.Wi);
    readInto(in, l.Wc);
    readInto(in, l.Wo);
    readInto(in, l.Uf);
    readInto(in, l.Ui);
    readInto(in, l.Uc);
    readInto(in, l.Uo);
    readInto(in, l.bf);
    readInto(in, l.bi);
    readInto(in, l.bc);
    readInto(in, l.bo);
    readInto(in, l.lnGamma);
    readInto(in, l.lnBeta);
  }

  private static void readInto(DataInputStream in, double[] dst) throws IOException {
    double[] a = ModelIO.readArr(in);
    if (a == null) return;
    System.arraycopy(a, 0, dst, 0, Math.min(a.length, dst.length));
  }

  private static long countLayer(LSTMLayer l) {
    return l.Wf.length
        + l.Wi.length
        + l.Wc.length
        + l.Wo.length
        + l.Uf.length
        + l.Ui.length
        + l.Uc.length
        + l.Uo.length
        + l.bf.length
        + l.bi.length
        + l.bc.length
        + l.bo.length
        + l.lnGamma.length
        + l.lnBeta.length;
  }
}
