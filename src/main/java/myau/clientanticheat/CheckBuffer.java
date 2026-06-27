package myau.clientanticheat;

public class CheckBuffer {
  private double value;

  public boolean flag(double add, double threshold) {
    this.value += add;
    return this.value >= threshold;
  }

  public boolean flagAndDecay(double add, double threshold, double decayRate) {
    this.value += add;
    if (this.value >= threshold) {
      return true;
    }
    this.decay(decayRate);
    return false;
  }

  public void decay(double amount) {
    this.value = Math.max(0.0D, this.value - amount);
  }

  public void multiDecay(double factor) {
    if (this.value > 0.0D) {
      this.value *= factor;
      this.value -= 0.1D;
      this.value = Math.max(0.0D, this.value);
    }
  }

  public void reset() {
    this.value = 0.0D;
  }

  public double get() {
    return this.value;
  }

  public void set(double value) {
    this.value = value;
  }

  public void increment() {
    this.value += 1.0D;
  }

  public void increment(double amount) {
    this.value += amount;
  }

  public double getAndDecay(double rate) {
    double current = this.value;
    this.decay(rate);
    return current;
  }

  public void clamp(double min, double max) {
    this.value = Math.max(min, Math.min(max, this.value));
  }

  public boolean exceeds(double threshold) {
    return this.value > threshold;
  }
}
