package soroldoni;

import fiji.tool.AbstractTrackingTool;
import fiji.tool.ToolWithOptions;

import fiji.util.IntArray;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;

import ij.gui.ImageCanvas;
import ij.gui.OvalRoi;
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.gui.PlotWindow;
import ij.gui.PointRoi;
import ij.gui.Roi;

import ij.process.ImageProcessor;

import java.awt.Color;
import java.awt.Rectangle;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A peak counter for profile stacks
 */
public class Peak_Counter_Tool extends AbstractTrackingTool implements KeyListener, ToolWithOptions {
	protected ImagePlus peakCountPlot;
	protected double[] peakCountPlotLimits;

	protected ProfileStack image;
	protected int slice;

	protected IntArray peaks;
	protected IntArray[] allPeaks;
	protected Map<ProfileStack, IntArray[]> allPeaksMap = new WeakHashMap<ProfileStack, IntArray[]>();

	protected int maxSpeed = 10;

	protected int handle;

	{
		// for debugging, all custom tools can be removed to make space for this one if necessary
		clearToolsIfNecessary = true;
	}

	@Override
	public String getToolIcon() {
		return "C111L7494"
			+ "D65Da5"
			+ "D56Da6"
			+ "D07D57Db7"
			+ "C00fL1838C111D48Db8Df8"
			+ "C00fL0919L3949C111Db9De9"
			+ "C00fD0aC111D2aC00fD4aC111DbaDea"
			+ "C00fL0b1bL3b4bLbbdb"
			+ "L1c3cLacbcC111DccC00fLdcec"
			+ "DadC111DcdC00fDed"
			+ "LaebeLdeee"
			+ "Lbfdf";
	}

	protected boolean getImage(MouseEvent e) {
		return getImage(getImagePlus(e));
	}

	protected boolean getImage(ImagePlus image) {
		if (image instanceof ProfileStack) {
			if (this.image != image) {
				this.image = (ProfileStack)image;
				if (allPeaksMap.get(image) == null)
					allPeaksMap.put(this.image, new IntArray[image.getStackSize()]);
				allPeaks = allPeaksMap.get(image);
				for (int i = 1; i <= image.getStackSize(); i++)
					initializePeaks(i);
				updatePeakCountPlot();
			}
			updateSlice();
			return true;
		}
		else {
			this.image = null;
			allPeaks = null;
			slice = -1;
			peaks = null;
			return false;
		}
	}

	protected void updateSlice() {
		if (image == null)
			return;
		slice = image.getSlice();
		peaks = getPeaks(slice);
	}

	protected IntArray getPeaks(int slice) {
		if (allPeaks[slice - 1] == null)
			allPeaks[slice - 1] = new IntArray();
		return allPeaks[slice - 1];
	}

	protected synchronized void updatePeakCountPlot() {
		String title = image.getTitle();
		if (title.startsWith("Profiles of "))
			title = title.substring(12);
		title = "Peak counts of " + title;
		double[] counts = new double[allPeaks.length];
		double[] regularized = new double[counts.length];
		for (int i = 0; i < counts.length; i++) {
			counts[i] = allPeaks[i] != null ? allPeaks[i].size() : 0;
			regularized[i] = i == 0 ? counts[0] : Math.max(regularized[i - 1], counts[i]);
		}
		double[] range = PlotUtils.range(1, counts.length);
		Plot plot = new Plot(title, "frame", "peak count", range, counts);
		peakCountPlotLimits = PlotUtils.setLimits(plot, range, counts);
		plot.draw();
		plot.setColor(Color.BLUE);
		plot.addPoints(range, regularized, Plot.LINE);
		if (peakCountPlot == null || !peakCountPlot.isVisible()) {
			peakCountPlot = plot.show().getImagePlus();
			final ImageCanvas canvas = peakCountPlot.getCanvas();
			canvas.addMouseMotionListener(new MouseAdapter() {
				@Override
				public void mouseMoved(MouseEvent e) {
					if (image == null)
						return;
					int x = canvas.offScreenX(e.getX());
					int frame = 1 + (int)((peakCountPlotLimits[1] - peakCountPlotLimits[0])
						* (x - Plot.LEFT_MARGIN) / (peakCountPlot.getWidth() - Plot.LEFT_MARGIN - Plot.RIGHT_MARGIN));
					if (frame >= 1 && frame <= image.getStackSize())
						image.setSlice(frame);
				}
			});
		}
		else
			((PlotWindow)peakCountPlot.getWindow()).drawPlot(plot);
	}

	/**
	 * @return index of peak, or -1-insert where <i>insert</i> is the insert position
	 */
	protected int peakPosition(int x) {
		if (peaks.size() == 0)
			return -1;
		if (x <= peaks.get(0))
			return x == peaks.get(0) ? 0 : -1;
		int len = peaks.size();
		if (x >= peaks.get(len - 1))
			return x == peaks.get(len - 1) ? len - 1 : -1 - len;

		// binary search
		int left = 0, right = len - 1;
		while (left + 1 < right) {
			int middle = (left + right) / 2;
			int value = peaks.get(middle);
			if (x == value)
				return middle;
			if (x < value)
				right = middle;
			else
				left = middle;
		}
		return -1 - right;
	}

	public void initializePeaks(int slice) {
		initializePeaks(slice, maxSpeed, (image.yMax - image.yMin) / (image.xMax - image.xMin));
	}

	public void initializePeaks(int slice, int window, double minimalSlope) {
		IntArray peaks = getPeaks(slice);
		peaks.clear();
		Extrema extrema = new Extrema(PlotUtils.toDouble(image.profiles[slice - 1]), window, minimalSlope, false);
		for (double extremum : extrema.getX())
			peaks.add((int)extremum);
		if (this.slice == slice)
			image.setRoi(toROI());
	}

	protected int snapAndAddPeak(int x) {
		return addPeak(snapPeak(slice, x));
	}

	protected int snapPeak(int slice, int x) {
		int center = x;
		for (int i = 0; i < maxSpeed && center - i >= 0; i++)
			if (image.get(slice, x) > image.get(slice, center - i))
				x = center - i;
		for (int i = 0; i < maxSpeed && center + i < image.profiles[slice - 1].length; i++)
			if (image.get(slice, x) > image.get(slice, center + i))
				x = center + i;
		return x;
	}

	protected int addPeak(int x) {
		if (x < 0 || x >= image.profiles[slice - 1].length)
			return -1;
		int index = peakPosition(x);
		if (index >= 0)
			return index;
		index = peaks.insert(-1 - index, x);
		return index;
	}

	protected int movePeak(int index, int x) {
		while (index > 0 && peaks.get(index - 1) > x) {
			peaks.set(index, peaks.get(index - 1));
			index--;
		}
		while (index + 1 < peaks.size() && peaks.get(index + 1) < x) {
			peaks.set(index, peaks.get(index + 1));
			index++;
		}
		peaks.set(index, x);
		return index;
	}

	protected int closeToPeak(int x, int y) {
		return closeToPeak(x, y, 10 / image.getCanvas().getMagnification());
	}

	protected int closeToPeak(int x, int y, double tolerance) {
		double best = Float.MAX_VALUE;
		int bestIndex = -1;
		for (int i = 0; i < peaks.size(); i++) {
			int distance = peaks.get(i);
			int x1 = image.distance2x(distance);
			double current = Math.abs(x - x1);
			if (best > current) {
				best = current;
				bestIndex = i;
			}
		}
		return best < tolerance ? bestIndex : -1;
	}

	protected PointRoi toROI() {
		float[] profile = image.profiles[slice - 1];
		int[] x = peaks.buildArray();
		int[] xValues = new int[x.length];
		int[] yValues = new int[x.length];
		for (int i = 0; i < x.length; i++) {
			xValues[i] = image.distance2x(x[i]);
			yValues[i] = image.intensity2y(image.get(slice, x[i]));
		}
		return new PointRoi(xValues, yValues, x.length);
	}

	@Override
	public Roi optimizeRoi(Roi roi, ImageProcessor ip) {
		return null;
	}

	protected Map<ProfileStack, Integer> firstAdjustedSlice = new WeakHashMap<ProfileStack, Integer>();

	protected void snapPeaks(int adjustedSlice) {
		if (firstAdjustedSlice.get(image) == null || firstAdjustedSlice.get(image).intValue() > adjustedSlice)
			firstAdjustedSlice.put(image, Integer.valueOf(adjustedSlice));
		snapPeaks();
	}

	protected void snapPeaks() {
		Integer first = firstAdjustedSlice.get(image);
		if (first == null || first.intValue() < 2)
			return;
		for (int slice = first.intValue() - 1; slice > 0; slice--)
			snapPeaks(slice, allPeaks[(slice + 1) - 1]);
	}

	protected void snapPeaks(int slice, IntArray initialPeaks) {
		IntArray peaks = getPeaks(slice);
		peaks.clear();
		for (int peak : initialPeaks) {
			peak = snapPeak(slice, peak);
			if (peak >= 0 && peak < image.profiles[slice - 1].length)
				peaks.add(peak);
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		super.mouseClicked(e);
		if (getImage(e)) {
			if (handle < 0)
				handle = snapAndAddPeak(image.x2distance(getOffscreenX(e)));
			else {
				peaks.remove(handle);
				handle = -1;
			}
			image.setRoi(toROI());
			snapPeaks(slice);
			updatePeakCountPlot();
		}
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mousePressed(MouseEvent e) {
		super.mousePressed(e);
		// TODO: switch to hand if close to peak
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		getImageCanvas(e).setCursor(DummyCanvas.getDefaultCursor());
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		super.mouseEntered(e);
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mouseExited(MouseEvent e) {
		super.mouseExited(e);
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (!getImage(getImagePlus(e)))
			return;
		int keyCode = e.getKeyCode();
		if (keyCode == KeyEvent.VK_P && (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0)
			initializePeaks();
		else
			return;
		e.consume();
	}

	@Override
	public void keyTyped(KeyEvent e) {}
	@Override
	public void keyReleased(KeyEvent e) {}

	protected OvalRoi mark(int distance) {
		return mark(distance, null);
	}

	protected OvalRoi mark(int distance, Color color) {
		float intensity = image.get(slice, distance);
		int x = image.distance2x(distance);
		int y = image.intensity2y(intensity);
		OvalRoi roi = new OvalRoi(x - 10, y - 10, 20, 20);
		if (color != null)
			roi.setStrokeColor(color);
		return roi;
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if (getImage(e)) {
			int offscreenX = getOffscreenX(e);
			int offscreenY = getOffscreenY(e);
			int distance = image.x2distance(offscreenX);
			float intensity = image.get(slice, distance);
			IJ.showStatus("distance: " + distance + ", intensity: " + intensity);

			Overlay overlay = new Overlay(mark(distance));
			handle = closeToPeak(offscreenX, offscreenY);
			if (handle >= 0) {
				overlay.add(mark(peaks.get(handle), Color.RED));
				getImageCanvas(e).setCursor(DummyCanvas.getHandCursor());
			}
			else
				getImageCanvas(e).setCursor(DummyCanvas.getDefaultCursor());
			image.setOverlay(overlay);
			image.mark(slice, distance);
			e.consume(); // prevent ImageJ from handling this event
		}
		else
			super.mouseMoved(e);
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if (!getImage(e)) {
			super.mouseDragged(e);
			return;
		}
		image.setOverlay(null);
		if (handle >= 0) {
			movePeak(handle, image.x2distance(getOffscreenX(e)));
			snapPeaks(slice);
			image.setRoi(toROI());
			getImageCanvas(e).setCursor(DummyCanvas.getMoveCursor());
		}
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void sliceChanged(ImagePlus image) {
		if (this.image == image)
			image.setOverlay(null);
		super.sliceChanged(image);
		if (getImage(image))
			setRoi(image, toROI());
	}

	protected void initializePeaks() {
		GenericDialogPlus gd = new GenericDialogPlus("Peak Finder");
		gd.addNumericField("Window", maxSpeed, 0);
		gd.addNumericField("Minimal_Slope", (image.yMax - image.yMin) / (image.xMax - image.xMin), 2);
		gd.addNumericField("From_slice", 1, 0);
		gd.addNumericField("To_slice", allPeaks.length, 0);
		gd.showDialog();
		if (gd.wasCanceled())
			return;
		int window = (int)gd.getNextNumber();
		double slope = gd.getNextNumber();
		int from = Math.max(1, (int)gd.getNextNumber());
		int to = Math.min(allPeaks.length, (int)gd.getNextNumber());

		for (int slice = from; slice <= to; slice++)
			initializePeaks(slice, window, slope);
		image.setRoi(toROI());
		updatePeakCountPlot();
	}

	@Override
	public void showOptionDialog() {
		if (!getImage(WindowManager.getCurrentImage()))
			return;
		final GenericDialogPlus gd = new GenericDialogPlus(getToolName() + " Options");
		gd.addButton("Determine peaks [P]", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gd.dispose();
				initializePeaks();
			}
		});
		gd.showDialog();
	}
}