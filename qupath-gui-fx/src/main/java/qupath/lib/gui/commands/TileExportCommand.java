package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferDouble;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.DoubleBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;


import org.bytedeco.javacpp.*;
import org.bytedeco.tensorflow.*;
import static org.bytedeco.tensorflow.global.tensorflow.*;
import org.bytedeco.onnxruntime.AllocatorWithDefaultOptions;
import org.bytedeco.onnxruntime.Env;
import org.bytedeco.onnxruntime.Session;
import org.bytedeco.onnxruntime.SessionOptions;
import org.bytedeco.onnxruntime.global.onnxruntime;
import org.controlsfx.control.ListSelectionView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.input.MouseEvent;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.TableColumn.CellDataFeatures;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.LabeledImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.writers.TileExporter;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Dialog box to export measurements
 * 
 * @author Melvin Gelbard
 *
 */

// TODO: Save current image?
public class TileExportCommand implements PathCommand {
	
	private QuPathGUI qupath;
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	
	private Dialog<ButtonType> dialog = null;
	private Project<BufferedImage> project;
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	private List<ImageChannel> selectedChannels = new ArrayList<>();
	
	// Functionality variables
	private Map<Set<ImageChannel>, List<String>> mapChannels = new HashMap<>();
	private Map<PixelCalibration, List<String>> mapPixelCalibrations = new HashMap<>();
	
	// GUI
	private TextField outputText = new TextField();
	private ComboBox<String> comboPathObject = new ComboBox<>();
	private ComboBox<String> comboImageExtension = new ComboBox<>();
	private ComboBox<String> comboLabelExtension = new ComboBox<>();
	private ComboBox<String> comboChannels = new ComboBox<>();
	private TextField tileWidthText = new TextField();
	private TextField tileHeightText = new TextField();
	private TextField overlapText = new TextField();
	private Label labelSameImageWarning = new Label();
	
	private ButtonType btnExport = new ButtonType("Export", ButtonData.OK_DONE);
	
	public TileExportCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		createAndShowDialog();
	}
	
	private void createAndShowDialog() {
		project = qupath.getProject();
		if (project == null) {
			Dialogs.showErrorMessage("No open project", "Open a project to run this command!");
			return;
		}
		
		
		onnxruntime onnx = new onnxruntime();
		//Scope scope = Scope.NewRootScope();
		
		
		// Check how the project is structured. If the entries have different 
		// parameters (e.g. pixel size or channels), then the export should
		// not include these entries at the same time.
		String projectPath = project.getPath().toString();
		updateChannelAndPixelCalibrationMap2(projectPath);


		BorderPane mainPane = new BorderPane();
		
		BorderPane imageEntryPane = new BorderPane();
		GridPane optionPane = new GridPane();
		
		List<Control> controls = new ArrayList<>();
		
		
		// TOP PANE (SELECT PROJECT ENTRIES FOR EXPORT)
		ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView = new ListSelectionView<>();
		List<ProjectImageEntry<BufferedImage>> currentImages = new ArrayList<>();
		project = qupath.getProject();
		listSelectionView.getSourceItems().setAll(project.getImageList());
		if (listSelectionView.getSourceItems().containsAll(previousImages)) {
			listSelectionView.getSourceItems().removeAll(previousImages);
			listSelectionView.getTargetItems().addAll(previousImages);
		}
		
		
		listSelectionView.setCellFactory(new Callback<ListView<ProjectImageEntry<BufferedImage>>,
	            ListCell<ProjectImageEntry<BufferedImage>>>() {
            @Override 
            public ListCell<ProjectImageEntry<BufferedImage>> call(ListView<ProjectImageEntry<BufferedImage>> list) {
                return new ListCell<ProjectImageEntry<BufferedImage>>() {
                	private Tooltip tooltip = new Tooltip();
                	@Override
            		protected void updateItem(ProjectImageEntry<BufferedImage> item, boolean empty) {
                		super.updateItem(item, empty);
                		if (item == null || empty) {
                			setText(null);
                			setGraphic(null);
                			setTooltip(null);
                			return;
                		}
                		setText(item.getImageName());
                		setGraphic(null);
                		tooltip.setText(item.toString());
            			setTooltip(tooltip);
            			setStyle("-fx-text-fill: black;");
            			
            			// TODO: Add more colours
            			String[] colours = new String[] {"green", "blue", "brown", "orange", "purple", "cyan", "darkgoldenrod", "darkmagenta", "darkturquoise"};
            			var similarEntries = getSimilarEntries(listSelectionView.getTargetItems());
            			if (similarEntries.size() > 1) {
            				int index = 0;
            				for (var values: similarEntries) {
            					if (values.contains(item)) {
            						setStyle("-fx-text-fill: " + colours[index] + ";");
            						break;
            					}
            					index++;
            				}
            				
            				labelSameImageWarning.setText("The selected images cannot be exported as\na batch. "
            						+ "Please choose images with similar\n"
            						+ " characteristics (colours).");
            				labelSameImageWarning.setVisible(true);
            				for (int field = 0; field < controls.size(); field++)
            					controls.get(field).setDisable(true);
            				dialog.getDialogPane().lookupButton(btnExport).setDisable(true);
            			} else {
            				labelSameImageWarning.setVisible(false);
            				if (validForm(controls, listSelectionView))
            					dialog.getDialogPane().lookupButton(btnExport).setDisable(false);
            				for (int field = 0; field < controls.size(); field++)
            					controls.get(field).setDisable(false);
            				controls.get(6).setDisable(true);
            				if (listSelectionView.getTargetItems().isEmpty())
                				dialog.getDialogPane().lookupButton(btnExport).setDisable(true);
            				for (var current : currentImages) {
            					if (listSelectionView.getTargetItems().contains(current)) {
                					labelSameImageWarning.setText(
                							"A selected image is open in the viewer!\n"
                							+ "Use 'File>Reload data' to see changes.");
                					labelSameImageWarning.setVisible(true);
                				}
            				}
            			}
                	}
                };
            }
        });
		
		
		// Create search bar and add listener to filter ListSelectionView
		TextField tfFilter = new TextField();
		tfFilter.setPromptText("Search entries");
		CheckBox cbWithData = new CheckBox("With data file only");
		tfFilter.setTooltip(new Tooltip("Enter text to filter image list"));
		cbWithData.setTooltip(new Tooltip("Filter image list to only images with associated data files"));
		tfFilter.textProperty().addListener((v, o, n) -> updateImageList(listSelectionView, project, n, cbWithData.selectedProperty().get()));
		cbWithData.selectedProperty().addListener((v, o, n) -> updateImageList(listSelectionView, project, tfFilter.getText(), cbWithData.selectedProperty().get()));
		
		GridPane paneFooter = new GridPane();

		paneFooter.setMaxWidth(Double.MAX_VALUE);
		cbWithData.setMaxWidth(Double.MAX_VALUE);
		paneFooter.add(tfFilter, 0, 0);
		paneFooter.add(cbWithData, 0, 1);
				
		PaneTools.setHGrowPriority(Priority.ALWAYS, tfFilter, cbWithData);
		PaneTools.setFillWidth(Boolean.TRUE, tfFilter, cbWithData);
		cbWithData.setMinWidth(CheckBox.USE_PREF_SIZE);
		paneFooter.setVgap(5);
		listSelectionView.setSourceFooter(paneFooter);

		// TARGET FOOTER PANE
		labelSameImageWarning = new Label(
				"A selected image is open in the viewer!\n"
				+ "Use 'File>Reload data' to see changes.");
		
		Label labelSelected = new Label();
		labelSelected.setTextAlignment(TextAlignment.CENTER);
		labelSelected.setAlignment(Pos.CENTER);
		labelSelected.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(labelSelected, Priority.ALWAYS);
		GridPane.setFillWidth(labelSelected, Boolean.TRUE);

		
		var paneSelected = new GridPane();
		PaneTools.addGridRow(paneSelected, 0, 0, "Selected images", labelSelected);

		// Get the current images that are open
		currentImages.addAll(qupath.getViewers().stream()
				.map(v -> {
					var imageData = v.getImageData();
					return imageData == null ? null : qupath.getProject().getEntry(imageData);
				})
				.filter(d -> d != null)
				.collect(Collectors.toList()));
		// Create a warning label to display if we need to
		labelSameImageWarning.setTextFill(Color.RED);
		labelSameImageWarning.setMaxWidth(Double.MAX_VALUE);
		labelSameImageWarning.setMinHeight(Label.USE_PREF_SIZE);
		labelSameImageWarning.setTextAlignment(TextAlignment.CENTER);
		labelSameImageWarning.setAlignment(Pos.CENTER);
		labelSameImageWarning.setVisible(false);
		PaneTools.setHGrowPriority(Priority.ALWAYS, labelSameImageWarning);
		PaneTools.setFillWidth(Boolean.TRUE, labelSameImageWarning);
		PaneTools.addGridRow(paneSelected, 1, 0,
				"'Run For Project' will save the data file for any image that is open - you will need to reopen the image to see the changes",
				labelSameImageWarning);
		listSelectionView.setTargetFooter(paneSelected);
		
		
		// BOTTOM PANE (OPTIONS)
		int row = 0;
		
		// Export params
		Label titleExportParam = new Label("Export parameters");
		titleExportParam.setStyle("-fx-font-weight: bold");
		PaneTools.addGridRow(optionPane, row++, 0, "", titleExportParam);
		
		Label pathOutputLabel = new Label("Output folder");
		var btnChooseFile = new Button("Choose directory");
		btnChooseFile.setOnAction(e -> {
			File pathOut = QuPathGUI.getSharedDialogHelper().promptForDirectory(null);
			if (pathOut != null) {
				if (pathOut.isFile())
					pathOut = new File(pathOut.getParent());
				outputText.setText(pathOut.getAbsolutePath());
				if (validForm(controls, listSelectionView))
					dialog.getDialogPane().lookupButton(btnExport).setDisable(false);
			}
		});
		
		outputText.textProperty().addListener((v, o, n) -> {
			if (!n.isEmpty() && validForm(controls, listSelectionView))
				dialog.getDialogPane().lookupButton(btnExport).setDisable(false);
			else
				dialog.getDialogPane().lookupButton(btnExport).setDisable(true);
		});
		
		//PaneTools.addGridRow(optionPane, row++, 0, "Enter the output file path (with format extension)", pathOutputLabel, outputText, btnChooseFile);
		optionPane.add(pathOutputLabel, 0, row);
		optionPane.add(outputText, 1, row);
		optionPane.add(btnChooseFile, 2, row++);
		pathOutputLabel.setLabelFor(outputText);
		
		Label pathObjectLabel = new Label("Objects to use");
		PaneTools.addGridRow(optionPane, row++, 0, "Choose to export either annotations or detections", pathObjectLabel, comboPathObject, comboPathObject, comboPathObject);
		pathObjectLabel.setLabelFor(comboPathObject);
		comboPathObject.getItems().setAll("Annotations", "Detections");
		comboPathObject.getSelectionModel().selectFirst();

		
		Label channelsLabel = new Label("Channels");
		comboChannels.getItems().setAll("All channels", "Custom channels");
		comboChannels.getSelectionModel().selectFirst();
		Button btnChooseChannels = new Button("Edit");
		btnChooseChannels.setOnAction(e -> {
			List<ImageChannel> tempAllChannels = new ArrayList<>(selectedChannels);
			List<ImageChannel> allChannels = new ArrayList<ImageChannel>();

			var selected = getTargetItems(listSelectionView);
			boolean found = false;
			for (int projectEntry = 0; projectEntry < selected.size(); projectEntry++) {
				for (Set<ImageChannel> setKey: mapChannels.keySet()) {
					String projectID = selected.get(projectEntry).getID();
					if (mapChannels.get(setKey).contains(projectID)) {
						allChannels.addAll(setKey);
						found = true;
						break;
					}
				}
				if (found)
					break;
			}
			
			ObservableList<ImageChannel> channelList = FXCollections.observableArrayList(allChannels);
			selectedChannels.addAll(allChannels);
			
			Dialog<ButtonType> dialogStage = new Dialog<>();
			TableView<ImageChannel> channelsTable = new TableView<>();
			TableColumn<ImageChannel, String> channelNumCol = new TableColumn<ImageChannel, String>("");
			TableColumn<ImageChannel, ImageChannel> channelNameCol = new TableColumn<ImageChannel, ImageChannel>("Name");
			TableColumn<ImageChannel, Boolean> channelCheckBoxCol = new TableColumn<ImageChannel, Boolean>("Selected");
			
			channelNumCol.setSortable(false);
			channelNumCol.setEditable(true);
			channelNumCol.setResizable(false);
			channelNumCol.setMaxWidth(30);
			channelNumCol.setStyle( "-fx-alignment: CENTER;");
			channelNumCol.setCellValueFactory(cellData -> {
				var index = cellData.getTableView().getItems().indexOf(cellData.getValue()) + "";
				return new ReadOnlyObjectWrapper<String>(index);
			});
			
			channelNameCol.setSortable(false);
			channelNameCol.setEditable(true);
			channelNameCol.setResizable(false);
			
			channelNameCol.setCellValueFactory(cellData -> {
				return new ReadOnlyObjectWrapper<ImageChannel>(cellData.getValue());
			});
			
			// Add name and colour of channel
			channelNameCol.setCellFactory(column -> {
			    return new TableCell<ImageChannel, ImageChannel>() {
			    	@Override
			        protected void updateItem(ImageChannel item, boolean empty) {
			    		super.updateItem(item, empty);
			            if (item == null || empty) {
			                setText(null);
			                setGraphic(null);
			                return;
			            }
			            setText(item.getName());
						Rectangle square = new Rectangle(0, 0, 10, 10);
						Integer rgb = item.getColor();
						if (rgb == null)
							square.setFill(Color.TRANSPARENT);
						else
							square.setFill(ColorToolsFX.getCachedColor(rgb));
						setGraphic(square);
			    	}
			    };
			   });
			
			
			
			
			// CheckBox column
			channelCheckBoxCol.setCellValueFactory(new Callback<CellDataFeatures<ImageChannel, Boolean>, ObservableValue<Boolean>>() {
			     @Override
				public ObservableValue<Boolean> call(CellDataFeatures<ImageChannel, Boolean> item) {
			    	 SimpleBooleanProperty property = new SimpleBooleanProperty(tempAllChannels.contains(item.getValue()));
			    	 property.addListener((v, o, n) -> {
			    		 selectedChannels.add(item.getValue());
		    			 channelsTable.refresh();
			    	 });
			    	 return property;
			     }
			  });
			
			channelCheckBoxCol.setCellFactory(column -> {
				CheckBoxTableCell<ImageChannel, Boolean> cell = new CheckBoxTableCell<>();
				cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
					if (event.isPopupTrigger())
						return;
					var tableView = cell.getTableView();
					ImageChannel channel = tableView.getSelectionModel().getSelectedItem();
					if (tempAllChannels.contains(channel)) {
						tempAllChannels.remove(channel);
						if (tempAllChannels.isEmpty())
							dialogStage.getDialogPane().lookupButton(ButtonType.OK).setDisable(true);
					} else {
						tempAllChannels.add(channel);
						dialogStage.getDialogPane().lookupButton(ButtonType.OK).setDisable(false);
					}
						
					tableView.refresh();
				});
				return cell;
			});
		
			channelCheckBoxCol.setSortable(false);
			channelCheckBoxCol.setEditable(true);
			channelCheckBoxCol.setResizable(false);
			
			
			channelsTable.setItems(channelList);
			channelsTable.getColumns().addAll(channelNumCol, channelNameCol, channelCheckBoxCol);
			
			BorderPane pane = new BorderPane();
			Button btnOk = new Button("Ok");
			Button btnCancel = new Button("Cancel");
			btnOk.setMaxWidth(Double.MAX_VALUE);
			btnCancel.setMaxWidth(Double.MAX_VALUE);

			pane.setTop(channelsTable);
			dialogStage.setHeaderText("Select channels to export");
			dialogStage.getDialogPane().setContent(pane);
			dialogStage.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
			Optional<ButtonType> result = dialogStage.showAndWait();
			
			if (result.orElse(ButtonType.OK) == ButtonType.OK) {
				selectedChannels.clear();
				selectedChannels.addAll(tempAllChannels);
				if (selectedChannels.size() != allChannels.size())
					comboChannels.getSelectionModel().clearAndSelect(1);
				else
					comboChannels.getSelectionModel().clearAndSelect(0);
			}
		});
		PaneTools.addGridRow(optionPane, row++, 0, "Specific channels to export", channelsLabel, comboChannels, btnChooseChannels);
		
		RadioButton downsampleRadio = new RadioButton("Downsample");
		downsampleRadio.setSelected(true);
		TextField downsampleText = new TextField();
		downsampleText.setText("1");
		downsampleText.setTextFormatter(new TextFormatter<>(c -> {
			String input = c.getControlNewText();
			if ((!input.matches("\\d+\\.?\\d*") || (input.length() > 7)) && (!input.isEmpty()))
				return null;
			return c;
		}));
		
		RadioButton pixelSizeRadio = new RadioButton("Pixel Size");
		pixelSizeRadio.setSelected(false);
		TextField pixelSizeText = new TextField();
		pixelSizeText.setDisable(true);
		pixelSizeText.setTextFormatter(new TextFormatter<>(c -> {
			String input = c.getControlNewText();
			if ((!input.matches("\\d+\\.?\\d*") || (input.length() > 7)) && (!input.isEmpty()))
				return null;
			return c;
		}));
		
		downsampleRadio.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
		    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					pixelSizeText.setDisable(true);
					pixelSizeRadio.setSelected(false);
					downsampleText.setDisable(false);
				} else if (!newValue && !pixelSizeRadio.isSelected())
					downsampleRadio.setSelected(true);
			}
			
		});
		
		pixelSizeRadio.selectedProperty().addListener(new ChangeListener<Boolean>() {
			@Override
		    public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
				if (newValue) {
					downsampleText.setDisable(true);
					downsampleRadio.setSelected(false);
					pixelSizeText.setDisable(false);
				} else if (!newValue && !downsampleRadio.isSelected())
					pixelSizeRadio.setSelected(true);
			}
		});

		
		PaneTools.addGridRow(optionPane, row++, 0, "Downsampling of the output images", downsampleRadio, downsampleText, pixelSizeRadio, pixelSizeText);
		
		Separator sep = new Separator();
		PaneTools.addGridRow(optionPane, row++, 0, "", sep, sep, sep, sep, sep, sep);
		
		
		Label titleTileParam = new Label("Tile parameters");
		titleTileParam.setStyle("-fx-font-weight: bold");
		PaneTools.addGridRow(optionPane, row++, 0, "", titleTileParam);
		
		Label imageExtensionLabel = new Label("Images extension");
		imageExtensionLabel.setLabelFor(comboImageExtension);
		comboImageExtension.getItems().setAll(".tif", ".png");
		comboImageExtension.getSelectionModel().selectFirst();
		Label labelExtensionLabel = new Label("Label extension");
		labelExtensionLabel.setLabelFor(comboLabelExtension);
		comboLabelExtension.getItems().setAll(".tif", ".png");
		comboLabelExtension.getSelectionModel().selectFirst();
		PaneTools.addGridRow(optionPane, row++, 0, "Image extension for the output images", imageExtensionLabel, comboImageExtension, labelExtensionLabel, comboLabelExtension);
		
		// Height
		Label tileHeightLabel = new Label("Tile height");
		tileHeightLabel.setLabelFor(tileHeightText);
		Label tileUnitLabel = new Label("px");
		tileHeightText.setText("256");
		
		// Width
		Label tileWidthLabel = new Label("Tile width");
		Label tileUnitLabel2 = new Label("px");
		tileWidthLabel.setLabelFor(tileWidthText);
		tileWidthText.setText("256");

		PaneTools.addGridRow(optionPane, row++, 0, "Enter the desired tile height", tileHeightLabel, tileHeightText, tileUnitLabel);
		PaneTools.addGridRow(optionPane, row++, 0, "Enter the desired tile width", tileWidthLabel, tileWidthText, tileUnitLabel2);
		
		// Overlap
		Label overlapLabel = new Label("Overlap");
		Label overlapUnitLabel = new Label("px");
		overlapLabel.setLabelFor(overlapText);
		overlapText.setText("0");
		PaneTools.addGridRow(optionPane, row++, 0, "Enter the desired overlap", overlapLabel, overlapText, overlapUnitLabel);
		
		// Include partial tiles
		CheckBox includePartialTiles = new CheckBox("Include partial tiles");
		PaneTools.addGridRow(optionPane, row++, 0, "Specify whether incomplete tiles at image boundaries should be included", includePartialTiles);
		
		// Run in background checkbox
		CheckBox runInBackground = new CheckBox("Run in background");
		PaneTools.addGridRow(optionPane, row++, 0, "Run the export in background", runInBackground);
		
		optionPane.setVgap(6.0);
		optionPane.setHgap(10.0);
		optionPane.setPadding(new Insets(10.0));
		
		dialog = new Dialog<>();
		dialog.getDialogPane().setMinHeight(400);
		dialog.getDialogPane().setMinWidth(600);
		dialog.setTitle("Export measurements");
		dialog.getDialogPane().getButtonTypes().addAll(btnExport, ButtonType.CANCEL);
		dialog.getDialogPane().lookupButton(btnExport).setDisable(true);
		dialog.getDialogPane().setContent(mainPane);		
		
		imageEntryPane.setCenter(listSelectionView)	;

		mainPane.setTop(imageEntryPane);
		mainPane.setBottom(optionPane);
		
		// Add all fields to control ArrayList to easily turn them on/off
		controls.addAll(Arrays.asList(	outputText,
										btnChooseFile,
										comboPathObject,
										comboChannels,
										btnChooseChannels,
										downsampleText,
										pixelSizeText,
										comboImageExtension,
										comboLabelExtension,
										tileHeightText,
										tileWidthText,
										overlapText,
										includePartialTiles,
										runInBackground
										));
		
		Optional<ButtonType> result = dialog.showAndWait();
		
		if (!result.isPresent() || result.get() != btnExport || result.get() == ButtonType.CANCEL)
			return;
		
		Map<String, String> parameters = new HashMap<>();
		parameters.put("height", tileHeightText.getText());
		parameters.put("width", tileWidthText.getText());
		parameters.put("pathOut", outputText.getText());
		parameters.put("downsample", downsampleText.getText());
		parameters.put("imageExtension", comboImageExtension.getSelectionModel().getSelectedItem());
		parameters.put("useDetections", comboPathObject.getSelectionModel().getSelectedItem());
		
		// TODO: Change next 2 lines
		List<PathClass> labelList = new ArrayList<PathClass>();
		//labelList.add();
		
		// TODO: handle user input for labels!!
		ExportTask worker = new ExportTask(listSelectionView.getTargetItems(), labelList, parameters);
		
		ProgressDialog progress = new ProgressDialog(worker);
		progress.setWidth(600);
		//progress.initOwner(dialog.getDialogPane().getScene().getWindow());
		progress.setTitle("Export measurements...");
		progress.getDialogPane().setHeaderText("Export image(s)");
		progress.getDialogPane().setGraphic(null);
		progress.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		progress.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, e -> {
			if (Dialogs.showYesNoDialog("Cancel export", "Are you sure you want to stop the export after the current image?")) {
				worker.quietCancel();
				progress.setHeaderText("Cancelling...");
//							worker.cancel(false);
				progress.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(true);
			}
			e.consume();
		});
		
		// Create & run task
		runningTask.set(qupath.createSingleThreadExecutor(this).submit(worker));
		progress.show();
	}
	
	/**
	 * Checks if the form is valid and can be used for export
	 * @param controls
	 * @return isValid
	 */
	private boolean validForm(List<Control> controls, ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView) {
		TextField outText = (TextField) controls.get(0);
		if (outText.getText().isEmpty())
			return false;
		if (getTargetItems(listSelectionView).isEmpty())
			return false;
		return true;
	}
	
	// TODO: How should we handle entries that have moved and URIs don't match anymore?
	private Map<Set<ImageChannel>, List<String>> updateChannelAndPixelCalibrationMap(String projectPath) {
		Map<Set<ImageChannel>, List<String>> mapChannels = new HashMap<>();
		
		try {
			JsonReader reader = new JsonReader(new FileReader(projectPath));
			JsonElement element = JsonParser.parseReader(reader);
			JsonObject obj = element.getAsJsonObject();
			
			var gson = GsonTools.getInstance();
			var something = gson.fromJson(element.toString(), qupath.getProject().getClass());
			
			
			JsonArray projectEntries = obj.get("images").getAsJsonArray();
            for (int i = 0; i < projectEntries.size(); i++){
            	try {
                    JsonObject projectEntry = projectEntries.get(i).getAsJsonObject();
                    String entryID = projectEntry.get("entryID").toString();
                    JsonObject serverBuilders = projectEntry.get("serverBuilder").getAsJsonObject();
                    
                    Set<ImageChannel> channelSet = new HashSet<ImageChannel>();
                    for (int j = 0; j < serverBuilders.size(); j++){
                        JsonObject metadata = serverBuilders.get("metadata").getAsJsonObject();
                        JsonArray channels = metadata.get("channels").getAsJsonArray();
                        
                        for (int channelNum = 0; channelNum < channels.size(); channelNum++) {
                        	channelSet.add(GsonTools.getInstance().fromJson(channels.get(channelNum), ImageChannel.class));
                        }
                    }
                    
                    if (mapChannels.containsKey(channelSet))
                    	mapChannels.get(channelSet).add(entryID);
                    else
                    	mapChannels.put(channelSet, new ArrayList<>(Arrays.asList(entryID)));
            	} catch (Exception e) {
            		logger.warn("Couldn't fetch info about: " + projectEntries.get(i));
            	}
            }
			
		} catch (FileNotFoundException ex) {
			ex.printStackTrace();
		}
		
		return mapChannels;
	}

	// TODO: How should we handle entries that have moved and URIs don't match anymore?
	// TODO: Sometimes same sets of channels, but the colours are different (e.g. "RED {255, 255, 174/255}")
	private void updateChannelAndPixelCalibrationMap2(String projectPath) {
		List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
		
		for (int i = 0; i < entries.size(); i++) {
			try {
				ImageServer<BufferedImage> server = entries.get(i).getServerBuilder().build();
				Set<ImageChannel> channelSet = new HashSet<ImageChannel>();
				String entryID = entries.get(i).getID();
				for (int channel = 0; channel < server.nChannels(); channel++) {
					channelSet.add(server.getChannel(channel));
				}
				
				// Store Set of ImageChannels
				if (mapChannels.containsKey(channelSet))
	            	mapChannels.get(channelSet).add(entryID);
	            else
	            	mapChannels.put(channelSet, new ArrayList<>(Arrays.asList(entryID)));
				
				// Store PixelCalibration
				PixelCalibration pixelCalibration = server.getPixelCalibration();
				if (mapPixelCalibrations.containsKey(pixelCalibration))
					mapPixelCalibrations.get(pixelCalibration).add(entryID);
				else
					mapPixelCalibrations.put(pixelCalibration, new ArrayList<>(Arrays.asList(entryID)));
				
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Returns a map with {@code Set<ImageChannel>} as keys and 
	 * a list of {@code ProjectImageEntry} as values.
	 * Each set of channels (keys) is paired with a list of 
	 * project entries (values) whose image is made out of 
	 * these exact channels.
	 * @param channelMap
	 * @param targetItems
	 * @return map
	 */
	private Collection<List<ProjectImageEntry<BufferedImage>>> getSimilarEntries(ObservableList<ProjectImageEntry<BufferedImage>> targetItems) {
		Map<Set<ImageChannel>, List<ProjectImageEntry<BufferedImage>>> tempMapChannels = new HashMap<>();
		Map<PixelCalibration, List<ProjectImageEntry<BufferedImage>>> tempMapPixelCalibrations = new HashMap<>();
		Map<List<Integer>, List<ProjectImageEntry<BufferedImage>>> out = new HashMap<>();
		
		for (ProjectImageEntry<BufferedImage> targetEntry: targetItems) {
			for (var channelEntrySet: mapChannels.entrySet()) {
				var channelKey = channelEntrySet.getKey();
				var channelValues = channelEntrySet.getValue();
				
				if (channelValues.contains(targetEntry.getID())) {
					for (var pixelEntrySet: mapPixelCalibrations.entrySet()) {
						var pixelKey = pixelEntrySet.getKey();
						var pixelValues = pixelEntrySet.getValue();
						
						if (pixelValues.contains(targetEntry.getID())) {
							// Update temp var for channels
							if (tempMapChannels.containsKey(channelKey)){
								tempMapChannels.get(channelKey).add(targetEntry);
							} else {
								tempMapChannels.put(channelKey, new ArrayList<>(Arrays.asList(targetEntry)));
							}
							
							// Update temp var for pixel calibrations
							if (tempMapPixelCalibrations.containsKey(pixelKey)){
								tempMapPixelCalibrations.get(pixelKey).add(targetEntry);
							} else {
								tempMapPixelCalibrations.put(pixelKey, new ArrayList<>(Arrays.asList(targetEntry)));
							}
						}
					}
				} else {
					continue;
				}
			}
		}
		
		
		// Iterate through the values (of type List<ProjectImageEntry>)
		for (var imageChan: tempMapChannels.values()) {
			int indexChan = 0;
			
			// Iterate through each element (of type ProjectImageEntry)
			for (var imageChanValue: imageChan) {
				int indexPix = 0;
				
				// Iterate through the values (of type List<ProjectImageEntry>)
				for (var imagePix: tempMapPixelCalibrations.values()) {
					if (imagePix.contains(imageChanValue)) {
						List<Integer> indices = new ArrayList<>(Arrays.asList(indexChan, indexPix));
						if (out.containsKey(indices)) {
							out.get(indices).add(imageChanValue);
						} else {
							out.put(indices, new ArrayList<ProjectImageEntry<BufferedImage>>(Arrays.asList(imageChanValue)));
						}
						break;
					}
					indexPix++;
				}
			}
			indexChan++;
		}
		return out.values();
	}

	private void updateImageList(final ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView, final Project<BufferedImage> project, final String filterText, final boolean withDataOnly) {
		String text = filterText.trim().toLowerCase();
		
		// Get an update source items list
		List<ProjectImageEntry<BufferedImage>> sourceItems = new ArrayList<>(project.getImageList());
		var targetItems = getTargetItems(listSelectionView);
		sourceItems.removeAll(targetItems);
		// Remove those without a data file, if necessary
		if (withDataOnly) {
			sourceItems.removeIf(p -> !p.hasImageData());
			targetItems.removeIf(p -> !p.hasImageData());
		}
		// Apply filter text
		if (text.length() > 0 && !sourceItems.isEmpty()) {
			Iterator<ProjectImageEntry<BufferedImage>> iter = sourceItems.iterator();
			while (iter.hasNext()) {
				if (!iter.next().getImageName().toLowerCase().contains(text))
					iter.remove();
			}
		}		
		
		if (getSourceItems(listSelectionView).equals(sourceItems))
			return;
		getSourceItems(listSelectionView).setAll(sourceItems);
	}
	
	/**
	 * We should just be able to call {@link ListSelectionView#getTargetItems()}, but in ControlsFX 11 there 
	 * is a bug that prevents this being correctly bound.
	 * @param <T>
	 * @param listSelectionView
	 * @return
	 */
	private static <T> ObservableList<T> getTargetItems(ListSelectionView<T> listSelectionView) {
		var skin = listSelectionView.getSkin();
		try {
			logger.debug("Attempting to access target list by reflection (required for controls-fx 11.0.0)");
			var method = skin.getClass().getMethod("getTargetListView");
			var view = (ListView<?>)method.invoke(skin);
			return (ObservableList<T>)view.getItems();
		} catch (Exception e) {
			logger.warn("Unable to access target list by reflection, sorry", e);
			return listSelectionView.getTargetItems();
		}
	}
	
	private static <T> ObservableList<T> getSourceItems(ListSelectionView<T> listSelectionView) {
		var skin = listSelectionView.getSkin();
		try {
			logger.debug("Attempting to access target list by reflection (required for controls-fx 11.0.0)");
			var method = skin.getClass().getMethod("getSourceListView");
			var view = (ListView<?>)method.invoke(skin);
			return (ObservableList<T>)view.getItems();
		} catch (Exception e) {
			logger.warn("Unable to access target list by reflection, sorry", e);
			return listSelectionView.getSourceItems();
		}
	}
	
	
	class ExportTask extends Task<Void> {
		
		private boolean quietCancel = false;
		private String pathOut;
		private String imageExtension;
		private List<ProjectImageEntry<BufferedImage>> imageList;
		private boolean useDetections;
		private int downsample;
		private int width;
		private int height;
		private HashMap<PathClass, Integer> labelMap = new HashMap<PathClass, Integer>();
		
		// Default: Exporting annotations
		private Class<? extends PathObject> type = PathAnnotationObject.class;
		
		
		public ExportTask(List<ProjectImageEntry<BufferedImage>> imageList, List<PathClass> labelList, Map<String, String> parameter) {
			this.pathOut = parameter.get("pathOut");
			this.imageList = imageList;
			this.useDetections = parameter.get("useDetections").equals("Detections") ? true : false;
			this.downsample = Integer.parseInt(parameter.get("downsample"));
			this.width = Integer.parseInt(parameter.get("width"));
			this.height = Integer.parseInt(parameter.get("heigth"));
			this.imageExtension = parameter.get("imageExtension");
			
			for (int i = 0; i < labelList.size(); i++)
				labelMap.put(labelList.get(i), i);
		}
		
		public void quietCancel() {
			this.quietCancel = true;
		}

		public boolean isQuietlyCancelled() {
			return quietCancel;
		}
		

		@Override
		protected Void call() {
			
			long startTime = System.currentTimeMillis();
			try {
				for (ProjectImageEntry<BufferedImage> entry: imageList) {
					try {
						ImageData<BufferedImage> imageData = entry.readImageData();
						LabeledImageServer labels = new LabeledImageServer.Builder(imageData)
							    .backgroundLabel(0, ColorTools.WHITE) // Specify background label (usually 0 or 255)
							    .downsample(downsample)
							    .addLabels(labelMap)
							    .useDetections()
//							    .lineThickness(3)
//							    .setBoundaryLabel('Boundary*', 3)
							    .multichannelOutput(false) // If true, each label is a different channel (required for multiclass probability)
							    .build();
						
						new TileExporter(imageData)
								.downsample(downsample)
							    .imageExtension(imageExtension)
							    .tileSize(width, height)
							    .labeledServer(labels)
							    .annotatedTilesOnly(true)
							    .overlap(64)
							    .writeTiles(pathOut);
					} catch (Exception e) {
						logger.warn("Error exporting tiles for " + entry.getImageName() + ". Continue anyway...");
					}
					
				}
			} catch (Exception e) {
				logger.warn("Error reading entries in the project: " + e);
			}
			
			
			
			long endTime = System.currentTimeMillis();
			
			long timeMillis = endTime - startTime;
			String time = null;
			if (timeMillis > 1000*60)
				time = String.format("Total processing time: %.2f minutes", timeMillis/(1000.0 * 60.0));
			else if (timeMillis > 1000)
				time = String.format("Total processing time: %.2f seconds", timeMillis/(1000.0));
			else
				time = String.format("Total processing time: %d milliseconds", timeMillis);
			logger.info("Processed {} images", imageList.size());
			logger.info(time);
			
			Dialogs.showMessageDialog("Export completed", "Successful export!");
			//Dialogs.showPlainMessage("Export completed", "Successful export completed!");
	
			return null;
			
		}
		
	}
	
}