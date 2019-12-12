package qupath.lib.images.writers;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

/**
 * ImageWriter implementation to write and read images to/from Numpy format.
 *
 */
public class NumpyWriter implements ImageWriter<BufferedImage> {

	@Override
	public String getName() {
		return "Numpy";
	}

	@Override
	public Collection<String> getExtensions() {
		return Collections.singleton("npy");
	}

	@Override
	public boolean supportsT() {
		return false;
	}

	@Override
	public boolean supportsZ() {
		return false;
	}

	@Override
	public boolean supportsRGB() {
		return true;
	}

	@Override
	public boolean suportsImageType(ImageServer<BufferedImage> server) {
		PixelType[] supported = new PixelType[]{
				PixelType.INT8,
				PixelType.UINT16, 
				PixelType.INT16, 
				PixelType.INT32, 
				PixelType.FLOAT32, 
				PixelType.FLOAT64
				};
		
		return server.isRGB() || Arrays.asList(supported).contains(server.getPixelType());
	}

	@Override
	public boolean supportsPyramidal() {
		return false;
	}

	@Override
	public boolean supportsPixelSize() {
		return false;
	}

	@Override
	public String getDetails() {
		return "Write data using Numpy format. Accepts different formats, depending on the function used. "
		+ "E.g. writeImage() does not accept char data, where as writeAsNumpy() does.";
	}

	@Override
	public Class<BufferedImage> getImageClass() {
		return BufferedImage.class;
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, String pathOutput)
			throws IOException {
		writeImage(server, RegionRequest.createInstance(server), pathOutput);
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
		writeImage(server, RegionRequest.createInstance(server), pathOutput);
	}

	@Override
	public void writeImage(ImageServer<BufferedImage> server, RegionRequest region, OutputStream stream)
			throws IOException {
		BufferedImage img = server.readBufferedImage(region);
		writeImage(img, stream);
	}

	@Override
	public void writeImage(BufferedImage img, OutputStream stream) throws IOException {
		byte[] out = imageToNumpyByteArray(img);
		stream.write(out);
	}


	@Override
	public void writeImage(ImageServer<BufferedImage> server, OutputStream stream) throws IOException {
		writeImage(server, RegionRequest.createInstance(server), stream);
	}
	
	/**
	 * Write array to stream in Numpy format.
	 * Array type supported: [{@code Character}, {@code Short}, 
	 * {@code Integer}, {@code Long}, {@code Float}, {@code Double}]
	 * @param array
	 * @param stream
	 * @throws IOException
	 */
	public void writeAsNumpy(Object array, OutputStream stream) throws IOException {
		byte[] header = null;
		byte[] data = null;
		
		if (boolean[].class.isInstance(array)) {
			boolean[] booleanArray = (boolean[])array;
	    	header = makeHeader("boolean", booleanArray.length);
	    	data = arrayToNumpyByteArray(booleanArray);
		}
		else if (char[].class.isInstance(array)) {
	    	char[] charArray = (char[])array;
	    	header = makeHeader("char", charArray.length);
	    	data = arrayToNumpyByteArray(charArray);
	    }
	    else if (short[].class.isInstance(array)) {
	    	short[] shortArray = (short[])array;
	    	header = makeHeader("short", shortArray.length);
	    	data = arrayToNumpyByteArray(shortArray);
	    }
	    else if (int[].class.isInstance(array)) {
	    	int[] intArray = (int[])array;
	    	header = makeHeader("int", intArray.length);
	    	data = arrayToNumpyByteArray(intArray);
	    }
	    else if (long[].class.isInstance(array)) {
	    	long[] longArray = (long[])array;
	    	header = makeHeader("long", longArray.length);
	    	data = arrayToNumpyByteArray(longArray);
	    }
	    else if (float[].class.isInstance(array)) {
	    	float[] floatArray = (float[])array;
	    	header = makeHeader("float", floatArray.length);
	    	data = arrayToNumpyByteArray(floatArray);
	    }
	    else if (double[].class.isInstance(array)) {
	    	double[] doubleArray = (double[])array;
	    	header = makeHeader("double", doubleArray.length);
	    	data = arrayToNumpyByteArray(doubleArray);
	    } else {
	    	throw new UnsupportedOperationException("Data type not supported.");
	    }
	    
	    stream.write(header);
	    stream.write(data);
	}
	
	
	/**
	 * Write array to file in Numpy format.
	 * Array type supported: [{@code Character}, {@code Short}, 
	 * {@code Integer}, {@code Long}, {@code Float}, {@code Double}]
	 * @param array
	 * @param pathOutput
	 * @throws IOException
	 */
	public void writeAsNumpy(Object array, String pathOutput) throws IOException {
		byte[] header = null;
		byte[] data = null;
		
		if (char[].class.isInstance(array)) {
	    	char[] charArray = (char[])array;
	    	header = makeHeader("char", charArray.length);
	    	data = arrayToNumpyByteArray(charArray);
	    }
	    else if (short[].class.isInstance(array)) {
	    	short[] shortArray = (short[])array;
	    	header = makeHeader("short", shortArray.length);
	    	data = arrayToNumpyByteArray(shortArray);
	    }
	    else if (int[].class.isInstance(array)) {
	    	int[] intArray = (int[])array;
	    	header = makeHeader("int", intArray.length);
	    	data = arrayToNumpyByteArray(intArray);
	    }
	    else if (long[].class.isInstance(array)) {
	    	long[] longArray = (long[])array;
	    	header = makeHeader("long", longArray.length);
	    	data = arrayToNumpyByteArray(longArray);
	    }
	    else if (float[].class.isInstance(array)) {
	    	float[] floatArray = (float[])array;
	    	header = makeHeader("float", floatArray.length);
	    	data = arrayToNumpyByteArray(floatArray);
	    }
	    else if (double[].class.isInstance(array)) {
	    	double[] doubleArray = (double[])array;
	    	header = makeHeader("double", doubleArray.length);
	    	data = arrayToNumpyByteArray(doubleArray);
	    } else {
	    	throw new UnsupportedOperationException("Data type not supported.");
	    }
	    
		FileOutputStream fos = new FileOutputStream(pathOutput);
        fos.write(header);
        fos.write(data);
        fos.close();
	}
	
	
	private byte[] arrayToNumpyByteArray(boolean[] array) {
		int length = array.length;
		byte[] data = new byte[length*1];
		
		for (int i = 0; i < data.length; i++)
			data[i] = array[i] == true ? (byte)1 : (byte)0;
		return data;
	}
	
	private byte[] arrayToNumpyByteArray(char[] array) {
		int length = array.length;
		byte[] data = new byte[length*2];
		
		for (int i = 0; i < data.length; i++) {
            data[i] =  ByteBuffer.allocate(2).putChar(array[i/2]).array()[i%2];
        }
		return data;
	}
	
	private byte[] arrayToNumpyByteArray(short[] array) {
		int length = array.length;
		byte[] data = new byte[length*2];
		
		for (int i = 0; i < data.length; i++) {
            data[i] =  ByteBuffer.allocate(2).putShort(array[i/2]).array()[i%2];
        }
		return data;
	}
	
	private byte[] arrayToNumpyByteArray(int[] array) {
		int length = array.length;
		byte[] data = new byte[length*4];
		
		for (int i = 0; i < data.length; i++) {
            data[i] =  ByteBuffer.allocate(4).putInt(array[i/4]).array()[i%4];
        }
		return data;
	}
	
	private byte[] arrayToNumpyByteArray(long[] array) {
		int length = array.length;
		byte[] data = new byte[length*8];
		
		for (int i = 0; i < data.length; i++) {
            data[i] =  ByteBuffer.allocate(8).putLong(array[i/8]).array()[i%8];
        }
		return data;
	}
	
	private byte[] arrayToNumpyByteArray(float[] array) {
		int length = array.length;
		byte[] data = new byte[length*4];
		
		for (int i = 0; i < data.length; i++) {
            data[i] =  ByteBuffer.allocate(4).putFloat(array[i/4]).array()[i%4];
        }
		return data;
	}
	
	private byte[] arrayToNumpyByteArray(double[] array) {
		int length = array.length;
		byte[] data = new byte[length*8];
		
		for (int i = 0; i < data.length; i++) {
            data[i] =  ByteBuffer.allocate(8).putDouble(array[i/8]).array()[i%8];
        }
		return data;
	}
	
	
	/**
	 * Create and return Numpy file header as a byte array.
	 * @param dataType
	 * @param length
	 * @return header
	 */
	private byte[] makeHeader(String dataType, int length) {
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

        Map<String, String> formats = Map.of(
        		"boolean", "|b1",	// byte
        		"short", "|i2",		// int16
        	    "int", "|i4",		// int32
        	    "long", "|i8",		// int64
        	    "float", "<f4",		// float32
        	    "double", "<f8", 	// float64
        	    "char", "<U2"		// string8
        	);
        
        String format = formats.get(dataType);
        
        // Convert format String to bytes
        for (int i = 0; i < format.toCharArray().length; i++) header[index++] = (byte)format.toCharArray()[i];
        
        // Metadata
        String shape = "', 'fortran_order': False, 'shape': (" + length + "), }";
        for (int i = 0; i < shape.toCharArray().length; i++) header[index++] = (byte)shape.toCharArray()[i];
        
        // BLANK_SPACE
        while (index < 127) header[index++] = (byte)(0x20);
        header[127] = (byte)(0x0A);
        
        return header;
	}

	/**
	 * Convert a BufferedImage to a byte array in Numpy format.
	 * Compatible formats: [uint8, uint16, uint32, int8, int16, int32, float32, float64].
	 * @param img
	 * @return byteArray
	 */
	private static byte[] imageToNumpyByteArray(BufferedImage img) {
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
             
        // Data
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
        		
        	case DataBuffer.TYPE_INT:
        		dataArray = new byte[nPixels * bands * 4];
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
	
	
}