package qupath.lib.gui.commands;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;

/**
 * Dialog box to export measurements
 * 
 * @author Melvin Gelbard
 *
 */
public class MeasurementExportCommand implements PathCommand {
	
	private QuPathGUI qupath;
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExportCommand.class);
	private Stage dialog = null;
	
	public MeasurementExportCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void run() {
		createAndShowDialog();
	}
	
	private void createAndShowDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.setTitle("Export Measurements");
		
		BorderPane pane = new BorderPane();
		//pane.setCenter(panel.getPane());
	
	
	}
	
	
}