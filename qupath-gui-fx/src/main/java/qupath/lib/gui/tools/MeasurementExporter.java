package qupath.lib.gui.tools;

import java.awt.image.BufferedImage;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;

import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import qupath.lib.gui.commands.SummaryMeasurementTableCommand;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectImageEntry;


/**
 * Helper class for exporting the measurements of one or more entries in a project.
 * 
 * @author Melvin Gelbard
 */
public class MeasurementExporter {
	
	private final static Logger logger = LoggerFactory.getLogger(MeasurementExporter.class);
	
	private Project<BufferedImage> project;
	
	private List<String> includeOnlyColumns = new ArrayList<String>();
	private List<String> excludeColumns = new ArrayList<String>();
	
	// Default: Exporting annotations
	private Class<? extends PathObject> type = PathAnnotationObject.class;
	
	private String separator;
	
	private List<ProjectImageEntry<BufferedImage>> imageList;
	
	public MeasurementExporter(Project<BufferedImage> project) {
		this.project = project;
	}
	
	public MeasurementExporter useDetections(boolean useDetection) {
		if (useDetection)
			this.type = PathDetectionObject.class;
		else
			this.type = PathAnnotationObject.class;
		return this;
	}
	
	/**
	 * Specify the columns that will be included in the export.
	 * The column names are case sensitive.
	 * @param includeOnlyColumns
	 */
	public MeasurementExporter includeOnlyColumns(String... includeOnlyColumns) {
		this.includeOnlyColumns = Arrays.asList(includeOnlyColumns);
		return this;
	}
	
	/**
	 * Specify the columns that will be excluded during the export.
	 * The column names are case sensitive.
	 * @param excludeColumns
	 */
	public MeasurementExporter excludeColumns(String... excludeColumns) {
		this.excludeColumns = Arrays.asList(excludeColumns);
		return this;
	}
	
	public MeasurementExporter separator(String sep) {
		this.separator = sep;
		return this;
	}
	
	public MeasurementExporter imageList(List<ProjectImageEntry<BufferedImage>> imageList) {
		this.imageList = imageList;
		return this;
	}
	
	public List<ProjectImageEntry<BufferedImage>> getImageList() {
		return imageList;
	}
	
	public List<String> getExcludeColumns() {
		return excludeColumns;
	}
	
	public List<String> getIncludeColumns() {
		return includeOnlyColumns;
	}
	
	public String getSeparator() {
		return separator;
	}
	
	public Class<? extends PathObject> getType() {
		return type;
	}
	
	/**
	 * Exports the measurements of one or more entries in the project.
	 * This function first opens all the images in the project to get 
	 * all the column names.
	 * Then, it opens again all the images in the project and writes measurements
	 * as it loops through all the entries. Not terribly efficient, better use
	 * exportAnnotationMeasurements2(...).

	 * @param pathOut
	 * @throws IOException
	 */
	public void exportAnnotationMeasurements(String pathOut) throws IOException {
		
		List<String> listCol = new LinkedList<>();
		
		// Get all columns to prepare header
		for (ProjectImageEntry<?> entry: imageList) {
			try {
				ImageData<?> imageData = entry.readImageData();
				ObservableMeasurementTableData model = new ObservableMeasurementTableData();
				model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
				String newCols = SummaryMeasurementTableCommand.getTableModelStrings(model, PathPrefs.getTableDelimiter(), excludeColumns).get(0);
				String[] newColsList = newCols.split("\t");
				for (int i = 0; i < newColsList.length; i++) {
					String col = newColsList[i];
					if (!listCol.contains(newColsList[i]) && !excludeColumns.contains(newColsList[i])){
						listCol.add(i, col);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		// To keep the same column order, just delete non-relevant columns
		if (includeOnlyColumns.size() > 0)
			listCol.removeIf(n -> !includeOnlyColumns.contains(n));

		// If no measurement to write
		if (listCol.size() == 0)
			return;
		
		// Write header
		FileWriter writer = new FileWriter(pathOut);
		for (int i = 0; i < listCol.size()-1; i++) {
			writer.append(listCol.get(i));
			writer.append(separator);
		}
		// Last column and "\n" to finish header
		writer.append(listCol.get(listCol.size()-1));
		writer.append("\n");

		
		// Write data
		for (ProjectImageEntry<?> entry: project.getImageList()) {
			
			try {
				ImageData<?> imageData = entry.readImageData();
				ObservableMeasurementTableData model = new ObservableMeasurementTableData();
				
				model.setImageData(imageData, imageData == null ? Collections.emptyList() : imageData.getHierarchy().getObjects(null, type));
				
				List<String> data = SummaryMeasurementTableCommand.getTableModelStrings(model, PathPrefs.getTableDelimiter(), excludeColumns);
				String[] header = data.get(0).split("\t");
				if (header.length <= 2)
					continue;
				// Start at index 1 because we skip the header
				for (int i = 1; i < data.size(); i++) {
					StringBuilder rowToWrite = new StringBuilder();
					String[] row = data.get(i).split("\t");
					int pos = 0;
					int lastIndexFound = 0;
					
					while (pos < listCol.size()) {
						boolean found = false;
						
						for (int j = lastIndexFound; j < header.length; j++) {
							if (header[j].equals(listCol.get(pos))) {
								if (row[j].equals("NaN")) {
									row[j] = "";
								}
								rowToWrite.append(row[j]);
								rowToWrite.append(separator);
								found = true;
								lastIndexFound = j;
								break;
							}
						}
						if (!found)
							rowToWrite.append(separator);
						pos++;
					}
					writer.append(rowToWrite);
					writer.append("\n");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		writer.flush();
		writer.close();		
	}
	
	
	
	/**
	 * Exports the measurements of one or more entries in the project.
	 * This function first opens all the images in the project to store 
	 * all the column names and values of all entries.
	 * Then, it loops through the maps containing the values to write
	 * them to given output file.
	 * @param pathOut
	 * @throws IOException
	 */
	public void exportAnnotationMeasurements2(String pathOut) throws IOException {
		long startTime = System.currentTimeMillis();
		
		Map<ProjectImageEntry<?>, String[]> imageCols = new HashMap<ProjectImageEntry<?>, String[]>();
		Map<ProjectImageEntry<?>, Integer> nImageEntries = new HashMap<ProjectImageEntry<?>, Integer>();
		List<String> allColumns = new ArrayList<String>();
		Multimap<String, String> valueMap = LinkedListMultimap.create();
		
		for (ProjectImageEntry<?> entry: imageList) {
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

			for (ProjectImageEntry<?> entry: imageList) {
				
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
	}

}
