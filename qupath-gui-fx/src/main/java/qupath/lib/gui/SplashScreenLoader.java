package qupath.lib.gui;


import javafx.application.Preloader;
import javafx.application.Preloader.ErrorNotification;
import javafx.application.Preloader.PreloaderNotification;
import javafx.application.Preloader.ProgressNotification;
import javafx.application.Preloader.StateChangeNotification;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import qupath.lib.gui.dialogs.Dialogs;

public class SplashScreenLoader extends Preloader {
	
	Scene scene;
	Stage preloaderStage;
	public static Label label;
	private ProgressBar bar;
	
	

	@Override
	public void handleProgressNotification(ProgressNotification info) {
		// TODO Auto-generated method stub
		super.handleProgressNotification(info);
	}

	@Override
	public void handleStateChangeNotification(StateChangeNotification info) {
		
		StateChangeNotification.Type type = info.getType();
		if (type == StateChangeNotification.Type.BEFORE_START) 
			preloaderStage.hide();
	}

	@Override
	public void handleApplicationNotification(PreloaderNotification info) {
		
		if (info instanceof ProgressNotification) {
			label.setText("Launching QuPath");
			bar.setProgress(((ProgressNotification) info).getProgress());
		}
	}

	@Override
	public boolean handleErrorNotification(ErrorNotification info) {
		// TODO Auto-generated method stub
		return super.handleErrorNotification(info);
	}

	@Override
	public void init() throws Exception {
		label = new Label("INIT");
		bar = new ProgressBar();
		BorderPane bp = new BorderPane();
		bp.setTop(label);
		bp.setBottom(bar);
		label.setMinSize(100, 100);
		scene = new Scene(bp);
	}

	@Override
	public void stop() throws Exception {
		// TODO Auto-generated method stub
		super.stop();
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.preloaderStage = primaryStage;
		
		// Set preloader scene and show stage
		preloaderStage.setScene(scene);
		preloaderStage.initStyle(StageStyle.UNDECORATED);
		preloaderStage.show();
		
	}
	

}
