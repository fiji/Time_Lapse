package sc.fiji.timelapse;

import fiji.Debug;

public class PhaseMapTestDrive {
	public static void main(final String... args) {
		final String testImage = PhaseMapTestDrive.class.getResource("/kymograph_140816_yfp_s0017.tif").getPath();
		Debug.runFilter(testImage, "Phase Map", " ", false);
	}
}
