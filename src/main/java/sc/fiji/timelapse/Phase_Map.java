package sc.fiji.timelapse;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
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
public class Phase_Map {
	private final static double[] DATA = {
 102.7778, 98.7768, 94.6039, 93.5387,
		96.7559, 100.6170, 101.3511, 99.1634, 95.4150, 93.2931, 93.0620, 94.4067,
		95.0145, 91.3656, 85.7460, 81.8193, 81.1583, 82.5666, 83.5125, 82.4781,
		79.7292, 76.8684, 75.6667, 75.7833, 77.1071, 77.3563, 75.2343, 73.1426,
		72.8452, 75.6537, 80.5355, 83.1111, 81.7732, 78.1705, 75.7785, 76.5999,
		78.9754, 81.4223, 82.1742, 80.3836, 77.6548, 77.0296, 79.9947, 86.4499,
		93.6043, 96.8819, 97.0000, 97.0000, 98.5363, 100.3333, 102.8655, 106.4932,
		106.3209, 103.6289, 101.5676, 104.4964, 114.2064, 124.8781, 128.0325,
		123.5076, 118.4095, 120.3274, 132.4116, 152.2553, 164.6311, 159.3684,
		145.0733, 135.2279, 137.1769, 153.9188, 175.7037, 179.5289, 170.3401,
		155.7301, 153.6190, 163.6312, 187.0240, 211.0800, 218.9913, 215.6850,
		203.3726, 187.8662, 180.3454, 186.2497, 197.6905, 204.2850, 201.2192,
		186.9690, 167.3154, 156.9617, 160.1731, 175.3750, 188.9225, 182.3175,
		159.0797, 134.9690, 129.1487, 130.9763
	};

	private static double phase(double[] data, int dataSize, double s, int tau) {
		double wR = 0, wI = 0;

		for (int i = -30; i < 0; i++) {
			double u = (i - tau) / s;
			double decay = Math.exp(-u * u / 2);
			double gaborR = Math.cos(6 * u) * decay, gaborI = Math.sin(6 * u) * decay;

			// normalization unnecessary:
			// gaborR /= Math.pow(Math.PI, 1.0 / 4);
			// gaborI /= Math.pow(Math.PI, 1.0 / 4);

			wR += data[1 - i] * gaborR;
			wI += data[1 - i] * -gaborI;
		}

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

		for (int i = 0; i < 0; i++) {
			double u = (dataSize + i - tau) / s;
			double decay = Math.exp(-u * u / 2);
			double gaborR = Math.cos(6 * u) * decay, gaborI = Math.sin(6 * u) * decay;

			// normalization unnecessary:
			// gaborR /= Math.pow(Math.PI, 1.0 / 4);
			// gaborI /= Math.pow(Math.PI, 1.0 / 4);

			wR += data[dataSize - 2 - i] * gaborR;
			wI += data[dataSize - 2 - i] * -gaborI;
		}

		// normalization unnecessary:
		// wR /= Math.sqrt(s);
		// wI /= Math.sqrt(s);

		return Math.atan2(wI, wR);
		//return wI * wI + wR * wR;
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
			red[i] = (byte)(i * i * 127 / 128 / 128);
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

		public void gauss(final double[] data, final int dataSize) {
			final double[] copy = Arrays.copyOf(data, dataSize);
			if (copy.length < kernel.length) {
				throw new IllegalArgumentException("Too few data");
			}
			for (int i = 0; i < radius; i++) {
				double value = 0;
				for (int j = -radius, k = radius + 1 - i; j < -i; j++, k--) {
					value += copy[k] * kernel[radius + j];
				}
				for (int j = -i; j <= radius; j++) {
					value += copy[i + j] * kernel[radius + j];
				}
				data[i] = value;
			}
			for (int i = kernel.length; i < copy.length - kernel.length; i++) {
				double value = 0;
				for (int j = -radius; j <= radius; j++) {
					value += copy[i + j] * kernel[radius + j];
				}
				data[i] = value;
			}
			for (int i = copy.length - radius; i < copy.length; i++) {
				double value = 0;
				for (int j = -radius; j < copy.length - i; j++) {
					value += copy[i + j] * kernel[radius + j];
				}
				for (int j = copy.length - i, k = copy.length - 1; j <= radius; j++, k--) {
					value += copy[k] * kernel[radius + j];
				}
				data[i] = value;
			}
		}

		public void gauss(final float[] data, final int dataSize) {
			final float[] copy = Arrays.copyOf(data, dataSize);
			if (copy.length < kernel.length) {
				throw new IllegalArgumentException("Too few data");
			}
			for (int i = 0; i < radius; i++) {
				float value = 0;
				for (int j = -radius, k = radius + 1 - i; j < -i; j++, k--) {
					value += copy[k] * kernel[radius + j];
				}
				for (int j = -i; j <= radius; j++) {
					value += copy[i + j] * kernel[radius + j];
				}
				data[i] = value;
			}
			for (int i = kernel.length; i < copy.length - kernel.length; i++) {
				float value = 0;
				for (int j = -radius; j <= radius; j++) {
					value += copy[i + j] * kernel[radius + j];
				}
				data[i] = value;
			}
			for (int i = copy.length - radius; i < copy.length; i++) {
				float value = 0;
				for (int j = -radius; j < copy.length - i; j++) {
					value += copy[i + j] * kernel[radius + j];
				}
				for (int j = copy.length - i, k = copy.length - 1; j <= radius; j++, k--) {
					value += copy[k] * kernel[radius + j];
				}
				data[i] = value;
			}
		}
	}

	private final static Gauss1D gauss = new Gauss1D(0.5);

	private final static double OCTAVE_NUMBER = 4, VOICES_PER_OCTAVE = 50, FOURIER_PERIOD = 4 * Math.PI / (6 + Math.sqrt(2 + 6 * 6));

	private static FloatProcessor phaseMap(final ByteProcessor kymograph) {
		final int width = kymograph.getWidth(), height = kymograph.getHeight();
		final float[] pixels = (float[]) kymograph.convertToFloat().getPixels();
		final float[] output = new float[width * height];
		final double[] data = new double[height];

		// gauss along x
		final float[] gaussData = new float[width];
		for (int t = 0; t < height; t++) {
			System.arraycopy(pixels, t * width, gaussData, 0, width);
			gauss.gauss(gaussData, width);
			System.arraycopy(gaussData, 0, pixels, t * width, width);
		}

		for (int x = 0; x < width; x++) {
			double voiceNumber = x < 100 ? 5 : x > 400 ? 20 : 5 + (x - 100) * 15 / 300;
			double s = Math.pow(2, OCTAVE_NUMBER - 1 + voiceNumber / VOICES_PER_OCTAVE) / FOURIER_PERIOD;

			int dataSize = height;
			for (int t = 0; t < height; t++) {
				data[t] = pixels[x + t * width];
				if (data[t] < 2) {
					dataSize = t;
					break;
				}
			}
			gauss.gauss(data, dataSize);
			for (int t = 0; t < dataSize; t++) {
				output[x + t * width] = (float)phase(data, dataSize, s, t);
			}
		}
		final FloatProcessor result = new FloatProcessor(width, height, output);
		result.setMinAndMax(-Math.PI, Math.PI);
		result.setLut(createLUT());
		return result;
	}

	public static void main(final String... args) {
		if (true) {
			final ImagePlus imp = IJ.openImage("C:\\kymograph.pgm");
			imp.show();
			final ImagePlus out = new ImagePlus("phase map", phaseMap((ByteProcessor) imp.getProcessor()));
			IJ.saveAs(out, "png", "C:\\new-phase-map.png");
			out.show();
			new ij.ImageJ();
			return;
		}

		double[] x = PlotUtils.range(0, DATA.length - 1);
		Plot plot = new Plot("Signal", "t", "intensity", x, DATA);
		plot.show();

		double voiceNumber = 10;
		double s = Math.pow(2, OCTAVE_NUMBER - 1 + voiceNumber / VOICES_PER_OCTAVE) / FOURIER_PERIOD;
		double[] phase = new double[DATA.length];
		for (int i = 0; i < phase.length; i++) {
			phase[i] = phase(DATA, DATA.length, s, i);
		}
		Plot plot2 = new Plot("Phase map", "tau", "phase", x, phase);
		plot2.show();
	}
}
