package org.concord.energy3d.model;

import java.nio.FloatBuffer;

import javax.swing.JOptionPane;

import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.scene.Scene.TextureMode;
import org.concord.energy3d.util.Util;

import com.ardor3d.bounding.BoundingBox;
import com.ardor3d.bounding.OrientedBoundingBox;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.renderer.Camera;
import com.ardor3d.renderer.IndexMode;
import com.ardor3d.renderer.Camera.ProjectionMode;
import com.ardor3d.renderer.state.OffsetState;
import com.ardor3d.scenegraph.Line;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.shape.Box;
import com.ardor3d.util.geom.BufferUtils;

public class Mirror extends HousePart {

	private static final long serialVersionUID = 1L;
	private transient ReadOnlyVector3 normal;
	private transient Mesh outlineMesh;
	private transient Box surround;
	private transient Mesh supportFrame;
	private double reflectivity = 0.75; // a number in (0, 1)
	private double mirrorWidth = 2;
	private double mirrorHeight = 3;
	private double relativeAzimuth;
	private double zenith = 90; // the zenith angle relative to the surface of the parent
	private transient double layoutGap = 0.01;

	public Mirror(boolean rotated) {
		super(1, 1, 0);
	}

	/** a number between 0 and 1 */
	public void setReflectivity(final double efficiency) {
		this.reflectivity = efficiency;
	}

	/** a number between 0 and 1 */
	public double getReflectivity() {
		return reflectivity;
	}

	public void setMirrorWidth(double mirrorWidth) {
		this.mirrorWidth = mirrorWidth;
	}

	public double getMirrorWidth() {
		return mirrorWidth;
	}

	public void setMirrorHeight(double mirrorHeight) {
		this.mirrorHeight = mirrorHeight;
	}

	public double getMirrorHeight() {
		return mirrorHeight;
	}

	@Override
	protected void init() {
		super.init();

		if (Util.isZero(mirrorWidth))
			mirrorWidth = 2;
		if (Util.isZero(mirrorHeight))
			mirrorHeight = 3;
		if (Util.isZero(zenith))
			zenith = 90;
		if (Util.isZero(reflectivity))
			reflectivity = 0.75;

		mesh = new Mesh("Reflecting Mirror");
		mesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
		mesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(6), 0);
		mesh.setModelBound(new OrientedBoundingBox());
		mesh.setUserData(new UserData(this));
		root.attachChild(mesh);

		surround = new Box("Mirror (Surround)");
		surround.setModelBound(new OrientedBoundingBox());
		final OffsetState offset = new OffsetState();
		offset.setFactor(1);
		offset.setUnits(1);
		surround.setRenderState(offset);
		root.attachChild(surround);

		outlineMesh = new Line("Mirror (Outline)");
		outlineMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(8));
		outlineMesh.setDefaultColor(ColorRGBA.BLACK);
		outlineMesh.setModelBound(new OrientedBoundingBox());
		root.attachChild(outlineMesh);

		supportFrame = new Mesh("Supporting Frame");
		supportFrame.getMeshData().setIndexMode(IndexMode.Quads);
		supportFrame.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(12));
		supportFrame.getMeshData().setNormalBuffer(BufferUtils.createVector3Buffer(12));
		supportFrame.setRenderState(offsetState);
		supportFrame.setModelBound(new BoundingBox());
		root.attachChild(supportFrame);

		updateTextureAndColor();
	}

	@Override
	public void setPreviewPoint(final int x, final int y) {
		final PickedHousePart picked = pickContainer(x, y, new Class<?>[] { Foundation.class });
		if (picked != null) {
			final Vector3 p = picked.getPoint().clone();
			snapToGrid(p, getAbsPoint(0), getGridSize(), false);
			points.get(0).set(toRelative(p));
		}
		if (container != null) {
			draw();
			setEditPointsVisible(true);
			setHighlight(!isDrawable());
		}
	}

	@Override
	public void updateEditShapes() {
		final Vector3 p = Vector3.fetchTempInstance();
		try {
			for (int i = 0; i < points.size(); i++) {
				getAbsPoint(i, p);
				final Camera camera = SceneManager.getInstance().getCamera();
				if (camera != null && camera.getProjectionMode() != ProjectionMode.Parallel) {
					final double distance = camera.getLocation().distance(p);
					getEditPointShape(i).setScale(distance > 0.1 ? distance / 10 : 0.01);
				} else {
					getEditPointShape(i).setScale(camera.getFrustumTop() / 4);
				}
				if (!Util.isZero(zenith - 90)) {
					double h = mirrorHeight / Scene.getInstance().getAnnotationScale();
					p.setZ(p.getZ() + 0.5 * h * Math.cos(Math.toRadians(zenith)));
				}
				getEditPointShape(i).setTranslation(p);
			}
		} finally {
			Vector3.releaseTempInstance(p);
		}
		/* remove remaining edit shapes */
		for (int i = points.size(); i < pointsRoot.getNumberOfChildren(); i++)
			pointsRoot.detachChildAt(points.size());
	}

	@Override
	protected void drawMesh() {
		if (container == null)
			return;

		normal = computeNormalAndKeepOnRoof();
		updateEditShapes();

		final double annotationScale = Scene.getInstance().getAnnotationScale();
		surround.setData(new Vector3(0, 0, 0), mirrorWidth / 2.0 / annotationScale, mirrorHeight / 2.0 / annotationScale, 0.15);
		surround.updateModelBound();

		final FloatBuffer boxVertexBuffer = surround.getMeshData().getVertexBuffer();
		final FloatBuffer vertexBuffer = mesh.getMeshData().getVertexBuffer();
		final FloatBuffer textureBuffer = mesh.getMeshData().getTextureBuffer(0);
		final FloatBuffer outlineBuffer = outlineMesh.getMeshData().getVertexBuffer();
		vertexBuffer.rewind();
		outlineBuffer.rewind();
		textureBuffer.rewind();
		int i = 8 * 3;
		vertexBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		textureBuffer.put(1).put(0);
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		i += 3;
		vertexBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		textureBuffer.put(0).put(0);
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		i += 3;
		vertexBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		vertexBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		textureBuffer.put(0).put(1);
		textureBuffer.put(0).put(1);
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		i += 3;
		vertexBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		textureBuffer.put(1).put(1);
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		i = 8 * 3;
		vertexBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));
		textureBuffer.put(1).put(0);
		outlineBuffer.put(boxVertexBuffer.get(i)).put(boxVertexBuffer.get(i + 1)).put(boxVertexBuffer.get(i + 2));

		mesh.updateModelBound();
		outlineMesh.updateModelBound();

		if (Util.isZero(zenith - 90)) {
			mesh.setTranslation(getAbsPoint(0));
			if (Util.isEqual(normal, Vector3.UNIT_Z)) {
				double a = Math.PI / 2 * 0.9999; // exactly 90 degrees will cause the solar panel to disappear
				setNormal(a, 0);
			}
		} else {
			double t = Math.toRadians(zenith);
			double h = mirrorHeight / Scene.getInstance().getAnnotationScale();
			mesh.setTranslation(getAbsPoint(0).addLocal(0, 0, 0.5 * h * Math.cos(t)));
			setNormal(t, Math.toRadians(relativeAzimuth));
		}
		mesh.setRotation(new Matrix3().lookAt(normal, Vector3.UNIT_Z));

		surround.setTranslation(mesh.getTranslation());
		surround.setRotation(mesh.getRotation());

		outlineMesh.setTranslation(mesh.getTranslation());
		outlineMesh.setRotation(mesh.getRotation());

		drawSupporFrame();

	}

	// ensure that a solar panel in special cases (on a flat roof or at a tilt angle) will have correct orientation
	private void setNormal(double zenith, double azimuth) {
		Foundation foundation = getTopContainer();
		Vector3 v = foundation.getAbsPoint(0);
		Vector3 vx = foundation.getAbsPoint(2).subtractLocal(v); // x direction
		Vector3 vy = foundation.getAbsPoint(1).subtractLocal(v); // y direction
		Matrix3 m = new Matrix3().applyRotationZ(-azimuth);
		Vector3 v1 = m.applyPost(vx, null);
		Vector3 v2 = m.applyPost(vy, null);
		v = new Matrix3().fromAngleAxis(zenith, v1).applyPost(v2, null);
		if (v.getZ() < 0)
			v.negateLocal();
		normal = v.normalizeLocal();
	}

	private void drawSupporFrame() {
		supportFrame.setDefaultColor(getColor());
		final FloatBuffer vertexBuffer = supportFrame.getMeshData().getVertexBuffer();
		final FloatBuffer normalBuffer = supportFrame.getMeshData().getNormalBuffer();
		vertexBuffer.rewind();
		normalBuffer.rewind();
		vertexBuffer.limit(vertexBuffer.capacity());
		normalBuffer.limit(normalBuffer.capacity());
		final ReadOnlyVector3 o = getAbsPoint(0);
		double t = Math.toRadians(zenith);
		double h = mirrorHeight / Scene.getInstance().getAnnotationScale();
		final Vector3 p = o.add(0, 0, 0.5 * h * Math.cos(t), null);
		Vector3 dir = normal.cross(Vector3.UNIT_Z, null).multiplyLocal(0.5);
		Util.addPointToQuad(normal, o, p, dir, vertexBuffer, normalBuffer);
		double w = mirrorWidth / Scene.getInstance().getAnnotationScale();
		dir.normalizeLocal().multiplyLocal(w * 0.5);
		Vector3 v1 = p.add(dir, null);
		dir.negateLocal();
		Vector3 v2 = p.add(dir, null);
		dir = new Vector3(normal).multiplyLocal(0.2);
		Util.addPointToQuad(normal, v1, v2, dir, vertexBuffer, normalBuffer);

		vertexBuffer.limit(vertexBuffer.position());
		normalBuffer.limit(normalBuffer.position());
		supportFrame.getMeshData().updateVertexCount();
		supportFrame.updateModelBound();
	}

	@Override
	public boolean isDrawable() {
		if (container == null) // FIXME: There is a chance that a solar panel can be left without a container
			return true;
		if (mesh.getWorldBound() == null)
			return true;
		final OrientedBoundingBox bound = (OrientedBoundingBox) mesh.getWorldBound().clone(null);
		bound.setExtent(bound.getExtent().divide(1.1, null).addLocal(0, 0, 1));
		for (final HousePart child : container.getChildren()) {
			if (child != this && child instanceof Mirror && bound.intersects(child.mesh.getWorldBound())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void updateTextureAndColor() {
		updateTextureAndColor(mesh, null, TextureMode.Full);
	}

	@Override
	protected String getTextureFileName() {
		return "mirror.png";
	}

	@Override
	public ReadOnlyVector3 getNormal() {
		return normal;
	}

	@Override
	public boolean isPrintable() {
		return false;
	}

	@Override
	public double getGridSize() {
		return mirrorWidth / Scene.getInstance().getAnnotationScale() / (SceneManager.getInstance().isFineGrid() ? 25.0 : 5.0);
	}

	@Override
	protected void computeArea() {
		area = mirrorWidth * mirrorHeight;
	}

	@Override
	protected HousePart getContainerRelative() {
		return container instanceof Wall ? container : getTopContainer();
	}

	@Override
	public void drawHeatFlux() {
		// this method is left empty on purpose -- don't draw heat flux
	}

	public void moveTo(final HousePart target) {
		setContainer(target);
	}

	@Override
	public boolean isCopyable() {
		return true;
	}

	private double overlap() {
		double w1 = mirrorWidth / Scene.getInstance().getAnnotationScale();
		final Vector3 center = getAbsCenter();
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p.getContainer() == container && p != this) {
				if (p instanceof Mirror) {
					Mirror s2 = (Mirror) p;
					double w2 = s2.mirrorWidth / Scene.getInstance().getAnnotationScale();
					double distance = p.getAbsCenter().distance(center);
					if (distance < (w1 + w2) * 0.499)
						return distance;
				}
			}
		}
		return -1;
	}

	@Override
	public HousePart copy(final boolean check) {
		final Mirror c = (Mirror) super.copy(false);
		if (check) {
			normal = container.getNormal();
			if (container instanceof Foundation) {
				Vector3 p0 = container.getAbsPoint(0);
				Vector3 p1 = container.getAbsPoint(1);
				Vector3 p2 = container.getAbsPoint(2);
				double a = -Math.toRadians(relativeAzimuth) * Math.signum(p2.subtract(p0, null).getX() * p1.subtract(p0, null).getY());
				Vector3 v = new Vector3(Math.cos(a), Math.sin(a), 0);
				final double length = (1 + layoutGap) * mirrorWidth / Scene.getInstance().getAnnotationScale();
				final double s = Math.signum(container.getAbsCenter().subtractLocal(Scene.getInstance().getOriginalCopy().getAbsCenter()).dot(v));
				double tx = length / p0.distance(p2);
				double ty = length / p0.distance(p1);
				double lx = s * v.getX() * tx;
				double ly = s * v.getY() * ty;
				final double newX = points.get(0).getX() + lx;
				if (newX > 1 - tx || newX < tx) // reject it if out of range
					return null;
				final double newY = points.get(0).getY() + ly;
				if (newY > 1 - ty || newY < ty) // reject it if out of range
					return null;
				c.points.get(0).setX(newX);
				c.points.get(0).setY(newY);
				double o = c.overlap();
				if (o >= 0) {
					JOptionPane.showMessageDialog(MainFrame.getInstance(), "Sorry, your new solar panel is too close to an existing one (" + o + ").", "Error", JOptionPane.ERROR_MESSAGE);
					return null;
				}
			}
		}
		return c;
	}

	public void setRelativeAzimuth(double relativeAzimuth) {
		this.relativeAzimuth = relativeAzimuth;
	}

	public double getRelativeAzimuth() {
		return relativeAzimuth;
	}

	public void setZenith(double zenith) {
		this.zenith = zenith;
	}

	public double getZenith() {
		return zenith;
	}

}