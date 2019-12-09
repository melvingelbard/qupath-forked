package qupath.lib.images.writers;

import java.awt.Color;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Native;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

/**
 * ImageWriter implementation to write images to Numpy format.
 *
 */

public class NumpyWriter implements ImageWriter<BufferedImage> {
	
	

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<String> getExtensions() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean supportsT() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsZ() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsRGB() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean suportsImageType(ImageServer<BufferedImage> server) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsPyramidal() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean supportsPixelSize() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public String getDetails() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Class<BufferedImage> getImageClass() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, String pathOutput)
			throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writeImage(BufferedImage img, String pathOutput) throws IOException {
		byte[] out = imageToNumpyByteArray(img);
		FileOutputStream fos = new FileOutputStream(pathOutput);
        fos.write(out);
        fos.close();
		
		
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, String pathOutput) throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, OutputStream stream)
			throws IOException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void writeImage(BufferedImage img, OutputStream stream) throws IOException {
		byte[] out = imageToNumpyByteArray(img);
		stream.write(out);
	}


	@Override
	public void writeImage(ImageServer<BufferedImage> server, OutputStream stream) throws IOException {
		// TODO Auto-generated method stub
		
	}

	/**
	 * Encode a BufferedImage in Numpy array representation to a byte array.
	 * Compatible formats: [uint8, uint16, uint32, int8, int16, int32, float32, float64].
	 * @param img
	 * @return byteArray
	 */
	public static byte[] imageToNumpyByteArray(BufferedImage img) {
		int width = img.getWidth();
		int height = img.getHeight();
		int bands = img.getSampleModel().getNumBands();
		DataBuffer buffer = img.getRaster().getDataBuffer();
		int dataType = buffer.getDataType();
		
		byte[] header = new byte[128];
		int index = 0;

		int magicNumber = (0x93);
		String numpyString = "NUMPY";
        int majorVersionNum = (0x01);
        int minorVersionNum = (0x00);
        
        // (0x93)
        header[index++] = (byte)magicNumber;
        
        // NUMPY
        for (int i = 0; i < numpyString.toCharArray().length; i++) header[index++] = (byte)numpyString.toCharArray()[i];
        
        // 10
        header[index++] = (byte)majorVersionNum;
        header[index++] = (byte)minorVersionNum;

        // HEADER_LENGTH
        header[index++] = (byte)(0x76);
        header[index++] = (byte)(0x00);
        
        // {'descr: '
        String descr = "{'descr': '";
        for (int i = 0; i < descr.toCharArray().length; i++) header[index++] = (byte)descr.toCharArray()[i];
        
        // DataType    
        Map<Integer, String> formats = Map.of(
        	    DataBuffer.TYPE_BYTE, "|u1", 	// int8
        	    DataBuffer.TYPE_USHORT, "<u2", 	// int16
        	    DataBuffer.TYPE_SHORT, "|i2",	// int16
        	    DataBuffer.TYPE_INT, "|i4",		// int32
        	    DataBuffer.TYPE_FLOAT, "<f4",	// float32
        	    DataBuffer.TYPE_DOUBLE, "<f8"	// float64
        	);
        if (dataType == DataBuffer.TYPE_UNDEFINED) throw new UnsupportedOperationException("Unsupported transfer type 'Undefined'");
        String format = BufferedImageTools.is8bitColorType(img.getType()) ? formats.get(DataBuffer.TYPE_BYTE) : formats.get(dataType);
        
        
        // Convert format String to bytes
        for (int i = 0; i < format.toCharArray().length; i++) header[index++] = (byte)format.toCharArray()[i];
        
        // Metadata
        String shape = "', 'fortran_order': False, 'shape': (" + width + ", " + height + ", " + bands + "), }";
        for (int i = 0; i < shape.toCharArray().length; i++) header[index++] = (byte)shape.toCharArray()[i];
        
        // BLANK_SPACE
        while (index < 127) header[index++] = (byte)(0x20);
        header[127] = (byte)(0x0A);
        
        
    	int nPixels = width * height;
    	byte[] dataArrayForNumpy;
        if (BufferedImageTools.is8bitColorType(img.getType())){
        	int[] pixelArray = new int[nPixels];
        	img.getRGB(0, 0, width, height, pixelArray, 0, width);
        	byte[] dataArray = new byte[nPixels * 3];
        	for (int i = 0; i < nPixels*3; i += 3) {
        		int rgb = pixelArray[i/3];
        		dataArray[i] = (byte)ColorTools.red(rgb);
        		dataArray[i+1] = (byte)ColorTools.green(rgb);
        		dataArray[i+2] = (byte)ColorTools.blue(rgb);
        	}
        	dataArrayForNumpy = dataArray;
        } else {
        	int type = img.getSampleModel().getTransferType();
        	byte[] dataArray;
        	ByteBuffer bufBytes;
        	switch (type) {
        	case DataBuffer.TYPE_BYTE:
        		dataArray = new byte[nPixels * bands * 1];
        		bufBytes = ByteBuffer.wrap(dataArray);
        		for (int b = 0; b < bands; b++) {
        			for (int i = 0; i < nPixels; i++) {
        				int bytePixel = img.getSampleModel().getSample(i%width, i/height, b, img.getRaster().getDataBuffer());
        				dataArray[i] = (byte)bytePixel;
        			}
        		}
        		break;
        		
        	case DataBuffer.TYPE_SHORT:
        	case DataBuffer.TYPE_USHORT:
        		dataArray = new byte[nPixels * bands * 2];
        		int[] tempIntShort = new int[nPixels];
        		bufBytes = ByteBuffer.wrap(dataArray);
        		ShortBuffer bufshort = bufBytes.asShortBuffer();
        		for (int b = 0; b < bands; b++) {
        			img.getSampleModel().getSamples(0, 0, width, height, b, tempIntShort, img.getRaster().getDataBuffer());
        			for (int i = 0; i < nPixels; i++) {
        				bufshort.put(nPixels * b + i, (short)tempIntShort[i]);
        			}
        		}
        		break;
        		
        	case DataBuffer.TYPE_FLOAT:
        		dataArray = new byte[nPixels * bands * 4];
        		float[] tempFloat = new float[nPixels];
        		bufBytes = ByteBuffer.wrap(dataArray);
        		FloatBuffer bufFloat = bufBytes.asFloatBuffer();
        		for (int b = 0; b < bands; b++) {
        			img.getSampleModel().getSamples(0, 0, width, height, b, tempFloat, img.getRaster().getDataBuffer());
        			for (int i = 0; i < nPixels; i++) {
        				bufFloat.put(nPixels * b + i, tempFloat[i]);
        			}
        		}
        		break;
        	case DataBuffer.TYPE_DOUBLE:
        		dataArray = new byte[nPixels * bands * 8];
        		double[] tempDouble = new double[nPixels];
        		bufBytes = ByteBuffer.wrap(dataArray);
        		DoubleBuffer bufDouble = bufBytes.asDoubleBuffer();
        		for (int b = 0; b < bands; b++) {
        			img.getSampleModel().getSamples(0, 0, width, height, b, tempDouble, img.getRaster().getDataBuffer());
        			for (int i = 0; i < nPixels; i++) {
        				bufDouble.put(nPixels * b + i, tempDouble[i]);
        			}
        		}
        		break;
        	
        	case DataBuffer.TYPE_INT:
        		dataArray = new byte[nPixels * bands * 8];
        		int[] tempInt = new int[nPixels];
        		bufBytes = ByteBuffer.wrap(dataArray);
        		IntBuffer bufInt = bufBytes.asIntBuffer();
        		for (int b = 0; b < bands; b++) {
        			img.getSampleModel().getSamples(0, 0, width, height, b, tempInt, img.getRaster().getDataBuffer());
        			for (int i = 0; i < nPixels; i++) {
        				bufInt.put(nPixels * b + i, tempInt[i]);
        			}
        		}
        		break;
        	default:
        		throw new UnsupportedOperationException("Unsupported transfer type " + type);
        	}
        	
        	dataArrayForNumpy = bufBytes.array();
        }
        
        // Merge both arrays
        int totalLength = header.length + dataArrayForNumpy.length;
        byte[] out = new byte[totalLength];
        int indexOut = 0;
        while (indexOut < header.length) out[indexOut] = header[indexOut++];
        while (indexOut < totalLength) out[indexOut] = dataArrayForNumpy[-header.length+indexOut++];
        
		return out;
	}
	
	public int[][][] readNumpyArray8Bit(ByteArrayInputStream bais) throws IOException {
		byte[] bytes = new byte[bais.available()];
		bais.read(bytes);
		int[][][] out = byteToArray8Bit(bytes);
		return out;
		
		
	}
	
	public int[][][] readNumpyArray8Bit(String pathInput) throws IOException {
		File file = new File(pathInput);
		byte[] bytes = Files.readAllBytes(file.toPath());
		int[][][] out = byteToArray8Bit(bytes);
		return out;
	}
	
	
	/**
	 * Takes a byte array and returns a multi-dimensional array representing the pixel values per band.
	 * This function only accepts Numpy data types |i1 (signed-8bit) and |u1 (unsigned-8bit).
	 * @param bytes
	 * @return int[][][]
	 * @throws IOException
	 */
	public static int[][][] byteToArray8Bit(byte[] bytes) throws IOException {
		byte magicNumber = (byte)(0x93);
		
		if (bytes[0] != magicNumber) throw new IOException("Input must be Numpy file");
		
		// File separation into header/data
		String bytesAsString = new String(bytes);
		String header = (String)bytesAsString.subSequence(0, 128);
		
		
		// Format (e.g. |i1, <u2, |i2, |i4, <f4, <f8, ..)
		String format = (String) bytesAsString.subSequence(21, 24);
		
		if (!format.equals("|i1") && !format.equals("|u1")) 
			throw new UnsupportedOperationException("Wrong data type: " + format + " instead of " + "|i1 or |u1");
		
		// Shape and dimension
		String shape = (String) header.subSequence(61, header.lastIndexOf('}')-3);
        String[] dimensionList = shape.replaceAll("\\s+", "").split(",");
        
        if (dimensionList.length < 2 || dimensionList.length > 3)
        	throw new IOException("Number of dimensions not supported: " + dimensionList.toString());
        
        int width = Integer.valueOf(dimensionList[0]);
        int height = Integer.valueOf(dimensionList[1]);
        
        int bands;
        if (dimensionList.length == 2) bands = 1;
        else bands = Integer.valueOf(dimensionList[2]);
        
        int nPixels = width * height * bands;

        // Data
		int[][][] out = new int[width][height][bands];
	    
		for (int pixelByte = 0; pixelByte < nPixels; pixelByte++) {
			int outPixel = format.equals("|u1") ? (bytes[pixelByte+128] & 0xFF) : bytes[pixelByte+128];
			out[(pixelByte / bands) % height][(pixelByte / (height*bands))][pixelByte % bands] = outPixel;	
		}
		
		return out;
		
	}
	
	public BufferedImage readNumpyArrayAsBufferedImage(ByteArrayInputStream bais) throws IOException {
		byte[] bytes = new byte[bais.available()];
		bais.read(bytes);
		BufferedImage img = byteToBufferedImage(bytes);
		return img;
	}
	
	public BufferedImage byteToBufferedImage(String pathInput) throws IOException {
		File file = new File(pathInput);
		byte[] bytes = Files.readAllBytes(file.toPath());
		BufferedImage img = byteToBufferedImage(bytes);
		return img;
		
	}

	
	/**
	 * Read Numpy file from given path and creates a BufferedImage from its data.
	 * Assumes this order (in Numpy file): [width, height, channels].
	 * Compatible formats: [int8, int16, uint16, int32, float32, float64].
	 * @param pathInput
	 * @return BufferedImage
	 */
	public BufferedImage byteToBufferedImage(byte[] bytes) throws IOException {
		byte magicNumber = (byte)(0x93);
		if (bytes[0] != magicNumber) throw new IOException("Input must be Numpy format");
		
		// Separation into header/data
		String bytesAsString = new String(bytes);
		String header = (String)bytesAsString.subSequence(0, 128);
		byte[] data = Arrays.copyOfRange(bytes, 128, bytes.length);
		
		// Format (e.g. |i1, <u2, |i2, |i4, <f4, <f8, ..)
		String format = (String) bytesAsString.subSequence(21, 24);
		
		//if (format != "|i1") throw new UnsupportedOperationException("Wrong data type: " + format + " instead of " + "|i1");
		
		// Shape and dimension
		String shape = (String) header.subSequence(61, header.lastIndexOf('}')-3);
        shape = shape.replaceAll("\\s+", "");
        String[] dimensionList = shape.split(",");
        int totalDataSize = 1;
        for (String dimension: dimensionList) totalDataSize *= Integer.valueOf(dimension);
        int width = Integer.valueOf(dimensionList[0]);
        int height = Integer.valueOf(dimensionList[1]);
        int channels = Integer.valueOf(dimensionList[2]);
        
        // Data
        PixelType pt;
        BandedSampleModel sampleModel;
        switch (format) {
        case "|u1":
        case "|i1":
        	pt = PixelType.INT8;
	        sampleModel = new BandedSampleModel(DataBufferByte.TYPE_BYTE, width, height, channels);
	        break;

        case "<u2":
        	pt = PixelType.UINT16;
	        sampleModel = new BandedSampleModel(DataBufferByte.TYPE_USHORT, width, height, channels);
	        break;
        	
        case "|i2":
        	pt = PixelType.INT16;
	        sampleModel = new BandedSampleModel(DataBufferByte.TYPE_SHORT, width, height, channels);
	        break;
        	
        case "|i4":
        	pt = PixelType.INT32;
	        sampleModel = new BandedSampleModel(DataBufferByte.TYPE_INT, width, height, channels);
	        break;
        	
        case "<f4":
        	pt = PixelType.FLOAT32;
	        sampleModel = new BandedSampleModel(DataBufferByte.TYPE_FLOAT, width, height, channels);
	        break;
        	
        case "<f8":
        	pt = PixelType.FLOAT64;
	        sampleModel = new BandedSampleModel(DataBufferByte.TYPE_DOUBLE, width, height, channels);
        	break;
        	
        default:
        	throw new UnsupportedOperationException("Unsupported data type " + format);
        }
        DataBufferByte dataBuffer = new DataBufferByte(data, totalDataSize);
        WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
        BufferedImage img = new BufferedImage(ColorModelFactory.createColorModel(pt, ImageChannel.getDefaultChannelList(channels)), raster, false, null);		
        
        return img;
	}
	
	
}