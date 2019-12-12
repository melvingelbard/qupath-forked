package qupath.lib.images.writers;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;

public class NumpyReader {
	
	public BufferedImage readNumpyAsBufferedImage(ByteArrayInputStream bais) throws IOException {
		byte[] bytes = new byte[bais.available()];
		bais.read(bytes);
		BufferedImage img = byteToBufferedImage(bytes);
		return img;
	}
	
	public BufferedImage readNumpyAsBufferedImage(String pathInput) throws IOException {
		File file = new File(pathInput);
		byte[] bytes = Files.readAllBytes(file.toPath());
		BufferedImage img = byteToBufferedImage(bytes);
		return img;
	}
	
	
	public int[][][] readNumpyAsRGBArray(ByteArrayInputStream bais) throws IOException {
		byte[] bytes = new byte[bais.available()];
		bais.read(bytes);
		int[][][] out = byteToRGBArray(bytes);
		return out;
	}
	
	public int[][][] readNumpyAsRGBArray(String pathInput) throws IOException {
		File file = new File(pathInput);
		byte[] bytes = Files.readAllBytes(file.toPath());
		int[][][] out = byteToRGBArray(bytes);
		return out;
	}
	
	public Object readNumpyArray(ByteArrayInputStream bais) throws IOException {
		byte[] bytes = new byte[bais.available()];
		bais.read(bytes);
		return byteToArray(bytes);
	}
	
	private Object byteToArray(byte[] bytes) throws IOException {
		byte magicNumber = (byte)(0x93);
		if (bytes[0] != magicNumber) throw new IOException("Input must be Numpy file");
		
		// File separation into header/data
		String bytesAsString = new String(bytes);
		String header = (String)bytesAsString.subSequence(0, 128);
		
		
		// Format (e.g. |i1, <u2, |i2, |i4, <f4, <f8, ..)
		Map<String, String> formats = Map.of(
        		"|b1", "boolean",	// byte
        		"|i2", "short",		// int16
        	    "|i4", "int",		// int32
        	    "|i8", "long",		// int64
        	    "<f4", "float",		// float32
        	    "<f8", "double", 	// float64
        	    "<U2", "char"		// string8
        	);
		
		String format = (String) bytesAsString.subSequence(21, 24);
		
		if (!formats.containsKey(format))
			throw new UnsupportedOperationException("Wrong data type. " + format + " cannot be read");
		
		// Shape and dimension
		String shape = (String) header.subSequence(61, header.lastIndexOf('}')-3);
        String[] dimensionList = shape.replaceAll("\\s+", "").split(",");
        
        if (dimensionList.length > 1)
        	throw new IOException("Number of dimensions not supported: " + dimensionList.toString());
        
        int arrayLength = Integer.valueOf(dimensionList[0]);
        int byteLength = bytes.length;
        Object out = new Object();

        // Data
        switch (formats.get(format)) {
        case "boolean":
        	boolean[] booleanTemp = new boolean[arrayLength];
        	for (int i = 0; i < byteLength-128; i++) booleanTemp[i] = bytes[i + 128] == (byte)1 ? true : false;
        	out = (Object)booleanTemp;
        	break;
        	
        case "short":
        	short[] shortTemp = new short[arrayLength];
        	for (int i = 0; i < byteLength-128; i+=2) 
        		shortTemp[i/2] = ByteBuffer.allocate(2).put(bytes, i+128, 2).position(0).getShort();
        	out = (Object)shortTemp;
        	break;
        case "int":
        	int[] intTemp = new int[arrayLength];
        	for (int i = 0; i < byteLength-128; i+=4)
        		intTemp[i/4] = ByteBuffer.allocate(4).put(bytes, i+128, 4).position(0).getInt();
        	out = (Object)intTemp;
        	break;
        	
        case "long":
        	long[] longTemp = new long[arrayLength];
        	for (int i = 0; i < byteLength-128; i+=8)
        		longTemp[i/8] = ByteBuffer.allocate(8).put(bytes, i+128, 8).position(0).getLong();
        	out = (Object)longTemp;
        	break;
        	
        case "float":
        	float[] floatTemp = new float[arrayLength];
        	for (int i = 0; i < byteLength-128; i+=4)
        		floatTemp[i/4] = ByteBuffer.allocate(4).put(bytes, i+128, 4).position(0).getFloat();
        	out = (Object)floatTemp;
        	break;
        	
        case "double":
        	double[] doubleTemp = new double[arrayLength];
        	for (int i = 0; i < byteLength-128; i+=8)
        		doubleTemp[i/8] = ByteBuffer.allocate(8).put(bytes, i+128, 8).position(0).getDouble();
        	out = (Object)doubleTemp;
        	break;
        	
        case "char":
        	char[] charTemp = new char[arrayLength];
        	for (int i = 0; i < byteLength-128; i+=2)
        		charTemp[i/2] = ByteBuffer.allocate(2).put(bytes, i+128, 2).position(0).getChar();
        	out = (Object)charTemp;
        	break;
        }
		
		return out;
	}

	
	/**
	 * Creates a BufferedImage from a given byte array.
	 * Assumes this order (in byte array): [width, height, channels].
	 * Compatible formats: [int8, int16, uint16, int32, float32, float64].
	 * @param pathInput
	 * @return BufferedImage
	 */
	private BufferedImage byteToBufferedImage(byte[] bytes) throws IOException {
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
	
	/**
	 * Takes a byte array and returns a multi-dimensional array representing the pixel values per band.
	 * This function only accepts Numpy data types |i1 (signed-8bit) and |u1 (unsigned-8bit).
	 * This function only accepts 2 (greyscale) or 3 (RGB) bands.
	 * @param bytes
	 * @return int[][][]
	 * @throws IOException
	 */
	private static int[][][] byteToRGBArray(byte[] bytes) throws IOException {
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
	

}
