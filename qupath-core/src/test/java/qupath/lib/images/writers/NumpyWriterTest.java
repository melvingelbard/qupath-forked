package qupath.lib.images.writers;

import static org.junit.Assert.fail;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class NumpyWriterTest {
	
	@Test
	public void testWriterWithDifferentImages() throws IOException {
		BufferedImage imgGray = createImage(BufferedImage.TYPE_BYTE_GRAY);
		BufferedImage imgRGB = createImage(BufferedImage.TYPE_INT_RGB);
		var images = new BufferedImage[] {imgGray, imgRGB};

		for (var img : images) {
			testWriter(new NumpyWriter(), img);
			testWriter8Bit(new NumpyWriter(), img);
		}
	}
	
	

	private void testWriter8Bit(NumpyWriter writer, BufferedImage img) {
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
			int[][][] imgRead = writer.readNumpyArray8Bit(stream);		
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



	void testWriter(NumpyWriter writer, BufferedImage img) {
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
			var imgRead = writer.readNumpyArrayAsBufferedImage(stream);
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