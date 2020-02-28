package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.controlsfx.control.ListSelectionView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;


import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import qupath.lib.images.servers.LabeledImageServer;
import qupath.lib.images.writers.TileExporter;
import qupath.lib.io.GsonTools;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

//import org.bytedeco.


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
	
	// GUI
	private TextField outputText = new TextField();
	private ComboBox<String> comboPathObject = new ComboBox<>();
	private ComboBox<String> comboImageExtension = new ComboBox<>();
	private ComboBox<String> comboLabelExtension = new ComboBox<>();
	private ComboBox<String> channelsComboBox = new ComboBox<>();
	private TextField tileWidthText = new TextField();
	private TextField tileHeightText = new TextField();
	private TextField overlapText = new TextField();
	
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
		
		
		// Check how the project is structured. If the entries have different 
		// parameters (e.g. pixel size or channels), then the export should
		// not include these entries at the same time.
		String projectPath = project.getPath().toString(); 
		Map<Set<ImageChannel>, List<String>> mapChannels = getProjectEntryChannelMap(projectPath);

		BorderPane mainPane = new BorderPane();
		
		BorderPane imageEntryPane = new BorderPane();
		GridPane optionPane = new GridPane();
		
		
		// TOP PANE (SELECT PROJECT ENTRIES FOR EXPORT)
		ListSelectionView<ProjectImageEntry<BufferedImage>> listSelectionView = new ListSelectionView<>();
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
            			var testttt = areSimilarEntries(mapChannels, listSelectionView.getTargetItems());
            			if (areSimilarEntries(mapChannels, listSelectionView.getTargetItems()).size() > 1) {
            				logger.warn("BAD");
            			}
            			if (listSelectionView.getTargetItems().isEmpty())
            				dialog.getDialogPane().lookupButton(btnExport).setDisable(true);
            			else
            				dialog.getDialogPane().lookupButton(btnExport).setDisable(false);
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
		List<ProjectImageEntry<BufferedImage>> currentImages = new ArrayList<>();
		Label labelSameImageWarning = new Label(
				"A selected image is open in the viewer!\n"
				+ "Use 'File>Reload data' to see changes.");
		
		Label labelSelected = new Label();
		labelSelected.setTextAlignment(TextAlignment.CENTER);
		labelSelected.setAlignment(Pos.CENTER);
		labelSelected.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(labelSelected, Priority.ALWAYS);
		GridPane.setFillWidth(labelSelected, Boolean.TRUE);
		Platform.runLater(() -> {
			getTargetItems(listSelectionView).addListener((ListChangeListener.Change<? extends ProjectImageEntry<?>> e) -> {
				labelSelected.setText(e.getList().size() + " selected");
				if (labelSameImageWarning != null && currentImages != null) {
					boolean visible = false;
					var targets = e.getList();
					for (var current : currentImages) {
						if (targets.contains(current)) {
							visible = true;
							break;
						}
					}
					labelSameImageWarning.setVisible(visible);
				}
			});
		});
		
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
		if (!currentImages.isEmpty()) {
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
		}
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
			}
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
		channelsComboBox.getItems().setAll("All channels", "Custom channels");
		channelsComboBox.getSelectionModel().selectFirst();
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
					channelsComboBox.getSelectionModel().clearAndSelect(1);
				else
					channelsComboBox.getSelectionModel().clearAndSelect(0);
			}
		});
		PaneTools.addGridRow(optionPane, row++, 0, "Specific channels to export", channelsLabel, channelsComboBox, btnChooseChannels);
		
		Label downsampleLabel = new Label("Downsample");
		TextField downsampleText = new TextField();
		downsampleText.setText("1");
		downsampleText.setTextFormatter( new TextFormatter<>(c ->{
			String input = c.getControlNewText();
			if (!input.matches("\\d*") || input.length() > 3)
				return null;
			return c;
		}));
		Label pixelSizeLabel = new Label("Pixel Size");
		TextField pixelSizeText = new TextField();
		pixelSizeText.setText("To implement?");
		
		PaneTools.addGridRow(optionPane, row++, 0, "Downsampling of the output images", downsampleLabel, downsampleText, pixelSizeLabel, pixelSizeText);
		
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
		//mainPane.setCenter(centrePane);
		mainPane.setBottom(optionPane);
		
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
	
	private Map<Set<ImageChannel>, List<String>> getProjectEntryChannelMap(String projectPath) {
		Map<Set<ImageChannel>, List<String>> mapChannels = new HashMap<>();
		
		try {
			JsonReader reader = new JsonReader(new FileReader(projectPath));
			JsonElement element = JsonParser.parseReader(reader);
			JsonObject obj = element.getAsJsonObject();
			
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
	
	private Map<Set<ImageChannel>, List<ProjectImageEntry<BufferedImage>>> areSimilarEntries(Map<Set<ImageChannel>, List<String>> channelMap, ObservableList<ProjectImageEntry<BufferedImage>> targetItems) {
		Map<Set<ImageChannel>, List<ProjectImageEntry<BufferedImage>>> out = new HashMap<>();
		
		for (ProjectImageEntry<BufferedImage> targetEntry: targetItems) {
			var testtt = targetEntry.getServerBuilder().toString();
			for (var entrySet: channelMap.entrySet()) {
				var key = entrySet.getKey();
				var value = entrySet.getValue();
				
				if (value.contains(targetEntry.getID())) {
					if (out.containsKey(key)){
						out.get(key).add(targetEntry);
					} else {
						out.put(key, new ArrayList<>(Arrays.asList(targetEntry)));
					}
				} else
					break;
					
			}
		}
		return out;
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