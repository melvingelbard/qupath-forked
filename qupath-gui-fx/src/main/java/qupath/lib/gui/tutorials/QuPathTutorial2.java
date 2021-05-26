/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

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
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Pagination;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.gui.tools.PaneTools;

public class QuPathTutorial2 {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPathTutorial.class);
	
	private final QuPathGUI qupath;
	private final String title;
	private final List<QuPathTutorialStep> steps;
    private final GridPane mainPane = new GridPane();
    private final Stage dialog = new Stage();
    private final ObjectProperty<QuPathTutorialStep> currentStep = new SimpleObjectProperty<>();
    
    private static final Node iconTick = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.TICK);
    private static final Node iconCross = IconFactory.createNode(QuPathGUI.TOOLBAR_ICON_SIZE, QuPathGUI.TOOLBAR_ICON_SIZE, IconFactory.PathIcons.CROSS);
    
    public QuPathTutorial2(QuPathGUI qupath, String title, List<QuPathTutorialStep> steps) {
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

        currentStep.set(steps.get(0));
        
        Pagination pagination = new Pagination(steps.size());
        PaneTools.addGridRow(mainPane, row++, 0, null, new Separator());
        PaneTools.addGridRow(mainPane, row++, 0, null, pagination);

//        pagination.setOnHiding(event -> {
//			var choice = Dialogs.showConfirmDialog("Leave tutorial", "Are you sure you want to finish and leave this tutorial?");
//			if (choice)
//				dialog.close();
//		});
        
        pagination.setPageFactory(index -> new TutorialStepPane(steps.get(index), index).pane);
        pagination.setMinWidth(250);

        dialog.setTitle("QuPath Tutorial");
        dialog.setMinWidth(250);
        dialog.setResizable(true);
        dialog.setScene(new Scene(mainPane));
        dialog.initOwner(QuPathGUI.getInstance().getStage());
        dialog.showAndWait();
    }
    
    
    private class TutorialStepPane {
    	
    	private final GridPane pane;
    	private final Button finishBtn;
    	
    	TutorialStepPane(QuPathTutorialStep step, int nStep) {    		
    		// Scroll pane for the main instructions
    		ScrollPane scrollPane = new ScrollPane();
    		scrollPane.setPrefSize(250, 120);
    		scrollPane.setContent(new Text(step.instruction));
    		scrollPane.setStyle("-fx-background-color:transparent;");
    		
            // Create button 
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
            pane = new GridPane();
            pane.setMinWidth(400);
            BorderPane interactionPane = new BorderPane();
            GridPane userInputPane = new GridPane();
            
            PaneTools.addGridRow(pane, 0, 0, "Step " + nStep, scrollPane);
//            PaneTools.addGridRow(stepPane, 1, 0, "Step " + nStep, new Separator());
            PaneTools.addGridRow(pane, 1, 0, "Step " + nStep, interactionPane);
            
            int inputPaneRow = 0;
            if (step.input != null) {
            	PaneTools.addGridRow(userInputPane, inputPaneRow++, 0, "Write here!", tfInput);
            	GridPane.setMargin(tfInput, new Insets(0.0, 10.0, 0.0, 10.0));
            	if (step.inputPredicate != null)
                	PaneTools.addGridRow(userInputPane, inputPaneRow-1, 2, null, checkInput, iconTick, iconCross);
                if (step.checkFun != null)
                	PaneTools.addGridRow(userInputPane, inputPaneRow++, 1, "Check your answer", verifyBtn, verifyBtn, verifyBtn);
            } else if (step.choices != null) {
            	List<CheckBox> checkBoxes = new ArrayList<>();
            	for (var choice: step.choices) {
            		CheckBox checkBox = new CheckBox(choice);
            		checkBoxes.add(checkBox);
            		PaneTools.addGridRow(userInputPane, inputPaneRow++, 0, null, checkBox);
            		logger.error(step.singleChoice + "");
            		if (step.singleChoice) {
            			checkBox.selectedProperty().addListener((v, o, n) -> {
            				logger.error("CHANGE");
            				if (!n)
            					return;
            				userInputPane.getChildren().forEach(child -> {
            					if (child.getClass() == CheckBox.class && child != checkBox)
            						((CheckBox)child).setSelected(false);
            				});
            			});
            		}
            	}
            	if (step.choicesFun != null) {
            		PaneTools.addGridRow(userInputPane, inputPaneRow++, 0, "Check your answer", verifyBtn, verifyBtn, verifyBtn);
            		verifyBtn.setOnAction(e -> {
            			var success = true;
            			for (var box: checkBoxes) {
            				if (box.isSelected() != step.choicesFun.test(box.getText()))
            					success = false;
            			}
            			if (success) {
	            			Dialogs.showMessageDialog("Pass", step.checkPass);
	            			iconTick.setVisible(true);
	            			iconCross.setVisible(false);
            			} else {
            				Dialogs.showErrorMessage("Error", step.checkFail);
                    		iconCross.setVisible(true);
                    		iconTick.setVisible(false);
            			}
            				
            		});
            	}
            }
            
            interactionPane.setRight(step != steps.get(steps.size() - 1) ? null : finishBtn);
            interactionPane.setCenter(userInputPane);
            GridPane.setHgrow(interactionPane, Priority.ALWAYS);
            GridPane.setHgrow(tfInput, Priority.ALWAYS);
            GridPane.setHgrow(verifyBtn, Priority.ALWAYS);
            
//            WebView browser = new WebView();
//            WebEngine webEngine = browser.getEngine();
//            webEngine.load("https://qupath.readthedocs.io/en/latest/");
//            PaneTools.addGridRow(pane, 1, 0, null, browser);
            
            GridPane.setMargin(scrollPane, new Insets(10.0, 10.0, 10.0, 10.0));
            pane.setVgap(5.0);
    	}
    }
    
}