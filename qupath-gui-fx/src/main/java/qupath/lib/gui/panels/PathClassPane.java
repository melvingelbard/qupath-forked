package qupath.lib.gui.panels;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.controlsfx.control.action.Action;
import org.controlsfx.control.action.ActionUtils;
import org.locationtech.jts.index.bintree.Root;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.dialogs.Dialogs.DialogButton;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;
import qupath.lib.gui.viewer.OverlayOptions;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;

/**
 * Component used to display and edit available {@linkplain PathClass PathClasses}.
 *
 * @author Pete Bankhead
 */
public class PathClassPane {

	private final static Logger logger = LoggerFactory.getLogger(PathClassPane.class);

	private QuPathGUI qupath;

	private Pane pane;

	/**
	 * List displaying available PathClasses
	 */
	private TreeView<PathClass> treeClasses;

	/**
	 * Filter visible classes
	 */
	private StringProperty filterText = new SimpleStringProperty("");

	/**
	 * If set, request that new annotations have their classification set automatically
	 */
	private BooleanProperty doAutoSetPathClass = new SimpleBooleanProperty(false);

	PathClassPane(QuPathGUI qupath) {
		this.qupath = qupath;
		pane = createClassPane();
	}


	Predicate<PathClass> createPredicate(String text) {
		if (text == null || text.isBlank())
			return p -> true;
		String text2 = text.toLowerCase();
		return (PathClass p) -> {
			return p == null || p == PathClassFactory.getPathClassUnclassified() ||
					p.toString().toLowerCase().contains(text.toLowerCase()) ||
					p.toString().toLowerCase().contains(text2);
		};
	}

	private Pane createClassPane() {
		TreeItem<PathClass> root = new TreeItem<PathClass>();
		treeClasses = new TreeView<>(root);
		treeClasses.setShowRoot(false);

		var filteredList = qupath.getAvailablePathClasses().filtered(createPredicate(null));
		List<TreeItem<PathClass>> filteredListTreeItems = asTreeItemList(filteredList);

		root.getChildren().addAll(filteredListTreeItems);
		addAllFilteredClass(root, filteredListTreeItems);
		treeClasses.setTooltip(new Tooltip("Annotation classes available (right-click to add or remove)"));

		treeClasses.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> updateAutoSetPathClassProperty());

		treeClasses.setCellFactory(v -> new PathClassTreeCell(qupath));

		treeClasses.getSelectionModel().select(0);
		treeClasses.setPrefSize(100, 200);

		treeClasses.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);


		var copyCombo = new KeyCodeCombination(KeyCode.C, KeyCodeCombination.SHORTCUT_DOWN);
		var pasteCombo = new KeyCodeCombination(KeyCode.V, KeyCodeCombination.SHORTCUT_DOWN);

		treeClasses.addEventFilter(KeyEvent.KEY_RELEASED, e -> {
			if (e.isConsumed())
				return;
			if (e.getCode() == KeyCode.BACK_SPACE) {
				promptToRemoveSelectedClasses();
				e.consume();
				return;
			} else if (e.getCode() == KeyCode.ENTER) {
				promptToEditSelectedClass(root);
				e.consume();
				return;
			} else if (e.getCode() == KeyCode.SPACE) {
				toggleSelectedClassesVisibility();
				e.consume();
				return;
			} else if (copyCombo.match(e)) {
				// Copy the list if needed
				String s = treeClasses.getSelectionModel().getSelectedItems()
						.stream().map(p -> p.toString()).collect(Collectors.joining(System.lineSeparator()));
				if (!s.isBlank()) {
					Clipboard.getSystemClipboard().setContent(
							Map.of(DataFormat.PLAIN_TEXT, s));
				}
				e.consume();
				return;
			} else if (pasteCombo.match(e)) {
				logger.debug("Paste not implemented for classification list!");
				e.consume();
				return;
			}
		});

		treeClasses.setOnMouseClicked(e -> {
			if (!e.isPopupTrigger() && e.getClickCount() == 2)
				promptToEditSelectedClass(root);
		});

		ContextMenu menuClasses = createClassesMenu();

		treeClasses.setContextMenu(menuClasses);

		// Add the class list
		BorderPane paneClasses = new BorderPane();
		paneClasses.setCenter(treeClasses);

		Action setSelectedObjectClassAction = new Action("Set class", e -> {
			var hierarchy = getHierarchy();
			if (hierarchy == null)
				return;
			PathClass pathClass = getSelectedPathClass();
			if (pathClass == PathClassFactory.getPathClassUnclassified())
				pathClass = null;
			var pathObjects = new ArrayList<>(hierarchy.getSelectionModel().getSelectedObjects());
			List<PathObject> changed = new ArrayList<>();
			for (PathObject pathObject : pathObjects) {
				if (pathObject.isTMACore())
					continue;
				if (pathObject.getPathClass() == pathClass)
					continue;
				pathObject.setPathClass(pathClass);
				changed.add(pathObject);
			}
			if (!changed.isEmpty()) {
				hierarchy.fireObjectClassificationsChangedEvent(this, changed);

				GuiTools.refreshTree(treeClasses);
			}
		});
		setSelectedObjectClassAction.setLongText("Set the class of the currently-selected annotation(s)");

		Action autoClassifyAnnotationsAction = new Action("Auto set");
		autoClassifyAnnotationsAction.setLongText("Automatically set all new annotations to the selected class");
		autoClassifyAnnotationsAction.selectedProperty().bindBidirectional(doAutoSetPathClass);

		doAutoSetPathClass.addListener((e, f, g) -> updateAutoSetPathClassProperty());

		Button btnSetClass = ActionUtils.createButton(setSelectedObjectClassAction);
		ToggleButton btnAutoClass = ActionUtils.createToggleButton(autoClassifyAnnotationsAction);

		// Create a button to show context menu (makes it more obvious to the user that it exists)
		Button btnMore = GuiTools.createMoreButton(menuClasses, Side.RIGHT);
		GridPane paneClassButtons = new GridPane();
		paneClassButtons.add(btnSetClass, 0, 0);
		paneClassButtons.add(btnAutoClass, 1, 0);
		paneClassButtons.add(btnMore, 2, 0);
		GridPane.setHgrow(btnSetClass, Priority.ALWAYS);
		GridPane.setHgrow(btnAutoClass, Priority.ALWAYS);

		var tfFilter = new TextField();
		tfFilter.setTooltip(new Tooltip("Type to filter classifications in list"));
		filterText.bind(tfFilter.textProperty());
		filterText.addListener((v, o, n) -> {
			filteredList.setPredicate(createPredicate(n));
			root.getChildren().clear();
			root.getChildren().addAll(asTreeItemList(filteredList));


		});
		var paneBottom = PaneTools.createRowGrid(tfFilter, paneClassButtons);

		PaneTools.setMaxWidth(Double.MAX_VALUE,
				btnSetClass, btnAutoClass, tfFilter);

		paneClasses.setBottom(paneBottom);
		return paneClasses;
	}

	private void addAllFilteredClass(TreeItem<PathClass> root, List<TreeItem<PathClass>> filteredListTreeItems) {
		for (int i = 0; i < filteredListTreeItems.size(); i++) {
			PathClass pc = PathClassTools.uniqueNames(filteredListTreeItems.get(i).getValue());
		}
	}


	ContextMenu createClassesMenu() {
		ContextMenu menu = new ContextMenu();

		Action actionAddClass = new Action("Add class", e -> promptToAddClass());
		Action actionRemoveClass = new Action("Remove class", e -> promptToRemoveSelectedClasses());
		Action actionResetClasses = new Action("Reset to default classes", e -> promptToResetClasses());
		Action actionImportClasses = new Action("Import classes from project", e -> promptToImportClasses());

//		Action actionPopulateFromImage = new Action("Populate from image (include sub-classes)", e -> promptToPopulateFromImage(false));
//		Action actionPopulateFromImageBase = new Action("Populate from image (base classes only)", e -> promptToPopulateFromImage(true));

		actionRemoveClass.disabledProperty().bind(Bindings.createBooleanBinding(() -> {
			PathClass item = treeClasses.getSelectionModel().getSelectedItem().getValue();
			return item == null || PathClassFactory.getPathClassUnclassified() == item;
		},
				treeClasses.getSelectionModel().selectedItemProperty()
		));

		MenuItem miRemoveClass = ActionUtils.createMenuItem(actionRemoveClass);
		MenuItem miAddClass = ActionUtils.createMenuItem(actionAddClass);
		MenuItem miResetAllClasses = ActionUtils.createMenuItem(actionResetClasses);
//		MenuItem miPopulateFromImage = ActionUtils.createMenuItem(actionPopulateFromImage);
//		MenuItem miPopulateFromImageBase = ActionUtils.createMenuItem(actionPopulateFromImageBase);

//		MenuItem miClearAllClasses = new MenuItem("Clear all classes");
//		miClearAllClasses.setOnAction(e -> promptToClearClasses());

		MenuItem miPopulateFromImage = new MenuItem("All classes (including sub-classes)");
		miPopulateFromImage.setOnAction(e -> promptToPopulateFromImage(false));
		MenuItem miPopulateFromImageBase = new MenuItem("Base classes only");
		miPopulateFromImageBase.setOnAction(e -> promptToPopulateFromImage(true));

		MenuItem miPopulateFromChannels = new MenuItem("Image channel names");
		miPopulateFromChannels.setOnAction(e -> promptToPopulateFromChannels());

		Menu menuPopulate = new Menu("Populate from image");
		menuPopulate.getItems().addAll(
				miPopulateFromImageBase, miPopulateFromImage,
				new SeparatorMenuItem(), miPopulateFromChannels);

		MenuItem miSelectObjects = new MenuItem("Select objects with class");
		miSelectObjects.disableProperty().bind(Bindings.createBooleanBinding(
				() -> {
					PathClass item = treeClasses.getSelectionModel().getSelectedItem().getValue();
					return item == null;
				},
				treeClasses.getSelectionModel().selectedItemProperty()));

		miSelectObjects.setOnAction(e -> {
			var hierarchy = getHierarchy();
			if (hierarchy == null)
				return;
			Set<PathClass> pathClasses = new HashSet<>(getSelectedPathClasses());
			if (pathClasses.contains(PathClassFactory.getPathClassUnclassified()))
				pathClasses.add(null);
			List<PathObject> pathObjectsToSelect = hierarchy.getObjects(null, null)
					.stream()
					.filter(p -> !p.isRootObject() && pathClasses.contains(p.getPathClass()))
					.collect(Collectors.toList());
			if (pathObjectsToSelect.isEmpty())
				hierarchy.getSelectionModel().clearSelection();
			else
				hierarchy.getSelectionModel().setSelectedObjects(pathObjectsToSelect, null);
		});

		MenuItem miSetHidden = new MenuItem("Hide classes in viewer");
		miSetHidden.setOnAction(e -> setSelectedClassesVisibility(false));
		MenuItem miSetVisible = new MenuItem("Show classes in viewer");
		miSetVisible.setOnAction(e -> setSelectedClassesVisibility(true));

//		MenuItem miToggleClassVisible = new MenuItem("Toggle display class");
//		miToggleClassVisible.setOnAction(e -> {
//			OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
//			for (var pathClass : getSelectedPathClasses()) {
//				if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
//					continue;
//				overlayOptions.setPathClassHidden(pathClass, !overlayOptions.isPathClassHidden(pathClass));
//			}
//			listClasses.refresh();
//		});

		menu.setOnShowing(e -> {
			var hierarchy = getHierarchy();
			menuPopulate.setDisable(hierarchy == null);
			miPopulateFromImage.setDisable(hierarchy == null);
			miPopulateFromImageBase.setDisable(hierarchy == null);
			miPopulateFromChannels.setDisable(qupath.getImageData() == null);
			var selected = getSelectedPathClasses();
			boolean hasClasses = !selected.isEmpty();
//			boolean hasClasses = selected.size() > 1 ||
//					(selected.size() == 1 && selected.get(0) != null && selected.get(0) != PathClassFactory.getPathClassUnclassified());
			miSetVisible.setDisable(!hasClasses);
			miSetHidden.setDisable(!hasClasses);
//			miRemoveClass.setDisable(!hasClasses);
		});

		MenuItem miImportFromProject = ActionUtils.createMenuItem(actionImportClasses);

		menu.getItems().addAll(
				miAddClass,
				miRemoveClass,
				miResetAllClasses,
//				miClearAllClasses,
				menuPopulate,
				miImportFromProject,
				new SeparatorMenuItem(),
				miSetVisible,
				miSetHidden,
//				miToggleClassVisible,
				new SeparatorMenuItem(),
				miSelectObjects);

		return menu;
	}

	/**
	 * Update pane to reflect the current status.
	 */
	public void refresh() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> refresh());
			return;
		}

		treeClasses.refresh();
	}


	void toggleSelectedClassesVisibility() {
		OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
			overlayOptions.setPathClassHidden(pathClass, !overlayOptions.isPathClassHidden(pathClass));
		}
		treeClasses.refresh();
	}


	void setSelectedClassesVisibility(boolean visible) {
		OverlayOptions overlayOptions = qupath.getViewer().getOverlayOptions();
		for (var pathClass : getSelectedPathClasses()) {
//			if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
//				continue;
			overlayOptions.setPathClassHidden(pathClass, !visible);
		}
		treeClasses.refresh();
	}

	void updateAutoSetPathClassProperty() {
		PathClass pathClass = null;
		if (doAutoSetPathClass.get()) {
			pathClass = getSelectedPathClass();
		}
		if (pathClass == null || pathClass == PathClassFactory.getPathClassUnclassified())
			PathPrefs.setAutoSetAnnotationClass(null);
		else
			PathPrefs.setAutoSetAnnotationClass(pathClass);
	}

	private PathObjectHierarchy getHierarchy() {
		var imageData = qupath.getImageData();
		return imageData == null ? null : imageData.getHierarchy();
	}

	/**
	 * Prompt to populate available class list from the channels of the current {@link ImageServer}.
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToPopulateFromChannels() {
		var imageData = qupath.getImageData();
		if (imageData == null)
			return false;

		var server = imageData.getServer();
		List<PathClass> newClasses = new ArrayList<>();
		for (var channel : server.getMetadata().getChannels()) {
			newClasses.add(PathClassFactory.getPathClass(channel.getName(), channel.getColor()));
		}
		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No channels found, somehow!");
			return false;
		}

		List<PathClass> currentClasses = new ArrayList<>(qupath.getAvailablePathClasses());
		currentClasses.remove(null);
		if (currentClasses.equals(newClasses)) {
			Dialogs.showInfoNotification("Set available classes", "Class lists are the same - no changes to make!");
			return false;
		}

		var btn = DialogButton.YES;
		if (qupath.getAvailablePathClasses().size() > 1)
			btn = Dialogs.showYesNoCancelDialog("Set available classes", "Keep existing available classes?");
		if (btn == DialogButton.YES) {
			newClasses.removeAll(qupath.getAvailablePathClasses());
			return qupath.getAvailablePathClasses().addAll(newClasses);
		} else if (btn == DialogButton.NO) {
			newClasses.add(0, PathClassFactory.getPathClassUnclassified());
			return qupath.getAvailablePathClasses().setAll(newClasses);
		} else
			return false;
	}

	/**
	 * Prompt to remove all available classifications ('null' remains)
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToClearClasses() {
		var available = qupath.getAvailablePathClasses();
		if (available.isEmpty() || (available.size() == 1 && available.get(0) == PathClassFactory.getPathClassUnclassified()))
			return false;
		if (Dialogs.showConfirmDialog("Remove classifications", "Remove all available classes?")) {
			available.setAll(PathClassFactory.getPathClassUnclassified());
			return true;
		} else
			return false;
	}


	/**
	 * Prompt to populate available class list from the current image.
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToPopulateFromImage(boolean baseClassesOnly) {
		var hierarchy = getHierarchy();
		if (hierarchy == null)
			return false;

		Set<PathClass> representedClasses = hierarchy.getFlattenedObjectList(null).stream()
				.filter(p -> !p.isRootObject())
				.map(p -> p.getPathClass())
				.filter(p -> p != null && p != PathClassFactory.getPathClassUnclassified())
				.map(p -> baseClassesOnly ? p.getBaseClass() : p)
				.collect(Collectors.toSet());

		List<PathClass> newClasses = new ArrayList<>(representedClasses);
		Collections.sort(newClasses);

		if (newClasses.isEmpty()) {
			Dialogs.showErrorMessage("Set available classes", "No classifications found in current image!");
			return false;
		}

		List<PathClass> currentClasses = new ArrayList<>(qupath.getAvailablePathClasses());
		currentClasses.remove(null);
		if (currentClasses.equals(newClasses)) {
			Dialogs.showInfoNotification("Set available classes", "Class lists are the same - no changes to make!");
			return false;
		}

		var btn = DialogButton.YES;
		if (qupath.getAvailablePathClasses().size() > 1)
			btn = Dialogs.showYesNoCancelDialog("Set available classes", "Keep existing available classes?");
		if (btn == DialogButton.YES) {
			newClasses.removeAll(qupath.getAvailablePathClasses());
			return qupath.getAvailablePathClasses().addAll(newClasses);
		} else if (btn == DialogButton.NO) {
			newClasses.add(0, PathClassFactory.getPathClassUnclassified());
			return qupath.getAvailablePathClasses().setAll(newClasses);
		} else
			return false;
	}

	/**
	 * Prompt to import available class list from another project.
	 * @return true if the class list was changed, false otherwise.
	 */
	boolean promptToImportClasses() {
		File file = QuPathGUI.getSharedDialogHelper().promptForFile("Import classifications", null, "QuPath project", ProjectIO.getProjectExtension());
		if (file == null)
			return false;
		if (!file.getAbsolutePath().toLowerCase().endsWith(ProjectIO.getProjectExtension())) {
			Dialogs.showErrorMessage("Import PathClasses", file.getName() + " is not a project file!");
			return false;
		}
		try {
			Project<?> project = ProjectIO.loadProject(file, BufferedImage.class);
			List<PathClass> pathClasses = project.getPathClasses();
			if (pathClasses.isEmpty()) {
				Dialogs.showErrorMessage("Import PathClasses", "No classes found in " + file.getName());
				return false;
			}
			ObservableList<PathClass> availableClasses = qupath.getAvailablePathClasses();
			if (pathClasses.size() == availableClasses.size() && availableClasses.containsAll(pathClasses)) {
				Dialogs.showInfoNotification("Import PathClasses", file.getName() + " contains same classifications - no changes to make");
				return false;
			}
			availableClasses.setAll(pathClasses);
			return true;
		} catch (Exception ex) {
			Dialogs.showErrorMessage("Error reading project", ex);
			return false;
		}
	}


	/**
	 * Prompt to reset classifications to the default list.
	 * @return true if the class list was changed, false otherwise
	 */
	boolean promptToResetClasses() {
		if (Dialogs.showConfirmDialog("Reset classes", "Reset all available classes?")) {
			return qupath.resetAvailablePathClasses();
		}
		return false;
	}


	/**
	 * Prompt to add a new classification.
	 * @return true if a new classification was added, false otherwise
	 */
	boolean promptToAddClass() {
		String input = Dialogs.showInputDialog("Add class", "Class name", "");
		if (input == null || input.trim().isEmpty())
			return false;
		PathClass pathClass = PathClassFactory.getPathClass(input);
		var list = qupath.getAvailablePathClasses();
		if (list.contains(pathClass)) {
			Dialogs.showErrorMessage("Add class", "Class '" + input + "' already exists!");
			return false;
		}
		list.add(pathClass);
		return true;
	}

	/**
	 * Prompt to edit the selected classification.
	 * @return true if changes were made, false otherwise
	 */
	boolean promptToEditSelectedClass(TreeItem<PathClass> root) {
		PathClass pathClassSelected = getSelectedPathClass();
		if (promptToEditClass(pathClassSelected)) {
			//					listModelPathClasses.fireListDataChangedEvent();

			GuiTools.refreshTree(treeClasses);
			var project = qupath.getProject();
			// Make sure we have updated the classes in the project
			if (project != null) {
				List<PathClass> allClasses = root.getChildren().stream()
						.map(i -> i.getValue())
						.collect(Collectors.toList());
				project.setPathClasses(allClasses);
			}
			var hierarchy = getHierarchy();
			if (hierarchy != null)
				hierarchy.fireHierarchyChangedEvent(treeClasses);

			return true;
		}
		return false;
	}

	/**
	 * Get the pane that may be used to display the classifications.
	 * @return
	 */
	public Pane getPane() {
		return pane;
	}

	/**
	 * Get the TreeView displaying the classes.
	 * @return
	 */
	TreeView<PathClass> getTreeView() {
		return treeClasses;
	}

	/**
	 * Prompt to edit the name/color of a class.
	 * @param pathClass
	 * @return
	 */
	public static boolean promptToEditClass(final PathClass pathClass) {
		//		if (pathClass == null)
		//			return false; // TODO: Make work on default ROI color

		boolean defaultColor = pathClass == null;

		BorderPane panel = new BorderPane();

		BorderPane panelName = new BorderPane();
		String name;
		Color color;

		if (defaultColor) {
			name = "Default object color";
			color = ColorToolsFX.getCachedColor(PathPrefs.getColorDefaultObjects());
			//			textField.setEditable(false);
			//			textField.setEnabled(false);
			Label label = new Label(name);
			label.setPadding(new Insets(5, 0, 10, 0));
			panelName.setCenter(label);
		} else {
			name = pathClass.getName();
			if (name == null)
				name = "";
			color = ColorToolsFX.getPathClassColor(pathClass);
			Label label = new Label(name);
			label.setPadding(new Insets(5, 0, 10, 0));
			panelName.setCenter(label);
			//				textField.setText(name);
			//				panelName.setLeft(new Label("Class name"));
			//				panelName.setCenter(textField);
		}

		panel.setTop(panelName);
		ColorPicker panelColor = new ColorPicker(color);

		panel.setCenter(panelColor);

		if (!Dialogs.showConfirmDialog("Edit class", panel))
			return false;

		//			String newName = textField.getText().trim();
		Color newColor = panelColor.getValue();
		//			if ((name.length() == 0 || name.equals(newName)) && newColor.equals(color))
		//				return false;

		Integer colorValue = newColor.isOpaque() ? ColorToolsFX.getRGB(newColor) : ColorToolsFX.getARGB(newColor);
		if (defaultColor) {
			if (newColor.isOpaque())
				PathPrefs.setColorDefaultObjects(colorValue);
			else
				PathPrefs.setColorDefaultObjects(colorValue);
		}
		else {
			//				if (!name.equals(pathClass.getName()) && PathClassFactory.pathClassExists(newName)) {
			//					logger.warn("Modified name already exists - cannot rename");
			//					return false;
			//				}
			//				pathClass.setName(newName);
			pathClass.setColor(colorValue);
		}
		return true;
	}



	/**
	 * Prompt to remove the currently selected class, if there is one.
	 *
	 * @return true if changes were made to the class list, false otherwise
	 */
	boolean promptToRemoveSelectedClasses() {
		List<PathClass> pathClasses = getSelectedPathClasses()
				.stream()
				.filter(p -> p != null && p != PathClassFactory.getPathClassUnclassified())
				.collect(Collectors.toList());
		if (pathClasses.isEmpty())
			return false;
		String message;
		if (pathClasses.size() == 1)
			message = "Remove '" + pathClasses.get(0).toString() + "' from class list?";
		else
			message = "Remove " + pathClasses.size() + " classes from list?";
		if (Dialogs.showConfirmDialog("Remove classes", message))
			return qupath.getAvailablePathClasses().removeAll(pathClasses);
		return false;
	}


	/**
	 * Get the currently-selected PathClass.
	 * @return
	 */
	PathClass getSelectedPathClass() {
		return treeClasses.getSelectionModel().getSelectedItem().getValue();
	}

	/**
	 * Get the currently-selected PathClasses.
	 * @return
	 */
	List<PathClass> getSelectedPathClasses() {
		return treeClasses.getSelectionModel().getSelectedItems()
				.stream()
				.map(p -> p.getValue().getName() == null ? null : p.getValue())
				.collect(Collectors.toList());
	}


	/**
	 * Extract annotations from a hierarchy with a specific classification.
	 * @param hierarchy
	 * @param pathClass
	 * @return
	 */
	static List<PathObject> getAnnotationsForClass(PathObjectHierarchy hierarchy, PathClass pathClass) {
		if (hierarchy == null)
			return Collections.emptyList();
		List<PathObject> annotations = new ArrayList<>();
		for (PathObject pathObject : hierarchy.getAnnotationObjects()) {
			if (pathClass.equals(pathObject.getPathClass()))
				annotations.add(pathObject);
		}
		return annotations;
	}

	static List<TreeItem<PathClass>> asTreeItemList(FilteredList<PathClass> filteredList){
		List<TreeItem<PathClass>> out = new ArrayList<>();
		for (int i = 0; i < filteredList.size(); i++) {
			out.add(new TreeItem<>(filteredList.get(i)));
		}
		return out;
	}


	/**
	 * A {@link ListCell} for displaying {@linkplain PathClass PathClasses}, including annotation counts
	 * for the classes if available.
	 */
	static class PathClassListCell extends ListCell<PathClass> {

		private QuPathGUI qupath;

		PathClassListCell(QuPathGUI qupath) {
			this.qupath = qupath;
		}

		@Override
		protected void updateItem(PathClass value, boolean empty) {
			super.updateItem(value, empty);
			QuPathViewer viewer = qupath == null ? null : qupath.getViewer();
			PathObjectHierarchy hierarchy = viewer == null ? null : viewer.getHierarchy();
			int size = 10;
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
			} else if (value.getBaseClass() == value && value.getName() == null) {
				setText("None");
				setGraphic(new Rectangle(size, size, ColorToolsFX.getCachedColor(0, 0, 0, 0)));
			} else {
				int n = 0;
				if (hierarchy != null) {
					try {
						// Try to count objects for class
						// May be possibility of concurrent modification exception?
						//						n = nLabelledObjectsForClass(hierarchy, value);
						n = getAnnotationsForClass(hierarchy, value).size();
					} catch (Exception e) {
						logger.debug("Exception while counting objects for class", e);
					}
				}
				if (n == 0)
					setText(value.toString());
				else
					setText(value.toString() + " (" + n + ")");
				setGraphic(new Rectangle(size, size, ColorToolsFX.getPathClassColor(value)));
			}
			if (!empty && viewer != null && viewer.getOverlayOptions().isPathClassHidden(value)) {
				setStyle("-fx-font-family:arial; -fx-font-style:italic;");
				setText(getText() + " (hidden)");
			} else
				setStyle("-fx-font-family:arial; -fx-font-style:normal;");
		}
	}

	/**
	 * A {@link TreeCell} for displaying {@linkplain PathClass PathClasses}, including annotation counts
	 * for the classes if available.
	 */
	static class PathClassTreeCell extends TreeCell<PathClass> {

		private QuPathGUI qupath;

		PathClassTreeCell(QuPathGUI qupath) {
			this.qupath = qupath;
		}

		@Override
		protected void updateItem(PathClass value, boolean empty) {
			super.updateItem(value, empty);
			QuPathViewer viewer = qupath == null ? null : qupath.getViewer();
			PathObjectHierarchy hierarchy = viewer == null ? null : viewer.getHierarchy();
			int size = 10;
			if (value == null || empty) {
				setText(null);
				setGraphic(null);
			} else if (value.getBaseClass() == value && value.getName() == null) {
				setText("None");
				setGraphic(new Rectangle(size, size, ColorToolsFX.getCachedColor(0, 0, 0, 0)));
			} else {
				int n = 0;
				if (hierarchy != null) {
					try {
						// Try to count objects for class
						// May be possibility of concurrent modification exception?
						//						n = nLabelledObjectsForClass(hierarchy, value);
						n = getAnnotationsForClass(hierarchy, value).size();
					} catch (Exception e) {
						logger.debug("Exception while counting objects for class", e);
					}
				}
				if (n == 0)
					setText(value.toString());
				else
					setText(value.toString() + " (" + n + ")");
				setGraphic(new Rectangle(size, size, ColorToolsFX.getPathClassColor(value)));
			}
			if (!empty && viewer != null && viewer.getOverlayOptions().isPathClassHidden(value)) {
				setStyle("-fx-font-family:arial; -fx-font-style:italic;");
				setText(getText() + " (hidden)");
			} else
				setStyle("-fx-font-family:arial; -fx-font-style:normal;");
		}
	}

}
