package qupath.lib.gui.tutorials;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;

public class QuPathTutorial {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPathTutorial.class);
	
	private final QuPathGUI qupath;
	private final String title;
	private final List<QuPathTutorialStep> steps;
    private final GridPane mainPane = new GridPane();
    private final Stage dialog = new Stage();
    private final ObjectProperty<QuPathTutorialStep> currentStep = new SimpleObjectProperty<>();
    
    private static final Node iconTick = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TICK);
    private static final Node iconCross = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.CROSS);
    
    public QuPathTutorial(QuPathGUI qupath, String title, List<QuPathTutorialStep> steps) {
    	this.qupath = qupath;
    	this.title = title;
    	this.steps = steps;
    }

    void run() {
        int row = 0;
        // Title
        var titleLabel = new Label(title);
        titleLabel.setFont(new Font(30));
        titleLabel.setMinWidth(300);
        titleLabel.setAlignment(Pos.CENTER);
        PaneTools.addGridRow(mainPane, row++, 0, null, titleLabel);

        List<TutorialStepPane> stepPanes = new ArrayList<>();
        currentStep.set(steps.get(0));

        for (var step: steps) {
        	var stepPane = new TutorialStepPane(step, ++row - 1);
            stepPane.getPane().setOnMouseClicked(mouseEvent -> stepPane.getPane().setExpanded(currentStep.get() == step));

        	stepPanes.add(stepPane);
        	PaneTools.addGridRow(mainPane, row, 0, "step " + (row - 1), stepPane.getPane());
        }
        
        for (int i = 0; i < stepPanes.size(); i++) {
        	final int ii = i;
        	if (i > 0) {
        		stepPanes.get(ii).getPane().setExpanded(false);
        		stepPanes.get(ii).getPreviousBtn().setOnAction(event -> {
        			stepPanes.get(ii).getPane().setExpanded(false);
        			stepPanes.get(ii - 1).getPane().setExpanded(true);
        			currentStep.set(steps.get(ii - 1));
        		});        		
        	}
        	if (i < stepPanes.size() - 1) {
        		stepPanes.get(i).getNextBtn().setOnAction(event -> {
        			stepPanes.get(ii).getPane().setExpanded(false);
        			stepPanes.get(ii + 1).getPane().setExpanded(true);
        			currentStep.set(steps.get(ii + 1));
        		});     
        	}
        	
        	if (ii == stepPanes.size() - 1) {
        		stepPanes.get(ii).getFinishButton().setOnAction(event -> {
        			var choice = Dialogs.showConfirmDialog("Leave tutorial", "Are you sure you want to finish and leave this tutorial?");
        			if (choice)
        				dialog.close();
        		});        		
        	}
        }

        dialog.setTitle("QuPath Tutorial");
        dialog.setResizable(false);
        dialog.setScene(new Scene(mainPane));
        mainPane.heightProperty().addListener((v, o, n) -> {
        	dialog.sizeToScene();
        	logger.error(mainPane.getHeight() + "");
        
        });
        dialog.showAndWait();
    }
    
    
    private class TutorialStepPane {
    	
    	private final TitledPane titledPane;
    	private Button previousBtn;
    	private Button nextBtn;
    	private Button finishBtn;
    	
    	TutorialStepPane(QuPathTutorialStep step, int nStep) {
    		// Scroll pane for the main instructions
    		ScrollPane scrollPane = new ScrollPane();
    		scrollPane.setPrefSize(120, 120);
    		scrollPane.setContent(new Text(step.instruction));
    		scrollPane.setStyle("-fx-background-color:transparent;");
    		
            // Create button 
    		previousBtn = new Button("Previous");
    		nextBtn = new Button("Next");
            var verifyBtn = new Button("Verify");
            finishBtn = new Button("Finish");
            var tfInput = new TextField(step.input != null ? step.input : "");
            var checkInput = new Button("Check answer");
            iconTick.setVisible(false);
            iconCross.setVisible(false);

            verifyBtn.setOnAction(e -> {
                if (step.checkFun.test(qupath))
                    Dialogs.showMessageDialog("Pass", step.checkPass);
                else
                    Dialogs.showErrorMessage("Error", step.checkFail);

            });
            
            checkInput.setOnAction(e -> {
            	if (step.inputPredicate.test(tfInput.getText())) {
            		Dialogs.showMessageDialog("Pass", step.checkPass);
            		iconTick.setVisible(true);
            		iconCross.setVisible(false);
            	} else {
            		Dialogs.showErrorMessage("Error", step.checkFail);
            		iconCross.setVisible(true);
            		iconTick.setVisible(false);
            	}
            });

            // Pane with instruction and buttons
            GridPane stepPane = new GridPane();
            BorderPane interactionPane = new BorderPane();
            GridPane userInputPane = new GridPane();
            
            PaneTools.addGridRow(stepPane, 0, 0, "Step " + nStep, scrollPane);
//            PaneTools.addGridRow(stepPane, 1, 0, "Step " + nStep, new Separator());
            PaneTools.addGridRow(stepPane, 1, 0, "Step " + nStep, interactionPane);
            
            int inputPaneRow = 0;
            if (step.input != null)
            	PaneTools.addGridRow(userInputPane, inputPaneRow++, 1, "Write here!", tfInput);
            else if (step.choices != null) {
//            	for (var choice: step.choices) {
//            		PaneTools.addGridRow(userInputPane, 0, 0, "Write here!", new );
//            		
//            	}
            	// TODO
            }
            if (step.inputPredicate != null)
            	PaneTools.addGridRow(userInputPane, inputPaneRow-1, 0, "Check your answer", iconTick, iconCross, checkInput);
            if (step.checkFun != null)
            	PaneTools.addGridRow(userInputPane, inputPaneRow++, 0, "Check your answer", verifyBtn, verifyBtn, verifyBtn);
            
            if (step != steps.get(0))
            	interactionPane.setLeft(previousBtn);
            interactionPane.setRight(step != steps.get(steps.size() - 1) ? nextBtn : finishBtn);
            interactionPane.setCenter(userInputPane);
            BorderPane.setMargin(previousBtn, new Insets(0, 10, 0, 0));
            BorderPane.setMargin(nextBtn, new Insets(0, 0, 0, 10));
            GridPane.setHgrow(interactionPane, Priority.ALWAYS);
            GridPane.setHgrow(verifyBtn, Priority.ALWAYS);
            

            stepPane.setVgap(5.0);
            titledPane = new TitledPane("Step " + nStep + ": " + step.title, stepPane);
    	}
    	
    	TitledPane getPane() {
    		return titledPane;
    	}

    	Button getPreviousBtn() {
    		return previousBtn;
    	}
    	
    	Button getNextBtn() {
    		return nextBtn;
    	}
    	
    	Button getFinishButton() {
    		return finishBtn;
    	}
    }
    
}