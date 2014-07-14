package soroldoni;

public class LinearRegression {
	protected boolean calculated;
	protected double a, b;
	protected double sx, sxx, sy, sxy;
	protected int count;

	public LinearRegression(double[] x, double[] y) {
		int length = Math.min(x.length, y.length);
		for (int i = 0; i < length; i++)
			add(x[i], y[i]);
	}

	public LinearRegression() {}

	public void add(double x, double y) {
		sx += x;
		sxx += x * x;
		sy += y;
		sxy += x * y;
		count++;
		calculated = false;
	}

	public void reset() {
		sx = sxx = sy = sxy = count = 0;
		calculated = false;
	}

	protected void calculate() {
		double m00, m01, m11, det;
		det = sxx * count - sx * sx;
		m00 = count / det;
		m01 = -sx / det;
		m11 = sxx / det;
		a = m00 * sxy + m01 * sy;
		b = m01 * sxy + m11 * sy;
		calculated = true;
	}

	public double getA() {
		if (!calculated)
			calculate();
		return a;
	}

	public double getB() {
		if (!calculated)
			calculate();
		return b;
	}

	public double get(double x) {
		if (!calculated)
			calculate();
		return a * x + b;
	}

	public double[] get(double[] x) {
		if (!calculated)
			calculate();
		double[] y = new double[x.length];
		for (int i = 0; i < x.length; i++)
			y[i] = a * x[i] + b;
		return y;
	}

	/**
	 * Filter out outliers.
	 *
	 * @param tolerance An outlier is a pair (x, y) deviating more than tolerance * average deviation
	 * @return new double[][] { x', y' }.
	 */
	public double[][] filterOutOutliers(double[] x, double[] y, double tolerance) {
		StandardDeviation stdDev = new StandardDeviation();
		for (int i = 0; i < x.length && i < y.length; i++)
			stdDev.add(Math.abs(distanceTo(x[i], y[i])));
		tolerance *= stdDev.getMean();

		int[] index = new int[x.length];
		int count = 0;
		for (int i = 0; i < x.length && i < y.length; i++)
			if (Math.abs(distanceTo(x[i], y[i])) <= tolerance)
				index[count++] = i;

		double[][] result = new double[2][count];
		for (int i = 0; i < count; i++) {
			result[0][i] = x[index[i]];
			result[1][i] = y[index[i]];
		}
		return result;
	}

	public double distanceTo(double x, double y) {
		return y - get(x);
	}
}