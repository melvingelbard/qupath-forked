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

import java.util.List;
import java.util.function.Predicate;

import qupath.lib.gui.QuPathGUI;

public class QuPathTutorialStep {
    	
	String title;
    String instruction;
    Predicate<QuPathGUI> checkFun;
    String checkPass;
    String checkFail;
    String input;
    Predicate<String> inputPredicate;
    List<String> choices;
    Predicate<String> choicesFun;
    boolean singleChoice;
    
    public static class Builder {
    	// Required parameters
    	private final String instruction;
    	
    	// Optional parameters
    	private String title = "";
    	private Predicate<QuPathGUI> checkFun = null;
    	private String checkPass = "";
    	private String checkFail = "";
    	private String input = null;
    	private Predicate<String> inputPredicate = null;
    	private List<String> choices = null;
    	private Predicate<String> choicesFun = null;
    	private boolean singleChoice = false;
        
        public Builder(String instruction) {
        	this.instruction = instruction;
        }
        
        public Builder title(String title) {
        	this.title = title;
        	return this;
        }

        public Builder checkFun(Predicate<QuPathGUI> checkFun) {
        	this.checkFun = checkFun;
        	return this;
        }

        public Builder checkPass(String checkPass) {
        	this.checkPass = checkPass;
        	return this;
        }

        public Builder checkFail(String checkFail) {
        	this.checkFail = checkFail;
        	return this;
        }
        
        
        public Builder input(String input, Predicate<String> inputPredicate) {
        	this.input = input;
        	this.inputPredicate = inputPredicate;
        	return this;
        }
        
        public Builder choices(List<String> choices, Predicate<String> fun) {
        	return this.choices(choices, fun, false);
        }

        public Builder choices(List<String> choices, Predicate<String> fun, boolean singleChoice) {
        	this.choices = choices;
        	this.choicesFun = fun;
        	this.singleChoice = singleChoice;
        	return this;
        }
        
        public QuPathTutorialStep build() {
        	return new QuPathTutorialStep(this);
        }
    }
    
    
    private QuPathTutorialStep(Builder builder) {
    	this.title = builder.title;
    	this.checkFail = builder.checkFail;
    	this.checkFun = builder.checkFun;
    	this.checkPass = builder.checkPass;
    	this.choices = builder.choices;
    	this.choicesFun = builder.choicesFun;
    	this.input = builder.input;
    	this.inputPredicate = builder.inputPredicate;
    	this.instruction = builder.instruction;
    }
}
