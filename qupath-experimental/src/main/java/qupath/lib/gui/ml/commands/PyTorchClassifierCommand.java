package qupath.lib.gui.ml.commands;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TreeMap;
import java.util.WeakHashMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.ml.PixelClassificationOverlay;
import qupath.lib.gui.ml.PixelClassifierPane;
import qupath.lib.gui.ml.PixelClassifierPane.ClassificationResolution;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.PathOverlay;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.io.GsonTools;
import qupath.opencv.ml.pixel.PixelClassifiers;
import qupath.opencv.ml.pixel.PyTorchPixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.classifiers.pixel.PixelClassifierMetadata;



/**
 * Command to apply a pre-trained PyTorch pixel classifier to an image, via Flask.
 * <p>
 * TODO: This command is unfinished!
 *
 * @author
 *
 */
public class PyTorchClassifierCommand implements PathCommand {
	private QuPathGUI qupath;

	private Stage stage;
	private String title = "Create PyTorch Classifier";
	private String flaskAddress;
	
	private static final Logger logger = LoggerFactory.getLogger(PixelClassifierCommand.class);

	/**
	 * Constructor.
	 * @param qupath
	 */
	public PyTorchClassifierCommand(QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		var viewer = qupath.getViewer();

		var imageData = viewer.getImageData();
		if (imageData == null) {
			Dialogs.showErrorMessage(title, "You need an image to run this command!");
			return;
		}

		var project = qupath.getProject();
		if (project == null) {
			Dialogs.showErrorMessage(title, "You need a project open to run this command!");
			return;
		}


		if (stage == null || !stage.isShowing())
			showGUI();
		else
			stage.toFront();
	}

	private ComboBox<ClassificationResolution> comboResolutions = new ComboBox<>();
	private ReadOnlyObjectProperty<ClassificationResolution> selectedResolution = comboResolutions.getSelectionModel().selectedItemProperty();

	private ComboBox<String> listOfAvailableModels= new ComboBox<>();
	private Map<String, PixelClassifier> availableClassifiers = new TreeMap<String, PixelClassifier>();

	private ObjectProperty<PixelClassificationOverlay> selectedOverlay = new SimpleObjectProperty<>();
	private Map<QuPathViewer, PathOverlay> map = new WeakHashMap<>();

	private void showGUI() {

		var pane = new GridPane();

		flaskAddress = Dialogs.showInputDialog("Flask Server", "URL", "");
		if (!isFlaskReachable(flaskAddress) && (flaskAddress != null)) {
			Dialogs.showErrorMessage(title, "Could not reach Flask server!");
			return;
		} else if (flaskAddress == null) return;

		availableClassifiers = getAvailableModels(flaskAddress);
		listOfAvailableModels.getItems().setAll(availableClassifiers.keySet());



		int row = 0;

		Label resolutionLabel = new Label("Resolution");
		resolutionLabel.setLabelFor(comboResolutions);
		PaneTools.addGridRow(pane, row++, 0, "Select image resolution to threshold", resolutionLabel, comboResolutions, comboResolutions);


		Label modelLabel = new Label("Models");
		modelLabel.setLabelFor(listOfAvailableModels);
		PaneTools.addGridRow(pane, row++, 0, "Select classification for pixels above the thresholds", modelLabel, listOfAvailableModels);


		selectedResolution.addListener((v, o, n) -> {
			if (listOfAvailableModels.getValue() != null){
				PixelClassifier classifier = availableClassifiers.get(listOfAvailableModels.getValue());
				if (sendClassifierChoiceToFlask(listOfAvailableModels.getValue())) updateClassification(classifier);
				//else Dialogs.showErrorMessage(title, "Error loading the classifier!");
			}
		});

		listOfAvailableModels.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> {
			if (listOfAvailableModels.getValue() != null){
				PixelClassifier classifier = availableClassifiers.get(listOfAvailableModels.getValue());
				if (sendClassifierChoiceToFlask(listOfAvailableModels.getValue())) updateClassification(classifier);
				//else Dialogs.showErrorMessage(title, "Error loading the classifier!");
			}
		});



		pane.setVgap(5.0);
		pane.setHgap(5.0);
		pane.setPadding(new Insets(10.0));

		PaneTools.setMaxWidth(Double.MAX_VALUE, comboResolutions, listOfAvailableModels);
		PaneTools.setFillWidth(Boolean.TRUE, comboResolutions, listOfAvailableModels);

		updateGUI();

		stage = new Stage();
		stage.setTitle("Select classifier");
		stage.initOwner(qupath.getStage());
		stage.setScene(new Scene(pane));
		//stage.setAlwaysOnTop(true);
		stage.show();

		stage.sizeToScene();

		stage.setOnHiding(e -> {
			for (var entry : map.entrySet()) {
				if (entry.getKey().getCustomPixelLayerOverlay() == entry.getValue())
					entry.getKey().resetCustomPixelLayerOverlay();
					selectedOverlay.set(null);
					PixelClassificationImageServer.setPixelLayer(entry.getKey().getImageData(), null);
			}
		});
	}


	/**
	 * Tells Flask API which classifier will be used
	 * @param classifierName
	 * @return boolean
	 */
	private boolean sendClassifierChoiceToFlask(String classifierName) {
		StringBuffer response = new StringBuffer();
	    try {
		    URL obj = new URL(flaskAddress + "setModel");
		    HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		    conn.setRequestMethod("POST");
		    conn.setDoOutput(true);
		    conn.setDoInput(true);
		    DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());

		    // Send classifier's name to Flask
		    outputStream.write(classifierName.getBytes());
		    outputStream.flush();
		    outputStream.close();

		    // Get response
		    BufferedReader responseBuffer = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    String inputLine;
			while ((inputLine = responseBuffer.readLine()) != null) {response.append(inputLine);}
			responseBuffer.close();

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	    if (response.toString().equals("OK")) return true;
	    else return false;
	}

	private void updateGUI() {

		var viewer = qupath.getViewer();
		var imageData = viewer.getImageData();


		comboResolutions.getItems().setAll(PixelClassifierPane.getDefaultResolutions(imageData, selectedResolution.get()));
		if (selectedResolution.get() == null)
			comboResolutions.getSelectionModel().selectLast();



	}

	private void updateClassification(PixelClassifier classifier) {
		var viewer = qupath.getViewer();
		if (viewer == null)
			return;

		var imageData = viewer.getImageData();
		if (imageData == null) {
			viewer.resetCustomPixelLayerOverlay();
			selectedOverlay.set(null);
			return;
		}

		var resolution = selectedResolution.get();
		if (resolution == null)
			return;


		if (resolution.getPixelCalibration() != classifier.getMetadata().getInputResolution()) {
			PixelClassifierMetadata newMetadata= new PixelClassifierMetadata.Builder().
					setMetadata(classifier.getMetadata())
					.inputResolution(resolution.getPixelCalibration())
					.inputChannels(qupath.getViewer().getServer())
					.setFlaskAddress(flaskAddress)
					.build();

			classifier = PixelClassifiers.createPyTorchClassifier(
					resolution.getPixelCalibration(),
					newMetadata);
		}




		//PixelClassificationImageServer server = new PixelClassificationImageServer(imageData, classifier);

		var overlay = PixelClassificationOverlay.createPixelClassificationOverlay(viewer, classifier);
		overlay.setLivePrediction(true);
		viewer.setCustomPixelLayerOverlay(overlay);
		selectedOverlay.set(overlay);
		map.put(viewer, overlay);
		imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, imageData.getHierarchy().getAnnotationObjects());
	}


	/**
	 * Check if Flask server is reachable.
	 * @param url
	 * @return boolean
	 */
	public static boolean isFlaskReachable(String url) {
		try {
			URL obj = new URL(url + "checkStatus");
		    HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		    conn.setRequestMethod("GET");
		    conn.setDoInput(true);
		    if (conn.getResponseCode() == 200) return true;

		} catch (Exception ex) {
			return false;
		}
		return false;
	}


	/**
	 * Call Flask and return a list of its available models
	 * @param String the url for the Flask API
	 * @return List<PixelClassifier> The models available
	 */
	private Map<String, PixelClassifier> getAvailableModels(String url) {
		Map<String, PixelClassifier> classifierMap = new TreeMap<String, PixelClassifier>();	// Thread Safe

		try {
			URL obj = new URL(url + "getAvailableModels");
		    HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
		    conn.setRequestMethod("POST");
		    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
		    conn.setRequestProperty("Accept", "application/json");
		    conn.setDoInput(true);

		    // Get list of available models (from the' models' directory)
		    BufferedReader responseBuffer = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		    String inputLine;
		    StringBuilder sb = new StringBuilder();
		    while ((inputLine = responseBuffer.readLine()) != null) {sb.append(inputLine);}
		    responseBuffer.close();

		    // Create JsonObject to iterate over all the available classifiers
		    Gson gson = new GsonBuilder().setPrettyPrinting().create();
		    JsonElement jsonElem = gson.fromJson(sb.toString(), JsonElement.class);
		    JsonObject jsonObj = jsonElem.getAsJsonObject();

		    // Create entry and put in Map
		    for (Map.Entry<String,JsonElement> entry: jsonObj.entrySet()) {
		    	try {
		    		PyTorchPixelClassifier classifier = GsonTools.getInstance().fromJson(entry.getValue().toString(), PyTorchPixelClassifier.class);
			    	classifierMap.put(getName(entry), classifier);
		    	} catch (Exception ex) {
		    		logger.error("Could not load " + entry.getValue());
		    	}
		    }

		} catch (Exception ex) {
			return  Collections.singletonMap(null, null);
		}

		return classifierMap;
	}


	/**
	 * Returns the name of the classifier, without extension
	 * @param Entry<String, JsonElement>
	 * @return
	 */
	private String getName(Entry<String, JsonElement> entry) {
		return entry.getKey().substring(0, entry.getKey().lastIndexOf('.'));
	}
}
