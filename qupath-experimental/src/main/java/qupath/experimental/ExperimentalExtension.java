package qupath.experimental;

import org.bytedeco.openblas.global.openblas;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.align.InteractiveImageAlignmentCommand;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.opencv.ml.pixel.features.ColorTransforms;
import qupath.lib.gui.ml.commands.CreateRegionAnnotationsCommand;
import qupath.lib.gui.ml.commands.ExportTrainingRegionsCommand;
import qupath.lib.gui.ml.commands.ObjectClassifierCommand;
import qupath.lib.gui.ml.commands.PixelClassifierLoadCommand;
import qupath.lib.gui.ml.commands.PixelClassifierCommand;
import qupath.lib.gui.ml.commands.SimpleThresholdCommand;
import qupath.lib.gui.ml.commands.SplitProjectTrainingCommand;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.io.GsonTools;
import qupath.opencv.ml.objects.features.FeatureExtractors;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ml.pixel.features.FeatureCalculators;

/**
 * Extension to make more experimental commands present in the GUI.
 */
public class ExperimentalExtension implements QuPathExtension {
	
	static {
		GsonTools.getDefaultBuilder()
			.registerTypeAdapterFactory(PixelClassifiers.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureCalculators.getTypeAdapterFactory())
			.registerTypeAdapterFactory(FeatureExtractors.getTypeAdapterFactory())
			.registerTypeAdapter(ColorTransforms.ColorTransform.class, new ColorTransforms.ColorTransformTypeAdapter());
	}
	
    @Override
    public void installExtension(QuPathGUI qupath) {
    	
    	// TODO: Check if openblas multithreading continues to have trouble with Mac/Linux
    	if (!GeneralTools.isWindows())
    		openblas.blas_set_num_threads(1);
    	
//		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifier.class);
//		PixelClassifiers.PixelClassifierTypeAdapterFactory.registerSubtype(OpenCVPixelClassifierDNN.class);
    	FeatureCalculators.initialize();
    	
    	MenuTools.addMenuItems(
                qupath.getMenu("Classify>Pixel classification", true),
                QuPathGUI.createCommandAction(new PixelClassifierCommand(), "Train pixel classifier (experimental)", null, new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN)),
                QuPathGUI.createCommandAction(new PixelClassifierLoadCommand(qupath), "Load pixel classifier (experimental)"),
                QuPathGUI.createCommandAction(new SimpleThresholdCommand(qupath), "Create simple thresholder (experimental)"),
                QuPathGUI.createCommandAction(new ObjectClassifierCommand(qupath), "Train detection classifier (experimental)")
        );
    	MenuTools.addMenuItems(
                qupath.getMenu("Analyze", true),
                QuPathGUI.createCommandAction(new InteractiveImageAlignmentCommand(qupath), "Interactive image alignment (experimental)")
        );
        
    	MenuTools.addMenuItems(
				qupath.getMenu("Extensions>AI", true),
				QuPathGUI.createCommandAction(new SplitProjectTrainingCommand(qupath), "Split project train/validation/test"),
				QuPathGUI.createCommandAction(new CreateRegionAnnotationsCommand(qupath), "Create region annotations"),
				QuPathGUI.createCommandAction(new ExportTrainingRegionsCommand(qupath), "Export training regions")
				);

    }

    @Override
    public String getName() {
        return "Experimental commands";
    }

    @Override
    public String getDescription() {
        return "New features that are still being developed or tested";
    }
}