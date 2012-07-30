package org.concord.energy3d.model;

import java.util.ArrayList;

import org.concord.energy3d.util.MeshLib;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.geometry.polygon.PolygonPoint;

import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyVector3;

public class HipRoof extends Roof {
	private static final long serialVersionUID = 1L;
	private transient boolean recalculateEditPoints;

	public HipRoof() {
		super(1, 3, 0.5);
	}

	@Override
	public void setPreviewPoint(final int x, final int y) {
		if (editPointIndex == -1) {
			pickContainer(x, y, Wall.class);
			recalculateEditPoints = true;
		} else if (editPointIndex == 0) {
			final ReadOnlyVector3 base = getCenter();
			Vector3 p = MeshLib.closestPoint(base, Vector3.UNIT_Z, x, y);
			p = grid(p, getAbsPoint(editPointIndex), getGridSize());
			height = Math.max(0, p.getZ() - base.getZ());
		} else if (editPointIndex == 1 || editPointIndex == 2) {
			Vector3 p = MeshLib.closestPoint(getAbsPoint(editPointIndex), Vector3.UNIT_Y, x, y);
			p = grid(p, getAbsPoint(editPointIndex), getGridSize(), false);
			points.get(editPointIndex).set(toRelative(p, container.getContainer()));
		}
		draw();
		drawWalls();
		if (container != null)
			setEditPointsVisible(true);

	}

	@Override
	protected Polygon makePolygon(final ArrayList<PolygonPoint> wallUpperPoints) {
		final Polygon polygon = new Polygon(wallUpperPoints);
		final Vector3 p1 = getAbsPoint(1);
		final PolygonPoint roofUpperPoint1 = new PolygonPoint(p1.getX(), p1.getY(), p1.getZ());
		final Vector3 p2 = getAbsPoint(2);
		final PolygonPoint roofUpperPoint2 = new PolygonPoint(p2.getX(), p2.getY(), p2.getZ());
		polygon.addSteinerPoint(roofUpperPoint1);
		if (!roofUpperPoint2.equals(roofUpperPoint1))
			polygon.addSteinerPoint(roofUpperPoint2);
		return polygon;
	}

	@Override
	protected void processRoofPoints(final ArrayList<PolygonPoint> wallUpperPoints, final ArrayList<ReadOnlyVector3> wallNormals) {
		super.processRoofPoints(wallUpperPoints, wallNormals);
		final ReadOnlyVector3 center = getCenter();
		if (recalculateEditPoints) {
			// upper points
			points.get(0).set(toRelative(center, container.getContainer()).addLocal(0, 0, height));
			if (editPointIndex == -1) {
				points.get(1).set(toRelative(center, container.getContainer()).addLocal(0, -0.2, height));
				points.get(2).set(toRelative(center, container.getContainer()).addLocal(0, 0.2, height));
			}
			recalculateEditPoints = false;
		} else {
			points.get(0).setZ(center.getZ() + height);
			points.get(1).setZ(center.getZ() + height);
			points.get(2).setZ(center.getZ() + height);
		}
	}
}
