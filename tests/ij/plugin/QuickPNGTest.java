package ij.plugin;

import ij.ImagePlus;
import ij.io.FileInfo;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.Test;
import static org.junit.Assert.*;

public class QuickPNGTest {
	@Test
	public void folderStacksSaveBesideCurrentSourceForRegularAndVirtualStacks() throws Exception {
		java.nio.file.Path folder = java.nio.file.Files.createTempDirectory("quick-folder-");
		try {
			java.nio.file.Files.write(folder.resolve("000.raw"), new byte[] {0, 1, 2, 3});
			java.nio.file.Files.write(folder.resolve("001.raw"), new byte[] {4, 5, 6, 7});
			FileInfo fi = new FileInfo();
			fi.width = fi.height = 2;
			fi.fileType = FileInfo.GRAY8;
			fi.nImages = 1;
			for (boolean virtual : new boolean[] {false, true}) {
				ImagePlus imp = virtual?Raw.openAllVirtual(folder.toString(), fi):Raw.openAll(folder.toString(), fi);
				assertNotNull(imp);
				assertEquals(folder.resolve("000.png").toFile(), QuickPNG.defaultTarget(imp));
				imp.setSlice(2);
				assertEquals(folder.resolve("001.png").toFile(), QuickPNG.defaultTarget(imp));
				assertEquals(4, imp.getProcessor().get(0));
			}
		} finally {
			java.nio.file.Files.deleteIfExists(folder.resolve("000.raw"));
			java.nio.file.Files.deleteIfExists(folder.resolve("001.raw"));
			java.nio.file.Files.deleteIfExists(folder);
		}
	}

	@Test
	public void targetUsesOriginalFileName() {
		ImagePlus imp = new ImagePlus("renamed", new FloatProcessor(2, 3));
		assertNull(QuickPNG.defaultTarget(imp));
		FileInfo fi = new FileInfo();
		fi.directory = "/tmp";
		fi.fileName = "sample_float32_128x128.raw";
		imp.setFileInfo(fi);
		assertEquals(new File("/tmp/sample_float32_128x128.png"), QuickPNG.defaultTarget(imp));
		assertEquals("sample.png", QuickPNG.pngName("sample"));
		assertEquals("sample.part.png", QuickPNG.pngName("sample.part.tif"));
	}

	@Test
	public void scaledExportPreservesSourceAndDisplayColors() throws Exception {
		float[] pixels = {0, 1, 2, 3};
		ImagePlus imp = new ImagePlus("sample", new FloatProcessor(2, 2, pixels));
		imp.setDisplayRange(0, 3);
		imp.setRoi(0, 0, 1, 1);
		ImagePlus output = QuickPNG.scaledImage(imp, 2.0, ImageProcessor.NONE);
		assertEquals(4, output.getWidth());
		assertEquals(4, output.getHeight());
		assertEquals(imp.getBufferedImage().getRGB(1, 1), output.getBufferedImage().getRGB(3, 3));
		assertEquals(2, imp.getWidth());
		assertSame(pixels, imp.getProcessor().getPixels());
		assertEquals(1, imp.getRoi().getBounds().width);
		File file = File.createTempFile("quick-png-", ".png");
		try {
			new PNG_Writer().writeImage(output, file.getPath(), -1);
			assertEquals(4, ImageIO.read(file).getWidth());
		} finally { file.delete(); }
	}

	@Test
	public void noneReplicatesEveryPixelInSavedPngAtIntegerScales() throws Exception {
		ImagePlus imp = new ImagePlus("pattern", new FloatProcessor(3, 2,
			new float[] {0, 64, 255, 128, 255, 0}));
		imp.setDisplayRange(0, 255);
		java.awt.image.BufferedImage original = imp.getBufferedImage();
		for (int scale : new int[] {2, 3, 4, 7}) {
			ImagePlus output = QuickPNG.scaledImage(imp, scale, ImageProcessor.NONE);
			File file = File.createTempFile("quick-png-none-", ".png");
			try {
				new PNG_Writer().writeImage(output, file.getPath(), -1);
				java.awt.image.BufferedImage png = ImageIO.read(file);
				assertEquals(original.getWidth()*scale, png.getWidth());
				assertEquals(original.getHeight()*scale, png.getHeight());
				for (int y=0; y<png.getHeight(); y++)
					for (int x=0; x<png.getWidth(); x++)
						assertEquals("scale="+scale+", x="+x+", y="+y,
							original.getRGB(x/scale, y/scale), png.getRGB(x, y));
			} finally { file.delete(); }
		}
	}

	@Test
	public void grayscaleExportDoesNotApplyRgbGammaConversion() throws Exception {
		ImagePlus imp = new ImagePlus("gray", new FloatProcessor(2, 2,
			new float[] {0, 128, 192, 255}));
		imp.setDisplayRange(0, 255);
		ImagePlus output = QuickPNG.scaledImage(imp, 2, ImageProcessor.NONE);
		File original = File.createTempFile("quick-original-", ".png");
		File scaled = File.createTempFile("quick-scaled-", ".png");
		try {
			PNG_Writer writer = new PNG_Writer();
			writer.writeImage(imp, original.getPath(), -1);
			writer.writeImage(output, scaled.getPath(), -1);
			java.awt.image.BufferedImage a = ImageIO.read(original);
			java.awt.image.BufferedImage b = ImageIO.read(scaled);
			assertEquals(a.getRaster().getNumBands(), b.getRaster().getNumBands());
			for (int y=0; y<4; y++)
				for (int x=0; x<4; x++)
					assertEquals(a.getRaster().getSample(x/2, y/2, 0), b.getRaster().getSample(x, y, 0));
		} finally { original.delete(); scaled.delete(); }
	}

	@Test
	public void interpolationProducesIntermediateColors() {
		ImagePlus imp = new ImagePlus("gradient", new FloatProcessor(3, 3,
			new float[] {0, 128, 255, 0, 128, 255, 0, 128, 255}));
		imp.setDisplayRange(0, 255);
		ImageProcessor nearest = QuickPNG.scaledImage(imp, 3.0, ImageProcessor.NONE).getProcessor();
		for (int method : new int[] {ImageProcessor.BILINEAR, ImageProcessor.BICUBIC}) {
			ImageProcessor interpolated = QuickPNG.scaledImage(imp, 3.0, method).getProcessor();
			assertEquals(9, interpolated.getWidth());
			boolean differs = false;
			for (int x=0; x<9; x++) {
				if (nearest.getPixel(x, 4)!=interpolated.getPixel(x, 4))
					differs = true;
			}
			assertTrue("Interpolation should smooth the gradient", differs);
		}
	}

	@Test
	public void scaledFileNameIncludesFactor() {
		assertEquals("sample_x2.0.png", QuickPNG.scaledPngName("sample.raw", 2.0));
		assertEquals("sample.part_x0.5.png", QuickPNG.scaledPngName("sample.part.png", 0.5));
		assertEquals("sample_x1.25.png", QuickPNG.scaledPngName("sample", 1.25));
	}

	@Test
	public void validatesScale() {
		for (double scale : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, 1e20}) {
			try {
				QuickPNG.scaledSize(128, 128, scale);
				fail("Accepted invalid scale: "+scale);
			} catch (IllegalArgumentException expected) { }
		}
		assertArrayEquals(new int[] {64, 32}, QuickPNG.scaledSize(128, 64, 0.5));
	}
}
