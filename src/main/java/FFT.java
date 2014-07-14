package soroldoni;

public class FFT {
	/*
	 * Thankfully borrowed from
	 * http://www.cs.princeton.edu/introcs/97data/FFT.java
	 * compute the FFT of x[], x.length must be a power of 2!
	 */
	public double[][] fft(double[][] x) {
		int n = x.length;

		if (n == 1)
			return new double[][] { x[0] };

		// Cooley-Tukey FFT
		if ((n & 1) != 0)
			throw new RuntimeException("n is not a power of 2");

		// fft of even terms
		double[][] even = new double[n/2][];
		for (int k = 0; k < n/2; k++)
			even[k] = x[2*k];
		double[][] q = fft(even);

		// fft of odd terms
		double[][] odd  = even;  // reuse the array
		for (int k = 0; k < n/2; k++)
			odd[k] = x[2*k + 1];
		double[][] r = fft(odd);

		// combine
		double[][] y = new double[n][2];
		for (int k = 0; k < n/2; k++) {
			double kth = -2 * k * Math.PI / n;
			double c = Math.cos(kth), s = Math.sin(kth);
			double kr0 = c * r[k][0] - s * r[k][1];
			double kr1 = c * r[k][1] + s * r[k][0];
			y[k][0] = q[k][0] + kr0;
			y[k][1] = q[k][1] + kr1;
			y[k + n/2][0] = q[k][0] - kr0;
			y[k + n/2][1] = q[k][1] - kr1;
		}
		return y;
	}

	public double[][] conjugate(double[][] x) {
		double[][] result = new double[x.length][2];
		for (int i = 0; i < x.length; i++) {
			result[i][0] = x[i][0];
			result[i][1] = -x[i][1];
		}
		return result;
	}

	public double[][] divide(double[][] x, double factor) {
		double[][] result = new double[x.length][2];
		for (int i = 0; i < x.length; i++) {
			result[i][0] = x[i][0] / factor;
			result[i][1] = x[i][1] / factor;
		}
		return result;
	}

	// compute the inverse FFT of x[]
	public double[][] ifft(double[][] x) {
		return divide(conjugate(fft(conjugate(x))), x.length);
	}

	public void print(double[][] values) {
		for (int i = 0; i < values.length; i++)
			System.out.print(" " + values[i][0] + ";"
					+ values[i][1]);
		System.out.println("");
	}

	public double[][] multiply(double[][] x, double[][] y) {
		double[][] r = new double[x.length][2];
		for (int i = 0; i < x.length; i++) {
			r[i][0] = x[i][0] * y[i][0] - x[i][1] * y[i][1];
			r[i][1] = x[i][1] * y[i][0] + x[i][0] * y[i][1];
		}
		return r;
	}
}