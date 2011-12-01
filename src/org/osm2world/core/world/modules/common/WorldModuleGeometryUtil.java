package org.osm2world.core.world.modules.common;

import static java.lang.Math.toRadians;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.osm2world.core.map_elevation.data.GroundState;
import org.osm2world.core.math.GeometryUtil;
import org.osm2world.core.math.PolygonXYZ;
import org.osm2world.core.math.VectorXYZ;
import org.osm2world.core.math.VectorXZ;
import org.osm2world.core.world.creation.WorldModule;
import org.osm2world.core.world.data.WorldObject;
import org.osm2world.core.world.data.WorldObjectWithOutline;

/**
 * offers some geometry-related utility functions for {@link WorldModule}s
 */
public final class WorldModuleGeometryUtil {

	private WorldModuleGeometryUtil() { }
	
	/**
	 * creates the vectors for a vertical triangle strip
	 * at a given elevation above a line of points
	 */
	public static final VectorXYZ[] createVectorsForVerticalTriangleStrip(
			List<? extends VectorXYZ> baseLine, float stripLowerYBound, float stripUpperYBound) {

		VectorXYZ[] result = new VectorXYZ[baseLine.size() * 2];

		for (int i = 0; i < baseLine.size(); i++) {

			VectorXYZ basePos = baseLine.get(i);
			
			result[i*2] = new VectorXYZ(
					basePos.getX(),
					basePos.getY() + stripLowerYBound,
					basePos.getZ());

			result[i*2+1] = new VectorXYZ(
					basePos.getX(),
					basePos.getY() + stripUpperYBound,
					basePos.getZ());

		}
		
		return result;
		
	}

	/**
	 * creates a triangle strip between two outlines with identical number of vectors
	 */
	public static final VectorXYZ[] createVectorsForTriangleStripBetween(
			List<? extends VectorXYZ> leftOutline, List<? extends VectorXYZ> rightOutline) {

		assert leftOutline.size() == rightOutline.size();
		
		VectorXYZ[] vs = new VectorXYZ[leftOutline.size() * 2];
		
		for (int i = 0; i < leftOutline.size(); i++) {
			vs[i*2] = leftOutline.get(i);
			vs[i*2+1] = rightOutline.get(i);
		}
		
		return vs;
		
	}
			
	
	/**
	 * @param ratio  0 is at left outline, 1 at right outline
	 */
	public static final List<VectorXYZ> createLineBetween(
			List<VectorXYZ> leftOutline, List<VectorXYZ> rightOutline, float ratio) {
		
		assert leftOutline.size() == rightOutline.size();
		
		List<VectorXYZ> result = new ArrayList<VectorXYZ>(leftOutline.size());
		
		for (int i = 0; i < leftOutline.size(); i++) {
			result.add(GeometryUtil.interpolateBetween(
					leftOutline.get(i), rightOutline.get(i), ratio));
		}
		
		return result;
		
	}
	

	//TODO: many uses of VisualizationUtil#drawColumn are a special case of this
	/**
	 * creates triangle stip vectors for a shape extruded along a line of coordinates
	 * 
	 * @param shape          shape relative to origin
	 * @param extrusionPath  nodes to extrude the shape along; needs at least 2 nodes
	 * @param upVectors      vector for "up" direction at each extrusion path node.
	 *                       You can use {@link Collections#nCopies(int, Object)}
	 *                       if you want the same up vector for all nodes.
	 * @return               list of vector arrays for triangle strips
	 */
	public static final List<VectorXYZ[]> createShapeExtrusionAlong(
			VectorXYZ[] shape,
			List<VectorXYZ> extrusionPath,
			List<VectorXYZ> upVectors) {
		
		if (extrusionPath.size() < 2) {
			throw new IllegalArgumentException("extrusion path needs at least 2 nodes");
		} else if (extrusionPath.size() != upVectors.size()) {
			throw new IllegalArgumentException("extrusionPath and upVectors must have same size");
		}
		
		VectorXYZ[][] shapeVectors = new VectorXYZ[extrusionPath.size()][shape.length];

		/*
		 * create shape at each node of the extrusion path.
		 * Special handling for first and last node,
		 * where calculation of "forward" vector is different.
		 */
		
		shapeVectors[0] = transformShape(shape,
				extrusionPath.get(0),
				extrusionPath.get(1).subtract(extrusionPath.get(0)).normalize(),
				upVectors.get(0));
		
		for (int pathI = 1; pathI < extrusionPath.size()-1; pathI ++) {
						
			VectorXYZ forwardVector =
				extrusionPath.get(pathI+1).subtract(extrusionPath.get(pathI-1));
			forwardVector = forwardVector.normalize();
			
			shapeVectors[pathI] = transformShape(shape,
					extrusionPath.get(pathI),
					forwardVector,
					upVectors.get(pathI));
			
		}

		int last = extrusionPath.size()-1;
		shapeVectors[last] = transformShape(shape,
				extrusionPath.get(last),
				extrusionPath.get(last).subtract(extrusionPath.get(last-1)).normalize(),
				upVectors.get(last));
		
		/* draw triangle strips */
		
		List<VectorXYZ[]> triangleStripList = new ArrayList<VectorXYZ[]>(shape.length-1);
		
		for (int i = 0; i+1 < shape.length; i++) {
			
			VectorXYZ[] triangleStripVectors = new VectorXYZ[2*shapeVectors.length];
			
			for (int j=0; j < shapeVectors.length; j++) {
				
				triangleStripVectors[j*2+1] = shapeVectors[j][i];
				triangleStripVectors[j*2+0] = shapeVectors[j][i+1];
				
			}
			
			triangleStripList.add(triangleStripVectors);
			
		}
				
		return triangleStripList;
		
	}
	
	/**
	 * creates an rotated version of an array of vectors
	 * by rotating them by the given angle around the parallel of the x axis
	 * defined by the given Y and Z coordinates
	 * 
	 * @angle  rotation angle in degrees
	 */
	public static final VectorXYZ[] rotateShapeX(VectorXYZ[] shape,
			double angle, double posY, double posZ) {
		
		VectorXYZ[] result = new VectorXYZ[shape.length];

		for (int i = 0; i < shape.length; ++i) {
			result[i] = shape[i].add(0f, -posY, -posZ);
			result[i] = result[i].rotateX(toRadians(angle));
			result[i] = result[i].add(0f, posY, posZ);
		}
		
		return result;
		
	}
	
	/**
	 * moves a shape that was defined at the origin to a new position.
	 * This is used by {@link #createShapeExtrusionAlong(VectorXYZ[], List, List)}
	 * 
	 * @param center   new center coordinate
	 * @param forward  new forward direction (unit vector)
	 * @param up       new up direction (unit vector)
	 * @return         array of 3d vectors; same length as shape
	 */
	public static final VectorXYZ[] transformShape (VectorXYZ[] shape,
			VectorXYZ center, VectorXYZ forward, VectorXYZ up) {

		VectorXYZ[] result = new VectorXYZ[shape.length];
		
		VectorXYZ right = forward.cross(up);
		
		final double[][] m = { //rotation matrix
				{right.x,   right.y,   right.z},
				{up.x,      up.y,      up.z},
				{forward.x, forward.y, forward.z}
		};
		
		for (int i = 0; i < shape.length; i++) {
			
			VectorXYZ v = shape[i];
	
			v = new VectorXYZ(
					m[0][0] * v.x + m[1][0] * v.y + m[2][0] * v.z,
					m[0][1] * v.x + m[1][1] * v.y + m[2][1] * v.z,
					m[0][2] * v.x + m[1][2] * v.y + m[2][2] * v.z
					);
			
			v = v.add(center);
			
			result[i] = v;
			
		}
		
		return result;
		
	}
	

	/**
	 * checks whether a position is on the area covered by a
	 * {@link WorldObjectWithOutline} from a collection
	 * of {@link WorldObject}s.
	 * 
	 * This can be used to avoid placing trees, bridge pillars
	 * and other randomly distributed features on roads, rails
	 * or other similar places where they don't belong.
	 * 
	 * @return true if the position is on at least one of the
	 *         {@link WorldObjectWithOutline} from the collection
	 */
	public static final boolean piercesWorldObject(VectorXZ pos,
			Collection<WorldObject> worldObjects) {
		
		//TODO: add support for avoiding a radius around the position, too.
		//this is easily possible once "inflating"/"shrinking" polygons is supported [would also be useful for water bodies etc.]
		
		for (WorldObject worldObject : worldObjects) {
			
			if (worldObject.getGroundState() == GroundState.ON
				&& (worldObject instanceof WorldObjectWithOutline)) {
				
				PolygonXYZ otherOutline =
					((WorldObjectWithOutline)worldObject).getOutlinePolygon();
				
				if (otherOutline != null &&
						otherOutline.getXZPolygon().isSimple() &&
						otherOutline.getSimpleXZPolygon().contains(pos)) {
					return true;
				}
				
			}
		
		}
		
		return false;
		
	}
	
}
