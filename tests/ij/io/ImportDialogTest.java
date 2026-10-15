package ij.io;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.io.File;
import java.io.RandomAccessFile;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class ImportDialogTest {
	private void infer(ImportDialog dialog, String name) throws Exception {
		for (String methodName : new String[] {"getDimensionsFromName", "getTypeFromName"}) {
			Method method = ImportDialog.class.getDeclaredMethod(methodName, String.class);
			method.setAccessible(true);
			method.invoke(dialog, name);
		}
	}

	private int value(ImportDialog dialog, String name) throws Exception {
		Field field = ImportDialog.class.getDeclaredField(name);
		field.setAccessible(true);
		return field.getInt(dialog);
	}

	@Test
	public void folderMetadataAndFilenamePrecedence() throws Exception {
		ImportDialog dialog = new ImportDialog("000.raw", "/tmp/float64_128x128x180/");
		Method method = ImportDialog.class.getDeclaredMethod("getParametersFromName");
		method.setAccessible(true);
		method.invoke(dialog);
		assertEquals(128, value(dialog, "width"));
		assertEquals(128, value(dialog, "height"));
		assertEquals(180, value(dialog, "nImages"));
		assertEquals(6, value(dialog, "choiceSelection"));
		dialog = new ImportDialog("000-float32-64x32.raw", "/tmp/float64-128x128x180/");
		method.invoke(dialog);
		assertEquals(64, value(dialog, "width"));
		assertEquals(32, value(dialog, "height"));
		assertEquals(1, value(dialog, "nImages"));
		assertEquals(5, value(dialog, "choiceSelection"));
	}

	@Test
	public void dimensionsAndTypes() throws Exception {
		String[] types = {"uint8", "int16", "uint16", "int32", "uint32", "float32", "float64"};
		for (String separator : new String[] {"_", "-"}) {
			for (int i=0; i<types.length; i++) {
				ImportDialog dialog = new ImportDialog();
				infer(dialog, "sample"+separator+types[i]+separator+"128x64x3.raw");
				assertEquals(128, value(dialog, "width"));
				assertEquals(64, value(dialog, "height"));
				assertEquals(3, value(dialog, "nImages"));
				assertEquals(i, value(dialog, "choiceSelection"));
			}
		}
	}

	@Test
	public void typeWithoutDimensionsAndTokenBoundaries() throws Exception {
		ImportDialog dialog = new ImportDialog();
		infer(dialog, "sample-FLOAT64.raw");
		assertEquals(6, value(dialog, "choiceSelection"));
		infer(dialog, "sample_notfloat32_uint16extra.raw");
		assertEquals(6, value(dialog, "choiceSelection"));
		infer(dialog, null);
		assertEquals(6, value(dialog, "choiceSelection"));
	}

	@Test
	public void dimensionsWithSuffixAndInvalidDimensions() throws Exception {
		ImportDialog dialog = new ImportDialog();
		infer(dialog, "sample-128x64-float32-le.raw");
		assertEquals(128, value(dialog, "width"));
		assertEquals(64, value(dialog, "height"));
		assertEquals(1, value(dialog, "nImages"));
		for (String name : new String[] {"sample_0x64.raw", "sample_999999999999x64.raw", "sample.raw"}) {
			infer(dialog, name);
			assertEquals(128, value(dialog, "width"));
			assertEquals(64, value(dialog, "height"));
		}
	}

	@Test
	public void explicitTypeOverridesFileSizeGuess() throws Exception {
		File file = File.createTempFile("raw_int32_", "_8x4.raw");
		try {
			RandomAccessFile data = new RandomAccessFile(file, "rw");
			try { data.setLength(8*4*4); } finally { data.close(); }
			ImportDialog dialog = new ImportDialog(file.getName(), file.getParent()+File.separator);
			infer(dialog, file.getName());
			assertEquals(3, value(dialog, "choiceSelection"));
			infer(dialog, "8x4.raw");
			assertEquals(8, value(dialog, "width"));
		} finally {
			file.delete();
		}
	}
}
