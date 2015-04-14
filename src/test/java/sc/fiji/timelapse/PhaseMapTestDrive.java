package sc.fiji.timelapse;

import fiji.Debug;

public class PhaseMapTestDrive {
	public static void main(final String... args) {
		final String testImage = PhaseMapTestDrive.class.getResource("/kymograph_140816_yfp_s0017.tif").getPath();
		Debug.runFilter(testImage, "Phase Map", " octave_number=3 plot_wave_counts show_profile_stack show_phase_profile_map ", false);
	}
}
