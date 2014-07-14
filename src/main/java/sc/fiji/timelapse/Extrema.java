package sc.fiji.timelapse;

import Jama.Matrix;

import ij.gui.Plot;

import java.awt.Color;

public class Extrema {
	protected double tolerance;
	protected double[] series;
	protected int extremaCount;

	protected double min, max;
	protected int left, right;

	protected double[] x, intensities;

	/**
	 * Determine the extrema of a given series.
	 *
	 * @param series contains the data to process
	 * @param tolerance When determining the x and y of a minimum,
	 *        fit a parabola in the intervals around local extrema
	 *        where the intensity differs less than tolerance * (max - min)
	 * @param findMaxima tells whether we want to find maxima instead of minima
	 */
	public Extrema(double[] series, double tolerance, boolean findMaxima) {
		this.series = series;
		this.tolerance = tolerance;
		if (findMaxima)
			negate(series);
		getMinAndMax();

		x = new double[series.length];
		intensities = new double[series.length];
		extremaCount = 0;
		for (int i = 1; i < series.length - 1; i++)
			if (getMinimumInterval(i))
				addMinimum(i);
		x = shorten(x, extremaCount);
		intensities = shorten(intensities, extremaCount);
		if (findMaxima) {
			negate(series);
			negate(intensities);
		}
	}

	/**
	 * Determine the extrema of a given series.
	 *
	 * The extrema are determined by linear regression in a window left and right of the points
	 * and keeping those with at least the regressed slope on either side.
	 *
	 * @param series contains the data to process
	 * @param window window to look in on either side of candidates
	 * @param minimalSlope a candidate is only an extremum if the slope exceeds minimalSlope on either side
	 * @param findMaxima true for maxima, false for minima
	 */
	public Extrema(double[] series, int window, double minimalSlope, boolean findMaxima) {
		this.series = series;

		double factor = findMaxima ? +1 : -1;
		x = new double[series.length];
		extremaCount = 0;
		for (int i = window; i < series.length - window; i++)
			if (getSlope(i - window, i) * factor >= minimalSlope &&
					getSlope(i, i + window) * factor < -minimalSlope)
				if (extremaCount > 0 && i - x[extremaCount - 1] < 4 * window) {
					if (series[(int)x[extremaCount - 1]] * factor < series[i] * factor)
						x[extremaCount - 1] = i;
				}
				else
					x[extremaCount++] = i;
		x = shorten(x, extremaCount);

		intensities = new double[extremaCount];
		for (int i = 0; i < extremaCount; i++)
			intensities[i] = series[(int)x[i]];
	}

	protected double getSlope(int from, int to) {
		LinearRegression regression = new LinearRegression();
		for (int i = from > 0 ? from : 0; i <= to && i < series.length; i++)
			regression.add(i, series[i]);
		return regression.getA();
	}

	protected void negate(double[] series) {
		for (int i = 0; i < series.length; i++)
			series[i] *= -1;
	}

	public double[] getX() {
		return x;
	}

	public double[] getIntensities() {
		return intensities;
	}

	protected boolean getMinimumInterval(int index) {
		double maxIntensity = series[index] + tolerance * (max - min);
		for (left = index; left > 0; left--)
			if (series[left - 1] < series[index])
				return false;
			else if (series[left - 1] > maxIntensity)
				break;
		for (right = index; right < series.length - 1; right++)
			if (series[right + 1] < series[index])
				return false;
			else if (series[right + 1] > maxIntensity)
				break;
		return true;
	}

	protected void addMinimum(int middle) {
		x[extremaCount] = middle;
		intensities[extremaCount] = series[middle];
		extremaCount++;
	}

	/**
	 * Fit a parabola to the interval [left;right].
	 */
	protected void addFittedMinimum(int middle) {
		if (left + 1 == right) {
			if (series[left] > series[right])
				left = right;
			else
				right = left;
		}
		if (left == right) {
			x[extremaCount] = left;
			intensities[extremaCount] = series[left];
			extremaCount++;
			return;
		}
		double x1, x2, x3, x4, y, yx1, yx2, total;
		x1 = x2 = x3 = x4 = y = yx1 = yx2 = 0;
		total = right + 1 - left;
		for (; left <= right; left++) {
			x1 += left;
			x2 += left * left;
			x3 += left * left * left;
			x4 += left * left * left * left;
			y += series[left];
			yx1 += series[left] * left;
			yx2 += series[left] * left * left;
		}
		x1 /= total;
		x2 /= total;
		x3 /= total;
		x4 /= total;
		y /= total;
		yx1 /= total;
		yx2 /= total;
		Matrix matrix = new Matrix(new double[][] {
			{ x4, x3, x2 },
			{ x3, x2, x1 },
			{ x2, x1, 1 }
		});
		Matrix rhs = new Matrix(new double[][] {
			{ yx2 }, { yx1 }, { y }
		});
		Matrix abc = matrix.solve(rhs);
		double a = abc.get(0, 0);
		double b = abc.get(1, 0);
		double c = abc.get(2, 0);
		x[extremaCount] = -b / 2 / a;
		intensities[extremaCount] = a * x[extremaCount] * x[extremaCount] + b * x[extremaCount] + c;
		extremaCount++;
	}

	protected double[] shorten(double[] array, int length) {
		double[] result = new double[length];
		System.arraycopy(array, 0, result, 0, length);
		return result;
	}

	protected void getMinAndMax() {
		min = series[0];
		max = series[0];
		for (int i = 11; i < series.length; i++)
			if (min > series[i])
				min = series[i];
			else if (max < series[i])
				max = series[i];
	}

	public Plot getPlot() {
		double[] x = PlotUtils.range(0, series.length - 1);
		Plot plot = new Plot("Profile", "x", "intensity", x, series);

		PlotUtils.setLimits(plot, x, series, getX(), getIntensities());

		plot.draw();
		plot.setColor(Color.BLUE);
		plot.addPoints(getX(), getIntensities(), Plot.CIRCLE);

		return plot;
	}
}
