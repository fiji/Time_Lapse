package sc.fiji.timelapse;

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;

import java.util.Arrays;

/**
 * This plugin generates a phase map given a kymograph.
 * <p>
 * The kymograph's horizontal axis is expected to be the temporal one, and the
 * vertical signal is assumed to be periodic.
 * </p>
 * 
 * @author Johannes Schindelin
 */
public class Phase_Map implements PlugInFilter {
	private final static double FOURIER_PERIOD = 4 * Math.PI / (6 + Math.sqrt(2 + 6 * 6));

	private double octaveNumber = 4, voicesPerOctave = 50;
	private double gaussSigma = 2, x0 = 100, x1 = 400, sigma0 = 1, sigma1 = 1, subtractionPoint = 50;
	private boolean plotWaveCounts, showProfileStack, anchorProfileStack, cutTailsFromProfileStack, showPhaseProfileMap;

	private ImagePlus imp;

	private double phase(double[] data, int dataSize, double s, int tau) {
		double wR = 0, wI = 0;

		for (int i = 0; i < dataSize; i++) {
			double u = (i - tau) / s;
			double decay = Math.exp(-u * u / 2);
			double gaborR = Math.cos(6 * u) * decay, gaborI = Math.sin(6 * u) * decay;

			// normalization unnecessary:
			// gaborR /= Math.pow(Math.PI, 1.0 / 4);
			// gaborI /= Math.pow(Math.PI, 1.0 / 4);

			wR += data[i] * gaborR;
			wI += data[i] * -gaborI;
		}

		// normalization unnecessary:
		// wR /= Math.sqrt(s);
		// wI /= Math.sqrt(s);

		return Math.atan2(wI, wR);
		//return Math.sqrt(wI * wI + wR * wR);
		//return wI;
	}

	protected static int[] gaussianLUT()
	{
		final int[] lut = new int[256];
		for (int i = 0; i < 255; i++) {
			double phase = (i - 127.5) * Math.PI / 127.5;
			double factor = Math.exp(-phase * phase);
			int red = (int)(factor * 255);
			int green = (int)(factor * 330);
			int blue = (int)(factor * 400);
			lut[i] = (red << 16) | ((green > 255 ? 255 : green) << 8) | (blue > 255 ? 255 : blue);
		}
		return lut;
	}

	protected static LUT createLUT()
	{
		final byte[] red = new byte[256];
		final byte[] green = new byte[256];
		final byte[] blue = new byte[256];
		for (int i = 0; i < 128; i++) {
			red[i] = (byte)((128 - i) * (128 - i) * 127 / 128 / 128);
			green[i] = (byte)((128 - i) * 220 / 128);
			if (i < 64) {
				blue[i] = (byte)(220 + i * (150 - 220) / 64);
			}
			else {
				blue[i] = (byte)((128 - i) * 150 / 64);
			}
		}
		for (int i = 128; i < 192; i++) {
			red[i] = green[i] = blue[i] = (byte)((i - 128) * 255 / 64);
		}
		for (int i = 192; i < 256; i++) {
			red[i] = (byte)(255 + (i - 192) * (127 - 255) / 64);
			green[i] = blue[i] = (byte)(255 + (i - 192) * (220 - 255) / 64);
		}
		return new LUT(red, green, blue);
	}

	protected static class Gauss1D {
		private final int radius;
		private final double[] kernel;

		public Gauss1D(double sigma) {
			radius = (int)Math.ceil(sigma * 2);
			kernel = new double[1 + 2 * radius];
			double total = 0;
			for (int i = -radius; i <= radius; i++) {
				kernel[i + radius] = Math.exp(-0.5 * i * i / sigma / sigma);
				total += kernel[i + radius];
			}
			for (int i = 0; i < kernel.length; i++) {
				kernel[i] /= total;
			}
		}

		public void gauss(final double[] data, final int offset, final int dataSize) {
			final double[] result = new double[dataSize];
			if (result.length < kernel.length) {
				throw new IllegalArgumentException("Too few data");
			}
			// mirror out-of-bounds strategy
			for (int i = 0; i < radius; i++) {
				double value = 0;
				for (int j = -radius, k = radius + 1 - i; j < -i; j++, k--) {
					value += data[offset + k] * kernel[radius + j];
				}
				for (int j = -i; j <= radius; j++) {
					value += data[offset + i + j] * kernel[radius + j];
				}
				result[i] = value;
			}
			for (int i = radius; i < dataSize - radius; i++) {
				double value = 0;
				for (int j = -radius; j <= radius; j++) {
					value += data[offset + i + j] * kernel[radius + j];
				}
				result[i] = value;
			}
			// mirror out-of-bounds strategy
			for (int i = dataSize - radius; i < dataSize; i++) {
				double value = 0;
				for (int j = -radius; j < dataSize - i; j++) {
					value += data[offset + i + j] * kernel[radius + j];
				}
				for (int j = dataSize - i, k = dataSize - 1; j <= radius; j++, k--) {
					value += data[offset + k] * kernel[radius + j];
				}
				result[i] = value;
			}
			System.arraycopy(result, 0, data, offset, dataSize);
		}

		public void gauss(final float[] data, final int offset, final int dataSize) {
			final float[] result = new float[dataSize];
			if (result.length < kernel.length) {
				throw new IllegalArgumentException("Too few data");
			}
			// mirror out-of-bounds strategy
			for (int i = 0; i < radius; i++) {
				float value = 0;
				for (int j = -radius, k = radius + 1 - i; j < -i; j++, k--) {
					value += data[offset + k] * kernel[radius + j];
				}
				for (int j = -i; j <= radius; j++) {
					value += data[offset + i + j] * kernel[radius + j];
				}
				result[i] = value;
			}
			for (int i = radius; i < dataSize - radius; i++) {
				float value = 0;
				for (int j = -radius; j <= radius; j++) {
					value += data[offset + i + j] * kernel[radius + j];
				}
				result[i] = value;
			}
			// mirror out-of-bounds strategy
			for (int i = dataSize - radius; i < dataSize; i++) {
				float value = 0;
				for (int j = -radius; j < dataSize - i; j++) {
					value += data[offset + i + j] * kernel[radius + j];
				}
				for (int j = dataSize - i, k = dataSize - 1; j <= radius; j++, k--) {
					value += data[offset + k] * kernel[radius + j];
				}
				result[i] = value;
			}
			System.arraycopy(result, 0, data, offset, dataSize);
		}
	}

	private float[] phaseMap(final ImageProcessor kymograph, final boolean showProfileMap) {
		final int width = kymograph.getWidth(), height = kymograph.getHeight();
		final FloatProcessor fp = (FloatProcessor)(kymograph instanceof FloatProcessor ?
				kymograph.duplicate() : kymograph.convertToFloat());
		final float[] pixels = (float[]) fp.getPixels();
		float[] output = new float[width * height];
		final double[] data = new double[height];

		final int[] rowLength = new int[height];

		// gauss along x
		final Gauss1D gauss = new Gauss1D(gaussSigma);
		for (int t = 0; t < height; t++) {
			gauss.gauss(pixels, t * width, width);
		}

		for (int x = 0; x < width; x++) {
			double voiceNumber = x < x0 ? sigma0 : x > x1 ? sigma1 : sigma0 + (x - x0) * (sigma1 - sigma0) / (x1 - x0);
			double s = Math.pow(2, octaveNumber - 1 + voiceNumber / voicesPerOctave) / FOURIER_PERIOD;

			int dataSize = height;
			for (int t = 0; t < height; t++) {
				data[t] = pixels[x + t * width];
				if (data[t] < 2) {
					dataSize = t;
					break;
				}
			}
			for (int t = 0; t < dataSize; t++) {
				output[x + t * width] = (float)phase(data, dataSize, s, t);
			}
		}

		if (!showProfileMap) {
			return output;
		}

		final float[] phaseMap = output;
		output = new float[width * height];

		// get row length
		for (int t = 0; t < height; t++) {
			int curWidth = width;
			for (int x = 0; x < width; x++) {
				if (pixels[x + t * width] < 2) {
					curWidth = x;
					break;
				}
			}
			rowLength[t] = curWidth;
		}

		// compute the spatial phase profile as a function of time
		for (int t = 0; t < height; t++) {
			for (int x = 0; x < rowLength[t]; x++) {
				float phaseOffset = ( phaseMap[x + t * width] - phaseMap[(int)subtractionPoint + t * width] + (float)(Math.PI) ) % (float)(2.*Math.PI);
				if (phaseOffset < 0) {
					phaseOffset += (float)(2.*Math.PI);
				}
				output[x + t * width] = phaseOffset - (float)(Math.PI);
			}
			for (int x = rowLength[t] + 1; x < width; x++) {
				output[x + t * width] = 0;
			}
		}

		return output;
	}

	private float[] getProfile(final float[] pixels, final int offset, final int length) {
		final float[] profile = new float[length];
		for (int i = 0; i < length; i++) {
			profile[i] = pixels[offset + i];
			if (i > 0) {
				float diff = (profile[i] - profile[i - 1]) / (float) Math.PI;
				if (Math.abs(diff) >= 0.5)
					profile[i] -= Math.PI * Math.round(diff);
			}
		}
		return profile;
	}

	private float[] getProfileAtTimepoint(final int t, final float[] map, final int width, final int height) {
		final int offset = t * width;
		int length = width;
		while (length > 0 && map[offset + length - 1] == 0)
			length--;
		return getProfile(map, offset, length);
	}

	/**
	 * Returns an array of increasing integers for use in plot windows.
	 * 
	 * @param from minimum value
	 * @param to maximum value + 1
	 * @return the range
	 */
	public static float[] range(final int from, final int to, final float factor) {
		final float[] range = new float[to - from];
		for (int i = 0; i < range.length; i++) {
			range[i] = from + i * factor;
		}
		return range;
	}

	/**
	 * Divides a list of float numbers by a factor, non-destructively.
	 * 
	 * @param array the input values
	 * @param factor the factor to divide by
	 * @return the multiplied values
	 */
	public static float[] divide(final float[] array, final float factor) {
		final float[] result = new float[array.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = array[i] / factor;
		}
		return result;
	}

	private ImagePlus getProfileStack(final String title, final float[] map,
			final int width, final int height, final float pixelSpacing, final String pixelSpacingUnit,
			final boolean anchorToZero, boolean cutTails) {
		ImageStack stack = null;
		final float[][] profiles = new float[height][];
		float maxX, minT, maxT;
		minT = Float.MAX_VALUE;
		maxX = maxT = -Float.MAX_VALUE;
		for (int t = 0; t < height; t++) {
			profiles[t] = getProfileAtTimepoint(t, map, width, height);
			if (maxX < profiles[t].length) {
				maxX = profiles[t].length;
			}
			if (anchorToZero) {
				for (int i = profiles[t].length - 1; i >= 0; i--) {
					profiles[t][i] -= profiles[t][0];
				}
			}
			if (cutTails && profiles[t].length > WAVE_COUNT_CUT_OFF) {
				final float[] sorted = Arrays.copyOf(profiles[t], profiles[t].length);
				Arrays.sort(sorted);
				float min = sorted[WAVE_COUNT_CUT_OFF];
				int length = profiles[t].length;
				while (length > 0 && profiles[t][length - 1] > min) {
					length--;
				}
				if (length < profiles[t].length) {
					profiles[t] = Arrays.copyOf(profiles[t], length);
				}
			}
			for (float f : profiles[t]) {
				if (minT > f) {
					minT = f;
				}
				if (maxT < f) {
					maxT = f;
				}
			}
		}
		final float pi2 = (float) (2 * Math.PI);
		for (int t = 0; t < height; t++) {
			final float[] x = range(0, profiles[t].length, pixelSpacing);
			final Plot plot = new Plot("profile",
					"distance" + ("".equals(pixelSpacingUnit) ? "" : (" (" + pixelSpacingUnit + ")")),
					"phase (× 2π)", x, divide(profiles[t], pi2));
			plot.setFrameSize(850, 400);
			plot.setLimits(0, maxX, minT / pi2, maxT / pi2);
			final ImageProcessor ip = plot.getProcessor();
			if (stack == null)
				stack = new ImageStack(ip.getWidth(), ip.getHeight());
			stack.addSlice("t=" + t, ip);
		}
		final ImagePlus result = new ImagePlus(title, stack);
		Calibration calibration = result.getCalibration();
		if (calibration == null) {
			result.setCalibration((calibration = new Calibration()));
		}
		calibration.pixelWidth = pixelSpacing;
		calibration.setUnit(pixelSpacingUnit);
		final StringBuilder builder = new StringBuilder();
		builder.append("[\n");
		for (int t = 0; t < profiles.length; t++) {
			builder.append("  [");
			for (int i = 0; i < profiles[t].length; i++) {
				if (i > 0) builder.append(", ");
				builder.append(profiles[t][i]);
			}
			builder.append(t + 1 < profiles.length ? "],\n" : "]\n");
		}
		builder.append("]\n");
		result.setProperty("Info", builder.toString());
		return result;
	}

	/**
	 * This constant declares how many values should be cut off
	 * when determining the minimum and maximum of a profile
	 * (akin to skipping a given percentile).
	 */
	private final static int WAVE_COUNT_CUT_OFF = 2;

	private float[] getWaveCounts(final float[] map, final int width, final int height) {
		final float[] waveCounts = new float[height];
		for (int t = 0; t < height; t++) {
			final float[] profile = getProfileAtTimepoint(t, map, width, height);
			if (profile.length <= WAVE_COUNT_CUT_OFF) continue;
			Arrays.sort(profile);
			waveCounts[t] = (float) ((profile[profile.length - WAVE_COUNT_CUT_OFF]
					- profile[WAVE_COUNT_CUT_OFF]) / 2 / Math.PI);
		}
		return waveCounts;
	}

	@Override
	public int setup(final String arg, final ImagePlus imp) {
		this.imp = imp;
		return DOES_ALL | NO_CHANGES;
	}

	@Override
	public void run(final ImageProcessor ip) {
		final GenericDialog gd = new GenericDialog("Phase Map");
		gd.addNumericField("Octave_number", octaveNumber, 0);
		gd.addNumericField("Voices_per_octave", voicesPerOctave, 0);
		gd.addNumericField("Gauss_sigma_(x-axis)", gaussSigma, 2);
		gd.addNumericField("x0", x0, 0);
		gd.addNumericField("x1", x1, 0);
		gd.addNumericField("sigma0", sigma0, 0);
		gd.addNumericField("sigma1", sigma1, 0);
		gd.addCheckbox("Plot_wave_counts", plotWaveCounts);
		gd.addCheckbox("Show_profile_stack", showProfileStack);
		gd.addCheckbox("Anchor_profile_stack i.e. normalize to start at (0,0)", anchorProfileStack);
		gd.addCheckbox("Cut_tails_from_profile_stack i.e. skip spurious signal at tail", cutTailsFromProfileStack);
		gd.addCheckbox("Show_phase_profile_map", showPhaseProfileMap);
		gd.addNumericField("Subtraction_point", subtractionPoint, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;

		octaveNumber = gd.getNextNumber();
		voicesPerOctave = gd.getNextNumber();
		gaussSigma = gd.getNextNumber();
		x0 = gd.getNextNumber();
		x1 = gd.getNextNumber();
		sigma0 = gd.getNextNumber();
		sigma1 = gd.getNextNumber();
		plotWaveCounts = gd.getNextBoolean();
		showProfileStack = gd.getNextBoolean();
		anchorProfileStack = gd.getNextBoolean();
		cutTailsFromProfileStack = gd.getNextBoolean();
		showPhaseProfileMap = gd.getNextBoolean();
		subtractionPoint = gd.getNextNumber();

		final int width = ip.getWidth(), height = ip.getHeight();
		final Calibration calibration = imp.getCalibration();
		final float pixelSpacing = calibration == null || calibration.pixelWidth == 0 ?
				1 : (float) calibration.pixelWidth;
		final String pixelSpacingUnit = calibration == null || "".equals(calibration.getUnit()) ?
				"pixels" : calibration.getUnit();
		final float frameInterval = calibration == null || calibration.frameInterval == 0 ?
				1 : (float) calibration.frameInterval;
		final String frameIntervalUnit = calibration == null || "".equals(calibration.getTimeUnit()) ?
				"" : calibration.getTimeUnit();

		float[] phaseMapPixels = phaseMap(ip, false);
		final FloatProcessor resultPhaseMap = new FloatProcessor(width, height, phaseMapPixels);
		resultPhaseMap.setMinAndMax(-Math.PI, Math.PI);
		resultPhaseMap.setLut(createLUT());
		final ImageProcessor phaseMap = resultPhaseMap;

		new ImagePlus("Phase Map of " + imp.getTitle(), phaseMap).show();

		if (plotWaveCounts) {
			final float[] counts = getWaveCounts(phaseMapPixels, width, height);
			final float[] x = range(0, counts.length, frameInterval);
			new Plot("Wave counts of " + imp.getTitle(),
					"time" + ("".equals(frameIntervalUnit) ? "" : (" (" + frameIntervalUnit + ")")),
					"wave count", x, counts).show();
		}

		if (showProfileStack)
			getProfileStack("Profile Stack  of " + imp.getTitle(),
					phaseMapPixels, width, height, pixelSpacing, pixelSpacingUnit,
					anchorProfileStack, cutTailsFromProfileStack).show();

		if (showPhaseProfileMap) {
			final FloatProcessor resultPhaseProfileMap = new FloatProcessor(width, height, phaseMap(ip, true));
			resultPhaseProfileMap.setMinAndMax(-Math.PI, Math.PI);
			resultPhaseProfileMap.setLut(createLUT());
			final ImageProcessor phaseProfileMap = resultPhaseProfileMap;

			new ImagePlus("Phase Profile Map of " + imp.getTitle(), phaseProfileMap).show();
		}

	}
}
