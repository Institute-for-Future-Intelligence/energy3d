package org.concord.energy3d.model;

import java.util.ArrayList;

import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.poly2tri.geometry.polygon.Polygon;
import org.poly2tri.geometry.polygon.PolygonPoint;

import com.ardor3d.math.Plane;
import com.ardor3d.math.Ray3;
import com.ardor3d.math.Vector2;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyVector3;

public class CustomRoof extends Roof {
	private static final long serialVersionUID = 1L;
	private static final double GRID_SIZE = 0.5;
	transient private boolean recalculateEditPoints;

	public CustomRoof() {
		super(1, 1, 0.5);
	}

	public void setPreviewPoint(int x, int y) {
		System.out.println("----------- CustomerRoof.setPreview(" + x + ", " + y);
		if (container != null)
			points.get(0).set(toRelative(getCenter(), container.getContainer())).addLocal(0, 0, height);

		if (editPointIndex == -1) {
			recalculateEditPoints = true;
			pickContainer(x, y, Wall.class);
		} else if (editPointIndex == 0) {
			final ReadOnlyVector3 base = getCenter();
			Vector3 p = closestPoint(base, Vector3.UNIT_Z, x, y);
			p = grid(p, GRID_SIZE);
			height = Math.max(0, p.getZ() - container.getPoints().get(1).getZ());
			final double z = container.getPoints().get(1).getZ() + height;
			for (final Vector3 v : points)
				v.setZ(z);
		} else if (gableEditPointToWallMap.containsKey(editPointIndex)) {
			final Wall wall = gableEditPointToWallMap.get(editPointIndex);
			final Vector3 base = wall.getAbsPoint(0);
			final Vector3 dir = wall.getAbsPoint(2).subtract(base, null).normalizeLocal();
			base.setZ(container.getPoints().get(1).getZ() + height);
			Vector3 p = closestPoint(base, dir, x, y);
			p = grid(p, GRID_SIZE);
			p = closestPoint(base, dir, p, Vector3.NEG_UNIT_Z);
			points.get(editPointIndex).set(toRelative(p, container.getContainer()));
		} else {
			final Ray3 pickRay = SceneManager.getInstance().getCanvas().getCanvasRenderer().getCamera().getPickRay(new Vector2(x, y), false, null);
			Vector3 p = new Vector3();
			if (pickRay.intersectsPlane(new Plane(Vector3.UNIT_Z, points.get(0).getZ()), p)) {
				p = grid(p, GRID_SIZE);
				points.get(editPointIndex).set(toRelative(p, container.getContainer()));
			}
		}
		draw();
		drawWalls();
		if (container != null) {
			showPoints();
		}
//		Scene.getInstance().updateTextSizes();
	}

	protected Polygon makePolygon(ArrayList<PolygonPoint> wallUpperPoints) {
		final Polygon polygon = new Polygon(wallUpperPoints);
		final ArrayList<ReadOnlyVector3> steinerPoints = new ArrayList<ReadOnlyVector3>();
		for (int i = 1; i < points.size(); i++) {
			final Vector3 p = getAbsPoint(i);
			if (!steinerPoints.contains(p))
				steinerPoints.add(p);
		}
		for (final ReadOnlyVector3 p : steinerPoints)
			polygon.addSteinerPoint(new PolygonPoint(p.getX(), p.getY(), p.getZ()));
		return polygon;
	}

	protected void processRoofPoints(ArrayList<PolygonPoint> wallUpperPoints, ArrayList<ReadOnlyVector3> wallNormals) {
		super.processRoofPoints(wallUpperPoints, wallNormals);

		if (recalculateEditPoints) {
			recalculateEditPoints = false;

			// add or update edit points
			final double z = container.getPoints().get(1).getZ() + height;
			if (wallUpperPoints.size() > points.size()) {
				final Vector3 v = new Vector3();
				for (int i = 0; i < wallUpperPoints.size() - 1; i = i + 1) {
					final PolygonPoint p1 = wallUpperPoints.get(i);
					final PolygonPoint p2 = wallUpperPoints.get(i + 1);
					// middle of wall = (p1 + p2) / 2
					v.set(p1.getX() + p2.getX(), p1.getY() + p2.getY(), 0).multiplyLocal(0.5);
					v.setZ(z);
					// add -normal*0.2 to middle point of wall
					v.addLocal(wallNormals.get(i).multiply(0.2, null).negateLocal());
					v.set(toRelative(v, container.getContainer()));
					if (i + 1 < points.size())
						points.get(i + 1).set(v);
					else
						points.add(v.clone());
				}
			}
		} else {
			for (final Vector3 p : points)
				p.setZ(container.getPoints().get(1).getZ() + height);
		}
	}

	@Override
	protected void setHeight(double newHeight, boolean finalize) {
		super.setHeight(newHeight, finalize);
		for (final Vector3 p : points)
			p.setZ(container.getPoints().get(1).getZ() + newHeight);
	}
}
