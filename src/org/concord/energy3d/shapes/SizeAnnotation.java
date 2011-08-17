package org.concord.energy3d.shapes;

import java.nio.FloatBuffer;

import org.concord.energy3d.scene.Scene;

import com.ardor3d.bounding.BoundingBox;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.scenegraph.Line;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.ui.text.BMText.Align;
import com.ardor3d.util.geom.BufferUtils;

public class SizeAnnotation extends Annotation {
	protected final Mesh arrows = new Mesh("Arrows");

	public SizeAnnotation() {
		super(new Line("Size annotation lines", BufferUtils.createVector3Buffer(12), null, null, null));
		arrows.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
		arrows.setDefaultColor(ColorRGBA.BLACK);
		arrows.setModelBound(new BoundingBox());
		this.attachChild(arrows);
		this.attachChild(label);
	}

	public void setRange(final ReadOnlyVector3 from, final ReadOnlyVector3 to, final ReadOnlyVector3 center, final ReadOnlyVector3 faceDirection, final boolean front, final Align align, boolean autoFlipOffset, final boolean rotateTextAlongLine, final boolean upsideDownText) {
		final double C = 0.1;
		final Vector3 v = new Vector3();
		final Vector3 offset = new Vector3();
		if (front)
			offset.set(faceDirection).normalizeLocal().multiplyLocal(C);
		else {
			offset.set(to).subtractLocal(from).normalizeLocal().crossLocal(faceDirection).multiplyLocal(C);
			if (autoFlipOffset) {
				v.set(from).subtractLocal(center).normalizeLocal();
				if (v.dot(offset) < 0)
					offset.negateLocal();
			}
		}

		final ReadOnlyVector3 dir = to.subtract(from, null).normalizeLocal();
		final int scale = upsideDownText ? -1 : 1;
		label.setRotation(new Matrix3().fromAxes(dir.multiply(scale, null), faceDirection, faceDirection.cross(dir, null).multiplyLocal(scale)));

		FloatBuffer vertexBuffer = mesh.getMeshData().getVertexBuffer();
		vertexBuffer.rewind();

		// main line
		final Vector3 newFrom = new Vector3(from).addLocal(offset);
		final Vector3 newTo = new Vector3(to).addLocal(offset);
		final Vector3 middle = new Vector3(newFrom).addLocal(newTo).multiplyLocal(0.5);
		final Vector3 body = new Vector3(to).subtractLocal(from).multiplyLocal(0.5);
		vertexBuffer.put(newFrom.getXf()).put(newFrom.getYf()).put(newFrom.getZf());
		double s = (body.length() - 0.15) / body.length();
		v.set(body).multiplyLocal(s).addLocal(newFrom);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.set(body).multiplyLocal(-s).addLocal(newTo);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		vertexBuffer.put(newTo.getXf()).put(newTo.getYf()).put(newTo.getZf());

		offset.multiplyLocal(0.5);
		// from End
		v.set(from);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.set(newFrom).addLocal(offset);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());

		// to End
		v.set(to);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.set(newTo).addLocal(offset);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());

		// arrow
		offset.multiplyLocal(0.5);
		body.set(to).subtractLocal(from).normalizeLocal().multiplyLocal(0.05);

		mesh.updateModelBound();

		vertexBuffer = arrows.getMeshData().getVertexBuffer();
		vertexBuffer.rewind();
		// arrow from
		v.set(newFrom);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.addLocal(offset).addLocal(body);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.set(newFrom).subtractLocal(offset).addLocal(body);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		// arrow to
		body.negateLocal();
		v.set(newTo);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.addLocal(offset).addLocal(body);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());
		v.set(newTo).subtractLocal(offset).addLocal(body);
		vertexBuffer.put(v.getXf()).put(v.getYf()).put(v.getZf());

		arrows.updateModelBound();

		label.setTranslation(middle);
		label.setText("" + Math.round(to.subtract(from, null).length() * Scene.getInstance().getAnnotationScale() * 100) / 100.0 + Scene.getInstance().getUnit().getNotation());
		label.setAlign(align);
		label.updateModelBound();
		System.out.println(label.getWorldBound());
		label.updateWorldBound(false);
		System.out.println(label.getWorldBound());
		this.updateWorldTransform(true);
		this.updateWorldBound(true);
	}

//	protected void updateTextSize() {
//		final BoundingBox bounds = (BoundingBox) Scene.getInstance().getOriginalHouseRoot().getWorldBound();
//		if (bounds != null) {
//			final double size = Math.max(bounds.getXExtent(), Math.max(bounds.getYExtent(), bounds.getZExtent()));
//			label.setFontScale(size / 20.0);
//		}
//	}

}
