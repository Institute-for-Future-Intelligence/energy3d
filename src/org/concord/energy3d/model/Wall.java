package org.concord.energy3d.model;

import java.nio.FloatBuffer;
import java.util.ArrayList;

import org.concord.energy3d.scene.SceneManager;
import org.poly2tri.Poly2Tri;
import org.poly2tri.polygon.Polygon;
import org.poly2tri.polygon.PolygonPoint;
import org.poly2tri.transform.coordinate.AnyToXYTransform;
import org.poly2tri.transform.coordinate.XYToAnyTransform;
import org.poly2tri.triangulation.TriangulationPoint;
import org.poly2tri.triangulation.point.TPoint;
import org.poly2tri.triangulation.tools.ardor3d.ArdorMeshMapper;

import com.ardor3d.bounding.BoundingBox;
import com.ardor3d.bounding.CollisionTreeManager;
import com.ardor3d.image.Texture;
import com.ardor3d.image.TextureStoreFormat;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.renderer.IndexMode;
import com.ardor3d.renderer.state.CullState;
import com.ardor3d.renderer.state.TextureState;
import com.ardor3d.renderer.state.CullState.Face;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.hint.CullHint;
import com.ardor3d.scenegraph.hint.PickingHint;
import com.ardor3d.util.TextureManager;
import com.ardor3d.util.geom.BufferUtils;

public class Wall extends HousePart {
	private static final long serialVersionUID = 1L;
	private static final double GRID_SIZE = 0.5;
	private static double defaultWallHeight = 1f;
	private static CullState CULL_FRONT = new CullState();
	private static CullState CULL_BACK = new CullState();
	// private double height = defaultWallHeight;
	private double wallThickness = 0.1;
	private transient Mesh mesh;
	private transient Mesh backMesh;
	private transient Mesh surroundMesh;
	private transient Mesh invisibleMesh;
	private transient Mesh windowsSurroundMesh;
	// private transient FloatBuffer vertexBuffer;
	// private transient FloatBuffer textureBuffer;
	private Snap[] neighbors = new Snap[2];
	
	static {
		CULL_FRONT.setCullFace(Face.Front);
		CULL_BACK.setCullFace(Face.Back);
	}
	

	public Wall() {
		super(2, 4);
		height = defaultWallHeight;
	}

	protected void init() {
		super.init();
		mesh = new Mesh("Wall");
		backMesh = new Mesh("Wall (Back)");
		surroundMesh = new Mesh("Wall (Surround)");
		invisibleMesh = new Mesh("Wall (Invisible)");
		windowsSurroundMesh = new Mesh("Wall (Windows Surround)");
		
		root.attachChild(mesh);
		mesh.getMeshData().setIndexMode(IndexMode.TriangleStrip);
		mesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(4));
		mesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(4), 0);
		mesh.setModelBound(null);
		mesh.getSceneHints().setPickingHint(PickingHint.Pickable, false);
//		mesh.setRenderState(CULL_BACK);
//		CullState cullState;
//		cullState = new CullState();
//		cullState.setCullFace(Face.Back);
//		mesh.setRenderState(cullState);
		
		root.attachChild(backMesh);
		backMesh.getMeshData().setIndexMode(IndexMode.TriangleStrip);
		backMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(4));
		backMesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(4), 0);
		backMesh.setDefaultColor(ColorRGBA.LIGHT_GRAY);
		backMesh.setModelBound(null);
		backMesh.getSceneHints().setPickingHint(PickingHint.Pickable, false);

		root.attachChild(surroundMesh);
		surroundMesh.getMeshData().setIndexMode(IndexMode.TriangleStrip);
		surroundMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(8));
		surroundMesh.getMeshData().setNormalBuffer(BufferUtils.createVector3Buffer(8));
		surroundMesh.setDefaultColor(ColorRGBA.GRAY);
		surroundMesh.setModelBound(null);
		surroundMesh.getSceneHints().setPickingHint(PickingHint.Pickable, false);		

		root.attachChild(invisibleMesh);
		invisibleMesh.getMeshData().setIndexMode(IndexMode.TriangleStrip);
		invisibleMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(4));
		invisibleMesh.setModelBound(new BoundingBox());
		invisibleMesh.getSceneHints().setCullHint(CullHint.Always);
		
		
		root.attachChild(windowsSurroundMesh);
		windowsSurroundMesh.getMeshData().setIndexMode(IndexMode.TriangleStrip);
		windowsSurroundMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(100));
		windowsSurroundMesh.getMeshData().setNormalBuffer(BufferUtils.createVector3Buffer(8));
		windowsSurroundMesh.setDefaultColor(ColorRGBA.GRAY);
		windowsSurroundMesh.setModelBound(null);
		windowsSurroundMesh.getSceneHints().setPickingHint(PickingHint.Pickable, false);		

		// surroundMesh.getMeshData().setTextureBuffer(textureBuffer, 0);

		// Add a material to the box, to show both vertex color and lighting/shading.
		// final MaterialState ms = new MaterialState();
		// ms.setColorMaterial(ColorMaterial.Diffuse);
		// mesh.setRenderState(ms);

		// ShadingState shadingState = new ShadingState();
		// shadingState.setShadingMode(ShadingMode.Flat);
		// surroundMesh.setRenderState(shadingState);

		// Add a texture to the box.
		final TextureState ts = new TextureState();
		ts.setTexture(TextureManager.load("wall7.jpg", Texture.MinificationFilter.Trilinear, TextureStoreFormat.GuessNoCompressedFormat, true));
		mesh.setRenderState(ts);

		UserData userData = new UserData(this);
		mesh.setUserData(userData);
		backMesh.setUserData(userData);
		surroundMesh.setUserData(userData);
		invisibleMesh.setUserData(userData);
	}

	// private Texture createTexture() {
	// final Texture3D tex = new Texture3D();
	// tex.setMinificationFilter(MinificationFilter.BilinearNoMipMaps);
	// tex.setTextureKey(TextureKey.getKey(null, false, Format.RGBA8, MinificationFilter.BilinearNoMipMaps));
	// final Image img = new Image();
	// final int C = 10;
	// img.setWidth(C);
	// img.setHeight(C);
	// img.setDepth(C);
	// img.setFormat(Format.RGB8);
	//
	// final List<ByteBuffer> data = Lists.newArrayList();
	// for (int i = 0; i < C; i++) {
	// int size = C * C * 3;
	// final ByteBuffer layer = BufferUtils.createByteBuffer(size);
	// for (int j=0; j<size; j++)
	// // layer.put((byte)(Math.random()*255));
	// if (i == 0 && j == 0 || (i == C-1 && j == size-1)) {
	// layer.put((byte)255);
	// } else
	// layer.put((byte)0);
	// layer.rewind();
	// Image colorImage = new Image(Image.Format.RGB8, C, C, layer);
	// data.add(colorImage.getData(0));
	// }
	// img.setData(data);
	// tex.setImage(img);
	// tex.setWrap(WrapMode.BorderClamp);
	// // tex.setEnvPlaneS(new Vector4(0.5, 0, 0, 0));
	// // tex.setEnvPlaneT(new Vector4(0, 0.5, 0, 0));
	// // tex.setEnvPlaneR(new Vector4(0, 0, 0.5, 0));
	// return tex;
	// }

	private Vector3 getUpperPoint(Vector3 p) {
		return new Vector3(p.getX(), p.getY(), height + points.get(0).getZ());
	}

	public void setPreviewPoint(int x, int y) {
		// System.out.println("moving wall...");
		if (editPointIndex == -1 || editPointIndex == 0 || editPointIndex == 2) {
			PickedHousePart picked = pick(x, y, new Class<?>[] { Foundation.class, null }); // Foundation.class);
			if (picked != null) {
				// container = picked.getUserData().getHousePart();
				Vector3 p = picked.getPoint();
				if (container != null)
					p.setZ(container.getHeight());
				// if (p != null) {
				int index = (editPointIndex == -1) ? points.size() - 2 : editPointIndex;
				Snap snap = snap(p, index);
				if (snap == null)
					p = grid(p, GRID_SIZE, false);
				setNeighbor(index, snap, true);
				if (index == 2) // make sure z of 2nd base point is same as 2st (needed for platform picking side)
					p.setZ(points.get(0).getZ());
				points.set(index, p);
				points.set(index + 1, getUpperPoint(p));
			}
		} else if (editPointIndex == 1 || editPointIndex == 3) {
			int lower = (editPointIndex == 1) ? 0 : 2;
			Vector3 base = points.get(lower);
			Vector3 closestPoint = closestPoint(base, base.add(0, 0, 1, null), x, y);
			Snap snap = snap(closestPoint, -1);
			if (snap == null)
				closestPoint = grid(closestPoint, GRID_SIZE);
			// neighbor[1] = snap(closestPoint);
			defaultWallHeight = height = findHeight(base, closestPoint);
			points.set(1, getUpperPoint(points.get(1)));
			points.set(3, getUpperPoint(points.get(3)));

		}
		draw();
		showPoints();

		for (Snap neighbor : this.neighbors)
			if (neighbor != null)
				neighbor.getNeighbor().draw();

	}

	@Override
	protected void draw() {
		boolean drawable = points.size() >= 4 && !points.get(0).equals(points.get(2));

		for (int i = 0; i < points.size(); i++) {
			pointsRoot.getChild(i).setTranslation(points.get(i));
		}		

		if (drawable) {
			Vector3 normal = points.get(2).subtract(points.get(0), null).cross(points.get(1).subtract(points.get(0), null), null).normalize(null);

			ArrayList<PolygonPoint> polyPoints = new ArrayList<PolygonPoint>();

			FloatBuffer invisibleVertexBuffer = invisibleMesh.getMeshData().getVertexBuffer();
			invisibleVertexBuffer.rewind();
			Vector3 p;

			p = points.get(0);
			invisibleVertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
			p = points.get(1);
			invisibleVertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
			p = points.get(2);
			invisibleVertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
			p = points.get(3);
			invisibleVertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());

			p = points.get(0);
			polyPoints.add(new PolygonPoint(p.getX(), p.getY(), p.getZ()));
			p = points.get(2);
			polyPoints.add(new PolygonPoint(p.getX(), p.getY(), p.getZ()));
			p = points.get(3);
			polyPoints.add(new PolygonPoint(p.getX(), p.getY(), p.getZ()));
			p = points.get(1);
			polyPoints.add(new PolygonPoint(p.getX(), p.getY(), p.getZ()));

			try {
				AnyToXYTransform toXY = new AnyToXYTransform(normal.getX(), normal.getY(), normal.getZ());
				XYToAnyTransform fromXY = new XYToAnyTransform(normal.getX(), normal.getY(), normal.getZ());

				for (TriangulationPoint tp : polyPoints)
					toXY.transform(tp);

				Polygon polygon = new Polygon(polyPoints);

				for (HousePart child : children) {
					if (child instanceof Window) {
						Window win = (Window) child;
						if (win.getPoints().size() < 4)
							continue;
						PolygonPoint pp;
						ArrayList<PolygonPoint> holePoints = new ArrayList<PolygonPoint>();
						ArrayList<Vector3> points = child.getPoints();
						// p = win.convertFromWallRelativeToAbsolute(points.get(0));
						p = points.get(0);
						pp = new PolygonPoint(p.getX(), p.getY(), p.getZ());
						toXY.transform(pp);
						holePoints.add(pp);
						// p = win.convertFromWallRelativeToAbsolute(points.get(2));
						p = points.get(2);
						pp = new PolygonPoint(p.getX(), p.getY(), p.getZ());
						toXY.transform(pp);
						holePoints.add(pp);
						// p = win.convertFromWallRelativeToAbsolute(points.get(3));
						p = points.get(3);
						pp = new PolygonPoint(p.getX(), p.getY(), p.getZ());
						toXY.transform(pp);
						holePoints.add(pp);
						// p = win.convertFromWallRelativeToAbsolute(points.get(1));
						p = points.get(1);
						pp = new PolygonPoint(p.getX(), p.getY(), p.getZ());
						toXY.transform(pp);
						holePoints.add(pp);
						polygon.addHole(new Polygon(holePoints));
					}

				}

				Vector3 p01 = points.get(1).subtract(points.get(0), null).normalizeLocal(); // .multiplyLocal(1/TEXTURE_SCALE_Y);
				Vector3 p02 = points.get(2).subtract(points.get(0), null).normalizeLocal(); // .multiplyLocal(1/TEXTURE_SCALE_X);
				TPoint o = new TPoint(points.get(0).getX(), points.get(0).getY(), points.get(0).getZ());
				TPoint u = new TPoint(p01.getX(), p01.getY(), p01.getZ());
				TPoint v = new TPoint(p02.getX(), p02.getY(), p02.getZ());

				toXY.transform(o);
				toXY.transform(u);
				toXY.transform(v);

				Poly2Tri.triangulate(polygon);
				ArdorMeshMapper.updateTriangleMesh(mesh, polygon, fromXY);
				ArdorMeshMapper.updateVertexNormals(mesh, polygon.getTriangles(), fromXY);
				ArdorMeshMapper.updateTextureCoordinates(mesh, polygon.getTriangles(), 1, o, u, v);
				mesh.getMeshData().updateVertexCount();

				Vector3 n = drawBackMesh(polygon, fromXY);

				drawSurroundMesh(n);
				
				drawWindowsSurroundMesh(n);

			} catch (Exception e) {
				e.printStackTrace();
			}

			// force bound update
			// mesh.updateModelBound();
			// backMesh.updateModelBound();
			// surroundMesh.updateModelBound();
			// root.updateWorldBound(true);
			// mesh.updateModelBound();
			// backMesh.updateModelBound();
			// surroundMesh.updateModelBound();
			invisibleMesh.updateModelBound();
			// root.updateGeometricState(0);
			CollisionTreeManager.INSTANCE.removeCollisionTree(root);

			for (HousePart child : children)
				child.draw();
		}

	}

	private Vector3 drawBackMesh(Polygon polygon, XYToAnyTransform fromXY) {
		Vector3 dir = points.get(2).subtract(points.get(0), null).normalizeLocal();
		if (neighbors[0] != null && neighbors[0].getNeighbor().isFirstPointInserted())
			reduceBackMeshWidth(polygon, dir, 0);

		if (neighbors[1] != null && neighbors[1].getNeighbor().isFirstPointInserted()) {
			dir.normalizeLocal().negateLocal();
			reduceBackMeshWidth(polygon, dir, 1);
		}

		Poly2Tri.triangulate(polygon);
		ArdorMeshMapper.updateTriangleMesh(backMesh, polygon, fromXY);
		ArdorMeshMapper.updateVertexNormals(backMesh, polygon.getTriangles(), fromXY);
		backMesh.getMeshData().updateVertexCount();

		Vector3 n = decideThicknessNormal();

		backMesh.setTranslation(n);
		return n;
	}

	private void reduceBackMeshWidth(Polygon polygon, final Vector3 dir, final int neighbor) {
		final int neighborPointIndex = neighbors[neighbor].getNeighborPointIndex();
		ArrayList<Vector3> points2 = neighbors[neighbor].getNeighbor().getPoints();
		Vector3 dir2 = points2.get(neighborPointIndex == 0 ? 2 : 0).subtract(points2.get(neighborPointIndex), null).normalizeLocal();
		final double angle = Math.max(0.1, dir2.smallestAngleBetween(dir) / 2);
		// System.out.println(angle);
		dir.multiplyLocal(wallThickness * Math.sin(Math.PI / 2 - angle) / Math.sin(angle));
		TriangulationPoint p = polygon.getPoints().get(neighbor == 0 ? 0 : 1);
		p.set(p.getX() + dir.getX(), p.getY() + dir.getY(), p.getZ());
		p = polygon.getPoints().get(neighbor == 0 ? 3 : 2);
		p.set(p.getX() + dir.getX(), p.getY() + dir.getY(), p.getZ());
	}

	private Vector3 decideThicknessNormal() {
		mesh.setRenderState(CULL_FRONT);
		backMesh.setRenderState(CULL_BACK);
		surroundMesh.setRenderState(CULL_BACK);
		
		FloatBuffer normalBuffer = mesh.getMeshData().getNormalBuffer();
		normalBuffer.position(0);
		// Vector3 n = new Vector3(normalBuffer.get(), normalBuffer.get(), normalBuffer.get());
		Vector3 p02 = points.get(2).subtract(points.get(0), null).normalizeLocal();
		Vector3 p01 = points.get(1).subtract(points.get(0), null).normalizeLocal();
		Vector3 n = p02.crossLocal(p01).normalizeLocal();
		n.multiplyLocal(wallThickness);

		Snap neighbor = this.neighbors[0];
		if (neighbor == null)
			neighbor = this.neighbors[1];

		if (neighbor != null && neighbor.getNeighbor().getPoints().size() >= 4) {
			Wall otherWall = (Wall) neighbor.getNeighbor();
			ArrayList<Vector3> otherPoints = otherWall.getPoints();
			int otherPointIndex = neighbor.getNeighborPointIndex();
			Vector3 a = otherPoints.get(otherPointIndex);
			Vector3 b = otherPoints.get(otherPointIndex == 0 ? 2 : 0);
			Vector3 ab = b.subtract(a, null).normalizeLocal();
			if (n.dot(ab) < 0) {
				n.negateLocal();
				mesh.setRenderState(CULL_BACK);
				backMesh.setRenderState(CULL_FRONT);
				surroundMesh.setRenderState(CULL_FRONT);
			}
		} else {
			ReadOnlyVector3 camera = SceneManager.getInstance().getCanvas().getCanvasRenderer().getCamera().getDirection();
			if (camera.dot(n) < 0) {
				n.negateLocal();
				mesh.setRenderState(CULL_BACK);
				backMesh.setRenderState(CULL_FRONT);
				surroundMesh.setRenderState(CULL_FRONT);
			}
		}
		return n;
	}

	private void drawSurroundMesh(ReadOnlyVector3 thickness) {
		FloatBuffer vertexBuffer = surroundMesh.getMeshData().getVertexBuffer();
		FloatBuffer normalBuffer = surroundMesh.getMeshData().getNormalBuffer();
		vertexBuffer.position(0);
		normalBuffer.position(0);
		Vector3 p2 = Vector3.fetchTempInstance();
		int[] order;
		if (neighbors[0] != null && neighbors[1] != null)
			order = new int[] { 1, 3 };
		else if (neighbors[0] != null)
			order = new int[] { 1, 3, 2 };
		else if (neighbors[1] != null)
			order = new int[] { 0, 1, 3 };
		else
			order = new int[] { 0, 1, 3, 2 };

		Vector3 sideNormal = thickness.cross(0, 0, 1, null).normalizeLocal();
		for (int i : order) {
			ReadOnlyVector3 p = points.get(i);
			vertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
			p2.set(p).addLocal(thickness);
			vertexBuffer.put(p2.getXf()).put(p2.getYf()).put(p2.getZf());

			if (i == 1 || i == 3) {
				normalBuffer.put(0).put(0).put(1);
				normalBuffer.put(0).put(0).put(1);
			} else if (i == 0) {
				normalBuffer.put(sideNormal.getXf()).put(sideNormal.getYf()).put(sideNormal.getZf());
				normalBuffer.put(sideNormal.getXf()).put(sideNormal.getYf()).put(sideNormal.getZf());
			} else if (i == 2) {
				sideNormal.negateLocal();
				normalBuffer.put(sideNormal.getXf()).put(sideNormal.getYf()).put(sideNormal.getZf());
				normalBuffer.put(sideNormal.getXf()).put(sideNormal.getYf()).put(sideNormal.getZf());
			}
		}

		while (vertexBuffer.position() < vertexBuffer.capacity())
			vertexBuffer.put(p2.getXf()).put(p2.getYf()).put(p2.getZf());
		Vector3.releaseTempInstance(p2);
	}

	private void drawWindowsSurroundMesh(Vector3 n) {
		FloatBuffer vertexBuffer = windowsSurroundMesh.getMeshData().getVertexBuffer();
		final int[] order = new int[] { 0, 1, 3, 2 };
		Vector3 p = new Vector3();
		for (HousePart child : children) {
			if (child instanceof Window) {
				for (int i : order) {
					p.set(child.getPoints().get(i));
					vertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
					p.addLocal(n);
					vertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
				}
				p.set(child.getPoints().get(0));
				vertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
				p.addLocal(n);
				vertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
//				p.set(child.getPoints().get(1));
//				vertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());				
			}
		}
		int pos = vertexBuffer.position();
		vertexBuffer.limit(pos != 0 ? pos-1 : 1);
		
	}

	public Snap next(Wall previous) {
		for (Snap s : neighbors)
			if (s != null && s.getNeighbor() != previous)
				return s;
		return null;
	}

	private void setNeighbor(int pointIndex, Snap newNeighbor, boolean updateNeighbors) {
		int i = pointIndex < 2 ? 0 : 1;
		Snap oldNeighbor = neighbors[i];
		if (updateNeighbors || oldNeighbor == null) // do not update if already has neighbor, unless this update was initiated by this wall
			neighbors[i] = newNeighbor;

		if (!updateNeighbors || oldNeighbor == newNeighbor || (oldNeighbor != null && oldNeighbor.equals(newNeighbor)))
			return;

		if (oldNeighbor != null)
			((Wall) oldNeighbor.getNeighbor()).removeNeighbor(oldNeighbor.getNeighborPointIndex(), pointIndex, this);

		if (newNeighbor != null)
			((Wall) newNeighbor.getNeighbor()).setNeighbor(newNeighbor.getNeighborPointIndex(), new Snap(this, newNeighbor.getNeighborPointIndex(), newNeighbor.getThisPointIndex()), false);
	}

	private void removeNeighbor(int pointIndex, int requestingPointIndex, Wall wall) {
		int i = pointIndex < 2 ? 0 : 1;
		if (neighbors[i] != null && neighbors[i].getNeighbor() == wall && neighbors[i].getNeighborPointIndex() == requestingPointIndex)
			neighbors[i] = null;
		draw();
	}

	public void delete() {
		for (int i = 0; i < neighbors.length; i++)
			if (neighbors[i] != null)
				((Wall) neighbors[i].getNeighbor()).setNeighbor(neighbors[i].getNeighborPointIndex(), null, false); // .removeNeighbor(this);
	}

}