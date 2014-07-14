package soroldoni;

import ij.ImagePlus;

import ij.gui.ImageCanvas;
import ij.gui.Overlay;
import ij.gui.PointRoi;
import ij.gui.PolygonRoi;

import ij.process.FloatPolygon;

import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

public class KymographMouseListener implements MouseMotionListener, WindowListener {
	protected ImageCanvas canvas;
	protected ImagePlus original;
	protected PolygonRoi[] rois;

	public KymographMouseListener(ImageCanvas canvas, ImagePlus original, PolygonRoi[] rois) {
		this.canvas = canvas;
		this.original = original;
		this.rois = rois;
	}

	public void mouseMoved(MouseEvent e) {
		int x = canvas.offScreenX(e.getX());
		int y = canvas.offScreenY(e.getY());
		original.setSlice(y + 1);
		PolygonRoi roi = rois[y];
		if (roi == null) {
			original.setOverlay(null);
			original.killRoi();
			return;
		}
		FloatPolygon polygon = roi.getFloatPolygon();
		if (polygon.npoints <= x)
			return;
		int x2 = (int)polygon.xpoints[x];
		int y2 = (int)polygon.ypoints[x];
		original.setOverlay(new Overlay(new PointRoi(x2, y2)));
		original.setRoi(roi);
	}

	public void mouseDragged(MouseEvent e) {}

	public void windowClosed(WindowEvent e) {
		canvas.removeMouseMotionListener(this);
		original.setOverlay(null);
	}

	public void windowActivated(WindowEvent e) {}
	public void windowClosing(WindowEvent e) {}
	public void windowDeactivated(WindowEvent e) {}
	public void windowDeiconified(WindowEvent e) {}
	public void windowIconified(WindowEvent e) {}
	public void windowOpened(WindowEvent e) {}
}