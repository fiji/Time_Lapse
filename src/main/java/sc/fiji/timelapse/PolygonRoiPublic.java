package sc.fiji.timelapse;

/**
 * This class makes a few things public that aren't public in PolygonRoi
 */

import ij.IJ;

import ij.gui.PolygonRoi;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PolygonRoiPublic extends PolygonRoi {
	protected boolean specifiedByUser = false;
	protected Color normalStrokeColor, specifiedStrokeColor;

	protected static Field nPointsField;
	protected static Field xField, yField, xpField, ypField, xpfField, ypfField;
	protected static Method resetBoundsMethod;

	static {
		try {
			xpField = PolygonRoi.class.getDeclaredField("xp");
			xpField.setAccessible(true);
		} catch (Exception e) {
			IJ.handleException(e);
		}

		try {
			ypField = PolygonRoi.class.getDeclaredField("yp");
			ypField.setAccessible(true);
		} catch (Exception e) {
			IJ.handleException(e);
		}

		try {
			nPointsField = PolygonRoi.class.getDeclaredField("nPoints");
			nPointsField.setAccessible(true);
		} catch (Exception e) {
			IJ.handleException(e);
		}

		try {
			xpfField = PolygonRoi.class.getDeclaredField("xpf");
			xpfField.setAccessible(true);
		} catch (Exception e) {
			// ignore; old IJ1 version
		}

		try {
			ypfField = PolygonRoi.class.getDeclaredField("ypf");
			ypfField.setAccessible(true);
		} catch (Exception e) {
			// ignore; old IJ1 version
		}

		try {
			resetBoundsMethod = PolygonRoi.class.getDeclaredMethod("resetBoundingRect", new Class[0]);
			resetBoundsMethod.setAccessible(true);
		} catch (Exception e) {
			IJ.handleException(e);
		}
	}

	public PolygonRoiPublic(int[] x, int[] y, int n, int type) {
		super(x, y, n, type);
		fitSpline();
		normalStrokeColor = strokeColor;
		specifiedStrokeColor = new Color(0, 0, 255, 77);
	}

	public PolygonRoiPublic(PolygonRoi roi) {
		this(getXCoordinates(roi), getYCoordinates(roi), getNCoordinates(roi), roi.getType());
	}

	protected static int[] getXCoordinates(PolygonRoi roi) {
		try {
			Rectangle bounds = roi.getBounds();
			int[] xp = (int[])xpField.get(roi);
			int[] result = new int[xp.length];
			for (int i = 0; i < xp.length; i++)
				result[i] = xp[i] + bounds.x;
			return result;
		} catch (Exception e) {
			IJ.handleException(e);
			return null;
		}
	}

	protected static int[] getYCoordinates(PolygonRoi roi) {
		try {
			Rectangle bounds = roi.getBounds();
			int[] yp = (int[])ypField.get(roi);
			int[] result = new int[yp.length];
			for (int i = 0; i < yp.length; i++)
				result[i] = yp[i] + bounds.y;
			return result;
		} catch (Exception e) {
			IJ.handleException(e);
			return null;
		}
	}

	protected static int getNCoordinates(PolygonRoi roi) {
		if (nPointsField == null)
			return -1;
		try {
			return ((Integer)nPointsField.get(roi)).intValue();
		} catch (Exception e) {
			IJ.handleException(e);
			return -1;
		}
	}

	@Override
	public void updatePolygon() {
		resetBoundingRect();
		fitSpline();
		super.updatePolygon();
	}

	@Override
	public void fitSpline() {
		if (getNCoordinates() > 2) {
			copyXpToXpf();
			super.fitSpline();
			copyXpfToXp();
		}
	}

	@Override
	public void draw(Graphics g) {
		if (specifiedByUser)
			strokeColor = specifiedStrokeColor;
		else
			strokeColor = normalStrokeColor;
		super.draw(g);
	}

	public void resetBoundingRect() {
		if (resetBoundsMethod == null)
			return;

		// work around spline fit changing the bounding box
		try {
			resetBoundsMethod.invoke(this, new Object[0]);
		} catch (Exception e) {
			IJ.handleException(e);
		}
	}

	protected void copyXpToXpf() {
		if (xpfField != null) try {
			xpfField.set(this, toFloat(xp));
		} catch (IllegalAccessException e) {
			IJ.handleException(e);
		}
		if (ypfField != null) try {
			ypfField.set(this, toFloat(yp));
		} catch (IllegalAccessException e) {
			IJ.handleException(e);
		}
	}

	// we need to reimplement this for bugwards compatibility
	public static float[] toFloat(int[] array) {
		float[] result = new float[array.length];
		for (int i = 0; i < result.length; i++)
			result[i] = array[i];
		return result;
	}

	protected void copyXpfToXp() {
		if (xpfField != null) try {
			xp = toIntR((float[])xpfField.get(this));
		} catch (IllegalAccessException e) {
			IJ.handleException(e);
		}
		if (ypfField != null) try {
			yp = toIntR((float[])ypfField.get(this));
		} catch (IllegalAccessException e) {
			IJ.handleException(e);
		}
	}

	// we need to reimplement this for bugwards compatibility
	public static int[] toIntR(float[] array) {
		int[] result = new int[array.length];
		for (int i = 0; i < result.length; i++)
			result[i] = (int)Math.floor(array[i] + 0.5);
		return result;
	}

	public boolean lineContains(int x, int y) {
		return lineContains(x, y, 5);
	}

	public boolean lineContains(int x, int y, int tolerance) {
		Rectangle bounds = getBounds();
		int n = getNCoordinates();
		int[] xp = getXCoordinates();
		int[] yp = getYCoordinates();
		for (int i = 1; i < n; i++) {
			int x1 = bounds.x + xp[i - 1];
			int y1 = bounds.y + yp[i - 1];
			int x2 = bounds.x + xp[i];
			int y2 = bounds.y + yp[i];

			double deltaX = x2 - x1;
			double deltaY = y2 - y1;
			double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

			double distance = Math.abs((x - x1) * deltaY - (y - y1) * deltaX);
			if (tolerance * length <= distance)
				continue;

			double factor = (x - x1) * deltaX + (y - y1) * deltaY;
			if (factor > 0 && factor < length * length)
				return true;
		}
		return false;
	}

	public void moveHandle(int handle, int x, int y) {
		if (handle < 0 || handle >= nPoints)
			return;
		Rectangle bounds = getBounds();
		xp[handle] = x - bounds.x;
		yp[handle] = y - bounds.y;
		copyXpToXpf();
		updatePolygon();
	}

	public int addHandle(int x, int y) {
		if (nPoints == 2 && xp[0] == xp[1] && yp[0] == yp[1]) {
			moveHandle(1, x, y);
			return 1;
		}
		return insertHandle(nPoints, x, y);
	}

	public int insertHandle(int beforeHandle, int x, int y) {
		if (xp.length < nPoints + 1) {
			int[] newX = new int[nPoints + 16];
			System.arraycopy(xp, 0, newX, 0, nPoints);
			xp = newX;
			xp2 = new int[nPoints + 16];
		}
		if (yp.length < nPoints + 1) {
			int[] newY = new int[nPoints + 16];
			System.arraycopy(yp, 0, newY, 0, nPoints);
			yp = newY;
			yp2 = new int[nPoints + 16];
		}
		if (beforeHandle < nPoints) {
			System.arraycopy(xp, beforeHandle, xp, beforeHandle + 1, nPoints - beforeHandle);
			System.arraycopy(yp, beforeHandle, yp, beforeHandle + 1, nPoints - beforeHandle);
		}
		nPoints++;
		if (maxPoints < nPoints)
			maxPoints = xp.length;
		moveHandle(beforeHandle, x, y);
		return beforeHandle;
	}

	public int insertHandle(int x, int y) {
		if (nPoints < 2)
			return addHandle(x, y);

		Rectangle bounds = getBounds();
		int x2 = x - bounds.x;
		int y2 = y - bounds.y;

		int bestHandle = -1, bestDist2 = Integer.MAX_VALUE;
		for (int i = 0; i < nPoints; i++) {
			int dist2 = (x2 - xp[i]) * (x2 - xp[i]) + (y2 - yp[i]) * (y2 - yp[i]);
			if (bestDist2 > dist2) {
				bestHandle = i;
				bestDist2 = dist2;
			}
		}

		int beforeHandle;
		if (bestHandle == 0)
			beforeHandle = (x2 - xp[0]) * (xp[1] - xp[0]) + (y2 - yp[0]) * (yp[1] - yp[0]) > 0 ? 1 : 0;
		else if (bestHandle == nPoints - 1)
			beforeHandle = (x2 - xp[nPoints - 1]) * (xp[nPoints - 2] - xp[nPoints - 1]) + (y2 - yp[nPoints - 1]) * (yp[nPoints - 2] - yp[nPoints - 1]) > 0 ? nPoints - 1 : nPoints;
		else
			beforeHandle = (x2 - xp[bestHandle - 1]) * (x2 - xp[bestHandle - 1]) + (y2 - yp[bestHandle - 1]) * (y2 - yp[bestHandle - 1])
				< (x2 - xp[bestHandle + 1]) * (x2 - xp[bestHandle + 1]) + (y2 - yp[bestHandle + 1]) * (y2 - yp[bestHandle + 1])
				? bestHandle : bestHandle + 1;
		return insertHandle(beforeHandle, x, y);
	}

	public void deleteHandle(int handle) {
		if (handle < 0 || handle >= nPoints)
			return;
		if (handle + 1 < nPoints) {
			System.arraycopy(xp, handle + 1, xp, handle, nPoints - handle - 1);
			System.arraycopy(yp, handle + 1, yp, handle, nPoints - handle - 1);
		}
		nPoints--;
		copyXpToXpf();
		updatePolygon();
	}

	public void move(int deltaX, int deltaY) {
		Rectangle bounds = getBounds();
		setLocation(bounds.x + deltaX, bounds.y + deltaY);
	}

	public double distanceToHandle(int handle, int x, int y) {
		Rectangle bounds = getBounds();
		x -= xp[handle] + bounds.x;
		y -= yp[handle] + bounds.y;
		return Math.sqrt(x * x + y * y);
	}

	@Override
	public String toString() {
		Rectangle bounds = getBounds();
		StringBuilder builder = new StringBuilder();
		builder.append("PolygonRoi(")
			.append(specifiedByUser ? "set by user" : "interpolated")
			.append("; ")
			.append(nPoints);
		for (int i = 0; i < nPoints; i++)
			builder.append(" ").append(xp[i] + bounds.x).append(",").append(yp[i] + bounds.y);
		builder.append(")");
		return builder.toString();
	}

	public static void main(String[] args) {
		PolygonRoiPublic roi = new PolygonRoiPublic(new int[] { 800, 800}, new int[] { 500, 500 }, 2, PolygonRoi.POLYLINE);
		//roi.addHandle(800, 500);
		roi.addHandle(100, 200);
		roi.addHandle(100, 500);
		roi.addHandle(800, 250);
		IJ.getImage().setRoi(roi);
		//IJ.getImage().setRoi((PolygonRoi)roi.clone());
		PolygonRoi poly = new PolygonRoi(getXCoordinates(roi), getYCoordinates(roi), getNCoordinates(roi), PolygonRoi.POLYLINE);
		poly.fitSpline();
		//IJ.getImage().setRoi(poly);
	}

	public static void assertEquals(String name1, Object object1, String name2, Object object2) {
		assertEquals(name1, object1, name2, object2, 4);
	}

	public static void assertEquals(String name1, Object object1, String name2, Object object2, int maxDepth) {
		if (maxDepth == 0)
			return;
		if (object1 == null || object2 == null) {
			if (object1 != object2)
				System.err.println(name1 + "(" + object1 + ") != " + name2 + "(" + object2 + ")");
			return;
		}
		Class<?> class1 = object1.getClass();
		Class<?> class2 = object2.getClass();
		if (class1 == PolygonRoiPublic.class)
			class1 = PolygonRoi.class;
		if (class1 != class2)
			System.err.println("Different class: " + name1 + "(" + class1.getName() + ") != " + name2 + "(" + class2.getName() + ")");
		else if (class1 == Boolean.class)
			assertEquals(name1, ((Boolean)object1).booleanValue(), name2, ((Boolean)object2).booleanValue());
		else if (class1 == Byte.class)
			assertEquals(name1, ((Byte)object1).byteValue(), name2, ((Byte)object2).byteValue());
		else if (class1 == Short.class)
			assertEquals(name1, ((Short)object1).shortValue(), name2, ((Short)object2).shortValue());
		else if (class1 == Integer.class)
			assertEquals(name1, ((Integer)object1).intValue(), name2, ((Integer)object2).intValue());
		else if (class1 == Long.class)
			assertEquals(name1, ((Long)object1).longValue(), name2, ((Long)object2).longValue());
		else if (class1 == Float.class)
			assertEquals(name1, ((Float)object1).floatValue(), name2, ((Float)object2).floatValue());
		else if (class1 == Double.class)
			assertEquals(name1, ((Double)object1).doubleValue(), name2, ((Double)object2).doubleValue());
		else if (class1 == String.class)
			assertEquals(name1, (String)object1, name2, (String)object2);
		else
			for (java.lang.reflect.Field field : class1.getDeclaredFields()) try {
				if (field.getDeclaringClass() != class1)
					continue;
				field.setAccessible(true);
				assertEquals(name1 + "." + field.getName(), field.get(object1), name2 + "." + field.getName(), field.get(object2), maxDepth - 1);
			} catch (Throwable t) {
				t.printStackTrace();
			}
	}

	public static void assertEquals(String name1, boolean value1, String name2, boolean value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, byte value1, String name2, byte value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, short value1, String name2, short value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, int value1, String name2, int value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, long value1, String name2, long value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, float value1, String name2, float value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, double value1, String name2, double value2) {
		if (value1 != value2)
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}

	public static void assertEquals(String name1, String value1, String name2, String value2) {
		if ((value1 == null && value2 != null) || (value1 != null && !value1.equals(value2)))
			System.err.println(name1 + "(" + value1 + ") != " + name2 + "(" + value2 + ")");
	}
}
