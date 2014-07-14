package sc.fiji.timelapse;

import ij.gui.ImageCanvas;

import java.awt.Cursor;

public class DummyCanvas extends ImageCanvas {
	private DummyCanvas() { super(null); }

	public static Cursor getDefaultCursor() {
		return defaultCursor;
	}

	public static Cursor getMoveCursor() {
		return moveCursor;
	}

	public static Cursor getHandCursor() {
		return handCursor;
	}
}
