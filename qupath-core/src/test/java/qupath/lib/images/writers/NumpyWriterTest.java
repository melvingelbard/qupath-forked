package qupath.lib.images.writers;

import static org.junit.Assert.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class NumpyWriterTest {
	
	@Test
	public void testWriterWithDifferentImages() throws IOException {
		BufferedImage imgGray = createImage(BufferedImage.TYPE_BYTE_GRAY);
		BufferedImage imgRGB = createImage(BufferedImage.TYPE_INT_RGB);
		var images = new BufferedImage[] {imgGray, imgRGB};

		for (var img : images) {
			testWriter(new NumpyWriter(), new NumpyReader(), img);
			testWriter8Bit(new NumpyWriter(), new NumpyReader(), img);
		}
	}
	
	@Test
	public void testWriterWithDifferentArrays() throws IOException {
		boolean[] booleanArray = new boolean[] {false, true, true, true, false};
		char[] charArray = new char[] {'a', 'b', 'c', 'd', 'e'};
		short[] shortArray = new short[] {1, 2, 3, 4, 5};
		int[] intArray = new int[] {1, 2, 3, 4, 5};
		long[] longArray = new long[] {1L, 2L, 3L, 4L, 5L};
		float[] floatArray = new float[] {(float)0.1, (float)0.2, (float)0.3, (float)0.4, (float)0.5};
		double[] doubleArray = new double[] {0.1, 0.2, 0.3, 0.4, 0.5};
		NumpyWriter nw = new NumpyWriter();
		NumpyReader nr = new NumpyReader();
		
		byte[] bytes;
		Object back;
		try (var outputStream = new ByteArrayOutputStream()) {
			// Write boolean[]
			nw.writeAsNumpy(booleanArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read boolean[]
			var inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(booleanArray, (boolean[])back);
						
			// Write char[]
			outputStream.reset();
			nw.writeAsNumpy(charArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read char[]
			inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(charArray, (char[])back);
			
			// Write short[]
			outputStream.reset();
			nw.writeAsNumpy(shortArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read short[]
			inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(shortArray, (short[])back);
			
			// Write int[]
			outputStream.reset();
			nw.writeAsNumpy(intArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read int[]
			inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(intArray, (int[])back);
			
			// Write long[]
			outputStream.reset();
			nw.writeAsNumpy(longArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read long[]
			inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(longArray, (long[])back);
			
			// Write float[]
			outputStream.reset();
			nw.writeAsNumpy(floatArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read float[]
			inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(floatArray, (float[])back);
			
			// Write double[]
			outputStream.reset();
			nw.writeAsNumpy(doubleArray, outputStream);
			bytes = outputStream.toByteArray();
			// Read double[]
			inputStream = new ByteArrayInputStream(bytes);
			back = nr.readNumpyArray(inputStream);
			Arrays.equals(doubleArray, (double[])back);
			
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error writing/reading array: " + e.getLocalizedMessage());
			return;
		}
	}


	private void testWriter8Bit(NumpyWriter writer, NumpyReader reader, BufferedImage img) {
		byte[] bytes;
		try (var stream = new ByteArrayOutputStream()) {
			writer.writeImage(img, stream);
			bytes = stream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error writing to byte array: " + e.getLocalizedMessage());
			return;
		}
		try (var stream = new ByteArrayInputStream(bytes)) {
			int[][][] imgRead = reader.readNumpyAsRGBArray(stream);		
			int[][][] imgOrig = imgTo3DimArray(img);
			compareArrays(imgOrig, imgRead);
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error reading from byte array: " + e.getLocalizedMessage());
			return;
		}
		
	}


	private int[][][] imgTo3DimArray(BufferedImage img) throws IOException {
		Raster raster = img.getRaster();
		SampleModel sampleModel = img.getSampleModel();
		int width = img.getWidth();
		int height = img.getHeight();
		int bands = img.getRaster().getNumBands();
		
		int[][][] out = new int[width][height][bands];
		for (int b = 0; b < bands; b++) {
			for (int w = 0; w < width; w++) {
				for (int h = 0; h < height; h++) {
					out[w][h][b] = sampleModel.getSample(w, h, b, raster.getDataBuffer());
				}
			}
		}
		return out;
	}



	private void compareArrays(int[][][] imgOrig, int[][][] imgRead) {
		int width = imgOrig.length;
		int height = imgOrig[0].length;
		int bands = imgOrig[0][0].length;
		
		if (width != imgRead.length) fail("Arrays are not the same dimensions (width)");
		if (height != imgRead[0].length) fail("Arrays are not the same dimensions (height)");
		if (bands != imgRead[0][0].length) fail("Arrays are not the same dimensions (bands)");
		
		for (int w = 0; w < width; w++) {
			for (int h = 0; h < height; h++) {
				for (int b = 0; b < bands; b++) {
					if (imgOrig[w][h][b] != imgRead[w][h][b]) fail("Arrays not equal");
				}
			}
		}
	}



	void testWriter(NumpyWriter writer, NumpyReader reader, BufferedImage img) {
		byte[] bytes;
		try (var stream = new ByteArrayOutputStream()) {
			writer.writeImage(img, stream);
			bytes = stream.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error writing to byte array: " + e.getLocalizedMessage());
			return;
		}
		try (var stream = new ByteArrayInputStream(bytes)) {
			var imgRead = reader.readNumpyAsBufferedImage(stream);
			compareImages(img, imgRead);
		} catch (IOException e) {
			e.printStackTrace();
			fail("Error reading from byte array: " + e.getLocalizedMessage());
			return;
		}
	}
	
	
	static void compareImages(BufferedImage imgOrig, BufferedImage imgRead) {
		Arrays.equals(getPixelArray(imgOrig), getPixelArray(imgRead));
	}

	
	static int[] getPixelArray(BufferedImage img) {
		int[] dataArray = new int[img.getWidth() * img.getHeight()];
		for (int i = 0; i < img.getWidth() * img.getHeight(); i++) {
			int bytePixel = img.getSampleModel().getSample(i%img.getWidth(), i/img.getHeight(), 0, img.getRaster().getDataBuffer());
			dataArray[i] = bytePixel;
		}
		return dataArray;
	}
	
	static BufferedImage createImage(int type) {
		BufferedImage img = new BufferedImage(128, 128, type);
		var g2d = img.createGraphics();
		g2d.setColor(Color.MAGENTA);
		g2d.fillOval(0, 0, img.getWidth(), img.getHeight());
		g2d.setColor(Color.YELLOW);
		g2d.setStroke(new BasicStroke(2f));
		g2d.drawRect(10, 10, 64, 64);
		g2d.dispose();
		return img;
	}
}