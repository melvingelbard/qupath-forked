package qupath.opencv.ml.pixel;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import qupath.lib.analysis.images.SimpleImages;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.writers.ImageWriterTools;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ml.pixel.features.FeatureCalculator;
import qupath.opencv.ml.pixel.features.PixelFeature;


public class PyTorchPixelClassifier implements PixelClassifier {


	private ColorModel colorModel;
	private PixelCalibration inputResolution;
	private PixelClassifierMetadata metadata;
	private FeatureCalculator<BufferedImage> featureCalculator;


	/**
	 * @param featureCalculator
	 * @param inputResolution
	 * @param metadata
	 */
	public PyTorchPixelClassifier(FeatureCalculator<BufferedImage> featureCalculator, PixelCalibration inputResolution, PixelClassifierMetadata metadata) {
		super();
		this.featureCalculator = featureCalculator;
		this.inputResolution = inputResolution;
		this.metadata = metadata;
		this.colorModel = getColorModel();
	}

	@Override
	public boolean supportsImage(ImageData<BufferedImage> imageData) {
		return true;
	}

	@Override
	public synchronized BufferedImage applyClassification(ImageData<BufferedImage> imageData, RegionRequest request)
			throws IOException {

		var server = imageData.getServer();
		var img = server.readBufferedImage(request);
		BufferedImage imgResult = null;

		//ImageWriterTools.writeImageAsNumpy(img, "M:/deep_learning/toNumpy.npy");

		if (featureCalculator != null) {
			float[] transformed;
			List<PixelFeature> features;
			try {
				features = featureCalculator.calculateFeatures(imageData, request);
			} catch (Exception ex) {
				features = new ArrayList<PixelFeature>();
			}

			if (features.size() == 1) {
				var feature = features.get(0).getFeature();
				transformed = SimpleImages.getPixels(feature, true);
				int arrayLength = transformed.length;

				//DataBufferByte dataBuffer = new DataBufferByte(byteArray, arrayLength);
		        //BandedSampleModel sampleModel = new BandedSampleModel(dataBuffer.getDataType(), img.getWidth(), img.getHeight(), 1);
		        //WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
		        //img = new BufferedImage(getColorModel(), raster, false, null);


		        img = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		        byte [] imgData = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		        for (int i = 0; i < arrayLength; i++) {
		        	imgData[i] = (byte)transformed[i];
		        }
			}
		}


		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		var imageWriter = ImageWriterTools.getCompatibleWriters(server, ".tif");
		try {
			imageWriter.get(0).writeImage(server, request, stream);
		} catch (IOException ex) {
			Dialogs.showErrorMessage("Image Format", ex);
		}


	    //ImageIO.write(img, "TIFF", stream);

	    // Create POST request (/predict) for Flask
	    URL obj = new URL(metadata.getFlaskAddress() + "predict");
	    HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
	    conn.setConnectTimeout(1000);
	    conn.setRequestMethod("POST");
	    conn.setDoOutput(true);
	    conn.setDoInput(true);
	    conn.setUseCaches(false);
	    DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());


	    // Send .tif to Flask
	    outputStream.write(stream.toByteArray());
	    outputStream.flush();
	    outputStream.close();
	    stream.flush();
	    stream.close();



	    // Get response from Flask
	    //if (conn.getResponseCode() == 500) return null; // Internal Server Error

	    BufferedImage prediction;
	    if (metadata.getOutputType() == ImageServerMetadata.ChannelType.CLASSIFICATION){
	    	InputStream inputStream = new BufferedInputStream(conn.getInputStream());
	        prediction = ImageIO.read(inputStream);
	        inputStream.close();

	        // Create new BufferedImage with colorModel and return it
	        imgResult = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_BYTE_INDEXED, (IndexColorModel)getColorModel());
	        imgResult.setData(prediction.getRaster());


	    } else if (metadata.getOutputType() == ImageServerMetadata.ChannelType.PROBABILITY) {

	    	InputStream inputStream = new BufferedInputStream(conn.getInputStream());
	        imgResult = ImageIO.read(inputStream);
	        inputStream.close();

	        //imgResult = new BufferedImage(getColorModel(), prediction.getRaster(), false, null);

	    }



		return imgResult;
	}


	private synchronized ColorModel getColorModel() {
		if (colorModel == null) {
			var metadata = getMetadata();
			if (metadata.getOutputType() == ImageServerMetadata.ChannelType.CLASSIFICATION)
				this.colorModel = (IndexColorModel)ColorModelFactory.getIndexedClassificationColorModel(metadata.getClassificationLabels());
			else if (metadata.getOutputType() == ImageServerMetadata.ChannelType.PROBABILITY)
				this.colorModel = ColorModelFactory.getProbabilityColorModel8Bit(metadata.getOutputChannels());
		}
		return colorModel;
	}


	static ImageChannel getChannel(PathClass pathClass) {
		if (pathClass == null || !pathClass.isValid())
			return ImageChannel.getInstance("None", null);
		return ImageChannel.getInstance(pathClass.getName(), PathClassTools.isIgnoredClass(pathClass) ? null : pathClass.getColor());
	}

	@Override
	public PixelClassifierMetadata getMetadata() {
		if (metadata == null) {
			this.metadata = new PixelClassifierMetadata.Builder()
					.inputResolution(inputResolution)
					.inputShape(64, 64)
					.setChannelType(ImageServerMetadata.ChannelType.CLASSIFICATION)
					.build();
		}
		return metadata;
	}
}
