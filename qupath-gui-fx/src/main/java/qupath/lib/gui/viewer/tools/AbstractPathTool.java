/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.viewer.tools;

import java.awt.Shape;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.viewer.ModeWrapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.QuPathViewerListener;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * Abstract implementation of a PathTool.
 * 
 * @author Pete Bankhead
 *
 */
abstract class AbstractPathTool implements PathTool, QuPathViewerListener {
	
	private final static Logger logger = LoggerFactory.getLogger(AbstractPathTool.class);

	QuPathViewer viewer;
	ModeWrapper modes;
	
	private PathObject constrainedAreaParent;
	private Geometry constrainedAreaBounds;
	private Collection<Geometry> constrainedAreasToRemove = new LinkedHashSet<>();
	
	/**
	 * Constructor.
	 * @param modes property storing the current selected Mode within QuPath.
	 */
	AbstractPathTool(ModeWrapper modes) {
		this.modes = modes;
	}
	
	void ensureCursorType(Cursor cursor) {
		// We don't want to change a waiting cursor unnecessarily
		Cursor currentCursor = viewer.getCursor();
		if (currentCursor == null || currentCursor == Cursor.WAIT)
			return;
		viewer.setCursor(cursor);
	}
	
	/**
	 * Returns true if the tool requests that pixel coordinates be snapped to integer values.
	 * Default returns true.
	 * 
	 * @return
	 */
	protected boolean requestPixelSnapping() {
		return PathPrefs.usePixelSnapping();
	}
	
	protected QuPathViewer getViewer() {
		return viewer;
	}
	
	protected Point2D mouseLocationToImage(MouseEvent e, boolean constrainToBounds, boolean snapToPixel) {
		var p = viewer.componentPointToImagePoint(e.getX(), e.getY(), null, constrainToBounds);
		if (snapToPixel)
			p.setLocation(Math.floor(p.getX()), Math.floor(p.getY()));
		return p;
	}

	
	/**
	 * Query whether parent clipping should be applied.
	 * 
	 * <p>This might depend upon the MouseEvent.
	 * 
	 * @param e
	 * @return
	 */
	boolean requestParentClipping(MouseEvent e) {
		return PathPrefs.getClipROIsForHierarchy() != (e.isShiftDown() && e.isShortcutDown());
	}
	
	
	/**
	 * Apply clipping based on the current parent object.
	 * <p>
	 * Returns an empty ROI if this result of the clipping is an empty area.
	 * 
	 * @param currentROI
	 * @return
	 */
	ROI refineROIByParent(ROI currentROI) {
		// Don't do anything with lines
		if (currentROI.isLine())
			return currentROI;
		// Handle areas
		var geometry = currentROI.getGeometry();
		geometry = refineGeometryByParent(geometry);
		if (geometry.isEmpty())
			return ROIs.createEmptyROI();
		else
			return GeometryTools.geometryToROI(geometry, currentROI.getImagePlane());
	}
	
	Geometry refineGeometryByParent(Geometry geometry) {
		return refineGeometryByParent(geometry, true);
	}
	
	private Geometry refineGeometryByParent(Geometry geometry, boolean tryAgain) {
		try {
			if (constrainedAreaBounds != null)
				geometry = geometry.intersection(constrainedAreaBounds);
			int count = 0;
			if (!constrainedAreasToRemove.isEmpty()) {
				var envelope = geometry.getEnvelopeInternal();
				for (var temp : constrainedAreasToRemove) {
					// Note: the relate operation tests for interior intersections, but can be very slow
					// Ideally, we would reduce these tests
					if (envelope.intersects(temp.getEnvelopeInternal()) && geometry.relate(temp, "T********")) {
						geometry = geometry.difference(temp);
						envelope = geometry.getEnvelopeInternal();
						count++;
					}
				}
			}
			logger.debug("Clipped ROI with {} geometries", count);
		} catch (Exception e) {
			if (tryAgain) {
				logger.warn("First Error refining ROI, will retry after buffer(0): {}", e.getLocalizedMessage());
				return refineGeometryByParent(geometry.buffer(0.0), false);
			}
			logger.warn("Error refining ROI: {}", e.getLocalizedMessage());
			logger.debug("", e);
		}
		return geometry;
	}
	
	
	/**
	 * Set the parent that may be used to constrain a new ROI, if possible.
	 * 
	 * @param hierarchy object hierarchy containing potential constraining objects
	 * @param xx x-coordinate in the image space of the starting point for the new object
	 * @param yy y-coordinate in the image space of the starting point for the new object
	 * @param exclusions objects not to consider (e.g. the new ROI being created)
	 */
	synchronized void setConstrainedAreaParent(final PathObjectHierarchy hierarchy, double xx, double yy, Collection<PathObject> exclusions) {
		
		// Reset parent area & its descendant annotation areas
		constrainedAreaBounds = null;
		constrainedAreasToRemove.clear();
		
		// Identify the smallest area annotation that contains the specified point
		constrainedAreaParent = getSelectableObjectList(xx, yy)
				.stream()
				.filter(p -> !p.isDetection() && p.hasROI() && p.getROI().isArea() && !exclusions.contains(p))
				.sorted(Comparator.comparing(p -> p.getROI().getArea()))
				.findFirst()
				.orElseGet(() -> null);
				
//		if (constrainedAreaParent == null)
//			return;
		
		// Check the parent is a valid potential parent
		boolean fullImage = false;
		if (constrainedAreaParent == null || !(constrainedAreaParent.hasROI() && constrainedAreaParent.getROI().isArea())) {
			constrainedAreaParent = hierarchy.getRootObject();
			fullImage = true;
		}
		
		// Get the parent Geometry
		if (constrainedAreaParent.hasROI() && constrainedAreaParent.getROI().isArea())
			constrainedAreaBounds = constrainedAreaParent.getROI().getGeometry();
		
		// Figure out what needs to be subtracted
		Collection<PathObject> toRemove;
		if (fullImage)
			toRemove = hierarchy.getAnnotationObjects();
		else
			toRemove = hierarchy.getObjectsForRegion(PathAnnotationObject.class, ImageRegion.createInstance(constrainedAreaParent.getROI()), null);
			
		Envelope boundsEnvelope = constrainedAreaBounds == null ? null : constrainedAreaBounds.getEnvelopeInternal();
		for (PathObject child : toRemove) {
			if (child.isDetection() || child == constrainedAreaParent|| !child.hasROI() || 
					!child.getROI().isArea() || child.getROI().contains(xx, yy))
				continue;
			Geometry childArea = child.getROI().getGeometry();
			Envelope childEnvelope = childArea.getEnvelopeInternal();
			// Quickly filter out objects that don't intersect with the bounds, or which entirely cover it
			if (constrainedAreaBounds != null &&
					(!boundsEnvelope.intersects(childEnvelope) || 
							(childEnvelope.covers(boundsEnvelope) && childArea.covers(constrainedAreaBounds))))
				continue;
			constrainedAreasToRemove.add(childArea);
		}
	}
	
	
	synchronized void resetConstrainedAreaParent() {
		this.constrainedAreaParent = null;
		this.constrainedAreaBounds = null;
		this.constrainedAreasToRemove.clear();;
	}
	
	
	synchronized PathObject getCurrentParent() {
		return constrainedAreaParent;
	}
	
	/**
	 * When drawing a constrained ROI, get a Geometry defining the outer limits.
	 * @return
	 */
	synchronized Geometry getConstrainedAreaBounds() {
		return constrainedAreaBounds;
	}
	
//	/**
//	 * When drawing a constrained ROI, get a Geometry defining the inner area that should be 'subtracted'.
//	 * @return
//	 */
//	synchronized Geometry getConstrainedAreaToSubtract() {
//		return constrainedAreaToRemove;
//	}
	
	
	
	/**
	 * Try to select an object with a ROI overlapping a specified coordinate.
	 * If there is no object found, the current selected object will be reset (to null).
	 * 
	 * @param x
	 * @param y
	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
	 * @return true if any object was selected
	 */
	boolean tryToSelect(double x, double y, int searchCount, boolean addToSelection) {
		return tryToSelect(x, y, searchCount, addToSelection, false);
	}
	
	boolean tryToSelect(double x, double y, int searchCount, boolean addToSelection, boolean toggleSelection) {
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return false;
		PathObject pathObject = getSelectableObject(x, y, searchCount);
		if (toggleSelection && hierarchy.getSelectionModel().getSelectedObject() == pathObject)
			hierarchy.getSelectionModel().deselectObject(pathObject);
		else
			viewer.setSelectedObject(pathObject, addToSelection);
		// Reset selection if we have nothing
		if (pathObject == null && addToSelection)
			viewer.setSelectedObject(null);
		return pathObject != null;
	}
	
	
	/**
	 * Determine which object would be selected by a click in this location - but do not actually apply the selection.
	 * 
	 * @param x
	 * @param y
	 * @param searchCount - how far up the hierarchy to go, i.e. how many parents to check if objects overlap
	 * @return true if any object was selected
	 */
	PathObject getSelectableObject(double x, double y, int searchCount) {
		List<PathObject> pathObjectList = getSelectableObjectList(x, y);
		if (pathObjectList == null || pathObjectList.isEmpty())
			return null;
//		int ind = pathObjectList.size() - searchCount % pathObjectList.size() - 1;
		int ind = searchCount % pathObjectList.size();
		return pathObjectList.get(ind);
	}
	
	
	/**
	 * Get a list of all selectable objects overlapping the specified x, y coordinates, ordered by depth in the hierarchy
	 * @param x
	 * @param y
	 * @return
	 */
	List<PathObject> getSelectableObjectList(double x, double y) {
		PathObjectHierarchy hierarchy = viewer.getHierarchy();
		if (hierarchy == null)
			return Collections.emptyList();
		
		Collection<PathObject> pathObjects = PathObjectTools.getObjectsForLocation(
				hierarchy, x, y, viewer.getZPosition(), viewer.getTPosition(), viewer.getMaxROIHandleSize());
		if (pathObjects.isEmpty())
			return Collections.emptyList();
		List<PathObject> pathObjectList = new ArrayList<>(pathObjects);
		if (pathObjectList.size() == 1)
			return pathObjectList;
		Collections.sort(pathObjectList, PathObjectHierarchy.HIERARCHY_COMPARATOR);
		return pathObjectList;
	}
	
	
	
	public void mouseClicked(MouseEvent e) {}

	public void mouseDragged(MouseEvent e) {}

	public void mouseEntered(MouseEvent e) {}

	public void mouseExited(MouseEvent e) {}
	
	public void mouseMoved(MouseEvent e) {}

	public void mousePressed(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
			return;
		}
		
		Object source = e.getSource();
		if (source instanceof Node) {
			Node node = (Node)source;
			if (node.isFocusTraversable() && !node.isFocused()) {
				node.requestFocus();
				e.consume();
			}
		}
//		// Ensure we can focus this component
//		Component component = e.getComponent();
//		if (!component.isFocusable()) {
//			component.setFocusable(true);
//		}
//		component.requestFocus();
	}

	public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger()) {
			e.consume();
			return;
		}
	}
	
	
	@Override
	public void registerTool(QuPathViewer viewer) {
		// Disassociate from any previous viewer
		if (this.viewer != null)
			deregisterTool(this.viewer);
		
		// Associate with new viewer
		this.viewer = viewer;
		if (viewer != null) {
			logger.trace("Registering {} to viewer {}", this, viewer);
			Node canvas = viewer.getView();
			
			canvas.setOnMouseDragged(e -> mouseDragged(e));
			canvas.setOnMouseDragReleased(e -> mouseDragged(e));
			
			canvas.setOnMouseMoved(e -> mouseMoved(e));

			canvas.setOnMouseClicked(e -> mouseClicked(e));
			canvas.setOnMousePressed(e -> mousePressed(e));
			canvas.setOnMouseReleased(e -> mouseReleased(e));
			
			canvas.setOnMouseEntered(e -> mouseEntered(e));
			canvas.setOnMouseExited(e -> mouseExited(e));

			viewer.addViewerListener(this);
		}
	}

	@Override
	public void deregisterTool(QuPathViewer viewer) {
		if (this.viewer == viewer) {
			
			logger.trace("Deregistering {} from viewer {}", this, viewer);

			this.viewer = null;
			
			Node canvas = viewer.getView();
			canvas.setOnMouseDragged(null);
			canvas.setOnMouseDragReleased(null);
			
			canvas.setOnMouseMoved(null);

			canvas.setOnMouseClicked(null);
			canvas.setOnMousePressed(null);
			canvas.setOnMouseReleased(null);
			
			canvas.setOnMouseEntered(null);
			canvas.setOnMouseExited(null);
			
			viewer.removeViewerListener(this);
		}
	}
	
	
	
	@Override
	public void imageDataChanged(QuPathViewer viewer, ImageData<BufferedImage> imageDataOld,
			ImageData<BufferedImage> imageDataNew) {}


	@Override
	public void visibleRegionChanged(QuPathViewer viewer, Shape shape) {}


	@Override
	public void selectedObjectChanged(QuPathViewer viewer, PathObject pathObjectSelected) {}


	@Override
	public void viewerClosed(QuPathViewer viewer) {}
	
	
}
