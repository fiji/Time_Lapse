package sc.fiji.timelapse;

/**
 * Use the difference to a Gaussian blurred version of the image as
 * measure of crispiness, and make a weighted sum of the z-slices.
 */

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;

import ij.macro.Interpreter;

import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.PlugInFilter;

import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

public class Gaussian_Stack_Focuser implements PlugInFilter {
	protected ImagePlus image;

	public int setup(String arg, ImagePlus image) {
		this.image = image;
		return DOES_8G | DOES_16 | DOES_32;
	}

	public void run(ImageProcessor ip) {
		double radius = IJ.getNumber("Radius_of_Gaussian_blur", 3);
		focus(image, radius, true).show();
	}

	public static ImagePlus focus(ImagePlus image, double radius, boolean showProgress) {
		int nSlices = image.getNSlices();
		int nChannels = image.getNChannels();
		int nFrames = image.getNFrames();

		ImageStack stack = image.getStack();
		ImageStack output = new ImageStack(image.getWidth(), image.getHeight());
		for (int frame = 1; frame <= nFrames; frame++)
			for (int channel = 1; channel <= nChannels; channel++) {
				FloatProcessor[] slices = new FloatProcessor[nSlices];
				for (int slice = 1; slice <= nSlices; slice++) {
					int index = image.getStackIndex(channel, slice, frame);
					ImageProcessor ip = stack.getProcessor(index);
					if (ip instanceof FloatProcessor)
						slices[slice - 1] = (FloatProcessor)ip;
					else
						slices[slice - 1] = (FloatProcessor)ip.convertToFloat();
				}
				output.addSlice("", focus(slices, radius));
				if (showProgress)
					IJ.showProgress((frame - 1) * nChannels + channel, nFrames * nChannels);
			}
		ImagePlus result = new ImagePlus("Focused " + image.getTitle(), output);
		result.setDimensions(nChannels, 1, nFrames);
		return result;
	}

	public static FloatProcessor focus(FloatProcessor[] slices, double radius) {
		boolean wasBatchMode = Interpreter.batchMode;
		Interpreter.batchMode = true;

		// calculate weights
		GaussianBlur blur = new GaussianBlur();
		int pixelCount = slices[0].getWidth() * slices[0].getHeight();
		FloatProcessor[] weights = new FloatProcessor[slices.length];
		for (int i = 0; i < slices.length; i++) {
			weights[i] = (FloatProcessor)slices[i].duplicate();
			blur.blur(weights[i], radius);
			float[] pixels1 = (float[])slices[i].getPixels();
			float[] pixels2 = (float[])weights[i].getPixels();
			for (int j = 0; j < pixelCount; j++)
				pixels2[j] = (float)Math.abs(pixels2[j] - pixels1[j]);
		}

		FloatProcessor result = (FloatProcessor)slices[0].duplicate();
		for (int j = 0; j < pixelCount; j++) {
			float cumul = 0, totalWeight = 0;
			for (int i = 0; i < slices.length; i++) {
				float value = slices[i].getf(j);
				float weight = weights[i].getf(j);
				cumul += value * weight;
				totalWeight += weight;
			}
			if (totalWeight != 0)
				result.setf(j, cumul / totalWeight);
		}
		Interpreter.batchMode = wasBatchMode;
		return result;
	}
}
