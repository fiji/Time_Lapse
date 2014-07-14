package soroldoni;

import ij.ImagePlus;

import ij.gui.Plot;
import ij.gui.PolygonRoi;

import ij.plugin.Straightener;

import ij.process.ImageProcessor;

public class PlotUtils {
	protected static double[] range(double start, double end) {
		return range(start, end, 1);
	}

	protected static double[] range(double start, double end, double step) {
		if (end < start)
			return new double[0];
		double[] x = new double[(int)Math.floor((end - start) / step) + 1];
		for (int i = 0; i < x.length; i++)
			x[i] = start + step * i;
		return x;
	}

	public static double[] trimOutliers(double[] series, double tolerance) {
		StandardDeviation stdDev = new StandardDeviation();
		for (double value : series)
			stdDev.add(value);
		tolerance *= stdDev.getStandardDeviation();
		double mean = stdDev.getMean();
		int start = 0;
		while (start < series.length - 1 && Math.abs(series[start] - mean) > tolerance)
			start++;
		int end = series.length;
		while (end > 1 && Math.abs(series[end - 1] - mean) > tolerance)
			end--;
		return start == 0 && end == series.length ? series : subSeries(series, start, end);
	}

	public static double[] subSeries(double[] series, int start) {
		return subSeries(series, start, series.length);
	}

	public static double[] subSeries(double[] series, int start, int end) {
		double[] result = new double[end - start];
		System.arraycopy(series, start, result, 0, result.length);
		return result;
	}

	public static double[] setLimits(Plot plot, double[]... coords) {
		double minX, maxX, minY, maxY;
		minX = maxX = coords[0][0];
		minY = maxY = coords[1][0];
		for (int i = 0; i < coords.length - 1; i += 2)
			for (int j = 0; j < coords[i].length && j < coords[i + 1].length; j++) {
				if (minX > coords[i][j])
					minX = coords[i][j];
				else if (maxX < coords[i][j])
					maxX = coords[i][j];
				if (minY > coords[i + 1][j])
					minY = coords[i + 1][j];
				else if (maxY < coords[i + 1][j])
					maxY = coords[i + 1][j];
			}
		return setLimits(plot, minX, maxX, minY, maxY);
	}

	public static double[] setLimits(Plot plot, double minX, double maxX, double minY, double maxY) {
		double marginX = minX != maxX ? (maxX - minX) * 0.05 : 1;
		double marginY = minY != maxY ? (maxY - minY) * 0.05 : 1;
		plot.setLimits(minX - marginX, maxX + marginX, minY - marginY, maxY + marginY);
		return new double[] { minX - marginX, maxX + marginX, minY - marginY, maxY + marginY };
	}

	protected static float[] toFloat(int[] array) {
		float[] result = new float[array.length];
		for (int i = 0; i < array.length; i++)
			result[i] = array[i];
		return result;
	}

	protected static float[] toFloat(double[] array) {
		float[] result = new float[array.length];
		for (int i = 0; i < array.length; i++)
			result[i] = (float)array[i];
		return result;
	}

	public static double[] toDouble(float[] array) {
		double[] result = new double[array.length];
		for (int i = 0; i < result.length; i++)
			result[i] = array[i];
		return result;
	}

	public static double[] log(double[] series) {
		double[] result = new double[series.length];
		for (int i = 0; i < series.length; i++)
			result[i] = Math.log(series[i]);
		return result;
	}

	public static double[] compensateForDecay(double[] series) {
		LinearRegression regression = new LinearRegression(PlotUtils.range(0, series.length - 1), series);
		double[] result = new double[series.length];
		for (int i = 0; i < series.length; i++)
			result[i] = series[i] - regression.get(i);
		return result;
	}

	public static double[] fft(double[] series) {
		int fftSize = 1;
		while (fftSize < series.length)
			fftSize *= 2;
		double[][] fftData = new double[fftSize][2];
		double average = 0;
		for (int i = 0; i < series.length; i++) {
			fftData[i][0] = series[i];
			average += series[i];
		}
		average /= series.length;
		for (int i = series.length; i < fftSize; i++)
			fftData[i][0] = average;
		double[][] transformed = new FFT().fft(fftData);
		double[] result = new double[transformed.length];
		for (int i = 0; i < result.length; i++)
			result[i] = transformed[i][0];
		return result;
	}

	protected static float[] getProfile(ImageProcessor ip, PolygonRoi roi, boolean fitSpline, int lineWidth) {
		if (fitSpline && !roi.isSplineFit()) {
			roi = (PolygonRoi)roi.clone();
			roi.fitSpline();
		}
		ImagePlus dummy = new ImagePlus("dummy", ip);
		dummy.setRoi(roi);
		Straightener straightener = new Straightener();
		ip = straightener.straightenLine(dummy, lineWidth);
		int w = ip.getWidth();
		float[] result = new float[w];
		// need to average explicitely
		for (int j = 0; j < w; j++) {
			float value = ip.getf(j, 0);
			for (int k = 1; k < lineWidth; k++)
				value += ip.getf(j, k);
			result[j] = value / lineWidth;
		}
		return result;
	}
}