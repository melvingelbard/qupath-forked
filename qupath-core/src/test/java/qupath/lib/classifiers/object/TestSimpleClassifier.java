package qupath.lib.classifiers.object;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import qupath.lib.classifiers.object.ObjectClassifiers.ClassifyByMeasurementFunction;
import qupath.lib.common.ColorTools;
import qupath.lib.images.ImageData;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.measurements.MeasurementListFactory;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestSimpleClassifier {
	
	private static PathClass pathClass1;
	private static PathClass pathClass2;
	private static PathClass pathClass3;
	private static PathClass pathClass4;
	private static PathClass pathClass5;
	private static PathClass pathClassUnclassified;
	
	private static PathObject po1;
	private static PathObject po2;
	private static PathObject po3;
	private static PathObject po4;
	private static PathObject po5;
	private static PathObject po6;
	private static PathObject po7;
	private static PathObject po8;
	private static PathObject po9;
	
	private static List<PathObject> pathObjects;
	
	private static MeasurementList ml1;
	private static MeasurementList ml2;
	private static MeasurementList ml3;
	private static MeasurementList ml4;
	
	private static Function<PathObject, PathClass> fun1;
	private static Function<PathObject, PathClass> fun2;
	private static Function<PathObject, PathClass> fun3;
	private static Function<PathObject, PathClass> fun4;
	
	private static Collection<PathClass> pathClasses;

	private static SimpleClassifier<BufferedImage> sc1;
	private static SimpleClassifier<BufferedImage> sc2;
	private static SimpleClassifier<BufferedImage> sc3;
	private static SimpleClassifier<BufferedImage> sc4;
	private static SimpleClassifier<BufferedImage> sc5;
	
	private static ImageData<BufferedImage> id;
	
	@BeforeAll
	static public void init() {
		
		pathClass1 = PathClassFactory.getPathClass("TestClass1",  Color.RED.getRGB());
		pathClass2 = PathClassFactory.getPathClass("TestClass2",  Color.GREEN.getRGB());
		pathClass3 = PathClassFactory.getPathClass("TestClass3",  Color.BLUE.getRGB());
		pathClass4 = PathClassFactory.getPathClass("test*",  Color.BLUE.getRGB());
		pathClass5 = PathClassFactory.getPathClass("Ignore*", ColorTools.makeRGB(180, 180, 180));
		pathClassUnclassified = PathClassFactory.getPathClassUnclassified();
		
		ml1 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml2 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml3 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		ml4 = MeasurementListFactory.createMeasurementList(16, MeasurementList.MeasurementListType.GENERAL);
		
		// Adding measurement to list2 (all measurements)
		ml2.addMeasurement("intensityMeasurement1", 0.0);
		ml2.addMeasurement("intensityMeasurement2", 0.2);
		ml2.addMeasurement("intensityMeasurement3", 0.6);
		ml2.addMeasurement("intensityMeasurement4", -4.6);
		
		// Adding measurement to list3 (missing intensityMeasurement3)
		ml3.addMeasurement("intensityMeasurement1", -1.0);
		ml3.addMeasurement("intensityMeasurement2", 0.9999);
		ml3.addMeasurement("intensityMeasurement4", 0.999);
		
		// Adding measurement to list4 (missing intensityMeasurement4)
		ml4.addMeasurement("intensityMeasurement1", 0.2);
		ml4.addMeasurement("intensityMeasurement2", 0.3);
		ml4.addMeasurement("intensityMeasurement3", 0.5);
		
		po1 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()), pathClass1, ml1);
		po2 = PathObjects.createDetectionObject(ROIs.createEllipseROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()), pathClass2, ml2);
		po3 = PathObjects.createDetectionObject(ROIs.createLineROI(10, 20, ImagePlane.getDefaultPlane()), pathClass3, ml3);
		po4 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()), null, ml4);
		po5 = PathObjects.createAnnotationObject(ROIs.createEllipseROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()));
		po6 = PathObjects.createAnnotationObject(ROIs.createLineROI(10, 20, ImagePlane.getDefaultPlane()));
		po7 = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass4);
		po8 = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass5);
		po9 = PathObjects.createAnnotationObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClassUnclassified);
		
		pathObjects = Arrays.asList(po1, po2, po3, po4, po5, po6, po7, po8, po9);
		
		fun1 = new ClassifyByMeasurementFunction("intensityMeasurement1", 0.5, pathClass1, pathClass2, pathClass3);
		fun2 = new ClassifyByMeasurementFunction("intensityMeasurement2", 0.5, pathClass4, pathClass5, pathClassUnclassified);
		fun3 = new ClassifyByMeasurementFunction("intensityMeasurement3", 0.5, pathClass1, pathClass2, pathClass3);
		fun4 = new ClassifyByMeasurementFunction("intensityMeasurement4", 0.5, pathClass1, pathClass2, pathClass3);
		
		pathClasses = Arrays.asList(pathClass1, pathClass3, pathClass5, pathClassUnclassified);
		
		sc1 = new SimpleClassifier<>(PathObjectFilter.ANNOTATIONS, fun1, pathClasses);
		sc2 = new SimpleClassifier<>(PathObjectFilter.DETECTIONS, fun2, pathClasses);
		sc3 = new SimpleClassifier<>(PathObjectFilter.DETECTIONS_ALL, fun3, pathClasses);
		sc4 = new SimpleClassifier<>(PathObjectFilter.TILES, fun4, pathClasses);
		sc5 = new SimpleClassifier<>(PathObjectFilter.ANNOTATIONS, fun4, new ArrayList<>());
		
		id = new ImageData<>(null);
	}
	
	@Test
	public void test_getPathClasses() {
		assertEquals(Arrays.asList(pathClass1, pathClass3, pathClass5, pathClassUnclassified), sc1.getPathClasses());
		assertEquals(Arrays.asList(pathClass1, pathClass3, pathClass5, pathClassUnclassified), sc2.getPathClasses());
		assertEquals(Arrays.asList(pathClass1, pathClass3, pathClass5, pathClassUnclassified), sc3.getPathClasses());
		assertEquals(Arrays.asList(pathClass1, pathClass3, pathClass5, pathClassUnclassified), sc4.getPathClasses());
		assertEquals(Arrays.asList(), sc5.getPathClasses());
	}
	
	@Test
	public void test_classifyObjects() {
//		Assertions.assertThrows(NullPointerException.class, () -> sc1.classifyObjects(id, false));
//		Assertions.assertThrows(NullPointerException.class, () -> sc1.classifyObjects(id, true));
		
		sc1.classifyObjects(id, pathObjects, false);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(pathClass2, po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(pathClass4, po7.getPathClass());
		assertEquals(pathClass5, po8.getPathClass());
		assertEquals(null, po9.getPathClass());

		sc1.classifyObjects(id, pathObjects, true);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(pathClass2, po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(null, po7.getPathClass());
		assertEquals(null, po8.getPathClass());
		assertEquals(null, po9.getPathClass());

		// TODO: Fix inconsistencies between SimpleClassifier.getPathClasses(), which will give you whatever
		// TODO: we gave in the constructor, against what's actually going to be used as PathClasses (which are 
		// TODO: defined by the Function<> variable)
		sc2.classifyObjects(id, pathObjects, false);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(pathClass2, po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(pathClass4, po7.getPathClass());
		assertEquals(pathClass5, po8.getPathClass());
		assertEquals(null, po9.getPathClass());
		
		
		// TODO
//		sc2.classifyObjects(id, pathObjects, true);
//
//		
//		
//		sc3.classifyObjects(id, pathObjects, false);
//		sc3.classifyObjects(id, pathObjects, true);
//		
//		sc4.classifyObjects(id, pathObjects, false);
//		sc4.classifyObjects(id, pathObjects, true);
//
//		sc5.classifyObjects(id, pathObjects, false);
//		sc5.classifyObjects(id, pathObjects, true);
	}
	
}
