package soroldoni;

public class StandardDeviation {
	protected double x, x2, count;

	public void reset() {
		x = x2 = count = 0;
	}

	public void add(double value) {
		count += 1;
		x += value;
		x2 += value * value;
	}

	public double getCount() {
		return count;
	}

	public double getMean() {
		return x / count;
	}

	public double getVariance() {
		return (x2 - x * x / count) / count;
	}

	public double getStandardDeviation() {
		return Math.sqrt(getVariance());
	}
}