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
    
    public static class Builder {
    	// Required parameters
    	private final String instruction;
    	
    	// Optional parameters
    	String title = "";
    	Predicate<QuPathGUI> checkFun = null;
        String checkPass = "";
        String checkFail = "";
        String input = null;
        Predicate<String> inputPredicate = null;
        List<String> choices = null;
        
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
        
        public Builder choices(List<String> choices) {
        	this.choices = choices;
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
    	this.input = builder.input;
    	this.inputPredicate = builder.inputPredicate;
    	this.instruction = builder.instruction;
    }
}
