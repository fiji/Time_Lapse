package soroldoni;

import fiji.tool.AbstractTrackingTool;
import fiji.tool.ToolToggleListener;
import fiji.tool.ToolWithOptions;

import fiji.util.gui.GenericDialogPlus;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;

import ij.gui.ImageCanvas;
import ij.gui.PolygonRoi;
import ij.gui.Line;
import ij.gui.Roi;

import ij.io.RoiDecoder;
import ij.io.RoiEncoder;

import ij.measure.ResultsTable;

import ij.plugin.CanvasResizer;
import ij.plugin.Straightener;

import ij.plugin.frame.RoiManager;

import ij.process.FloatPolygon;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.awt.List;
import java.awt.Polygon;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.lang.reflect.Field;

import java.util.Hashtable;

import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * This is an interactive LOI Interpolator
 */
public class LOI_Interpolator_Tool extends AbstractTrackingTool
	implements KeyListener, ToolToggleListener, ToolWithOptions, MouseWheelListener {
	protected int x, y;
	protected boolean moving, constructing;
	protected PolygonRoiPublic roi;

	{
		// for debugging, all custom tools can be removed to make space for this one if necessary
		clearToolsIfNecessary = true;
	}

	@Override
	public String getToolIcon() {
		return "C888D93"
			+ "D84C555Dc4"
			+ "C888D35D75C555Db5C000De5"
			+ "C888L4666C555Db6C000De6"
			+ "C555Da7C000Dd7"
			+ "C555L8898C000Dd8"
			+ "C555L6979C000Dc9"
			+ "C555L3a5aC000Dba"
			+ "L9bab"
			+ "L6c8c"
			+ "L3d5d";
	}

	@Override
	public Roi optimizeRoi(Roi roi, ImageProcessor ip) {
		if (roi instanceof PolygonRoi)
			return new PolygonRoiPublic((PolygonRoi)roi);
		return null;
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		super.mouseClicked(e);
		if (constructing && e.getClickCount() > 1) {
			constructing = false;
			x = getOffscreenX(e);
			y = getOffscreenY(e);
			for (int i = 0; i < e.getClickCount(); i++, activeHandle--) {
				if (activeHandle < 1 || roi.distanceToHandle(activeHandle, x, y) > 15)
					break;
				roi.deleteHandle(activeHandle);
			}
			ImagePlus image = getImagePlus(e);
			image.setRoi(roi);
			image.updateAndDraw();

			Roi[] rois = getRois(image);
			int currentSlice = image.getCurrentSlice();
			interpolateFrom(rois, currentSlice);
		}
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mousePressed(MouseEvent e) {
		x = getOffscreenX(e);
		y = getOffscreenY(e);
		ImagePlus image = getImagePlus(e);
		if (updateROI(image)) {
			if (!constructing &&
					(e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0)
				activeHandle = roi.insertHandle(x, y);
			if (constructing)
				activeHandle = roi.addHandle(x, y);
			if (activeHandle < 0 && roi.lineContains(x, y)) {
				moving = true;
				getImageCanvas(e).setCursor(DummyCanvas.getMoveCursor());
			}
			else if (!constructing && activeHandle >= 0 && (e.getModifiersEx() & MouseEvent.ALT_DOWN_MASK) != 0) {
				roi.deleteHandle(activeHandle);
				roi.specifiedByUser = true;
				roi.setImage(image);
				image.setRoi(roi);
				e.consume();
				return;
			}
		}
		else
			activeHandle = -1;
		if (!moving && activeHandle < 0) {
			roi = new PolygonRoiPublic(new int[] { x, x }, new int[] { y, y }, 2, Roi.POLYLINE);
			roi.specifiedByUser = true;
			roi.setImage(image);
			image.setRoi(roi);
			constructing = true;
			activeHandle = 1;
		}
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mouseReleased(MouseEvent e) {
		super.mouseReleased(e);
		if (roi == null)
			return; // defensive
		if (moving) {
			moving = false;
			getImageCanvas(e).setCursor(DummyCanvas.getDefaultCursor());
		}
		else
			roi.updatePolygon();
		roi.specifiedByUser = true;
		ImagePlus image = getImagePlus(e);
		image.setRoi(roi);
		Roi[] rois = getRois(image);
		int currentSlice = image.getCurrentSlice();
		rois[currentSlice - 1] = roi;
		interpolateFrom(rois, currentSlice);
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
	public void mouseMoved(MouseEvent e) {
		super.mouseMoved(e);
		ImagePlus image = getImagePlus(e);
		if (updateROI(image)) {
			if (constructing) {
				roi.moveHandle(activeHandle, getOffscreenX(e), getOffscreenY(e));
				image.setRoi(roi);
				image.updateAndDraw();
			}
			else {
				activeHandle = getHandle(roi, e.getX(), e.getY());
				if (activeHandle < 0 && roi.lineContains(getOffscreenX(e), getOffscreenY(e)))
					getImageCanvas(e).setCursor(DummyCanvas.getMoveCursor());
			}
		}
		else
			activeHandle = -1;
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		ImagePlus image = getImagePlus(e);
		if (updateROI(image)) {
			if (activeHandle >= 0) {
				roi.setImage(image);
				roi.moveHandle(activeHandle, getOffscreenX(e), getOffscreenY(e));
				image.setRoi(roi);
			}
			else if (moving) {
				roi.specifiedByUser = true;
				int deltaX = getOffscreenX(e) - x;
				int deltaY = getOffscreenY(e) - y;
				if (deltaX != 0 || deltaY != 0) {
					roi.move(deltaX, deltaY);
					x += deltaX;
					y += deltaY;
					image.updateAndDraw();
				}
			}
		}
		else
			super.mouseDragged(e);
		e.consume(); // prevent ImageJ from handling this event
	}

	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		if ((e.getModifiersEx() & (e.CTRL_DOWN_MASK | e.ALT_DOWN_MASK)) == e.CTRL_DOWN_MASK) {
			int newWidth = Line.getWidth() - e.getWheelRotation();
			Line.setWidth(newWidth);
			if (roi != null) {
				roi.updateWideLine(newWidth);
				getImagePlus(e).setRoi(roi);
			}
			e.consume();
		}
	}

	@Override
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();
		if (keyCode == KeyEvent.VK_K)
			doRegistration(getImagePlus(e), (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0);
		else if (keyCode == KeyEvent.VK_E && (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) == 0)
			showStraightenedStack(getImagePlus(e));
		else if (keyCode == KeyEvent.VK_P && (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0)
			showPeaks(getImagePlus(e));
		else if (keyCode == KeyEvent.VK_L && (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0)
			loadROIs(getImagePlus(e));
		else
			return;
		e.consume();
	}

	@Override
	public void keyTyped(KeyEvent e) {}
	@Override
	public void keyReleased(KeyEvent e) {}

	@Override
	public void sliceChanged(ImagePlus image) {
		super.sliceChanged(image);
	}

	@Override
	public void showOptionDialog() {
		final GenericDialogPlus gd = new GenericDialogPlus(getToolName() + " Options");
		gd.addButton("Show kymograph [k]", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gd.dispose();
				ImagePlus image = doRegistration(WindowManager.getCurrentImage(), false);
				if (image == null)
					IJ.error("Need ellipse ROIs!");
			}
		});
		gd.addButton("Show kymograph & spreadsheet [K]", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gd.dispose();
				ImagePlus image = doRegistration(WindowManager.getCurrentImage(), true);
				if (image == null)
					IJ.error("Need ellipse ROIs!");
			}
		});
		gd.addButton("Export straightened stack [e]", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gd.dispose();
				ImagePlus image = WindowManager.getCurrentImage();
				if (image == null)
					IJ.error("Need an image!");
				else
					showStraightenedStack(image);
			}
		});
		gd.addButton("Find peaks [P]", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gd.dispose();
				ImagePlus image = WindowManager.getCurrentImage();
				if (image == null)
					IJ.error("Need an image!");
				else
					showPeaks(image);
			}
		});
		gd.addButton("Adjust line width [Ctrl+mouse wheel]", new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				gd.dispose();
				IJ.runPlugIn("ij.plugin.frame.LineWidthAdjuster", "");
			}
		});
		addIOButtons(gd);
		gd.showDialog();
	}

	@Override
	public void toolToggled(boolean enabled) {
	}

	protected boolean updateROI(ImagePlus image) {
		Roi roi = image.getRoi();
		if (roi == null || !(roi instanceof PolygonRoi)) {
			if (constructing && this.roi != null)
				return true;
			this.roi = null;
			return false;
		}
		if (this.roi == roi)
			return true;
		if (roi instanceof PolygonRoiPublic)
			this.roi = (PolygonRoiPublic)roi;
		else {
			this.roi = new PolygonRoiPublic((PolygonRoi)roi);
			image.setRoi(roi);
			roi.setImage(image);
		}
		return true;
	}

	protected static boolean specifiedByUser(Roi roi) {
		return roi != null && (roi instanceof PolygonRoiPublic) && ((PolygonRoiPublic)roi).specifiedByUser;
	}

	protected void interpolateFrom(Roi[] rois, int currentSlice) {
		boolean interpolated = false;
		for (int i = currentSlice - 1; i > 0; i--)
			if (specifiedByUser(rois[i - 1])) {
				interpolateROIs(rois, i, currentSlice);
				interpolated = true;
				break;
			}
		if (!interpolated)
			interpolateROIs(rois, 0, currentSlice);
		else
			interpolated = false;
		for (int i = currentSlice + 1; i <= rois.length; i++)
			if (specifiedByUser(rois[i - 1])) {
				interpolateROIs(rois, currentSlice, i);
				interpolated = true;
				break;
			}
		if (!interpolated)
			interpolateROIs(rois, currentSlice, rois.length + 1);
	}

	protected static void interpolateROIs(Roi[] rois) {
		int previousSlice = 0;
		for (int slice = 1; slice <= rois.length; slice++) {
			if (!specifiedByUser(rois[slice - 1]))
				continue;
			interpolateROIs(rois, previousSlice, slice);
			previousSlice = slice;
		}
		if (previousSlice > 0) // otherwise there weren't any ROIs
			interpolateROIs(rois, previousSlice, rois.length + 1);
	}

	protected static void interpolateROIs(Roi[] rois, int from, int to) {
		if (from >= to)
			return;
		Polygon poly1 = getPolygon(rois, from > 0 ? from - 1 : to - 1);
		Polygon poly2 = getPolygon(rois, to <= rois.length ? to - 1 : from - 1);
		normalizePointCounts(poly1, poly2);
		for (int j = from; j < to - 1; j++) {
			int[] x = new int[poly1.npoints];
			int[] y = new int[poly1.npoints];
			for (int k = 0; k < poly1.npoints; k++) {
				x[k] = poly1.xpoints[k] + (j + 1 - from) * (poly2.xpoints[k] - poly1.xpoints[k]) / (to - from);
				y[k] = poly1.ypoints[k] + (j + 1 - from) * (poly2.ypoints[k] - poly1.ypoints[k]) / (to - from);
			}
			rois[j] = new PolygonRoiPublic(x, y, poly1.npoints, Roi.POLYLINE);
			((PolygonRoiPublic)rois[j]).updatePolygon();
		}
	}

	protected static void normalizePointCounts(Polygon poly1, Polygon poly2) {
		if (poly1.npoints != poly2.npoints) {
			int n = poly1.npoints + poly2.npoints;
			resamplePolygon(poly1, n);
			resamplePolygon(poly2, n);
		}
	}

	protected static void resamplePolygon(Polygon polygon, int npoints) {
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

	protected static Polygon getPolygon(Roi[] rois, int sliceIndex) {
		if (rois[sliceIndex] == null || !(rois[sliceIndex] instanceof PolygonRoi))
			return null;
		PolygonRoi roi = (PolygonRoi)rois[sliceIndex];
		if (roi.isSplineFit()) {
			roi = (PolygonRoi)roi.clone();
			roi.removeSplineFit();
		}
		return roi.getPolygon();
	}

	protected ImagePlus doRegistration(ImagePlus image, boolean showSpreadsheet) {
		Roi[] rois = getRois(image);
		if (rois == null)
			return null;
		PolygonRoi[] polygons = new PolygonRoi[rois.length];
		System.arraycopy(rois, 0, polygons, 0, rois.length);
		return showKymograph(image, polygons, Line.getWidth(), showSpreadsheet, false);
	}

	protected static ImagePlus showKymograph(ImagePlus image, PolygonRoi[] rois, int lineWidth, boolean showSpreadsheet, boolean useFullLineWidth) {
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
			return null;
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

		float[] pixels = new float[width * height];
		for (int i = 0; i < height; i++)
			System.arraycopy(values[i], 0, pixels, i * width, values[i].length);
		FloatProcessor fp = new FloatProcessor(width, height, pixels, null);
		ImagePlus result = new ImagePlus("Kymograph of " + image.getTitle(), fp);
		result.show();
		ImageCanvas canvas = result.getCanvas();
		KymographMouseListener listener = new KymographMouseListener(canvas, image, rois);
		canvas.addMouseMotionListener(listener);
		result.getWindow().addWindowListener(listener);
		return result;
	}

	@Override
	protected void exportToROIManager(ImagePlus image) {
		Roi[] rois = map.get(image);
		if (rois == null)
			return;
		int currentSlice = image.getCurrentSlice();
		RoiManager manager = RoiManager.getInstance();
		if (manager == null)
			manager = new RoiManager();
		for (int i = 0; i < rois.length; i++)
			if (specifiedByUser(rois[i])) {
				image.setSliceWithoutUpdate(i + 1);
				manager.add(image, rois[i], i + 1);
			}
		image.setSlice(currentSlice);
	}

	@Override
	protected void importFromROIManager() {
		ImagePlus image = WindowManager.getCurrentImage();
		if (image == null)
			return;

		Roi[] rois = getRois(image);
		RoiManager manager = RoiManager.getInstance();
		if (manager == null)
			return;
		List labels = manager.getList();
		@SuppressWarnings("unchecked")
		Hashtable<String, Roi> table = (Hashtable<String, Roi>)manager.getROIs();
		for (int i = 0; i < labels.getItemCount(); i++) {
			String label = labels.getItem(i);
			int index = manager.getSliceNumber(label) - 1;
			if (index >= 0 && index < rois.length) {
				Roi roi = table.get(label);
				if (roi instanceof PolygonRoiPublic)
					; // ignore
				else if (roi instanceof PolygonRoi)
					roi = new PolygonRoiPublic((PolygonRoi)roi);
				else
					continue;
				((PolygonRoiPublic)roi).specifiedByUser = true;
				rois[index] = roi;
			}
		}

		interpolateROIs(rois);

		setRoi(image, rois[image.getCurrentSlice() - 1]);
	}

	@Override
	protected void saveROIs(Roi[] rois, String path) {
		try {
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(path));
			DataOutputStream dataOut = new DataOutputStream(new BufferedOutputStream(out));
			RoiEncoder roiEncoder = new RoiEncoder(dataOut);
			for (int i = 0; i < rois.length; i++) {
				if (!specifiedByUser(rois[i]))
					continue;
				out.putNextEntry(new ZipEntry(getROILabel(i + 1, rois.length, rois[i]) + ".roi"));
				roiEncoder.write(rois[i]);
				dataOut.flush();
			}
			dataOut.close();
		}
		catch (IOException e) {
			IJ.handleException(e);
		}
	}

	@Override
	protected void loadROIs(ImagePlus image, String path) {
		try {
			Roi[] rois = new Roi[image.getStackSize()];
			byte[] buf = new byte[16384];
			ZipInputStream in = new ZipInputStream(new FileInputStream(path));
			for (;;) {
				ZipEntry entry = in.getNextEntry();
				if (entry == null)
					break;
				String name = entry.getName();
				if (!entry.getName().endsWith(".roi"))
					continue;
				int slice, minus = name.indexOf('-');
				try {
					slice = Integer.parseInt(minus < 0 ? name : name.substring(0, minus));
				} catch (NumberFormatException e) {
					IJ.log("Skipping ROI with invalid name: " + name);
					continue;
				}
				if (slice < 1 || slice > rois.length) {
					IJ.log("Skipping ROI for invalid slice: " + slice);
					continue;
				}
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				for (;;) {
					int count = in.read(buf);
					if (count < 0)
						break;
					buffer.write(buf, 0, count);
				}
				RoiDecoder roiDecoder = new RoiDecoder(buffer.toByteArray(), entry.getName());
				Roi roi = roiDecoder.getRoi();
				if (roi != null) {
					if (roi instanceof PolygonRoiPublic)
						; // ignore
					else if (roi instanceof PolygonRoi)
						roi = new PolygonRoiPublic((PolygonRoi)roi);
					else
						continue;
					((PolygonRoiPublic)roi).specifiedByUser = true;
					rois[slice - 1] = roi;
				}
			}
			in.close();
			map.put(image, rois);

			interpolateROIs(rois);

			setRoi(image, rois[image.getCurrentSlice() - 1]);
		}
		catch (IOException e) {
			IJ.handleException(e);
		}
	}

	protected void showStraightenedStack(ImagePlus image) {
		ImagePlus result = getStraightenedStack(image);
		if (result != null)
			result.show();
	}

	protected ImagePlus getStraightenedStack(ImagePlus image) {
		if (image == null)
			return null;
		return getStraightenedStack(image, Line.getWidth(), getRois(image));
	}

	protected static ImagePlus getStraightenedStack(ImagePlus image, int lineWidth, Roi[] rois) {
		if (rois == null)
			return null;

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
		return new ImagePlus("Straightened-" + image.getTitle(), result);
	}

	public void showPeaks(ImagePlus image) {
		showPeaks(image, getRois(image));
	}

	public void showPeaks(ImagePlus image, Roi[] rois) {
		showPeaks(image, toPolygonRois(rois));
	}

	public void showPeaks(ImagePlus image, PolygonRoi[] rois) {
		if (rois == null)
			return;
		new ProfileStack(image, rois).show();
		new Peak_Counter_Tool().run("");
	}

	protected PolygonRoi[] toPolygonRois(Roi[] rois) {
		if (rois == null)
			return null;
		PolygonRoi[] polygons = new PolygonRoi[rois.length];
		for (int i = 0; i < rois.length; i++)
			if (rois[i] instanceof PolygonRoi)
				polygons[i] = (PolygonRoi)rois[i];
		return polygons;
	}
}