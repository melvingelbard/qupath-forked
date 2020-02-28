package qupath.lib.classifiers.pixel;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;

/**
 * Metadata to control the behavior of a pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierMetadata {
	
	private int inputPadding = 0;
	
	private PixelCalibration inputResolution;
	
	private int inputWidth = -1;
	private int inputHeight = -1;
	private int inputNumChannels = 3;
	
	private int outputWidth = -1;
	private int outputHeight = -1;
	
	private List<String> inputChannelNames;
	private ImageChannel[] inputChannels;
	private ImageServerMetadata.ChannelType outputType = ImageServerMetadata.ChannelType.CLASSIFICATION;
	private List<ImageChannel> outputChannels;
	private Map<Integer, PathClass> classificationLabels;
	
	private String flaskAddress = null;
	
	
	/**
	 * Create a copy of an object
	 * Taken from:
	 * https://stackoverflow.com/questions/64036/how-do-you-make-a-deep-copy-of-an-object-in-java
	 */
	public static <T> T deepCopy(T anObject, Class<T> classInfo) {
	    Gson gson = new GsonBuilder().create();
	    String text = gson.toJson(anObject);
	    T newObject = gson.fromJson(text, classInfo);
	    return newObject;
	}
	
	/**
     * Requested pixel size for input.
	 * 
	 * @return
	 */
	public PixelCalibration getInputResolution() {
    	return inputResolution;
    };
    
    /**
     * Requested input padding (above, below, left and right).
     * @return
     */
    public int getInputPadding() {
    	return inputPadding;
    }
    
//    /**
//     * Returns {@code true} if the input size must be strictly applied, {@code false} if 
//     * different input image sizes can be handled.
//     */
//    public boolean strictInputSize() {
//    	return strictInputSize;
//    }

    /**
     * Requested width of input image, or -1 if the classifier is not fussy
     */
    public int getInputWidth() {
    	return inputWidth;
    }

    /**
     * Requested height of input image, or -1 if the classifier is not fussy
     */
    public int getInputHeight() {
    	return inputHeight;
    }

    /**
     * Requested number of channels in input image; default is 3 (consistent with assuming RGB)
     */
    public int getInputNumChannels() {
    	return inputNumChannels;
    }

    /**
     * Output image width for a specified inputWidth, or -1 if the inputWidth is not specified
     */
    private int getOutputWidth() {
    	return outputWidth;
    }

    /**
     * Output image height for a specified inputHeight, or -1 if the inputHeight is not specified
     */
    private int getOutputHeight() {
    	return outputHeight;
    }

    /**
     * Type of output; default is OutputType.Probability
     */
    public ImageServerMetadata.ChannelType getOutputType() {
    	return outputType;
    }
    
    /**
     * Output Flask Address on which the model is stored
     */
	public String getFlaskAddress() {
		return flaskAddress;
	}
	
	/**
	 * Output a list of all the channels used as input
	 */
	public synchronized ImageChannel[] getInputChannels(){
		return inputChannels;
	}

	
	public List<String> getInputChannelName() {
		return inputChannelNames;
	}

    /**
     * List representing the names &amp; display colors for each output channel,
     * or for the output classifications if <code>outputType == OutputType.Classification</code>
     */
    public synchronized List<ImageChannel> getOutputChannels() {
    	if (outputChannels == null && classificationLabels != null) {
    		outputChannels = PathClassifierTools.classificationLabelsToChannels(classificationLabels, true);
    	}
    	return outputChannels == null ? Collections.emptyList() : Collections.unmodifiableList(outputChannels);
    }
    
    /**
     * Map between integer labels and classifications. For a labelled image (output type is CLASSIFICATION) then 
     * the labels correspond to pixel values. Otherwise they correspond to channel numbers.
     * @return
     */
    public synchronized Map<Integer, PathClass> getClassificationLabels() {
    	if (classificationLabels == null && outputChannels != null) {
    		classificationLabels = new LinkedHashMap<>();
    		for (int i = 0; i < outputChannels.size(); i++) {
    			var channel = outputChannels.get(i);
    			classificationLabels.put(i, PathClassFactory.getPathClass(channel.getName(), channel.getColor()));
    		}
    	}
    	return classificationLabels == null ? Collections.emptyMap() : Collections.unmodifiableMap(classificationLabels);
    }
    
    
    private PixelClassifierMetadata(Builder builder) {
    	this.inputResolution = builder.inputResolution;
    	this.inputPadding = builder.inputPadding;
    	this.inputWidth = builder.inputWidth;
    	this.inputHeight = builder.inputHeight;
    	this.inputChannels = builder.inputChannels;
    	this.inputNumChannels = builder.inputNumChannels;
    	this.inputChannelNames = builder.inputChannelNames;
    	this.classificationLabels = builder.classificationLabels;
    	this.outputWidth = builder.outputWidth;
    	this.outputHeight = builder.outputHeight;
    	this.outputType = builder.outputType;
    	this.outputChannels = builder.outputChannels;
    	this.flaskAddress = builder.flaskAddress;
//    	this.strictInputSize = builder.strictInputSize;
    }
    
    
    /**
     * Builder to create {@link PixelClassifierMetadata} objects.
     */
    public static class Builder {
    	
    	private int inputPadding = 0;
    	
    	private PixelCalibration inputResolution;
    	
    	private int inputWidth = -1;
    	private int inputHeight = -1;
    	private int inputNumChannels = 3;
    	
    	private int outputWidth = -1;
    	private int outputHeight = -1;
    	
    	private List<String> inputChannelNames = new ArrayList<>();
    	private ImageChannel[] inputChannels;
    	private ImageServerMetadata.ChannelType outputType = ImageServerMetadata.ChannelType.CLASSIFICATION;
    	private List<ImageChannel> outputChannels = new ArrayList<>();
    	
    	private String flaskAddress = null;
    	
    	private Map<Integer, PathClass> classificationLabels;
    	
    	/**
    	 * Build a new PixelClassifierMetadata object.
    	 * @return
    	 */
    	public PixelClassifierMetadata build() {
    		return new PixelClassifierMetadata(this);
    	}
    	
    	
    	public Builder setMetadata(PixelClassifierMetadata metadata) {
    		this.inputPadding = deepCopy(metadata.getInputPadding(), Integer.class);
    		this.inputResolution = deepCopy(metadata.getInputResolution(), PixelCalibration.class);
    		this.inputWidth = deepCopy(metadata.getInputWidth(), Integer.class);
    		this.inputHeight = deepCopy(metadata.getInputHeight(), Integer.class);
    		this.inputChannelNames = metadata.getInputChannelName();
    		this.inputChannels = metadata.getInputChannels();
    		this.inputNumChannels = deepCopy(metadata.getInputNumChannels(), Integer.class);
    		
    		this.outputWidth = deepCopy(metadata.getOutputWidth(), Integer.class);
    		this.outputHeight = deepCopy(metadata.getOutputHeight(), Integer.class);
    		
    		this.outputType = deepCopy(metadata.getOutputType(), ImageServerMetadata.ChannelType.class);
    		this.outputChannels = metadata.getOutputChannels();
    		this.flaskAddress = deepCopy(metadata.getFlaskAddress(), String.class);
    		
    		return this;
    		
    	}
    	
    	/**
    	 * Amount of padding requested for the left, right, top and bottom of the image tile being classified.
    	 * This can be used to reduce boundary artifacts.
    	 * @param inputPadding
    	 * @return
    	 */
    	public Builder inputPadding(int inputPadding) {
    		this.inputPadding = inputPadding;
    		return this;
    	}
    	
    	/**
    	 * Specify the output channel type.
    	 * @param type
    	 * @return
    	 */
    	public Builder setChannelType(ImageServerMetadata.ChannelType type) {
    		this.outputType = type;
    		return this;
    	}
    	
    	/**
    	 * Pixel size defining the resolution at which the classifier should operate.
    	 * @param inputResolution
    	 * @return
    	 */
    	public Builder inputResolution(PixelCalibration inputResolution) {
    		this.inputResolution = inputResolution;
    		return this;
    	}
    	
    	/**
    	 * Preferred input image width and height. This may either be a hint or strictly enforced.
    	 * @param width
    	 * @param height
    	 * @return
    	 */
    	public Builder inputShape(int width, int height) {
    		this.inputWidth = width;
    		this.inputHeight = height;
    		return this;
    	}
    	
    	
    	/**
    	 * Specify channels for input
    	 * @param server
    	 * @return
    	 */
    	public Builder inputChannels(ImageServer<BufferedImage> server) {
    		if (this.inputChannels != null) Arrays.fill(this.inputChannels, null);
    		else this.inputChannels = new ImageChannel[this.inputChannelNames.size()];
    		
    		if (!(this.inputChannelNames == null)) {
        		for (int i = 0; i < this.inputChannelNames.size(); i++) {
        			this.inputChannels[i] = (ImageChannel.getInstance(this.inputChannelNames.get(i), null));
        		}
    		}
    		return this;
    	}
    	
//    	/**
//    	 * Strictly enforce the input shape. Otherwise it is simply a request, but the pixel classifier may use a different size.
//    	 * @return
//    	 */
//    	public Builder strictInputSize() {
//    		this.strictInputSize = true;
//    		return this;
//    	}
    	
    	/**
    	 * Specify channels for output.
    	 * @param channels
    	 * @return
    	 */
    	public Builder outputChannels(ImageChannel... channels) {
    		this.outputChannels.addAll(Arrays.asList(channels));
    		return this;
    	}
    	
    	/**
    	 * Specify channels for output.
    	 * @param channels
    	 * @return
    	 */
    	public Builder outputChannels(Collection<ImageChannel> channels) {
    		this.outputChannels.addAll(channels);
    		return this;
    	}
    	
    	/**
    	 * Set the Flask address to use.
    	 * @param flaskAddress
    	 * @return
    	 */
		public Builder setFlaskAddress(String flaskAddress) {
			this.flaskAddress = flaskAddress;
			return this;
		}
    	
    	/**
    	 * Specify classification labels. This may be used instead of outputChannels.
    	 * @param labels
    	 * @return
    	 */
    	public Builder classificationLabels(Map<Integer, PathClass> labels) {
    		this.classificationLabels = new LinkedHashMap<>(labels);
    		return this;
    	}
    	    	
    }
    
}