package qupath.lib.gui.commands;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.controlsfx.control.ListSelectionView;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.util.Callback;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.MeasurementExporter;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Dialog box to export measurements
 * 
 * @author Melvin Gelbard
 *
 */

// TODO: Save current image?
public class MeasurementExportCommand implements PathCommand {
	
	private QuPathGUI qupath;
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);
	private ObjectProperty<Future<?>> runningTask = new SimpleObjectProperty<>();
	
	private Dialog<ButtonType> dialog = null;
	private Project<BufferedImage> project;
	private List<ProjectImageEntry<BufferedImage>> previousImages = new ArrayList<>();
	
	// GUI
	private TextField outputText = new TextField();
	private ComboBox<String> comboPathObject = new ComboBox<>();
	private TextField separatorText = new TextField();
	private TextField includeText = new TextField();
	private TextField excludeText = new TextField();
	
	private ButtonType btnExport = new ButtonType("Export", ButtonData.OK_DONE);
	
	public MeasurementExportCommand(final QuPathGUI qupath) {
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
		
		BorderPane mainPane = new BorderPane();
		
		BorderPane imageEntryPane = new BorderPane();
		GridPane optionPane = new GridPane();
		
		
		// TOP PANE (SELECT PROJECT ENTRY FOR EXPORT)
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
		Label pathOutputLabel = new Label("Output File");
		var btnChooseFile = new Button("Choose path");
		btnChooseFile.setOnAction(e -> {
			File pathOut = QuPathGUI.getSharedDialogHelper().promptForDirectory(null);
			if (pathOut != null) {
				if (pathOut.isDirectory())
					pathOut = new File(pathOut.getAbsolutePath() + "/export.csv");
				outputText.setText(pathOut.getAbsolutePath());
			}
		});
		
		//PaneTools.addGridRow(optionPane, row++, 0, "Enter the output file path (with format extension)", pathOutputLabel, outputText, btnChooseFile);
		optionPane.add(pathOutputLabel, 0, row);
		optionPane.add(outputText, 1, row);
		optionPane.add(btnChooseFile, 2, row++);
		pathOutputLabel.setLabelFor(outputText);
		

		Label pathObjectLabel = new Label("Apply on");
		PaneTools.addGridRow(optionPane, row++, 0, "Choose to export either annotations or detections", pathObjectLabel, comboPathObject, comboPathObject, comboPathObject);
		pathObjectLabel.setLabelFor(comboPathObject);
		comboPathObject.getItems().setAll("Annotations", "Detections");
		comboPathObject.getSelectionModel().selectFirst();


		Label separatorLabel = new Label("Separator");
		separatorLabel.setLabelFor(separatorText);
		separatorText.setPromptText("Default: ', '");
		PaneTools.addGridRow(optionPane, row++, 0, "Enter a value separator (default: ', ')", separatorLabel, separatorText, separatorText);
		
		Label includeLabel = new Label("Columns to include (Optional)");
		includeLabel.setLabelFor(includeText);
		includeText.setPromptText("Image, Name, Class, ...");
		PaneTools.addGridRow(optionPane, row++, 0, "Enter the specific column(s) to include (case sensitive)", includeLabel, includeText, includeText);
		
		Label excludeLabel = new Label("Columns to exclude (Optional)");
		excludeLabel.setLabelFor(excludeText);
		excludeText.setPromptText("Image, Name, Class, ...");
		PaneTools.addGridRow(optionPane, row++, 0, "Enter the specific column(s) to include (case sensitive)", excludeLabel, excludeText, excludeText);
		
		
		// Add listener to includeOnlyColumns
		includeText.textProperty().addListener((observable, oldValue, newValue) -> {
			if (!newValue.isEmpty()) {
				excludeText.setEditable(false);
				excludeText.setDisable(true);
			}
				
			else {
				excludeText.setEditable(true);
				excludeText.setDisable(false);
			}
				
		});

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
		
		String selectedItem = comboPathObject.getSelectionModel().getSelectedItem();
		boolean useDetections = selectedItem.equals("Detections") ? true : false;
		String[] exclude = Arrays.stream(excludeText.getText().split(",")).map(String::trim).toArray(String[]::new);
		String[] include = Arrays.stream(includeText.getText().split(",")).map(String::trim).toArray(String[]::new);
		
		MeasurementExporter exporter;
		exporter = new MeasurementExporter(project)
			.imageList(listSelectionView.getTargetItems())
			.separator(separatorText.getText())
			.excludeColumns(exclude)
			.includeOnlyColumns(include)
			.useDetections(useDetections);
		
		ExportTask worker = new ExportTask(exporter, outputText.getText());
		
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
		private List<ProjectImageEntry<BufferedImage>> imageList;
		private List<String> excludeColumns;
		private List<String> includeOnlyColumns;
		private String separator = ", ";
		
		// Default: Exporting annotations
		private Class<? extends PathObject> type = PathAnnotationObject.class;
		
		
		public ExportTask(MeasurementExporter exporter, String pathOut) {
			this.pathOut = pathOut;
			this.imageList = exporter.getImageList();
			this.excludeColumns = exporter.getExcludeColumns();
			this.includeOnlyColumns = exporter.getIncludeColumns();
			if (!exporter.getSeparator().isEmpty())
				this.separator = exporter.getSeparator();
			this.type = exporter.getType();
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
			
			Map<ProjectImageEntry<?>, String[]> imageCols = new HashMap<ProjectImageEntry<?>, String[]>();
			Map<ProjectImageEntry<?>, Integer> nImageEntries = new HashMap<ProjectImageEntry<?>, Integer>();
			List<String> allColumns = new ArrayList<String>();
			Multimap<String, String> valueMap = LinkedListMultimap.create();			
			
			int counter = 0;
			
			for (ProjectImageEntry<?> entry: imageList) {
				if (isQuietlyCancelled() || isCancelled()) {
					logger.warn("Export cancelled");
					return null;
				}
				
				updateProgress(counter, imageList.size()*2);
				counter++;
				updateMessage("Calculating measurements for " + entry.getImageName() + " (" + counter + "/" + imageList.size()*2 + ")");
				
				try {
					ImageData<?> imageData = entry.readImageData();
					ObservableMeasurementTableData model = new ObservableMeasurementTableData();
					model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
					
					List<String> data = SummaryMeasurementTableCommand.getTableModelStrings(model, PathPrefs.getTableDelimiter(), excludeColumns);
					String[] header = data.get(0).split("\t");
					imageCols.put(entry, header);
					nImageEntries.put(entry, data.size()-1);
					
					for (String col: header) {
						if (!allColumns.contains(col)  && !excludeColumns.contains(col))
							allColumns.add(col);
					}
					
					// To keep the same column order, just delete non-relevant columns
					if (!includeOnlyColumns.get(0).isEmpty())
						allColumns.removeIf(n -> !includeOnlyColumns.contains(n));
					
					for (int i = 1; i < data.size(); i++) {
						
						String[] row = data.get(i).split("\t");
						// Put value in map
						for (int elem = 0; elem < row.length; elem++) {
							if (allColumns.contains(header[elem]))
								valueMap.put(header[elem], row[elem]);
						}
					}
					
					

				} catch (Exception e) {
					e.printStackTrace();
				}
			
			}
			
			try {
				FileWriter writer = new FileWriter(pathOut);
				writer.write(String.join(separator, allColumns));
				writer.write("\n");
	
				Iterator[] its = new Iterator[allColumns.size()];
				for (int col = 0; col < allColumns.size(); col++) {
					its[col] = valueMap.get(allColumns.get(col)).iterator();
				}
				
				int counter2 = 0;
				for (ProjectImageEntry<?> entry: imageList) {
					if (isQuietlyCancelled() || isCancelled()) {
						logger.warn("Export cancelled with " + (imageList.size() - counter2) + " image(s) remaining");
						writer.flush();
						writer.close();
						return null;
					}
					
					counter++;
					updateProgress(counter, imageList.size()*2);
					updateMessage("Exporting measurements of " + entry.getImageName() + " (" + counter + "/" + imageList.size()*2 + ")");
					
					for (int nObject = 0; nObject < nImageEntries.get(entry); nObject++) {
						for (int nCol = 0; nCol < allColumns.size(); nCol++) {
							if (Arrays.stream(imageCols.get(entry)).anyMatch(allColumns.get(nCol)::equals)) {
								String val = (String)its[nCol].next();
								// NaN values -> blank
								if (val.equals("NaN"))
									val = "";
								writer.write(val);
								writer.write(separator);
							} else
								writer.write(separator);
						}
						writer.write("\n");
					}
					counter2++;
				}
				
				
				writer.flush();
				writer.close();
			} catch (IOException e) {
				logger.error("Error writing to file: " + e);
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