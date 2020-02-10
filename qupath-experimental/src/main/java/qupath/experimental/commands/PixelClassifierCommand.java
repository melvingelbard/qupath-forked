package qupath.experimental.commands;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.ml.PixelClassifierPane;

/**
 * Open GUI for the current viewer to train a new pixel classifier.
 * 
 * @author Pete Bankhead
 *
 */
public class PixelClassifierCommand implements PathCommand {

	@Override
	public void run() {
		var viewer = QuPathGUI.getInstance().getViewer();
		var imageData = viewer == null ? null : viewer.getImageData();
		if (imageData == null) {
			Dialogs.showNoImageError("Pixel classifier");
		} else
			new PixelClassifierPane(viewer);
	}

}
