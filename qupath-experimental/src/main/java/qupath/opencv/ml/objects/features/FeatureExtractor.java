package qupath.opencv.ml.objects.features;

import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.List;

import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;

public interface FeatureExtractor {
	
	public List<String> getFeatureNames();
	
	public int nFeatures();

	public void extractFeatures(ImageData<BufferedImage> imageData, Collection<PathObject> pathObjects, FloatBuffer buffer);
	
}