package sc.fiji.timelapse;

import fiji.statistics.RoiStatistics;
import fiji.util.FloatArray;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.OvalRoi;
import ij.gui.Plot;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.measure.Measurements;
import ij.measure.ResultsTable;
import ij.plugin.filter.Analyzer;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.util.ArrayList;
import java.util.Hashtable;

public class Circle_Interpolator implements PlugInFilter {
	ImagePlus image;
	RoiManager roiManager;
	OvalRoi[] rois;
	public boolean debug = false;

	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return NO_CHANGES | DOES_ALL;
	}

	public void run(ImageProcessor ip) {
		roiManager = RoiManager.getInstance();
		if (roiManager == null || roiManager.getCount() < 2) {
			IJ.error("Need at least two oval selections in the ROI Manager");
			return;
		}

		getRois();

		int currentSlice = image.getCurrentSlice();
		Roi savedRoi = image.getRoi();

		if (rois[currentSlice - 1] != null)
			image.setRoi(rois[currentSlice - 1]);

		GenericDialog gd = new GenericDialog("Circle interpolator");
		gd.addSlider("slice", 1, image.getStackSize(), currentSlice);
		SliderListener listener = new SliderListener();
		listener.textField = (TextField)gd.getNumericFields().lastElement();
		Scrollbar slider = (Scrollbar)gd.getSliders().lastElement();
		slider.addAdjustmentListener(listener);
		gd.addCheckbox("plot_average_intensity", true);
		gd.addCheckbox("combine_plots", false);
		gd.addCheckbox("show_results", true);
		gd.addCheckbox("show_raw_values instead of means", false);
		gd.showDialog();

		image.setSlice(currentSlice);
		if (savedRoi == null)
			image.killRoi();
		else
			image.setRoi(savedRoi);

		if (gd.wasCanceled())
			return;

		boolean plotIt = gd.getNextBoolean();
		boolean combinePlots = gd.getNextBoolean();
		boolean showResults = gd.getNextBoolean();
		boolean showRawValues = gd.getNextBoolean();

		if (plotIt || showResults || showRawValues)
			plot(plotIt, combinePlots, showResults, showRawValues);
	}

	int getRois() {
		rois = new OvalRoi[image.getStackSize()];
		@SuppressWarnings({ "unchecked", "deprecation" })
		Hashtable<String, Roi> table = (Hashtable<String, Roi>) roiManager.getROIs();
		int min = rois.length, max = -1;
		for (Object key : table.keySet()) {
			String label = (String)key;
			int sliceNumber = roiManager.getSliceNumber(label);
			Roi roi = (Roi)table.get(label);
			if (sliceNumber < 1 || sliceNumber > rois.length)
				IJ.log("Ignoring ROI with invalid slice " + sliceNumber);
			else if (rois[sliceNumber - 1] != null)
				IJ.log("Ignoring duplicate ROI for slice " + sliceNumber);
			else if (roi.getType() != Roi.OVAL)
				IJ.log("Ignoring ROI which is not an oval ROI: " + label);
			else {
				rois[sliceNumber - 1] = (OvalRoi)roi.clone();
				min = Math.min(min, sliceNumber - 1);
				max = Math.max(max, sliceNumber - 1);
			}
		}
		if (max <= min)
			return 0;

		int interpolated = 0;
		for (int i = min; i < max; i++) {
			int next = i + 1;
			while (next < max && rois[next] == null)
				next++;
			Rectangle rect1 = rois[i].getBounds();
			Rectangle rect2 = rois[next].getBounds();
			for (int j = i + 1; j < next; j++) {
				int x = rect1.x + (j - i) * (rect2.x - rect1.x) / (next - i);
				int y = rect1.y + (j - i) * (rect2.y - rect1.y) / (next - i);
				int width = rect1.width + (j - i) * (rect2.width - rect1.width) / (next - i);
				int height = rect1.height + (j - i) * (rect2.height - rect1.height) / (next - i);
				rois[j] = new OvalRoi(x, y, width, height);
				if (debug)
					addToRoiManager(j + 1, rois[j]);
			}
			interpolated += next - i - 1;
		}
		return interpolated;
	}

	void addToRoiManager(int sliceNumber, Roi roi) {
		int x = roi.getBounds().x, y = roi.getBounds().y;
		int max = Math.max(sliceNumber, Math.max(x, y));
		int digits = (int)Math.ceil(Math.log10(max));
		digits = Math.max(4, digits);
		String format = "%0" + digits + "d";
		String label =
			String.format(format + "-" + format + "-" + format,
					sliceNumber, x, y);
		roiManager.getList().add(label);
		roiManager.getROIs().put(label, roi);
	}

	void plot(boolean showPlots, boolean combined, boolean showResults, boolean showRawValues) {
		ImageStack stack = image.getStack();
		Calibration cal = image.getCalibration();
		int options = Measurements.AREA | Measurements.MEAN;
		int min, max;
		for (min = 0; min < rois.length; min++)
			if (rois[min] != null)
				break;
		for (max = min; max < rois.length - 1; max++)
			if (rois[max + 1] == null)
				break;
		double[] areas = new double[max - min + 1];
		double[] means = new double[max - min + 1];
		double[] maxs = new double[max - min + 1];
		double[] x = new double[max - min + 1];
		double minValue = Double.MAX_VALUE, maxValue = -Double.MAX_VALUE;
		ArrayList<FloatArray> rawValues = showRawValues ? new ArrayList<FloatArray>() : null;
		for (int i = min; i <= max; i++) {
			if (rois[i] == null)
				continue;
			ImageProcessor ip = stack.getProcessor(i + 1);
			ip.setRoi(rois[i]);
			ImageStatistics stats = ImageStatistics
				.getStatistics(ip, options, cal);
			areas[i - min] = stats.area;
			means[i - min] = stats.mean;
			maxs[i - min] = stats.max;
			if (minValue > stats.area)
				minValue = stats.area;
			if (minValue > stats.mean)
				minValue = stats.mean;
			if (maxValue < stats.area)
				maxValue = stats.area;
			if (maxValue < stats.mean)
				maxValue = stats.mean;
			x[i - min] = i + 1;
			if (showRawValues) {
				final FloatArray array = new FloatArray();
				RoiStatistics roiStatistics = new RoiStatistics(rois[i]);
				roiStatistics.iterate(ip, new RoiStatistics.PixelHandler() {
					public void handle(int x, int y, float value) {
						array.add(value);
					}
				});
				rawValues.add(array);
			}
		}
		if (showPlots) {
			if (combined) {
				Plot plot = new Plot("Area / Mean / Max", "slice", "area / mean / max", x, areas);
				plot.setLimits(x[0], x[x.length - 1], minValue, maxValue);
				plot.setColor(Color.BLUE);
				plot.draw();
				plot.setColor(Color.GREEN);
				plot.addPoints(x, means, plot.LINE);
				plot.draw();
				plot.setColor(Color.RED);
				plot.addPoints(x, maxs, plot.LINE);
				plot.show();
			}
			else {
				new Plot("Area", "slice", "area", x, areas).show();
				new Plot("Mean", "slice", "mean", x, means).show();
				new Plot("Max", "slice", "max", x, maxs).show();
			}
		}
		if (showResults || showRawValues) {
			ResultsTable rt = Analyzer.getResultsTable();
			if (rt != null) {
				rt = new ResultsTable();
				Analyzer.setResultsTable(rt);
			}
			for (int i = min; i <= max; i++) {
				rt.incrementCounter();
				rt.addValue("slice", i + 1);
				rt.addValue("area", areas[i - min]);
				rt.addValue("mean", means[i - min]);
				rt.addValue("max", maxs[i - min]);
				if (showRawValues) {
					int counter = 1;
					for (float value : rawValues.get(i).buildArray())
						rt.addValue("value " + counter++, value);
				}
			}
			rt.show("Results");
		}
	}

	class SliderListener implements AdjustmentListener {
		TextField textField;

		public void adjustmentValueChanged(AdjustmentEvent event) {
			int sliceNumber = Integer.parseInt(textField.getText());
			image.setSlice(sliceNumber);
			OvalRoi roi = rois[sliceNumber - 1];
			if (roi == null)
				image.killRoi();
			else
				image.setRoi(roi);
		}
	}
}
