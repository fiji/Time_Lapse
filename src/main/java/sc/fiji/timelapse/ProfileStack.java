package sc.fiji.timelapse;

import ij.ImagePlus;
import ij.ImageStack;

import ij.gui.Line;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PolygonRoi;

import ij.measure.Calibration;

import ij.process.ImageProcessor;

import java.awt.Rectangle;

public class ProfileStack extends ImagePlus {
	protected ImagePlus image;

	protected float[][] profiles;
	protected PolygonRoi[] rois;

	protected float xMin, yMin, xMax, yMax;

	public ProfileStack(ImagePlus image, PolygonRoi[] rois) {
		this.image = image;
		this.rois = rois;
		makeProfiles();
	}

	protected void makeProfiles() {
		xMin = 0;
		yMin = Float.MAX_VALUE;
		xMax = yMax = -Float.MAX_VALUE;

		ImageStack stack = image.getStack();
		profiles = new float[stack.getSize()][];

		for (int slice = 1; slice <= profiles.length; slice++)
			if (rois[slice - 1] != null) {
				profiles[slice - 1] = PlotUtils.getProfile(stack.getProcessor(slice), rois[slice - 1], true, Line.getWidth());
				if (xMax < profiles[slice - 1].length)
					xMax = profiles[slice - 1].length;
				for (float value : profiles[slice - 1]) {
					if (yMax < value)
						yMax = value;
					if (yMin > value)
						yMin = value;
				}
			}

		// add some margins
		float xMargin = xMax > xMin ? 0.05f * (xMax - xMin) : 0;
		float yMargin = yMax > yMin ? 0.05f * (yMax - yMin) : 0;
		xMin -= xMargin;
		xMax += xMargin;
		yMin -= yMargin;
		yMax += yMargin;

		ImageStack plots = null;
		for (int i = 0; i < profiles.length; i++) {
			double[] values = rois[i] != null ? PlotUtils.toDouble(profiles[i]) : new double[] { yMin };
			if (values.length < 1)
				values = new double[] { yMin };
			double[] xValues = PlotUtils.range(0, values.length - 1);
			Plot plot = new Plot("Slice " + (i + 1), "distance", "intensity", xValues, values);
			plot.setLimits(xMin, xMax, yMin, yMax);
			ImageProcessor ip = plot.getProcessor();
			if (plots == null)
				plots = new ImageStack(ip.getWidth(), ip.getHeight());
			plots.addSlice("Slice " + (i + 1), ip);
		}

		setTitle("Profiles of " + image.getTitle());
		setStack(plots);
		Calibration calibration = new Calibration();
		calibration.pixelWidth = (xMax - xMin) / (getWidth() - Plot.LEFT_MARGIN - Plot.RIGHT_MARGIN);
		calibration.pixelHeight = (yMin - yMax) / (getHeight() - Plot.TOP_MARGIN - Plot.BOTTOM_MARGIN);
		calibration.xOrigin = Plot.LEFT_MARGIN - xMin / calibration.pixelWidth;
		calibration.yOrigin = getHeight() - Plot.BOTTOM_MARGIN - yMin / calibration.pixelHeight;
		setCalibration(calibration);
	}

	public void show() {
		if (isVisible())
			return;
		super.show();
		setSlice(profiles.length);
	}

	public int x2distance(int x) {
		return (int)(xMin + (xMax - xMin) * (x - Plot.LEFT_MARGIN) / (getWidth() - Plot.LEFT_MARGIN - Plot.RIGHT_MARGIN));
	}

	public int distance2x(int distance) {
		return Plot.LEFT_MARGIN + (int)((getWidth() - Plot.LEFT_MARGIN - Plot.RIGHT_MARGIN) * (distance - xMin) / (xMax - xMin));
	}

	public float get(int slice, int distance) {
		if (slice < 1 || slice > profiles.length)
			return 0;
		float[] profile = profiles[slice - 1];
		if (distance < 0 || distance >= profile.length)
			return 0;
		return profile[distance];
	}

	public int intensity2y(float intensity) {
		return getHeight() - Plot.BOTTOM_MARGIN - (int)((getHeight() - Plot.TOP_MARGIN - Plot.BOTTOM_MARGIN) * (intensity - yMin) / (yMax - yMin));
	}

	public void mark(int slice, int distance) {
		image.setPosition(slice);
		if (distance < 0) {
			unmark();
			return;
		}
		PolygonRoi roi = rois[slice - 1];
		if (roi == null) {
			image.killRoi();
			return;
		}
		image.setRoi(roi);
		roi = (PolygonRoi)roi.clone();
		roi.fitSplineForStraightening();
		if (distance >= roi.getNCoordinates()) {
			unmark();
			return;
		}
		Rectangle bounds = roi.getBounds();
		int x = roi.getXCoordinates()[distance] + bounds.x;
		int y = roi.getYCoordinates()[distance] + bounds.y;
		Overlay overlay = new Overlay(new OvalRoi(x - 10, y - 10, 21, 21));
		image.setOverlay(overlay);
	}

	public void unmark() {
		image.setOverlay(null);
	}
}
