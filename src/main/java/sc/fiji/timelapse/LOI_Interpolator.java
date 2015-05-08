package sc.fiji.timelapse;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.gui.ImageCanvas;
import ij.gui.Line;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Roi;
import ij.measure.ResultsTable;
import ij.plugin.CanvasResizer;
import ij.plugin.Straightener;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.Polygon;
import java.awt.Scrollbar;
import java.awt.TextField;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.lang.reflect.Field;
import java.util.Hashtable;

public class LOI_Interpolator implements PlugInFilter {
	protected ImagePlus image;
	protected RoiManager roiManager;
	protected PolygonRoi[] rois;
	public boolean debug = false;

	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return NO_CHANGES | DOES_8G | DOES_16 | DOES_32;
	}

	public void run(ImageProcessor ip) {
		roiManager = RoiManager.getInstance();
		if (roiManager == null || roiManager.getCount() < 2) {
			IJ.error("Need at least two line selections in the ROI Manager");
			return;
		}

		getRois();

		int currentSlice = image.getCurrentSlice();
		Roi savedRoi = image.getRoi();

		if (rois[currentSlice - 1] != null) {
			rois[currentSlice - 1].fitSpline();
			image.setRoi(rois[currentSlice - 1]);
		}

		GenericDialog gd = new GenericDialog("Line of interest interpolator");
		gd.addSlider("slice", 1, image.getStackSize(), currentSlice);
		SliderListener listener = new SliderListener();
		listener.textField = (TextField)gd.getNumericFields().lastElement();
		Scrollbar slider = (Scrollbar)gd.getSliders().lastElement();
		slider.addAdjustmentListener(listener);
		gd.addCheckbox("ROIs_are_flipped", false);
		gd.addCheckbox("average_over_line_width", true);
		gd.addCheckbox("show_kymograph", true);
		gd.addCheckbox("show_spread_sheet", true);
		gd.addCheckbox("add_to_ROI_manager", false);
		gd.addCheckbox("export_straightened_stack", false);
		gd.showDialog();

		image.setSlice(currentSlice);
		if (savedRoi == null)
			image.killRoi();
		else
			image.setRoi(savedRoi);

		if (gd.wasCanceled())
			return;

		boolean flipROIs = gd.getNextBoolean();
		boolean useFullLineWidth = !gd.getNextBoolean();
		boolean showKymograph = gd.getNextBoolean();
		boolean showSpreadsheet = gd.getNextBoolean();
		boolean addToROIManager = gd.getNextBoolean();
		boolean exportStraightenedStack = gd.getNextBoolean();

		if (flipROIs)
			for (int i = 0; i < rois.length; i++)
				if (rois[i] != null)
					rois[i] = flipROI(rois[i]);

		showKymograph(showKymograph, showSpreadsheet, useFullLineWidth);
		if (addToROIManager) {
			roiManager.runCommand("Select All");
			roiManager.runCommand("Delete");
			for (int i = 0; i < rois.length; i++)
				if (rois[i] != null) {
					image.setSliceWithoutUpdate(i + 1);
					roiManager.addRoi(rois[i]);
				}
			image.setSlice(currentSlice);
		}
		if (exportStraightenedStack) {
			int lineWidth = Line.getWidth();
			Straightener straightener = new Straightener();
			int w = 1, h = lineWidth;
			ImageStack stack = image.getStack();
			ImageStack result = new ImageStack(w, h);
			CanvasResizer resizer = new CanvasResizer();
			try {
				Field zeroFill = resizer.getClass().getDeclaredField("zeroFill");
				zeroFill.setAccessible(true);
				zeroFill.set(resizer, Boolean.TRUE);
			} catch (Exception e) { e.printStackTrace(); }
			for (int i = 0; i < rois.length; i++)
				if (rois[i] == null)
					result.addSlice("", new FloatProcessor(w, h));
				else {
					ImagePlus dummy = new ImagePlus("dummy", stack.getProcessor(i + 1));
					dummy.setRoi(rois[i]);
					ImageProcessor ip2 = straightener.straightenLine(dummy, lineWidth);
					if (w < ip2.getWidth()) {
						w = ip2.getWidth();
						if (result.getSize() > 0)
							result = resizer.expandStack(result, w, h, 0, 0);
						else
							result = new ImageStack(w, h);
					}
					else if (w > ip2.getWidth())
						ip2 = resizer.expandImage(ip2, w, h, 0, 0);
					result.addSlice("", ip2);
				}
			new ImagePlus("Straightened-" + image.getTitle(), result).show();
		}
	}

	protected int getRois() {
		rois = new PolygonRoi[image.getStackSize()];
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
			else if (roi.getType() != Roi.POLYLINE)
				IJ.log("Ignoring ROI which is not a segmented line: " + label);
			else {
				rois[sliceNumber - 1] = (PolygonRoi)roi.clone();
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
			Polygon poly1 = getPolygon(i);
			Polygon poly2 = getPolygon(next);
			normalizePointCounts(poly1, poly2);
			for (int j = i + 1; j < next; j++) {
				int[] x = new int[poly1.npoints];
				int[] y = new int[poly1.npoints];
				for (int k = 0; k < poly1.npoints; k++) {
					x[k] = poly1.xpoints[k] + (j - i) * (poly2.xpoints[k] - poly1.xpoints[k]) / (next - i);
					y[k] = poly1.ypoints[k] + (j - i) * (poly2.ypoints[k] - poly1.ypoints[k]) / (next - i);
				}
				rois[j] = new PolygonRoi(x, y, poly1.npoints, Roi.POLYLINE);
				if (debug)
					addToRoiManager(j + 1, rois[j]);
			}
			interpolated += next - i - 1;
		}
		return interpolated;
	}

	protected PolygonRoi flipROI(PolygonRoi roi) {
		if (roi.isSplineFit()) {
			roi = (PolygonRoi)roi.clone();
			roi.removeSplineFit();
		}
		Polygon polygon = roi.getPolygon();
		for (int i = 0; i < polygon.npoints / 2; i++) {
			int dummy = polygon.xpoints[i];
			polygon.xpoints[i] = polygon.xpoints[polygon.npoints - 1 - i];
			polygon.xpoints[polygon.npoints - 1 - i] = dummy;
			dummy = polygon.ypoints[i];
			polygon.ypoints[i] = polygon.ypoints[polygon.npoints - 1 - i];
			polygon.ypoints[polygon.npoints - 1 - i] = dummy;
		}
		return new PolygonRoi(polygon, roi.getType());
	}

	protected Polygon getPolygon(int sliceIndex) {
		PolygonRoi roi = rois[sliceIndex];
		if (roi.isSplineFit()) {
			roi = (PolygonRoi)roi.clone();
			roi.removeSplineFit();
		}
		return roi.getPolygon();
	}

	protected void normalizePointCounts(Polygon poly1, Polygon poly2) {
		if (poly1.npoints != poly2.npoints) {
			int n = poly1.npoints + poly2.npoints;
			resamplePolygon(poly1, n);
			resamplePolygon(poly2, n);
		}
	}

	protected void resamplePolygon(Polygon polygon, int npoints) {
		int[] x = new int[npoints], y = new int[npoints];

		PolygonRoi roi = new PolygonRoi(polygon, Roi.POLYLINE);
		roi.fitSpline();
		FloatPolygon floatPolygon = roi.getFloatPolygon();
		float[] x1 = floatPolygon.xpoints, y1 = floatPolygon.ypoints;

		for (int i = 0; i < npoints; i++) {
			float index = i * (floatPolygon.npoints - 1) / (float)(npoints - 1);
			int j = (int)Math.floor(index);
			float f1 = index - j;
			if (f1 < 1e-5) {
				x[i] = (int)Math.round(x1[j]);
				y[i] = (int)Math.round(y1[j]);
			}
			else {
				float f2 = 1 - f1;
				x[i] = (int)Math.round(x1[j] * f2 + x1[j + 1] * f1);
				y[i] = (int)Math.round(y1[j] * f2 + y1[j + 1] * f1);
			}
		}
		polygon.npoints = npoints;
		polygon.xpoints = x;
		polygon.ypoints = y;
	}

	protected void addToRoiManager(int sliceNumber, Roi roi) {
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

	protected void showKymograph(boolean showImage, boolean showSpreadsheet, boolean useFullLineWidth) {
		if (!showImage && !showSpreadsheet)
			return;
		int lineWidth = Line.getWidth();
		int linesPerROI = useFullLineWidth ? lineWidth : 1;
		int width = 0;
		int height = rois.length * linesPerROI;
		float[][] values = new float[height][];
		Straightener straightener = new Straightener();
		ImageStack stack = image.getStack();
		for (int i = 0; i < rois.length; i++)
			if (rois[i] == null)
				for (int j = 0; j < linesPerROI; j++)
					values[i * linesPerROI + j] = new float[0];
			else {
				// rois[i].isSplineFit() does not say whether it was fit for straightening...
				rois[i].removeSplineFit();
				ImagePlus dummy = new ImagePlus("dummy", stack.getProcessor(i + 1));
				dummy.setRoi(rois[i]);
				ImageProcessor ip = straightener.straightenLine(dummy, lineWidth);
				int w = ip.getWidth();
				if (!useFullLineWidth && lineWidth > 1)
					// need to average explicitely
					for (int j = 0; j < w; j++) {
						float value = ip.getf(j, 0);
						for (int k = 1; k < lineWidth; k++)
							value += ip.getf(j, k);
						ip.setf(j, 0, value / lineWidth);
					}
				float[] pixels = (float[])ip.getPixels();
				for (int j = 0; j < linesPerROI; j++) {
					values[i * linesPerROI + j] = new float[w];
					System.arraycopy(pixels, j * w, values[i * linesPerROI + j], 0, w);
				}
				width = Math.max(ip.getWidth(), width);
			}
		if (width == 0) {
			IJ.error("No ROIs!");
			return;
		}
		if (showImage) {
			float[] pixels = new float[width * height];
			for (int i = 0; i < height; i++)
				System.arraycopy(values[i], 0, pixels, i * width, values[i].length);
			FloatProcessor fp = new FloatProcessor(width, height, pixels, null);
			ImagePlus image = new ImagePlus("Kymograph of " + this.image.getTitle(), fp);
			image.show();
			ImageCanvas canvas = image.getCanvas();
			KymographMouseListener listener = new KymographMouseListener(canvas, this.image, rois);
			canvas.addMouseMotionListener(listener);
			image.getWindow().addWindowListener(listener);
		}
		if (showSpreadsheet) {
			ResultsTable table = new ResultsTable();
			for (int i = 0; i < height; i++) {
				table.incrementCounter();
				for (int j = 0; j < values[i].length; j++)
					table.addValue(j, values[i][j]);
			}
			table.show("Kymograph values of " + image.getTitle());
		}
	}

	protected class SliderListener implements AdjustmentListener {
		TextField textField;

		public void adjustmentValueChanged(AdjustmentEvent event) {
			int sliceNumber = Integer.parseInt(textField.getText());
			image.setSlice(sliceNumber);
			PolygonRoi roi = rois[sliceNumber - 1];
			if (roi != null && !roi.isSplineFit())
				roi.fitSpline();
			if (roi == null)
				image.killRoi();
			else
				image.setRoi(roi);
		}
	}
}
