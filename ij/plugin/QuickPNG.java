package ij.plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.Prefs;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.FileInfo;
import ij.io.SaveDialog;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.io.File;

/** Exports the current image beside its source without changing its file info. */
public class QuickPNG implements PlugIn {
	private static double lastScale = 2.0;

	public void run(String arg) {
		ImagePlus imp = WindowManager.getCurrentImage();
		if (imp==null) {
			IJ.noImage();
			return;
		}
		double scale = 1.0;
		int interpolation = ImageProcessor.NONE;
		boolean scaled = "scaled".equals(arg);
		if (scaled) {
			GenericDialog gd = new GenericDialog("Save Scaled PNG");
			gd.addNumericField("Scale factor (1.0 = original size):", lastScale, 2);
			gd.addChoice("Interpolation:", ImageProcessor.getInterpolationMethods(), "None");
			gd.showDialog();
			if (gd.wasCanceled())
				return;
			scale = gd.getNextNumber();
			interpolation = gd.getNextChoiceIndex();
			try {
				scaledSize(imp.getWidth(), imp.getHeight(), scale);
			} catch (IllegalArgumentException e) {
				IJ.error("Save PNG", e.getMessage());
				return;
			}
			lastScale = scale;
		}
		File target = defaultTarget(imp);
		if (scaled && target!=null)
			target = new File(target.getParentFile(), scaledPngName(target.getName(), scale));
		if (target==null) {
			SaveDialog sd = new SaveDialog("Save PNG", scaled?scaledPngName(imp.getTitle(), scale):pngName(imp.getTitle()), ".png");
			if (sd.getFileName()==null)
				return;
			target = new File(sd.getDirectory(), sd.getFileName());
		}
		if (target.exists() && !IJ.showMessageWithCancel("Save PNG", "Replace existing file?\n"+target.getPath()))
			return;
		try {
			ImagePlus output = scaled?scaledImage(imp, scale, interpolation):imp;
			new PNG_Writer().writeImage(output, target.getPath(), Prefs.getTransparentIndex());
			IJ.showStatus("Saved PNG: "+target.getPath());
		} catch (Exception e) {
			IJ.error("Save PNG", "Unable to save PNG:\n"+target.getPath()+"\n"+e.getMessage());
		}
	}

	static String pngName(String name) {
		int dot = name.lastIndexOf('.');
		return (dot>0?name.substring(0, dot):name)+".png";
	}

	static String scaledPngName(String name, double scale) {
		String png = pngName(name);
		return png.substring(0, png.length()-4)+"_x"+Double.toString(scale)+".png";
	}

	static File defaultTarget(ImagePlus imp) {
		Object sources = imp.getProperty("QuickPNG.SourceFiles");
		if (sources instanceof String[]) {
			String[] files = (String[])sources;
			int slice = imp.getCurrentSlice()-1;
			if (slice>=0 && slice<files.length) {
				File source = new File(files[slice]);
				return new File(source.getParentFile(), pngName(source.getName()));
			}
		}
		if (imp.getStack() instanceof FileInfoVirtualStack) {
			FileInfoVirtualStack stack = (FileInfoVirtualStack)imp.getStack();
			String dir = stack.getDirectory();
			String name = stack.getFileName(imp.getCurrentSlice());
			if (dir!=null && name!=null)
				return new File(dir, pngName(name));
		}
		FileInfo fi = imp.getOriginalFileInfo();
		if (fi==null || fi.directory==null || fi.directory.length()==0
				|| fi.fileName==null || fi.fileName.length()==0)
			return null;
		return new File(fi.directory, pngName(fi.fileName));
	}

	static int[] scaledSize(int width, int height, double scale) {
		double w = width*scale, h = height*scale;
		if (Double.isNaN(scale) || Double.isInfinite(scale) || scale<=0
				|| w>Integer.MAX_VALUE || h>Integer.MAX_VALUE)
			throw new IllegalArgumentException("Enter a finite, positive scale factor within the supported image size.");
		int iw = (int)Math.max(1, Math.round(w));
		int ih = (int)Math.max(1, Math.round(h));
		if ((long)iw*ih>Integer.MAX_VALUE)
			throw new IllegalArgumentException("The scaled image is too large.");
		return new int[] {iw, ih};
	}

	/** Scale the displayed colors and visible overlays using the selected interpolation. */
	static ImagePlus scaledImage(ImagePlus imp, double scale, int interpolation) {
		int[] size = scaledSize(imp.getWidth(), imp.getHeight(), scale);
		ImagePlus rendered = imp.getOverlay()!=null && !imp.getHideOverlay() && !imp.tempOverlay()
				?imp.flatten():imp;
		ImageProcessor ip = rendered.isComposite()
				?new ColorProcessor(rendered.getBufferedImage()):rendered.getProcessor().duplicate();
		ip.resetRoi();
		ip.setInterpolationMethod(interpolation);
		return new ImagePlus(imp.getTitle(), ip.resize(size[0], size[1]));
	}
}
