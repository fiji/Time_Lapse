package sc.fiji.timelapse;

import static sc.fiji.timelapse.Phase_Map.divide;
import static sc.fiji.timelapse.Phase_Map.range;
import fiji.util.FloatArray;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Plot;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Combine_Profile_Stacks implements PlugIn {

	@Override
	public void run(String arg) {
		final List<ImagePlus> images = new ArrayList<ImagePlus>();
		final Map<ImagePlus, float[][]> map = new HashMap<ImagePlus, float[][]>();
		final int[] ids = WindowManager.getIDList();
		if (ids.length < 2) {
			IJ.error("Need at least 2 profile stacks!");
			return;
		}

		for (final int id : ids) {
			final ImagePlus imp = WindowManager.getImage(id);
			final float[][] profiles = parseProfiles(imp.getProperty("Info"));
			if (profiles == null) continue;
			images.add(imp);
			map.put(imp, profiles);
		}

		if (images.size() < 2) {
			IJ.error("Need at least 2 profile stacks!");
			return;
		}

		final GenericDialog gd = new GenericDialog("Combine Profile Stacks");
		for (final ImagePlus imp : images) {
			gd.addCheckbox(imp.getTitle(), true);
		}
		gd.showDialog();
		if (gd.wasCanceled()) return;

		final List<float[][]> profiles = new ArrayList<float[][]>();
		float pixelSpacing = 1;
		String pixelSpacingUnit = null;
		for (final ImagePlus imp : images) {
			if (gd.getNextBoolean()) {
				if (pixelSpacingUnit == null) {
					final Calibration calibration = imp.getCalibration();
					if (calibration != null) {
						pixelSpacing = (float) calibration.pixelWidth;
						pixelSpacingUnit = calibration.getUnit();
					}
				}
				profiles.add(map.get(imp));
			}
		}
		if (pixelSpacingUnit == null) {
			pixelSpacingUnit = "pixels";
		}

		combineProfileStacks("Combined profile stack", profiles, pixelSpacing, pixelSpacingUnit).show();
	}

	private ImagePlus combineProfileStacks(final String title, List<float[][]> profiles,
			final float pixelSpacing, final String pixelSpacingUnit) {
		int maxX, maxT;
		float minPhase, maxPhase;
		maxX = maxT = 0;
		maxPhase = -Float.MAX_VALUE;
		minPhase = Float.MAX_VALUE;
		for (final float[][] list : profiles) {
			if (maxT < list.length) maxT = list.length;
			for (final float[] profile : list) {
				if (maxX < profile.length) maxX = profile.length;
				for (float f : profile) {
					if (minPhase > f) minPhase = f;
					if (maxPhase < f) maxPhase = f;
				}
			}
		}

		ImageStack imageStack = null;

		final float[][] median = new float[maxT][];
		final float[] tmp = new float[maxT];
		final float[][] p = new float[maxT][];
		for (int t = 0; t < maxT; t++) {
			int count = 0;
			for (int i = 0; i < profiles.size(); i++) {
				final float[][] stack = profiles.get(i);
				if (t < stack.length) p[count++] = stack[t];
			}
			for (int i = count; i < p.length; i++) p[i] = null;
			Arrays.sort(p, new Comparator<float[]>() {

				@Override
				public int compare(float[] a, float[] b) {
					if (a == null) return b == null ? 0 : 1;
					if (b == null) return -1;
					return b.length - a.length;
				}

			});

			median[t] = new float[p[0].length];
			for (int i = 0; i < p[0].length; i++) {
				int j = 0;
				for (; j < count && i < p[j].length; j++) {
					tmp[j] = p[j][i];
				}
				for (int k = j; k < tmp.length; k++) tmp[k] = Float.MAX_VALUE;
				Arrays.sort(tmp);
				if ((j & 1) == 1) {
					median[t][i] = tmp[j / 2];
				} else {
					median[t][i] = (tmp[j / 2] + tmp[j / 2 - 1]) / 2;
				}
			}

			final float pi2 = (float) (2 * Math.PI);
			final Plot plot = new Plot("Profile t=" + t,
					"x" + ("".equals(pixelSpacingUnit) ? "" : (" (" + pixelSpacingUnit + ")")),
					"phase (× 2π)");
			plot.setLimits(0, maxX, minPhase / pi2, maxPhase / pi2);
			plot.setFrameSize(850, 400);

			for (int i = 0; i < count; i++) {
				plot.addPoints(range(0, p[i].length, pixelSpacing), divide(p[i], pi2), Plot.LINE);
			}
			plot.setColor(Color.BLUE);
			plot.setLineWidth(2);
			plot.addPoints(range(0, median[t].length, pixelSpacing), divide(median[t], pi2), Plot.LINE);

			final ImageProcessor ip = plot.getProcessor();
			if (imageStack == null) imageStack = new ImageStack(ip.getWidth(), ip.getHeight());
			imageStack.addSlice("t=" + t, ip);
		}

		return new ImagePlus(title, imageStack);
	}

	/**
	 * Parses profile data in JSON format.
	 * <p>
	 * This is a non-validating JSON parser expecting 2-dimensional float array.
	 * </p>
	 * @param property the "Info" property of a profile stack
	 * @return the parsed array of profiles
	 */
	private float[][] parseProfiles(Object property) {
		if (property == null || !(property instanceof String)) return null;
		final String info = (String) property;
		final char[] array = info.toCharArray();
		int offset = 0;
		while (offset < array.length && array[offset] != '[') offset++;
		if (offset >= array.length) return null;
		offset++;
		final List<float[]> list = new ArrayList<float[]>();
		while (offset < array.length) {
			char ch = array[offset++];
			if (Character.isWhitespace(ch) || ch == ',') continue;
			if (ch == ']') break;
			if (ch != '[') {
				System.err.println("Unexpected character @" + (offset - 1) + ": " + ch);
				return null;
			}

			final FloatArray profile = new FloatArray();
			for (;;) {
				while (offset < array.length && (array[offset] == ',' ||
						Character.isWhitespace(array[offset]))) {
					offset++;
				}
				if (offset >= array.length || array[offset] == ']') {
					offset++;
					break;
				}

				int end = offset;
				while (end < array.length && (array[end] == '.' || array[end] == 'E' || array[end] == 'e' ||
						array[end] == '-' || Character.isDigit(array[end]))) {
					end++;
				}
				try {
					profile.add(Float.parseFloat(info.substring(offset, end)));
				} catch (Exception e) {
					return null;
				}
				offset = end;
			}
			list.add(profile.buildArray());
		}
		return list.toArray(new float[list.size()][]);
	}

	public static void main(String... args) {
		new ImageJ();
		IJ.openImage("/home/dscho/Desktop/Fiji.app/profile_stacks/Profile Stack  of Kymograph of 120807_yfp_s0001.tif").show();
		IJ.openImage("/home/dscho/Desktop/Fiji.app/profile_stacks/Profile Stack  of Kymograph of 120807_yfp_s0002.tif").show();
		IJ.openImage("/home/dscho/Desktop/Fiji.app/profile_stacks/Profile Stack  of Kymograph of 120807_yfp_s0006.tif").show();
		IJ.openImage("/home/dscho/Desktop/Fiji.app/profile_stacks/Profile Stack  of Kymograph of 120807_yfp_s0007.tif").show();
		IJ.openImage("/home/dscho/Desktop/Fiji.app/profile_stacks/Profile Stack  of Kymograph of 120807_yfp_s0008.tif").show();
		new Combine_Profile_Stacks().run("");
		/*
		float[][][] profiles = {
				{
					{ 0, 5, 3, 2, 1 },
					{ 0, 4, 2, 1, -1, 0 },
					{ 1, 3, 1, 0, -2 }
				},
				{
					{ 1, 6, 3, 2, 0 },
					{ 1, 5, 5, 3, 1 }
				}
		};
		final ImagePlus result = new Combine_Profile_Stacks().combineProfileStacks("blub", Arrays.asList(profiles));
		result.show();
		/*
		final String info = "\n[\n [ 0.0, 1.0e-2, -5 ], [1, .2e-20\n]\n]";
		final float[][] profiles = new Combine_Profile_Stacks().parseProfiles(info);
		System.err.println(Arrays.toString(profiles));
		*/
	}
}
