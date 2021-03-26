package qupath.lib.classifiers.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import qupath.lib.classifiers.object.ObjectClassifiers.ClassifyByMeasurementFunction;
import qupath.lib.common.ColorTools;
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
public class TestCompositeClassifier {
	
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
	
	private static MeasurementList ml1;
	private static MeasurementList ml2;
	private static MeasurementList ml3;
	private static MeasurementList ml4;
	
	private static Function<PathObject, PathClass> fun1;
	private static Function<PathObject, PathClass> fun2;
	private static Function<PathObject, PathClass> fun3;
	private static Function<PathObject, PathClass> fun4;

	// TODO: Create tests with OpenCVMLClassifiers (not only SimpleClassifiers)
	private static SimpleClassifier<BufferedImage> sc1;
	private static SimpleClassifier<BufferedImage> sc2;
	private static SimpleClassifier<BufferedImage> sc3;
	private static SimpleClassifier<BufferedImage> sc4;
	
	private static CompositeClassifier<BufferedImage> cc1;
	private static CompositeClassifier<BufferedImage> cc2;
	private static CompositeClassifier<BufferedImage> cc3;
	private static CompositeClassifier<BufferedImage> cc4;
	private static CompositeClassifier<BufferedImage> cc5;
	
	@BeforeEach
	public void init() {
		
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
		po5 = PathObjects.createDetectionObject(ROIs.createEllipseROI(0, 0, 10, 10, ImagePlane.getDefaultPlane()));
		po6 = PathObjects.createDetectionObject(ROIs.createLineROI(10, 20, ImagePlane.getDefaultPlane()));
		po7 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass4);
		po8 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClass5);
		po9 = PathObjects.createDetectionObject(ROIs.createRectangleROI(0, 0, 5, 5, ImagePlane.getDefaultPlane()), pathClassUnclassified);
		
		fun1 = new ClassifyByMeasurementFunction("intensityMeasurement1", 0.5, pathClass1, pathClass2, pathClass3);
		fun2 = new ClassifyByMeasurementFunction("intensityMeasurement2", 0.5, pathClass4, pathClass5, pathClassUnclassified);
		fun3 = new ClassifyByMeasurementFunction("intensityMeasurement3", 0.5, pathClass1, pathClass2, pathClass3);
		fun4 = new ClassifyByMeasurementFunction("intensityMeasurement4", 0.5, pathClass1, pathClass2, pathClass3);
		
		sc1 = new SimpleClassifier<>(PathObjectFilter.DETECTIONS_ALL, fun1, Arrays.asList(pathClass1, pathClass2, pathClass3));
		sc2 = new SimpleClassifier<>(PathObjectFilter.DETECTIONS_ALL, fun2, Arrays.asList(pathClass4, pathClass5, pathClassUnclassified));
		sc3 = new SimpleClassifier<>(PathObjectFilter.DETECTIONS_ALL, fun3, Arrays.asList(pathClass1, pathClass4, null));
		sc4 = new SimpleClassifier<>(PathObjectFilter.ANNOTATIONS, fun4, Arrays.asList(pathClass1, pathClass2, pathClass3));
		
		cc1 = new CompositeClassifier<>(Arrays.asList(sc1));
		cc2 = new CompositeClassifier<>(Arrays.asList(sc2));
		cc3 = new CompositeClassifier<>(Arrays.asList(sc3));
		cc4 = new CompositeClassifier<>(Arrays.asList(sc1, sc2, sc3, sc4));
		cc5 = new CompositeClassifier<>(Arrays.asList());
	}
	
	@Test
	public void test_getPathClasses() {
		// First composite classifier
		assertTrue(Arrays.asList(pathClass1, pathClass2, pathClass3).containsAll(cc1.getPathClasses()));
		assertTrue(cc1.getPathClasses().containsAll(Arrays.asList(pathClass1, pathClass2, pathClass3)));

		// Second composite classifier
		assertTrue(Arrays.asList(pathClass4, pathClass5, pathClassUnclassified).containsAll(cc2.getPathClasses()));
		assertTrue(cc2.getPathClasses().containsAll(Arrays.asList(pathClass4, pathClass5, pathClassUnclassified)));

		// Third composite classifier
		assertTrue(Arrays.asList(pathClass1, pathClass4, null).containsAll(cc3.getPathClasses()));
		assertTrue(cc3.getPathClasses().containsAll(Arrays.asList(pathClass1, pathClass4, null)));
		
		// Fourth composite classifier
		assertTrue(Arrays.asList(pathClass1, pathClass2, pathClass3, pathClass4, pathClass5, pathClassUnclassified, null).containsAll(cc4.getPathClasses()));
		assertTrue(cc4.getPathClasses().containsAll(Arrays.asList(pathClass1, pathClass2, pathClass3, pathClass4, pathClass5, pathClassUnclassified)));

		// Fifth composite classifier
		assertTrue(cc5.getPathClasses().isEmpty());
	}
	
	@Test
	public void test_createMap() {
		Map<PathObject, PathClass> map1 = new HashMap<>();
		Map<PathObject, PathClass> map2 = new HashMap<>();
		Map<PathObject, PathClass> map3 = new HashMap<>();
		Map<PathObject, PathClass> map4 = new HashMap<>();
		Map<PathObject, PathClass> map5 = new HashMap<>();
		
		map1.put(po1, pathClass1);
		map1.put(po2, pathClass2);
		map1.put(po3, pathClass3);
		map1.put(po4, null);

		map2.put(po5, null);
		map2.put(po6, null);
		map2.put(po7, pathClass4);
		map2.put(po8, pathClass5);
		map2.put(po9, null);

		map4.put(po1, pathClass1);
		map4.put(po2, pathClass2);
		map4.put(po4, null);
		
		map5.put(po1, pathClass1);
		map5.put(po2, pathClass2);

		var mapCreated1 = CompositeClassifier.createMap(Arrays.asList(po1, po2, po3, po4));
		var mapCreated2 = CompositeClassifier.createMap(Arrays.asList(po5, po6, po7, po8, po9));
		var mapCreated3 = CompositeClassifier.createMap(Arrays.asList());
		var mapCreated4 = CompositeClassifier.createMap(Arrays.asList(po1, po2, null, po4));
		var mapCreated5 = CompositeClassifier.createMap(Arrays.asList(po1, po1, po2, po1));
		
		assertEquals(map1, mapCreated1);
		assertEquals(map2, mapCreated2);
		assertEquals(map3, mapCreated3);
		assertEquals(map4, mapCreated4);
		assertEquals(map5, mapCreated5);
	}
	
	@Test
	public void test_resetClassifications() {
		Map<PathObject, PathClass> map1 = new HashMap<>();
		Map<PathObject, PathClass> map2 = new HashMap<>();
		
		map1.put(po1, null);
		map1.put(po2, pathClass3);
		map1.put(po3, pathClassUnclassified);
		map1.put(po4, pathClass2);

		map2.put(po5, pathClass4);
		map2.put(po6, pathClass5);
		map2.put(po7, null);
		map2.put(po8, pathClass1);
		map2.put(po9, pathClassUnclassified);
		
		CompositeClassifier.resetClassifications(Arrays.asList(po1, po2, po3, po4), map1);
		assertEquals(null, po1.getPathClass());
		assertEquals(pathClass3, po2.getPathClass());
		assertEquals(null, po3.getPathClass());
		assertEquals(pathClass2, po4.getPathClass());
		
		CompositeClassifier.resetClassifications(Arrays.asList(po1, po2, po3, po4), map2);
		assertEquals(null, po1.getPathClass());
		assertEquals(null, po2.getPathClass());
		assertEquals(null, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		
		// Reset PathClasses manually
		po1.setPathClass(pathClass1);
		po2.setPathClass(pathClass2);
		po3.setPathClass(pathClass3);
		po4.setPathClass(null);
		po5.setPathClass(null);
		po6.setPathClass(null);
		po7.setPathClass(pathClass4);
		po8.setPathClass(pathClass5);
		po9.setPathClass(pathClassUnclassified);		
		
		CompositeClassifier.resetClassifications(Arrays.asList(), map1);
		CompositeClassifier.resetClassifications(Arrays.asList(), map2);
		assertEquals(pathClass1, po1.getPathClass());
		assertEquals(pathClass2, po2.getPathClass());
		assertEquals(pathClass3, po3.getPathClass());
		assertEquals(null, po4.getPathClass());
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(pathClass4, po7.getPathClass());
		assertEquals(pathClass5, po8.getPathClass());
		assertEquals(null, po9.getPathClass());
		
		CompositeClassifier.resetClassifications(Arrays.asList(po5, po6, po7, po8, po9), map1);
		assertEquals(null, po5.getPathClass());
		assertEquals(null, po6.getPathClass());
		assertEquals(null, po7.getPathClass());
		assertEquals(null, po8.getPathClass());
		assertEquals(null, po9.getPathClass());
		
		CompositeClassifier.resetClassifications(Arrays.asList(po5, po6, po7, po8, po9), map2);
		assertEquals(pathClass4, po5.getPathClass());
		assertEquals(pathClass5, po6.getPathClass());
		assertEquals(null, po7.getPathClass());
		assertEquals(pathClass1, po8.getPathClass());
		assertEquals(null, po9.getPathClass());
	}

}
