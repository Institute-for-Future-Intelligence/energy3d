package org.concord.energy3d.model;

import java.awt.EventQueue;
import java.awt.HeadlessException;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.FloatBuffer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.Scene.TextureMode;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.shapes.SizeAnnotation;
import org.concord.energy3d.simulation.Thermostat;
import org.concord.energy3d.simulation.UtilityBill;
import org.concord.energy3d.undo.AddArrayCommand;
import org.concord.energy3d.undo.DeleteMeshCommand;
import org.concord.energy3d.util.FontManager;
import org.concord.energy3d.util.MeshLib;
import org.concord.energy3d.util.SelectUtil;
import org.concord.energy3d.util.TriangleMeshLib;
import org.concord.energy3d.util.Util;

import com.ardor3d.bounding.BoundingBox;
import com.ardor3d.bounding.CollisionTreeManager;
import com.ardor3d.bounding.OrientedBoundingBox;
import com.ardor3d.extension.effect.bloom.BloomRenderPass;
import com.ardor3d.extension.model.collada.jdom.ColladaImporter;
import com.ardor3d.extension.model.collada.jdom.data.ColladaStorage;
import com.ardor3d.intersection.PickResults;
import com.ardor3d.intersection.Pickable;
import com.ardor3d.intersection.PickingUtil;
import com.ardor3d.intersection.PrimitivePickResults;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Ray3;
import com.ardor3d.math.Vector2;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyColorRGBA;
import com.ardor3d.math.type.ReadOnlyTransform;
import com.ardor3d.math.type.ReadOnlyVector2;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.renderer.state.RenderState.StateType;
import com.ardor3d.renderer.state.TextureState;
import com.ardor3d.scenegraph.Line;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.MeshData;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.Spatial;
import com.ardor3d.scenegraph.hint.CullHint;
import com.ardor3d.scenegraph.hint.SceneHints;
import com.ardor3d.scenegraph.shape.Cylinder;
import com.ardor3d.ui.text.BMText;
import com.ardor3d.ui.text.BMText.Align;
import com.ardor3d.ui.text.BMText.Justify;
import com.ardor3d.util.geom.BufferUtils;
import com.ardor3d.util.resource.URLResourceSource;

public class Foundation extends HousePart implements Thermalizable {
	private static final long serialVersionUID = 1L;
	private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));

	public static final int BUILDING = 0;
	public static final int PV_STATION = 1;
	public static final int CSP_STATION = 2;

	public static final int FERMAT_SPIRAL = 0;
	public static final int EQUAL_AZIMUTHAL_SPACING = 0;
	public static final int RADIAL_STAGGER = 1;

	private static transient BloomRenderPass bloomRenderPass;
	private transient ArrayList<Vector3> orgPoints;
	private transient Mesh boundingMesh;
	private transient Mesh outlineMesh;
	private transient Mesh sideMesh[];
	private transient BMText floatingLabel;
	private transient Cylinder solarReceiver; // this is temporarily used to model the receiver of a concentrated power tower (there got to be a better solution)
	private transient Line azimuthArrow;
	private transient double newBoundingHeight;
	private transient double boundingHeight;
	private transient double minX;
	private transient double minY;
	private transient double maxX;
	private transient double maxY;
	private transient double passiveSolarNow; // energy terms of current hour
	private transient double photovoltaicNow;
	private transient double heatingNow;
	private transient double coolingNow;
	private transient double totalEnergyNow;
	private transient double passiveSolarToday; // energy terms of current day
	private transient double photovoltaicToday;
	private transient double heatingToday;
	private transient double coolingToday;
	private transient double totalEnergyToday;
	private transient boolean resizeHouseMode;
	private transient boolean useOrgPoints;
	private Thermostat thermostat = new Thermostat();
	private UtilityBill utilityBill;
	private FoundationPolygon foundationPolygon;
	private double solarReceiverEfficiency = 0.86;
	private double volumetricHeatCapacity = 0.5; // unit: kWh/m^3/C (1 kWh = 3.6 MJ)
	private double uValue = 0.568; // default is R10 (IECC code for Massachusetts: https://energycode.pnl.gov/EnergyCodeReqs/index.jsp?state=Massachusetts)
	private double childGridSize = 2.5;
	private boolean lockEdit;
	private boolean groupMaster;
	private List<NodeState> importedNodeStates; // for now, save only the node states
	private transient List<Node> importedNodes; // for now, do not save the actual nodes (this is why we can't use Map<Node, NodeState> here)
	private transient Mesh selectedMesh;
	private transient Line selectedMeshOutline;
	private transient Line selectedNodeBoundingBox;

	public Foundation() {
		super(2, 12, 1);
		root.getSceneHints().setCullHint(CullHint.Always);
	}

	public Foundation(final double xLength, final double yLength) {
		super(2, 12, 1, true);
		points.get(0).set(-xLength / 2.0, -yLength / 2.0, 0);
		points.get(2).set(xLength / 2.0, -yLength / 2.0, 0);
		points.get(1).set(-xLength / 2.0, yLength / 2.0, 0);
		points.get(3).set(xLength / 2.0, yLength / 2.0, 0);
	}

	@Override
	protected boolean mustHaveContainer() {
		return false;
	}

	@Override
	protected void init() {
		super.init();
		resizeHouseMode = false;

		if (Util.isZero(uValue)) {
			uValue = 0.19;
		}
		if (Util.isZero(volumetricHeatCapacity)) {
			volumetricHeatCapacity = 0.5;
		}
		if (Util.isZero(solarReceiverEfficiency)) {
			solarReceiverEfficiency = 0.86;
		}
		if (Util.isZero(childGridSize)) {
			childGridSize = 2.5;
		}
		if (thermostat == null) {
			thermostat = new Thermostat();
		}

		mesh = new Mesh("Foundation");
		mesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
		mesh.getMeshData().setNormalBuffer(BufferUtils.createVector3Buffer(6));
		mesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(6), 0);
		mesh.setRenderState(offsetState);
		mesh.setModelBound(new BoundingBox());
		root.attachChild(mesh);

		if (foundationPolygon == null) {
			foundationPolygon = new FoundationPolygon(this);
		} else {
			foundationPolygon.draw();
		}

		root.attachChild(foundationPolygon.getRoot());

		sideMesh = new Mesh[4];
		for (int i = 0; i < 4; i++) {
			final Mesh mesh = new Mesh("Foundation (Side " + i + ")");
			mesh.setUserData(new UserData(this));
			mesh.setRenderState(offsetState);
			mesh.setModelBound(new BoundingBox());
			final MeshData meshData = mesh.getMeshData();
			meshData.setVertexBuffer(BufferUtils.createVector3Buffer(6));
			meshData.setNormalBuffer(BufferUtils.createVector3Buffer(6));
			mesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(6), 0);
			root.attachChild(mesh);
			sideMesh[i] = mesh;
		}

		boundingMesh = new Line("Foundation (Bounding)");
		boundingMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(24));
		boundingMesh.setModelBound(new BoundingBox());
		Util.disablePickShadowLight(boundingMesh);
		boundingMesh.getSceneHints().setCullHint(CullHint.Always);
		root.attachChild(boundingMesh);

		outlineMesh = new Line("Foundation (Outline)");
		outlineMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(24));
		outlineMesh.setDefaultColor(ColorRGBA.BLACK);
		outlineMesh.setModelBound(new BoundingBox());
		Util.disablePickShadowLight(outlineMesh);
		root.attachChild(outlineMesh);

		final UserData userData = new UserData(this);
		mesh.setUserData(userData);
		boundingMesh.setUserData(userData);

		setLabelOffset(-0.11);

		floatingLabel = new BMText("Floating Label", "0", FontManager.getInstance().getPartNumberFont(), Align.Center, Justify.Center);
		Util.initHousePartLabel(floatingLabel);
		floatingLabel.setFontScale(0.5);
		floatingLabel.setVisible(false);
		root.attachChild(floatingLabel);

		azimuthArrow = new Line("Azimuth Arrow");
		azimuthArrow.setLineWidth(2);
		azimuthArrow.setModelBound(null);
		Util.disablePickShadowLight(azimuthArrow);
		azimuthArrow.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
		azimuthArrow.setDefaultColor(ColorRGBA.WHITE);
		root.attachChild(azimuthArrow);

		solarReceiver = new Cylinder("Solar Receiver", 10, 10, 10, 0, true);
		solarReceiver.setDefaultColor(ColorRGBA.WHITE);
		solarReceiver.setRenderState(offsetState);
		solarReceiver.setModelBound(new BoundingBox());
		solarReceiver.setVisible(false);
		root.attachChild(solarReceiver);

		selectedMeshOutline = new Line("Outline of Selected Mesh");
		selectedMeshOutline.setLineWidth(2f);
		selectedMeshOutline.setStipplePattern((short) 0xf0f0);
		selectedMeshOutline.setModelBound(null);
		Util.disablePickShadowLight(selectedMeshOutline);
		selectedMeshOutline.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(1));
		selectedMeshOutline.setDefaultColor(new ColorRGBA(0f, 0f, 0f, 1f));
		root.attachChild(selectedMeshOutline);

		selectedNodeBoundingBox = new Line("Bounding Box of Selected Mesh");
		selectedNodeBoundingBox.setLineWidth(0.01f);
		selectedNodeBoundingBox.setStipplePattern((short) 0xf0f0);
		selectedNodeBoundingBox.setModelBound(null);
		Util.disablePickShadowLight(selectedNodeBoundingBox);
		selectedNodeBoundingBox.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(24));
		selectedNodeBoundingBox.setDefaultColor(new ColorRGBA(1f, 1f, 0f, 1f));
		root.attachChild(selectedNodeBoundingBox);

		updateTextureAndColor();

		if (points.size() == 8) {
			for (int i = 0; i < 4; i++) {
				points.add(new Vector3());
			}
		}

		if (importedNodeStates != null) {
			try {
				for (final Iterator<NodeState> it = importedNodeStates.iterator(); it.hasNext();) {
					final NodeState ns = it.next();
					final Node n = importCollada(ns.getSourceURL(), null);
					if (n == null) {
						it.remove();
						EventQueue.invokeLater(new Runnable() {
							@Override
							public void run() {
								try {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), Paths.get(ns.getSourceURL().toURI()).toFile() + " was not found!", "File problem", JOptionPane.ERROR_MESSAGE);
								} catch (final HeadlessException e) {
									e.printStackTrace();
								} catch (final URISyntaxException e) {
									e.printStackTrace();
								}
							}
						});
					} else {
						final ArrayList<Integer> reversedFaceMeshes = ns.getMeshesWithReversedNormal();
						if (reversedFaceMeshes != null) {
							for (final Integer i : reversedFaceMeshes) {
								NodeWorker.reverseFace(NodeWorker.getMesh(n, i));
							}
						}
						final ArrayList<Integer> deletedMeshes = ns.getDeletedMeshes();
						if (deletedMeshes != null && !deletedMeshes.isEmpty()) {
							final List<Mesh> toDelete = new ArrayList<Mesh>();
							for (final Integer i : deletedMeshes) {
								toDelete.add(NodeWorker.getMesh(n, i));
							}
							for (final Mesh m : toDelete) {
								n.detachChild(m);
							}
						}
					}
				}
			} catch (final Throwable t) {
				Util.reportError(t);
			}
			setRotatedNormalsForImportedMeshes();
		}

	}

	public void setResizeHouseMode(final boolean resizeHouseMode) {
		this.resizeHouseMode = resizeHouseMode;
		if (!isFrozen()) {
			if (resizeHouseMode) {
				scanChildrenHeight();
			}
			setEditPointsVisible(resizeHouseMode);
			updateFloatingLabelPosition();
			boundingMesh.getSceneHints().setCullHint(resizeHouseMode ? CullHint.Inherit : CullHint.Always);
		}
	}

	public boolean isResizeHouseMode() {
		return resizeHouseMode;
	}

	@Override
	public void setEditPointsVisible(final boolean visible) {
		for (int i = 0; i < points.size(); i++) {
			final boolean visible_i = visible && (resizeHouseMode || i < 4 || i > 7);
			getEditPointShape(i).setVisible(visible_i);
			getEditPointShape(i).getSceneHints().setAllPickingHints(visible_i);
		}
	}

	public void setMovePointsVisible(final boolean visible) {
		final int n = points.size();
		for (int i = n - 4; i < n; i++) {
			final Spatial editPoint = pointsRoot.getChild(i);
			((Mesh) editPoint).setVisible(visible);
			final SceneHints sceneHints = editPoint.getSceneHints();
			sceneHints.setAllPickingHints(visible);
		}
	}

	@Override
	public void complete() {
		super.complete();
		newBoundingHeight = points.get(4).getZ() - height; // problem?
		applyNewHeight(boundingHeight, newBoundingHeight, true);
		if (!resizeHouseMode && orgPoints != null) {
			final int xi, yi;
			if (Util.isEqual(points.get(0).getX(), points.get(2).getX())) {
				xi = 1;
				yi = 0;
			} else {
				xi = 0;
				yi = 1;
			}
			final double dx = Math.abs(points.get(2).getValue(xi) - points.get(0).getValue(xi));
			final double dxOrg = Math.abs(orgPoints.get(2).getValue(xi) - orgPoints.get(0).getValue(xi));
			final double ratioX = dx / dxOrg;
			final double dy = Math.abs(points.get(1).getValue(yi) - points.get(0).getValue(yi));
			final double dyOrg = Math.abs(orgPoints.get(1).getValue(yi) - orgPoints.get(0).getValue(yi));
			final double ratioY = dy / dyOrg;
			reverseFoundationResizeEffect(getChildren(), dx, dxOrg, ratioX, dy, dyOrg, ratioY);
			orgPoints = null;
		}
	}

	private void reverseFoundationResizeEffect(final ArrayList<HousePart> children, final double dx, final double dxOrg, final double ratioX, final double dy, final double dyOrg, final double ratioY) {
		final ArrayList<HousePart> roofs = new ArrayList<HousePart>();
		for (final HousePart child : children) {
			reverseFoundationResizeEffect(child, dx, dxOrg, ratioX, dy, dyOrg, ratioY);
			if (child instanceof Wall) {
				final HousePart roof = ((Wall) child).getRoof();
				if (roof != null && !roofs.contains(roof)) {
					reverseFoundationResizeEffect(roof, dx, dxOrg, ratioX, dy, dyOrg, ratioY);
					roofs.add(roof);
				}
			} else if (child instanceof Rack) {
				reverseFoundationResizeEffect(child.getChildren(), dx, dxOrg, ratioX, dy, dyOrg, ratioY);
			}
		}
		for (final HousePart roof : roofs) {
			reverseFoundationResizeEffect(roof.getChildren(), dx, dxOrg, ratioX, dy, dyOrg, ratioY);
		}
	}

	private void reverseFoundationResizeEffect(final HousePart child, final double dx, final double dxOrg, final double ratioX, final double dy, final double dyOrg, final double ratioY) {
		for (final Vector3 childPoint : child.getPoints()) {
			double x = childPoint.getX() / ratioX;
			if (editPointIndex == 0 || editPointIndex == 1) {
				x += (dx - dxOrg) / dx;
			}
			childPoint.setX(x);
			double y = childPoint.getY() / ratioY;
			if (editPointIndex == 0 || editPointIndex == 2) {
				y += (dy - dyOrg) / dy;
			}
			childPoint.setY(y);
		}
	}

	@Override
	public void setPreviewPoint(final int x, final int y) {
		if (lockEdit && editPointIndex < 4) {
			return;
		}
		final int index;
		if (editPointIndex == -1) {
			index = isFirstPointInserted() ? 3 : 0;
		} else if (SceneManager.getInstance().isTopView() && editPointIndex > 3) {
			index = editPointIndex - 4;
		} else {
			index = editPointIndex;
		}

		final PickedHousePart pick = SelectUtil.pickPart(x, y, (HousePart) null);
		Vector3 p;
		if (pick != null && index < 4) {
			p = pick.getPoint().clone();
			snapToGrid(p, getAbsPoint(index), getGridSize());
			root.getSceneHints().setCullHint(CullHint.Never);
		} else {
			p = points.get(index).clone();
		}

		if (!isFirstPointInserted()) {
			points.get(index).set(p);
			points.get(1).set(p.add(0, 0.1, 0, null));
			points.get(2).set(p.add(0.1, 0, 0, null));
			points.get(3).set(p.add(0.1, 0.1, 0, null));
		} else {
			if (index < 4) {
				p = ensureDistanceFromOtherFoundations(p, index);
				if (!resizeHouseMode) {
					ensureIncludesChildren(p, index);
				}

				final int oppositeIndex;
				if (index == 0) {
					oppositeIndex = 3;
				} else if (index == 1) {
					oppositeIndex = 2;
				} else if (index == 2) {
					oppositeIndex = 1;
				} else {
					oppositeIndex = 0;
				}

				if (!Util.isEqual(p.getX(), points.get(oppositeIndex).getX()) && !Util.isEqual(p.getY(), points.get(oppositeIndex).getY())) {
					points.get(index).set(p);
					if (index == 0) {
						points.get(1).set(Util.projectPointOnLine(p, points.get(3), points.get(1), false));
						points.get(2).set(Util.projectPointOnLine(p, points.get(3), points.get(2), false));
					} else if (index == 3) {
						points.get(1).set(Util.projectPointOnLine(p, points.get(0), points.get(1), false));
						points.get(2).set(Util.projectPointOnLine(p, points.get(0), points.get(2), false));
					} else if (index == 1) {
						points.get(0).set(Util.projectPointOnLine(p, points.get(2), points.get(0), false));
						points.get(3).set(Util.projectPointOnLine(p, points.get(2), points.get(3), false));
					} else if (index == 2) {
						points.get(0).set(Util.projectPointOnLine(p, points.get(1), points.get(0), false));
						points.get(3).set(Util.projectPointOnLine(p, points.get(1), points.get(3), false));
					}
				}
			} else if (index < 8) {
				final int lower = editPointIndex - 4;
				final Vector3 base = getAbsPoint(lower);
				final Vector3 closestPoint = Util.closestPoint(base, Vector3.UNIT_Z, x, y);
				if (closestPoint == null) {
					return;
				}
				final Vector3 currentPoint = getAbsPoint(index);
				snapToGrid(closestPoint, currentPoint, getGridSize());
				if (closestPoint.getZ() < height + getGridSize()) {
					closestPoint.setZ(height + getGridSize());
				}
				if (!closestPoint.equals(currentPoint)) {
					newBoundingHeight = Math.max(0, closestPoint.getZ() - height);
					applyNewHeight(boundingHeight, newBoundingHeight, false);
				}
			}
			syncUpperPoints();
		}

		if (resizeHouseMode) {
			drawChildren();
		}
		draw();
		setEditPointsVisible(true);
		updateHandlesOfAllFoudations();
	}

	@Override
	public void drawChildren() {
		final List<HousePart> children = new ArrayList<HousePart>();
		collectChildren(this, children);
		for (final HousePart part : children) {
			if (part instanceof Roof) {
				part.draw();
			}
		}
		for (final HousePart part : children) {
			part.draw();
		}
	}

	private void collectChildren(final HousePart part, final List<HousePart> children) {
		if (!children.contains(part)) {
			children.add(part);
		}
		for (final HousePart child : part.getChildren()) {
			collectChildren(child, children);
		}
	}

	// private Vector3 ensureNotTooSmall(final Vector3 p, final int index) {
	// final double MIN_LENGHT = getGridSize();
	// final double x2 = getAbsPoint(index == 0 || index == 1 ? 2 : 0).getX();
	// if (getAbsPoint(index).getX() > x2) {
	// if (p.getX() - x2 < MIN_LENGHT)
	// p.setX(x2 + MIN_LENGHT);
	// } else {
	// if (x2 - p.getX() < MIN_LENGHT)
	// p.setX(x2 - MIN_LENGHT);
	// }
	//
	// final double y2 = getAbsPoint(index == 0 || index == 2 ? 1 : 0).getY();
	// if (getAbsPoint(index).getY() > y2) {
	// if (p.getY() - y2 < MIN_LENGHT)
	// p.setY(y2 + MIN_LENGHT);
	// } else {
	// if (y2 - p.getY() < MIN_LENGHT)
	// p.setY(y2 - MIN_LENGHT);
	// }
	//
	// return p;
	// }

	private void syncUpperPoints() {
		for (int i = 0; i < 4; i++) {
			points.get(i + 4).set(points.get(i)).setZ(Math.max(height, newBoundingHeight + height));
		}
	}

	private void ensureIncludesChildren(final Vector3 p, final int index) {
		if (children.isEmpty()) {
			return;
		}

		useOrgPoints = true;
		final List<Vector2> insidePoints = new ArrayList<Vector2>(children.size() * 2);
		for (final HousePart part : children) {
			if (part.children.size() > 2 && part.getPoints().size() > 1) {
				final Vector3 p0 = part.getAbsPoint(0);
				final Vector3 p2 = part.getAbsPoint(2);
				insidePoints.add(new Vector2(p0.getX(), p0.getY()));
				insidePoints.add(new Vector2(p2.getX(), p2.getY()));
			} else { // if the child is a solar panel, a rack, or a mirror
				final Vector3 p0 = part.getAbsPoint(0);
				insidePoints.add(new Vector2(p0.getX(), p0.getY()));
			}
		}

		final Vector3 p0 = getAbsPoint(0);
		final Vector3 p1 = getAbsPoint(1);
		final Vector3 p2 = getAbsPoint(2);
		final ReadOnlyVector2 p0_2d = new Vector2(p0.getX(), p0.getY());
		final ReadOnlyVector2 p1_2d = new Vector2(p1.getX(), p1.getY());
		final ReadOnlyVector2 p2_2d = new Vector2(p2.getX(), p2.getY());
		useOrgPoints = false;

		double uScaleMin = Double.MAX_VALUE;
		double uScaleMax = -Double.MAX_VALUE;
		double vScaleMin = Double.MAX_VALUE;
		double vScaleMax = -Double.MAX_VALUE;
		for (final Vector2 insidePoint : insidePoints) {
			final double uScale = Util.projectPointOnLineScale(insidePoint, p0_2d, p2_2d);
			if (uScaleMin > uScale) {
				uScaleMin = uScale;
			}
			if (uScaleMax < uScale) {
				uScaleMax = uScale;
			}
			final double vScale = Util.projectPointOnLineScale(insidePoint, p0_2d, p1_2d);
			if (vScaleMin > vScale) {
				vScaleMin = vScale;
			}
			if (vScaleMax < vScale) {
				vScaleMax = vScale;
			}
		}

		final double uScaleP = Util.projectPointOnLineScale(new Vector2(p.getX(), p.getY()), p0_2d, p2_2d);
		final double vScaleP = Util.projectPointOnLineScale(new Vector2(p.getX(), p.getY()), p0_2d, p1_2d);
		final double uScaleP0 = Util.projectPointOnLineScale(new Vector2(points.get(0).getX(), points.get(0).getY()), p0_2d, p2_2d);
		final double uScaleP2 = Util.projectPointOnLineScale(new Vector2(points.get(2).getX(), points.get(2).getY()), p0_2d, p2_2d);
		final double vScaleP0 = Util.projectPointOnLineScale(new Vector2(points.get(0).getX(), points.get(0).getY()), p0_2d, p1_2d);
		final double vScaleP1 = Util.projectPointOnLineScale(new Vector2(points.get(1).getX(), points.get(1).getY()), p0_2d, p1_2d);
		final boolean isOnRight = uScaleP2 >= uScaleP0 && (index == 2 || index == 3);
		final boolean isOnTop = vScaleP1 >= vScaleP0 && (index == 1 || index == 3);

		final double uScale;
		if (isOnRight && uScaleP < uScaleMax) {
			uScale = uScaleMax;
		} else if (!isOnRight && uScaleP > uScaleMin) {
			uScale = uScaleMin;
		} else {
			uScale = uScaleP;
		}

		final double vScale;
		if (isOnTop && vScaleP < vScaleMax) {
			vScale = vScaleMax;
		} else if (!isOnTop && vScaleP > vScaleMin) {
			vScale = vScaleMin;
		} else {
			vScale = vScaleP;
		}

		final Vector3 u = p2.subtract(p0, null);
		final Vector3 v = p1.subtract(p0, null);
		p.set(p0).addLocal(u.multiplyLocal(uScale)).addLocal(v.multiplyLocal(vScale));
	}

	private Vector3 ensureDistanceFromOtherFoundations(final Vector3 p, final int index) {
		if (Scene.getInstance().getDisallowFoundationOverlap()) {
			for (final HousePart part : Scene.getInstance().getParts()) {
				if (part instanceof Foundation && part != this) {
					final Vector3 p0 = part.getAbsPoint(0);
					final Vector3 p1 = part.getAbsPoint(1);
					final Vector3 p2 = part.getAbsPoint(2);
					final double minDistance = 0;
					final double minX = Math.min(p0.getX(), Math.min(p1.getX(), p2.getX())) - minDistance;
					final double maxX = Math.max(p0.getX(), Math.max(p1.getX(), p2.getX())) + minDistance;
					final double minY = Math.min(p0.getY(), Math.min(p1.getY(), p2.getY())) - minDistance;
					final double maxY = Math.max(p0.getY(), Math.max(p1.getY(), p2.getY())) + minDistance;
					if (isFirstPointInserted()) {
						final double oppositeX = getAbsPoint(index == 0 || index == 1 ? 2 : 0).getX();
						final double oppositeY = getAbsPoint(index == 0 || index == 2 ? 1 : 0).getY();
						if (!(oppositeX <= minX && p.getX() <= minX || oppositeX >= maxX && p.getX() >= maxX || oppositeY <= minY && p.getY() <= minY || oppositeY >= maxY && p.getY() >= maxY)) {
							return getAbsPoint(index);
						}
					} else {
						if (p.getX() > minX && p.getX() < maxX && p.getY() > minY && p.getY() < maxY) {
							double shortestDistance = Double.MAX_VALUE;
							double distance;
							final Vector3 newP = new Vector3();
							distance = p.getX() - minX;
							if (distance < shortestDistance) {
								shortestDistance = distance;
								newP.set(minX, p.getY(), p.getZ());
							}
							distance = maxX - p.getX();
							if (distance < shortestDistance) {
								shortestDistance = distance;
								newP.set(maxX, p.getY(), p.getZ());
							}
							distance = p.getY() - minY;
							if (distance < shortestDistance) {
								shortestDistance = distance;
								newP.set(p.getX(), minY, p.getZ());
							}
							distance = maxY - p.getY();
							if (distance < shortestDistance) {
								shortestDistance = distance;
								newP.set(p.getX(), maxY, p.getZ());
							}
							return newP;
						}
					}
				}
			}
		}
		return p;
	}

	private void applyNewHeight(final double orgHeight, final double newHeight, final boolean finalize) {
		if (newHeight == 0) {
			return;
		}
		final double scale = newHeight / orgHeight;
		applyNewHeight(children, scale, finalize);
		if (finalize) {
			boundingHeight = newHeight;
		}
	}

	private void applyNewHeight(final ArrayList<HousePart> children, final double scale, final boolean finalize) {
		for (final HousePart child : children) {
			if (child instanceof Wall || child instanceof Floor || child instanceof Roof) {
				child.setHeight(child.orgHeight * scale, finalize);
				applyNewHeight(child.getChildren(), scale, finalize);
			}
		}
	}

	/** Rescale the building in the original X, Y, Z directions. */
	public void rescale(final double scaleX, final double scaleY, final double scaleZ) {
		final boolean currentOverlapAllowance = Scene.getInstance().getDisallowFoundationOverlap();
		Scene.getInstance().setDisallowFoundationOverlap(false);
		final double a = Math.toRadians(getAzimuth());
		if (!Util.isZero(a)) {
			rotate(a, null);
		}
		final Vector3 center = getAbsCenter().multiplyLocal(1, 1, 0);
		move(center.negateLocal(), center.length());
		for (int i = 0; i < points.size(); i++) {
			points.get(i).multiplyLocal(scaleX, scaleY, 1);
		}
		applyNewHeight(children, scaleZ, true);
		final List<Roof> roofs = getRoofs();
		for (final Roof r : roofs) {
			r.setOverhangLength(r.getOverhangLength() * scaleZ);
		}
		move(center.negateLocal(), center.length());
		if (!Util.isZero(a)) {
			rotate(-a, null);
		}
		Scene.getInstance().setDisallowFoundationOverlap(currentOverlapAllowance);
	}

	/** Scale house for upgrading to new version. This can be removed in 2017. Don't call this if you intend to scale a building. Call rescale instead. */
	public void scaleHouseForNewVersion(final double scale) {
		final double h = points.get(4).getZ() - height;
		applyNewHeight(h, h * 10, true);
		final double oldHeight = height;
		height *= scale;
		final double addHeight = height - oldHeight;
		for (final HousePart wall : children) {
			for (final Vector3 point : wall.points) {
				point.addLocal(0, 0, addHeight);
			}
			for (final HousePart floor : wall.children) {
				if (floor instanceof Floor) {
					floor.setHeight(floor.getHeight() + addHeight);
				}
			}
		}
		for (int i = 0; i < points.size(); i++) {
			points.get(i).multiplyLocal(10);
		}
	}

	@Override
	protected void drawMesh() {
		if (boundingHeight == 0) {
			scanChildrenHeight();
		}
		final boolean drawable = points.size() == 12;
		if (drawable) {
			drawTopMesh();
			drawSideMesh();
			drawOutline(boundingMesh, points.get(7).getZf());
			drawOutline(outlineMesh, (float) height);
			updateHandles();
			drawSolarReceiver();
			drawImports();
			updateFloatingLabelPosition();
			foundationPolygon.draw();
		}
	}

	public void drawSolarReceiver() {
		if (solarReceiver == null) {
			return;
		}
		int countMirrors = 0;
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Mirror) {
				final Mirror m = (Mirror) p;
				if (m.getHeliostatTarget() == this) {
					countMirrors++;
				}
			}
		}
		solarReceiver.setVisible(countMirrors > 0);
		if (solarReceiver.isVisible()) {
			if (bloomRenderPass == null) {
				bloomRenderPass = new BloomRenderPass(SceneManager.getInstance().getCamera(), 10);
				// bloomRenderPass.setNrBlurPasses(1);
				SceneManager.getInstance().getPassManager().add(bloomRenderPass);
			}
			if (!bloomRenderPass.contains(solarReceiver)) {
				bloomRenderPass.add(solarReceiver);
			}
			bloomRenderPass.setBlurIntensityMultiplier(Math.min(0.01f * countMirrors, 0.8f));
			double rx = 0;
			double ry = 0;
			double xmin = Double.MAX_VALUE;
			double xmax = -Double.MAX_VALUE;
			double ymin = Double.MAX_VALUE;
			double ymax = -Double.MAX_VALUE;
			int count = 0;
			for (final HousePart p : children) {
				if (p instanceof Wall) {
					final Vector3 c = p.getAbsCenter();
					rx += c.getX();
					ry += c.getY();
					if (xmin > c.getX()) {
						xmin = c.getX();
					} else if (xmax < c.getX()) {
						xmax = c.getX();
					}
					if (ymin > c.getY()) {
						ymin = c.getY();
					} else if (ymax < c.getY()) {
						ymax = c.getY();
					}
					count++;
				}
			}
			solarReceiver.setHeight(getSolarReceiverHeight() * 0.15);
			Vector3 o;
			if (count == 0) {
				o = getAbsCenter();
				o.setZ(getSolarReceiverHeight() - solarReceiver.getHeight() * 0.5);
				solarReceiver.setRadius(10);
			} else {
				o = new Vector3(rx / count, ry / count, getSolarReceiverHeight() - solarReceiver.getHeight() * 0.5);
				final double r1 = Math.max((xmax - xmin), (ymax - ymin)) / 2;
				final double r2 = Math.max(r1 * 0.4, 4);
				solarReceiver.setRadius(r1 + r2);
			}
			solarReceiver.setTranslation(o);
		}
	}

	public static void updateBloom() {
		if (bloomRenderPass != null) {
			bloomRenderPass.markNeedsRefresh();
		}
	}

	public Vector3 getSolarReceiverCenter() {
		double rx = 0;
		double ry = 0;
		int count = 0;
		for (final HousePart p : children) {
			if (p instanceof Wall) {
				final Vector3 c = p.getAbsCenter();
				rx += c.getX();
				ry += c.getY();
				count++;
			}
		}
		Vector3 o;
		if (count == 0) {
			o = getAbsCenter();
			o.setZ(getSolarReceiverHeight());
		} else {
			o = new Vector3(rx / count, ry / count, getSolarReceiverHeight());
		}
		return o;
	}

	private double getSolarReceiverHeight() {
		return getBoundingHeight() + height - 0.1; // shift a small distance to avoid collision with a possible roof
	}

	public void drawSideMesh() {
		final FloatBuffer vertexBuffer0 = sideMesh[0].getMeshData().getVertexBuffer();
		final FloatBuffer vertexBuffer1 = sideMesh[1].getMeshData().getVertexBuffer();
		final FloatBuffer vertexBuffer2 = sideMesh[2].getMeshData().getVertexBuffer();
		final FloatBuffer vertexBuffer3 = sideMesh[3].getMeshData().getVertexBuffer();
		vertexBuffer0.rewind();
		vertexBuffer1.rewind();
		vertexBuffer2.rewind();
		vertexBuffer3.rewind();
		final Vector3 p0 = getAbsPoint(0);
		final Vector3 p1 = getAbsPoint(1);
		final Vector3 p2 = getAbsPoint(2);
		final Vector3 p3 = getAbsPoint(3);
		vertexBuffer0.put(p0.getXf()).put(p0.getYf()).put((float) height);
		vertexBuffer0.put(p0.getXf()).put(p0.getYf()).put(0f);
		vertexBuffer0.put(p2.getXf()).put(p2.getYf()).put(0f);
		vertexBuffer0.put(p2.getXf()).put(p2.getYf()).put(0f);
		vertexBuffer0.put(p2.getXf()).put(p2.getYf()).put((float) height);
		vertexBuffer0.put(p0.getXf()).put(p0.getYf()).put((float) height);

		vertexBuffer1.put(p2.getXf()).put(p2.getYf()).put((float) height);
		vertexBuffer1.put(p2.getXf()).put(p2.getYf()).put(0f);
		vertexBuffer1.put(p3.getXf()).put(p3.getYf()).put(0f);
		vertexBuffer1.put(p3.getXf()).put(p3.getYf()).put(0f);
		vertexBuffer1.put(p3.getXf()).put(p3.getYf()).put((float) height);
		vertexBuffer1.put(p2.getXf()).put(p2.getYf()).put((float) height);

		vertexBuffer2.put(p3.getXf()).put(p3.getYf()).put((float) height);
		vertexBuffer2.put(p3.getXf()).put(p3.getYf()).put(0f);
		vertexBuffer2.put(p1.getXf()).put(p1.getYf()).put(0f);
		vertexBuffer2.put(p1.getXf()).put(p1.getYf()).put(0f);
		vertexBuffer2.put(p1.getXf()).put(p1.getYf()).put((float) height);
		vertexBuffer2.put(p3.getXf()).put(p3.getYf()).put((float) height);

		vertexBuffer3.put(p1.getXf()).put(p1.getYf()).put((float) height);
		vertexBuffer3.put(p1.getXf()).put(p1.getYf()).put(0f);
		vertexBuffer3.put(p0.getXf()).put(p0.getYf()).put(0f);
		vertexBuffer3.put(p0.getXf()).put(p0.getYf()).put(0f);
		vertexBuffer3.put(p0.getXf()).put(p0.getYf()).put((float) height);
		vertexBuffer3.put(p1.getXf()).put(p1.getYf()).put((float) height);

		final FloatBuffer normalBuffer0 = sideMesh[0].getMeshData().getNormalBuffer();
		final FloatBuffer normalBuffer1 = sideMesh[1].getMeshData().getNormalBuffer();
		final FloatBuffer normalBuffer2 = sideMesh[2].getMeshData().getNormalBuffer();
		final FloatBuffer normalBuffer3 = sideMesh[3].getMeshData().getNormalBuffer();
		normalBuffer0.rewind();
		normalBuffer1.rewind();
		normalBuffer2.rewind();
		normalBuffer3.rewind();
		final ReadOnlyVector3 n1 = p0.subtract(p1, null).normalizeLocal();
		final ReadOnlyVector3 n2 = p2.subtract(p0, null).normalizeLocal();
		ReadOnlyVector3 normal = n1;
		((UserData) sideMesh[0].getUserData()).setNormal(normal);
		for (int i = 0; i < 6; i++) {
			normalBuffer0.put(normal.getXf()).put(normal.getYf()).put(normal.getZf());
		}
		normal = n2;
		((UserData) sideMesh[1].getUserData()).setNormal(normal);
		for (int i = 0; i < 6; i++) {
			normalBuffer1.put(normal.getXf()).put(normal.getYf()).put(normal.getZf());
		}
		normal = n1.negate(null);
		((UserData) sideMesh[2].getUserData()).setNormal(normal);
		for (int i = 0; i < 6; i++) {
			normalBuffer2.put(normal.getXf()).put(normal.getYf()).put(normal.getZf());
		}
		normal = n2.negate(null);
		((UserData) sideMesh[3].getUserData()).setNormal(normal);
		for (int i = 0; i < 6; i++) {
			normalBuffer3.put(normal.getXf()).put(normal.getYf()).put(normal.getZf());
		}

		for (int i = 0; i < 4; i++) {
			sideMesh[i].updateModelBound();
			CollisionTreeManager.INSTANCE.removeCollisionTree(sideMesh[i]);
		}
	}

	public void drawTopMesh() {
		mesh.setVisible(!Scene.getInstance().isGroundImageEnabled());
		final FloatBuffer vertexBuffer = mesh.getMeshData().getVertexBuffer();
		vertexBuffer.rewind();
		ReadOnlyVector3 p;
		final Vector3 p0 = getAbsPoint(0);
		final Vector3 p1 = getAbsPoint(1);
		final Vector3 p2 = getAbsPoint(2);
		final Vector3 p3 = getAbsPoint(3);
		p = p0;
		vertexBuffer.put(p.getXf()).put(p.getYf()).put((float) height);
		p = p2;
		vertexBuffer.put(p.getXf()).put(p.getYf()).put((float) height);
		p = p1;
		vertexBuffer.put(p.getXf()).put(p.getYf()).put((float) height);
		vertexBuffer.put(p.getXf()).put(p.getYf()).put((float) height);
		p = p2;
		vertexBuffer.put(p.getXf()).put(p.getYf()).put((float) height);
		p = p3;
		vertexBuffer.put(p.getXf()).put(p.getYf()).put((float) height);

		final ReadOnlyVector3 normal = Vector3.UNIT_Z;
		final FloatBuffer normalBuffer = mesh.getMeshData().getNormalBuffer();
		normalBuffer.rewind();
		for (int i = 0; i < 6; i++) {
			normalBuffer.put(normal.getXf()).put(normal.getYf()).put(normal.getZf());
		}

		final FloatBuffer textureBuffer = mesh.getMeshData().getTextureBuffer(0);
		textureBuffer.rewind();
		textureBuffer.put(0).put(0);
		textureBuffer.put(1).put(0);
		textureBuffer.put(0).put(1);
		textureBuffer.put(0).put(1);
		textureBuffer.put(1).put(0);
		textureBuffer.put(1).put(1);

		mesh.updateModelBound();
		CollisionTreeManager.INSTANCE.removeCollisionTree(mesh);
	}

	private void drawOutline(final Mesh mesh, final float height) {
		final FloatBuffer buf = mesh.getMeshData().getVertexBuffer();
		buf.rewind();
		final Vector3 p0 = getAbsPoint(0);
		final Vector3 p1 = getAbsPoint(1);
		final Vector3 p2 = getAbsPoint(2);
		final Vector3 p3 = getAbsPoint(3);

		putOutlinePoint(buf, p0);
		putOutlinePoint(buf, p2);
		putOutlinePoint(buf, p2);
		putOutlinePoint(buf, p3);
		putOutlinePoint(buf, p3);
		putOutlinePoint(buf, p1);
		putOutlinePoint(buf, p1);
		putOutlinePoint(buf, p0);

		putOutlinePoint(buf, p0, height);
		putOutlinePoint(buf, p2, height);
		putOutlinePoint(buf, p2, height);
		putOutlinePoint(buf, p3, height);
		putOutlinePoint(buf, p3, height);
		putOutlinePoint(buf, p1, height);
		putOutlinePoint(buf, p1, height);
		putOutlinePoint(buf, p0, height);

		putOutlinePoint(buf, p0);
		putOutlinePoint(buf, p0, height);
		putOutlinePoint(buf, p2);
		putOutlinePoint(buf, p2, height);
		putOutlinePoint(buf, p3);
		putOutlinePoint(buf, p3, height);
		putOutlinePoint(buf, p1);
		putOutlinePoint(buf, p1, height);

		mesh.updateModelBound();
	}

	@Override
	public void drawGrids(final double gridSize) {
		final ReadOnlyVector3 p0 = getAbsPoint(0);
		final ReadOnlyVector3 p1 = getAbsPoint(1);
		final ReadOnlyVector3 p2 = getAbsPoint(2);
		final ReadOnlyVector3 width = p2.subtract(p0, null);
		final ReadOnlyVector3 height = p1.subtract(p0, null);
		final ArrayList<ReadOnlyVector3> points = new ArrayList<ReadOnlyVector3>();

		final int cols = (int) (width.length() / gridSize);

		for (int col = 0; col < cols + 1; col++) {
			final ReadOnlyVector3 lineP1 = width.normalize(null).multiplyLocal(col * gridSize).addLocal(p0);
			points.add(lineP1);
			final ReadOnlyVector3 lineP2 = lineP1.add(height, null);
			points.add(lineP2);
		}

		final int rows = (int) (height.length() / gridSize);

		for (int row = 0; row < rows + 1; row++) {
			final ReadOnlyVector3 lineP1 = height.normalize(null).multiplyLocal(row * gridSize).addLocal(p0);
			points.add(lineP1);
			final ReadOnlyVector3 lineP2 = lineP1.add(width, null);
			points.add(lineP2);
		}
		if (points.size() < 2) {
			return;
		}
		final FloatBuffer buf = BufferUtils.createVector3Buffer(points.size());
		for (final ReadOnlyVector3 p : points) {
			buf.put(p.getXf()).put(p.getYf()).put((float) this.height + 0.1f);
		}

		gridsMesh.getMeshData().setVertexBuffer(buf);
	}

	private void putOutlinePoint(final FloatBuffer buf, final Vector3 p) {
		putOutlinePoint(buf, p, 0);
	}

	private void putOutlinePoint(final FloatBuffer buf, final Vector3 p, final float height) {
		buf.put(p.getXf()).put(p.getYf()).put(p.getZf() + height);
	}

	public void scanChildrenHeight() {
		if (!isFirstPointInserted()) {
			return;
		}
		boundingHeight = scanChildrenHeight(this) - height;
		for (int i = 4; i < Math.min(8, points.size()); i++) {
			points.get(i).setZ(boundingHeight + height);
		}
		if (importedNodes != null) {
			boolean taller = false;
			for (final Node n : importedNodes) {
				final OrientedBoundingBox b = Util.getOrientedBoundingBox(n);
				final double bh = b.getCenter().getZ() + b.getExtent().getZ();
				if (bh > boundingHeight) {
					boundingHeight = bh;
					taller = true;
				}
			}
			if (taller) {
				boundingHeight -= height; // subtract as bounding box height includes the foundation height
			}
		}
		newBoundingHeight = boundingHeight;
		syncUpperPoints();
		updateEditShapes();
	}

	private double scanChildrenHeight(final HousePart part) {
		double maxHeight = height;
		if (part instanceof Wall || part instanceof Roof) {
			for (int i = 0; i < part.points.size(); i++) {
				final ReadOnlyVector3 p = part.getAbsPoint(i);
				maxHeight = Math.max(maxHeight, p.getZ());
			}
		}
		for (final HousePart child : part.children) {
			maxHeight = Math.max(maxHeight, scanChildrenHeight(child));
		}
		return maxHeight;
	}

	@Override
	public void flatten(final double flattenTime) {
		root.setRotation((new Matrix3().fromAngles(flattenTime * Math.PI / 2, 0, 0)));
		super.flatten(flattenTime);
	}

	@Override
	public void drawAnnotations() {
		final int[] order = { 0, 1, 3, 2, 0 };
		int annotCounter = 0;
		for (int i = 0; i < order.length - 1; i++, annotCounter++) {
			final SizeAnnotation annot = fetchSizeAnnot(annotCounter++);
			annot.setRange(getAbsPoint(order[i]), getAbsPoint(order[i + 1]), getCenter(), getNormal(), false, Align.Center, true, true, false);
			annot.setLineWidth(original == null ? 1f : 2f);
		}
	}

	@Override
	public void setEditPoint(int editPoint) {
		if (!resizeHouseMode && editPoint > 3 && editPoint < 8) {
			editPoint -= 4;
		}
		// super.setEditPoint(editPoint);
		editPointIndex = editPoint;
		if (editPoint < 8) {
			drawCompleted = false;
		}

		if (!resizeHouseMode) {
			saveOrgPoints();
			minX = Double.MAX_VALUE;
			minY = Double.MAX_VALUE;
			maxX = -Double.MAX_VALUE;
			maxY = -Double.MAX_VALUE;
			for (final HousePart part : children) {
				if (part.children.size() > 2 && part.getPoints().size() > 1) {
					final Vector3 p1 = part.getAbsPoint(0);
					final Vector3 p2 = part.getAbsPoint(2);
					minX = Math.min(p1.getX(), minX);
					minX = Math.min(p2.getX(), minX);
					minY = Math.min(p1.getY(), minY);
					minY = Math.min(p2.getY(), minY);
					maxX = Math.max(p1.getX(), maxX);
					maxX = Math.max(p2.getX(), maxX);
					maxY = Math.max(p1.getY(), maxY);
					maxY = Math.max(p2.getY(), maxY);
				} else { // if the child is a solar panel, a rack, or a mirror
					final Vector3 p1 = part.getAbsPoint(0);
					minX = Math.min(p1.getX(), minX);
					minY = Math.min(p1.getY(), minY);
					maxX = Math.max(p1.getX(), maxX);
					maxY = Math.max(p1.getY(), maxY);
				}
			}
		}
	}

	public void saveOrgPoints() {
		orgPoints = new ArrayList<Vector3>(4);
		for (int i = 0; i < 4; i++) {
			orgPoints.add(points.get(i).clone());
		}
	}

	@Override
	protected String getTextureFileName() {
		return Scene.getInstance().getTextureMode() == TextureMode.Full ? "foundation.jpg" : null;
	}

	@Override
	public ReadOnlyVector3 getCenter() {
		return super.getCenter().multiply(new Vector3(1, 1, 0), null);
	}

	@Override
	public boolean isPrintable() {
		return false;
	}

	@Override
	public double getGridSize() {
		return SceneManager.getInstance().isFineGrid() ? 1.0 : 5.0;
	}

	@Override
	public void updateTextureAndColor() {
		for (final Mesh mesh : sideMesh) {
			mesh.setDefaultColor(Scene.getInstance().getTextureMode() == TextureMode.Full ? ColorRGBA.GRAY : (getColor() == null ? Scene.getInstance().getFoundationColor() : getColor()));
			updateTextureAndColor(mesh, getColor() == null ? Scene.getInstance().getFoundationColor() : getColor());
		}
		updateTextureAndColor(mesh, getColor() == null ? Scene.getInstance().getFoundationColor() : getColor());
		if (!SceneManager.getInstance().getSolarHeatMap() && importedNodes != null) {
			final int n = importedNodes.size();
			if (n > 0) {
				Node ni;
				for (int i = 0; i < n; i++) {
					ni = importedNodes.get(i);
					if (root.getChildren().contains(ni)) {
						for (final Spatial s : ni.getChildren()) {
							if (s instanceof Mesh) {
								if (s instanceof Line) {
									continue;
								}
								final Mesh m = (Mesh) s;
								final UserData ud = (UserData) m.getUserData();
								final TextureState ts = (TextureState) m.getLocalRenderState(StateType.Texture);
								if (ts == null || ts.getTexture() == null) {
									m.clearRenderState(StateType.Texture);
									// m.setDefaultColor(nis.getDefaultColor());
								} else {
									if (ud.getTextureBuffer() == null || Util.isZero(ud.getTextureBuffer())) {
										m.clearRenderState(StateType.Texture);
										// m.setDefaultColor(nis.getDefaultColor());
									} else {
										m.getMeshData().setTextureBuffer(ud.getTextureBuffer(), 0);
										m.setRenderState(ud.getRenderState());
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public void move(final Vector3 d, final ArrayList<Vector3> movePoints) {
		if (lockEdit) {
			return;
		}
		if (selectedMesh != null) { // if a mesh is selected, move the parent node
			translateImportedNode(selectedMesh.getParent(), d.getX(), d.getY(), d.getZ());
			draw();
			drawMeshSelection(selectedMesh);
		} else {
			final List<Vector3> orgPoints = new ArrayList<Vector3>(movePoints.size());
			for (int i = 0; i < points.size(); i++) {
				orgPoints.add(points.get(i));
			}
			for (int i = 0; i < points.size(); i++) {
				final Vector3 newP = movePoints.get(i).add(d, null);
				points.set(i, newP);
				if (i == points.size() - 1 && ensureDistanceFromOtherFoundations(newP, i) != newP) {
					for (int j = 0; j < points.size(); j++) {
						points.set(j, orgPoints.get(j));
					}
					return;
				}
			}
			if (SceneManager.getInstance().getSelectedPart() == this) {
				drawAzimuthArrow();
			}
			draw();
			drawChildren();
			updateHandlesOfAllFoudations();
		}
	}

	public void move(final Vector3 v, final double steplength) {
		v.normalizeLocal();
		v.multiplyLocal(steplength);
		final ArrayList<Vector3> movePoints = new ArrayList<Vector3>(points.size());
		for (final Vector3 p : points) {
			movePoints.add(p.clone());
		}
		move(v, movePoints);
		if (solarReceiver.isVisible()) { // when the foundation with a solar receiver moves, update the mirrors that link with it
			for (final HousePart x : Scene.getInstance().getParts()) {
				if (x instanceof Mirror) {
					final Mirror m = (Mirror) x;
					if (m.getHeliostatTarget() == this) {
						m.draw();
					}
				}
			}
		}
	}

	public double getBoundingHeight() {
		// return boundingHeight; // do not just return the boundingHeight because it may represent a previous value
		return scanChildrenHeight(this) - height;
	}

	public double getPassiveSolarNow() {
		return passiveSolarNow;
	}

	public void setPassiveSolarNow(final double passiveSolarNow) {
		this.passiveSolarNow = passiveSolarNow;
	}

	public double getPhotovoltaicNow() {
		return photovoltaicNow;
	}

	public void setPhotovoltaicNow(final double photovoltaicNow) {
		this.photovoltaicNow = photovoltaicNow;
	}

	public double getHeatingNow() {
		return heatingNow;
	}

	public void setHeatingNow(final double heatingNow) {
		this.heatingNow = heatingNow;
	}

	public double getCoolingNow() {
		return coolingNow;
	}

	public void setCoolingNow(final double coolingNow) {
		this.coolingNow = coolingNow;
	}

	public double getTotalEnergyNow() {
		return totalEnergyNow;
	}

	public void setTotalEnergyNow(final double totalEnergyNow) {
		this.totalEnergyNow = totalEnergyNow;
	}

	public double getPassiveSolarToday() {
		return passiveSolarToday;
	}

	public void setPassiveSolarToday(final double passiveSolarToday) {
		this.passiveSolarToday = passiveSolarToday;
	}

	public double getPhotovoltaicToday() {
		return photovoltaicToday;
	}

	public void setPhotovoltaicToday(final double photovoltaicToday) {
		this.photovoltaicToday = photovoltaicToday;
	}

	public double getHeatingToday() {
		return heatingToday;
	}

	public void setHeatingToday(final double heatingToday) {
		this.heatingToday = heatingToday;
	}

	public double getCoolingToday() {
		return coolingToday;
	}

	public void setCoolingToday(final double coolingToday) {
		this.coolingToday = coolingToday;
	}

	public double getTotalEnergyToday() {
		return totalEnergyToday;
	}

	public void setTotalEnergyToday(final double totalEnergyToday) {
		this.totalEnergyToday = totalEnergyToday;
	}

	/** If center is null, use the center of this foundation */
	public void rotate(final double angle, ReadOnlyVector3 center) {
		final Matrix3 matrix = new Matrix3().fromAngles(0, 0, angle);
		if (center == null) {
			center = toRelative(getCenter().clone());
		}
		for (int i = 0; i < points.size(); i++) {
			final Vector3 p = getAbsPoint(i);
			final Vector3 op = p.subtract(center, null);
			matrix.applyPost(op, op);
			op.add(center, p);
			points.get(i).set(toRelative(p));
		}
		if (SceneManager.getInstance().getSelectedPart() == this) {
			drawAzimuthArrow();
		}
		setRotatedNormalsForImportedMeshes();
	}

	/** @return the azimuth of the reference vector of this foundation in degrees */
	public double getAzimuth() {
		final Vector3 v = getAbsPoint(0);
		final Vector3 v1 = getAbsPoint(1);
		final double ly = v.distance(v1);
		double a = 90 + Math.toDegrees(Math.asin((v.getY() - v1.getY()) / ly));
		if (v.getX() > v1.getX()) {
			a = 360 - a;
		}
		if (Util.isZero(a - 360)) {
			a = 0;
		}
		return a;
	}

	@Override
	public Vector3 getAbsPoint(final int index, final Vector3 result) {
		if (useOrgPoints && orgPoints != null) {
			return super.toAbsolute(orgPoints.get(index), result);
		} else {
			return super.getAbsPoint(index, result);
		}
	}

	public void setLockEdit(final boolean b) {
		lockEdit = b;
	}

	public boolean getLockEdit() {
		return lockEdit;
	}

	public void drawAzimuthArrow() {
		final double cos30 = Math.cos(Math.toRadians(30));
		final double sin30 = Math.sin(Math.toRadians(30));
		final FloatBuffer arrowVertices = azimuthArrow.getMeshData().getVertexBuffer();
		arrowVertices.clear();
		final Vector3 v = getAbsPoint(0).subtractLocal(getAbsPoint(1)).normalizeLocal().multiplyLocal(20).negateLocal();
		final Vector3 p = getAbsPoint(1).addLocal(getAbsPoint(3)).multiplyLocal(0.5);
		arrowVertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
		p.addLocal(v);
		arrowVertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
		final double arrowX = v.getX() / v.length();
		final double arrowY = v.getY() / v.length();
		final float r = 3;
		float wingx = (float) (r * (arrowX * cos30 + arrowY * sin30));
		float wingy = (float) (r * (arrowY * cos30 - arrowX * sin30));
		arrowVertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
		arrowVertices.put(p.getXf() - wingx).put(p.getYf() - wingy).put(p.getZf());
		wingx = (float) (r * (arrowX * cos30 - arrowY * sin30));
		wingy = (float) (r * (arrowY * cos30 + arrowX * sin30));
		arrowVertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
		arrowVertices.put(p.getXf() - wingx).put(p.getYf() - wingy).put(p.getZf());
		azimuthArrow.getMeshData().updateVertexCount();
		azimuthArrow.updateModelBound();
		updateAzimuthArrowVisibility(SceneManager.getInstance().getSelectedPart() == this);
	}

	public void updateAzimuthArrowVisibility(final boolean b) {
		azimuthArrow.setVisible(b);
	}

	/** Draw the heat flux through the floor area on the foundation */
	@Override
	public void drawHeatFlux() {

		FloatBuffer arrowsVertices = heatFlux.getMeshData().getVertexBuffer();
		final int cols = (int) Math.max(2, getAbsPoint(0).distance(getAbsPoint(2)) / Scene.getInstance().getHeatVectorGridSize());
		final int rows = (int) Math.max(2, getAbsPoint(0).distance(getAbsPoint(1)) / Scene.getInstance().getHeatVectorGridSize());
		arrowsVertices = BufferUtils.createVector3Buffer(rows * cols * 6);
		heatFlux.getMeshData().setVertexBuffer(arrowsVertices);
		final double heat = calculateHeatVector();
		if (heat != 0) {
			final ReadOnlyVector3 o = getAbsPoint(0);
			final ReadOnlyVector3 u = getAbsPoint(2).subtract(o, null);
			final ReadOnlyVector3 v = getAbsPoint(1).subtract(o, null);
			final ReadOnlyVector3 normal = getNormal().negate(null);
			final Vector3 a = new Vector3();
			double g, h;
			boolean init = true;
			final Building building = new Building(this);
			for (int j = 0; j < cols; j++) {
				h = j + 0.5;
				for (int i = 0; i < rows; i++) {
					g = i + 0.5;
					a.setX(o.getX() + g * v.getX() / rows + h * u.getX() / cols);
					a.setY(o.getY() + g * v.getY() / rows + h * u.getY() / cols);
					if (building.contains(a.getX(), a.getY(), init)) {
						a.setZ(o.getZ());
						drawArrow(a, normal, arrowsVertices, heat);
					}
					if (init) {
						init = false;
					}
				}
			}
			heatFlux.getMeshData().updateVertexCount();
			heatFlux.updateModelBound();
		}

		updateHeatFluxVisibility();

	}

	@Override
	protected void computeArea() {
		if (isDrawCompleted()) {
			final Vector3 p0 = getAbsPoint(0);
			final Vector3 p1 = getAbsPoint(1);
			final Vector3 p2 = getAbsPoint(2);
			final double C = 100.0;
			final double scale = Scene.getInstance().getAnnotationScale();
			area = Math.round(Math.round(p2.subtract(p0, null).length() * scale * C) / C * Math.round(p1.subtract(p0, null).length() * scale * C) / C * C) / C;
		} else {
			area = 0.0;
		}
	}

	@Override
	public boolean isCopyable() {
		return false;
	}

	@Override
	public void setUValue(final double uValue) {
		this.uValue = uValue;
	}

	@Override
	public double getUValue() {
		return uValue;
	}

	@Override
	public void setVolumetricHeatCapacity(final double volumetricHeatCapacity) {
		this.volumetricHeatCapacity = volumetricHeatCapacity;
	}

	@Override
	public double getVolumetricHeatCapacity() {
		return volumetricHeatCapacity;
	}

	public Thermostat getThermostat() {
		return thermostat;
	}

	public void setUtilityBill(final UtilityBill utilityBill) {
		this.utilityBill = utilityBill;
	}

	public UtilityBill getUtilityBill() {
		return utilityBill;
	}

	// assuming that there may be multiple roofs on this foundation, return a list of them
	public List<Roof> getRoofs() {
		final List<Roof> roofs = new ArrayList<Roof>();
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Wall && p.getTopContainer() == this) {
				for (final HousePart c : p.getChildren()) {
					if (c instanceof Roof && !roofs.contains(c)) {
						roofs.add((Roof) c);
					}
				}
			}
		}
		return roofs;
	}

	public List<Window> getWindows() {
		final List<Window> list = new ArrayList<Window>();
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Window && p.getTopContainer() == this) {
				list.add((Window) p);
			}
		}
		return list;
	}

	public int countParts(final Class<?> clazz) {
		int count = 0;
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p.getTopContainer() == this && clazz.isInstance(p)) {
				count++;
			}
		}
		return count;
	}

	public int countParts(final Class<?>[] clazz) {
		int count = 0;
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p.getTopContainer() == this) {
				for (final Class<?> c : clazz) {
					if (c.isInstance(p)) {
						count++;
					}
				}
			}
		}
		return count;
	}

	public List<SolarPanel> getSolarPanels() {
		final List<SolarPanel> list = new ArrayList<SolarPanel>();
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				list.add((SolarPanel) p);
			}
		}
		return list;
	}

	public List<Rack> getRacks() {
		final List<Rack> list = new ArrayList<Rack>();
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				list.add((Rack) p);
			}
		}
		return list;
	}

	/** return the total number of solar panels including those built into racks */
	public int getNumberOfSolarPanels() {
		int count = 0;
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p.getTopContainer() == this) {
				if (p instanceof SolarPanel) {
					count++;
				} else if (p instanceof Rack) {
					count += ((Rack) p).getNumberOfSolarPanels();
				}
			}
		}
		return count;
	}

	public List<Mirror> getMirrors() {
		final List<Mirror> list = new ArrayList<Mirror>();
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Mirror && p.getTopContainer() == this) {
				list.add((Mirror) p);
			}
		}
		return list;
	}

	public void updateHandlesOfAllFoudations() {
		for (final HousePart part : Scene.getInstance().getParts()) {
			if (part instanceof Foundation) {
				((Foundation) part).updateHandles();
			}
		}
	}

	public void updateHandles() {
		if (points.size() < 8) {
			return;
		}
		final Vector3 p0 = getAbsPoint(0);
		final Vector3 u = getAbsPoint(2).subtractLocal(p0).multiplyLocal(0.5);
		final Vector3 v = getAbsPoint(1).subtractLocal(p0).multiplyLocal(0.5);
		updateHandle(points.get(8), u);
		updateHandle(points.get(9), u.negateLocal());
		updateHandle(points.get(10), v);
		updateHandle(points.get(11), v.negateLocal());
		final ReadOnlyColorRGBA c = Scene.getInstance().isGroundImageLightColored() ? ColorRGBA.DARK_GRAY : ColorRGBA.WHITE;
		for (int i = 0; i < 8; i++) {
			getEditPointShape(i).setDefaultColor(c);
		}
		if (pointsRoot.getNumberOfChildren() > 8) {
			for (int i = 8; i < 12; i++) {
				getEditPointShape(i).setDefaultColor(ColorRGBA.ORANGE);
			}
		}
	}

	private void updateHandle(final Vector3 p, final ReadOnlyVector3 dir) {
		final ReadOnlyVector3 step = dir.normalize(null).multiplyLocal(3);
		final ReadOnlyVector3 center = getCenter();
		p.set(center).addLocal(dir).addLocal(step);
		final Point2D p2D = new Point2D.Double();
		for (final HousePart part : Scene.getInstance().getParts()) {
			if (part != this && part instanceof Foundation) {
				final ArrayList<Vector3> points = part.getPoints();
				final Path2D foundationPoly = new Path2D.Double();
				foundationPoly.moveTo(points.get(0).getX(), points.get(0).getY());
				foundationPoly.lineTo(points.get(2).getX(), points.get(2).getY());
				foundationPoly.lineTo(points.get(3).getX(), points.get(3).getY());
				foundationPoly.lineTo(points.get(1).getX(), points.get(1).getY());
				foundationPoly.closePath();
				p2D.setLocation(p.getX(), p.getY());
				if (foundationPoly.contains(p2D)) {
					while (foundationPoly.contains(p2D)) {
						p.addLocal(step);
						p2D.setLocation(p.getX(), p.getY());
					}
					p.addLocal(step);
				}
			}
		}
	}

	@Override
	public void setHeight(final double height) {
		final double delta = height - this.height;
		this.height = height;
		for (final HousePart c : children) {
			final int n = c.points.size();
			for (int i = 0; i < n; i++) {
				final Vector3 v = c.points.get(i);
				v.setZ(v.getZ() + delta);
			}
		}
	}

	@Override
	public Spatial getCollisionSpatial() {
		return root;
	}

	@Override
	public Mesh getRadiationMesh() {
		throw new RuntimeException("Not supported for foundation");
	}

	@Override
	public Spatial getRadiationCollisionSpatial() {
		throw new RuntimeException("Not supported for foundation");
	}

	public Mesh getRadiationMesh(final int index) {
		if (index == 0) {
			return mesh;
		} else {
			return sideMesh[index - 1];
		}
	}

	public Mesh getRadiationCollisionSpatial(final int index) {
		return getRadiationMesh(index);
	}

	@Override
	public void delete() {
		super.delete();
		if (bloomRenderPass != null) {
			if (bloomRenderPass.contains(solarReceiver)) {
				bloomRenderPass.remove(solarReceiver);
			}
		}
	}

	private List<HousePart> removeChildrenOfClass(final Class<?>[] clazz) {
		final List<HousePart> removed = new ArrayList<HousePart>();
		for (final HousePart c : children) {
			for (final Class<?> z : clazz) {
				if (z.isInstance(c)) {
					removed.add(c);
				}
			}
		}
		for (final HousePart x : removed) {
			Scene.getInstance().remove(x, false);
		}
		return removed;
	}

	private static boolean nearAxes(final double x, final double eps) {
		return Math.abs(x) < eps || Math.abs(x - 90) < eps || Math.abs(x - 180) < eps || Math.abs(x - 270) < eps || Math.abs(x - 360) < eps;
	}

	private void addMirror(final Vector3 p, final double w, final double h, final double az) {
		final Mirror m = new Mirror();
		m.setContainer(this);
		Scene.getInstance().add(m, false);
		m.complete();
		m.setRelativeAzimuth(90 - az);
		final Vector3 v = m.toRelative(p);
		m.points.get(0).setX(v.getX());
		m.points.get(0).setY(v.getY());
		m.points.get(0).setZ(height);
		m.setMirrorWidth(w);
		m.setMirrorHeight(h);
		m.draw();
	}

	public int addCircularMirrorArrays(final MirrorCircularFieldLayout layout) {
		EnergyPanel.getInstance().clearRadiationHeatMap();
		final Class<?>[] clazz = new Class[] { Mirror.class };
		final AddArrayCommand command = new AddArrayCommand(removeChildrenOfClass(clazz), this, clazz);
		final double a = 0.5 * Math.min(getAbsPoint(0).distance(getAbsPoint(2)), getAbsPoint(0).distance(getAbsPoint(1)));
		final Vector3 center = getAbsCenter();
		final double w = (layout.getMirrorWidth() + layout.getAzimuthalSpacing()) / Scene.getInstance().getAnnotationScale();
		final double h = (layout.getMirrorHeight() + layout.getRadialSpacing()) / Scene.getInstance().getAnnotationScale();
		final double rows = a / h;
		final int nrows = (int) (rows > 2 ? rows - 2 : rows);
		final double roadHalfWidth = 0.5 * layout.getAxisRoadWidth() / Scene.getInstance().getAnnotationScale();
		switch (layout.getType()) {
		case EQUAL_AZIMUTHAL_SPACING:
			for (int r = nrows - 1; r >= 0; r--) {
				double b = a * (1.0 - r / rows);
				b += b * b * layout.getRadialSpacingIncrement();
				if (b > a) {
					break;
				}
				final double roadAngle = Math.toDegrees(Math.atan(roadHalfWidth / b));
				final int n = (int) (2 * Math.PI * b / w);
				for (int i = 0; i < n; i++) {
					final double theta = i * 2.0 * Math.PI / n;
					final double az = Math.toDegrees(theta);
					if (az >= layout.getStartAngle() && az < layout.getEndAngle()) {
						if (!Util.isZero(roadAngle) && nearAxes(az, roadAngle)) {
							continue;
						}
						final Vector3 p = new Vector3(center.getX() + b * Math.cos(theta), center.getY() + b * Math.sin(theta), 0);
						addMirror(p, layout.getMirrorWidth(), layout.getMirrorHeight(), az);
					}
				}
			}
			break;
		case RADIAL_STAGGER: // http://www.powerfromthesun.net/Book/chapter10/chapter10.html#10.1.3%20%20%20Field%20Layout
			final double rmin = a * (1.0 - (nrows - 5) / rows);
			final int n = (int) (rmin / layout.getMirrorWidth() * Scene.getInstance().getAnnotationScale());
			for (int i = 0; i < n; i++) {
				double theta = i * 2.0 * Math.PI / n;
				double az = Math.toDegrees(theta);
				if (az >= layout.getStartAngle() && az < layout.getEndAngle()) {
					for (int j = 0; j < nrows; j++) {
						final double r = a * (1.0 - j / rows);
						final Vector3 p = new Vector3(center.getX() + r * Math.cos(theta), center.getY() + r * Math.sin(theta), 0);
						addMirror(p, layout.getMirrorWidth(), layout.getMirrorHeight(), az);
					}
				}
				theta = (i + 0.5) * 2.0 * Math.PI / n;
				az = Math.toDegrees(theta);
				if (az >= layout.getStartAngle() && az < layout.getEndAngle()) {
					for (int j = 0; j < nrows; j++) {
						final double r = a * (1.0 - j / rows) - 0.5 * h;
						final Vector3 p = new Vector3(center.getX() + r * Math.cos(theta), center.getY() + r * Math.sin(theta), 0);
						addMirror(p, layout.getMirrorWidth(), layout.getMirrorHeight(), az);
					}
				}
			}
			break;
		}
		SceneManager.getInstance().getUndoManager().addEdit(command);
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				EnergyPanel.getInstance().updateProperties();
			}
		});
		return countParts(Mirror.class);
	}

	public int addSpiralMirrorArrays(final MirrorSpiralFieldLayout layout) {
		EnergyPanel.getInstance().clearRadiationHeatMap();
		final Class<?>[] clazz = new Class[] { Mirror.class };
		final AddArrayCommand command = new AddArrayCommand(removeChildrenOfClass(clazz), this, clazz);
		final double a = 0.5 * Math.min(getAbsPoint(0).distance(getAbsPoint(2)), getAbsPoint(0).distance(getAbsPoint(1)));
		final double b = layout.getScalingFactor() * Math.max(layout.getMirrorWidth(), layout.getMirrorHeight()) / Scene.getInstance().getAnnotationScale();
		final Vector3 center = getAbsCenter();
		final double theta0 = layout.getStartTurn() * 2 * Math.PI;
		final double roadHalfWidth = 0.5 * layout.getAxisRoadWidth() / Scene.getInstance().getAnnotationScale();
		switch (layout.getType()) {
		case FERMAT_SPIRAL:
			for (int i = 1; i < 10000; i++) {
				double r = b * Math.sqrt(i);
				r += r * r * layout.getRadialSpacingIncrement();
				if (r > a) {
					break;
				}
				final double theta = i * GOLDEN_ANGLE;
				if (theta < theta0) {
					continue;
				}
				final double roadAngle = Math.toDegrees(Math.atan(roadHalfWidth / r));
				double az = Math.toDegrees(theta);
				az = az % 360;
				if (az >= layout.getStartAngle() && az < layout.getEndAngle()) {
					if (!Util.isZero(roadAngle) && nearAxes(az, roadAngle)) {
						continue;
					}
					final Vector3 p = new Vector3(center.getX() + r * Math.cos(theta), center.getY() + r * Math.sin(theta), 0);
					addMirror(p, layout.getMirrorWidth(), layout.getMirrorHeight(), az);
				}
			}
			break;
		}
		SceneManager.getInstance().getUndoManager().addEdit(command);
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				EnergyPanel.getInstance().updateProperties();
			}
		});
		return countParts(Mirror.class);
	}

	public int addRectangularMirrorArrays(final MirrorRectangularFieldLayout layout) {
		EnergyPanel.getInstance().clearRadiationHeatMap();
		final Class<?>[] clazz = new Class[] { Mirror.class };
		final AddArrayCommand command = new AddArrayCommand(removeChildrenOfClass(clazz), this, clazz);
		final double az = Math.toRadians(getAzimuth());
		if (!Util.isZero(az)) {
			rotate(az, null);
		}
		final Vector3 p0 = getAbsPoint(0);
		final double a = p0.distance(getAbsPoint(2));
		final double b = p0.distance(getAbsPoint(1));
		final double x0 = Math.min(Math.min(p0.getX(), getAbsPoint(1).getX()), getAbsPoint(2).getX());
		final double y0 = Math.min(Math.min(p0.getY(), getAbsPoint(1).getY()), getAbsPoint(2).getY());
		final double w = (layout.getMirrorWidth() + layout.getColumnSpacing()) / Scene.getInstance().getAnnotationScale();
		final double h = (layout.getMirrorHeight() + layout.getRowSpacing()) / Scene.getInstance().getAnnotationScale();
		switch (layout.getRowAxis()) {
		case 0: // north-south axis
			int rows = (int) Math.floor(b / w);
			int cols = (int) Math.floor(a / h);
			for (int c = 0; c < cols; c++) {
				for (int r = 0; r < rows; r++) {
					final Vector3 p = new Vector3(x0 + h * (c + 0.5), y0 + w * (r + 0.5), 0);
					addMirror(p, layout.getMirrorWidth(), layout.getMirrorHeight(), az);
				}
			}
			break;
		case 1: // east-west axis
			rows = (int) Math.floor(a / w);
			cols = (int) Math.floor(b / h);
			for (int c = 0; c < cols; c++) {
				for (int r = 0; r < rows; r++) {
					final Vector3 p = new Vector3(x0 + w * (r + 0.5), y0 + h * (c + 0.5), 0);
					addMirror(p, layout.getMirrorWidth(), layout.getMirrorHeight(), az);
				}
			}
			break;
		}
		if (!Util.isZero(az)) {
			rotate(-az, null);
		}
		Scene.getInstance().redrawAll();
		SceneManager.getInstance().getUndoManager().addEdit(command);
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				EnergyPanel.getInstance().updateProperties();
			}
		});
		return countParts(Mirror.class);
	}

	public void addSolarPanelArrays(final SolarPanel solarPanel, final double rowSpacing, final double colSpacing, final int rowAxis) {
		EnergyPanel.getInstance().clearRadiationHeatMap();
		final Class<?>[] clazz = new Class[] { Rack.class, SolarPanel.class };
		final AddArrayCommand command = new AddArrayCommand(removeChildrenOfClass(clazz), this, clazz);
		final double az = Math.toRadians(getAzimuth());
		if (!Util.isZero(az)) {
			rotate(az, null);
		}
		final Vector3 p0 = getAbsPoint(0);
		final double a = p0.distance(getAbsPoint(2));
		final double b = p0.distance(getAbsPoint(1));
		final double x0 = Math.min(Math.min(p0.getX(), getAbsPoint(1).getX()), getAbsPoint(2).getX());
		final double y0 = Math.min(Math.min(p0.getY(), getAbsPoint(1).getY()), getAbsPoint(2).getY());
		final double w = (solarPanel.getPanelWidth() + colSpacing) / Scene.getInstance().getAnnotationScale();
		final double h = (solarPanel.getPanelHeight() + rowSpacing) / Scene.getInstance().getAnnotationScale();
		Path2D.Double path = null;
		if (foundationPolygon != null && foundationPolygon.isVisible()) {
			path = new Path2D.Double();
			final int n = foundationPolygon.points.size();
			Vector3 v = foundationPolygon.getAbsPoint(0);
			path.moveTo(v.getX(), v.getY());
			for (int i = 1; i < n / 2; i++) { // use only the first half of the vertices from the polygon
				v = foundationPolygon.getAbsPoint(i);
				path.lineTo(v.getX(), v.getY());
			}
			path.closePath();
		}
		switch (rowAxis) {
		case Trackable.NORTH_SOUTH_AXIS:
			int rows = (int) Math.floor(b / w);
			int cols = (int) Math.floor(a / h);
			for (int c = 0; c < cols; c++) {
				for (int r = 0; r < rows; r++) {
					final double x = x0 + h * (c + 0.5);
					final double y = y0 + w * (r + 0.5);
					if (path != null && !path.contains(x, y)) {
						continue;
					}
					final SolarPanel sp = (SolarPanel) solarPanel.copy(false);
					sp.setContainer(this);
					final Vector3 v = sp.toRelative(new Vector3(x, y, 0));
					sp.points.get(0).setX(v.getX());
					sp.points.get(0).setY(v.getY());
					sp.points.get(0).setZ(height);
					sp.setRotationAxis(rowAxis);
					Scene.getInstance().add(sp, false);
					sp.complete();
					sp.setRelativeAzimuth(90);
					sp.draw();
				}
			}
			break;
		case Trackable.EAST_WEST_AXIS:
			rows = (int) Math.floor(a / w);
			cols = (int) Math.floor(b / h);
			for (int c = 0; c < cols; c++) {
				for (int r = 0; r < rows; r++) {
					final double x = x0 + w * (r + 0.5);
					final double y = y0 + h * (c + 0.5);
					if (path != null && !path.contains(x, y)) {
						continue;
					}
					final SolarPanel sp = (SolarPanel) solarPanel.copy(false);
					sp.setContainer(this);
					final Vector3 v = sp.toRelative(new Vector3(x, y, 0));
					sp.points.get(0).setX(v.getX());
					sp.points.get(0).setY(v.getY());
					sp.points.get(0).setZ(height);
					sp.setRotationAxis(rowAxis);
					Scene.getInstance().add(sp, false);
					sp.complete();
					sp.draw();
				}
			}
			break;
		}
		if (!Util.isZero(az)) {
			rotate(-az, null);
		}
		Scene.getInstance().redrawAll();
		SceneManager.getInstance().getUndoManager().addEdit(command);
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				EnergyPanel.getInstance().updateProperties();
			}
		});
	}

	public void addSolarRackArrays(final SolarPanel panel, double tiltAngle, final int panelRowsPerRack, final double rowSpacing, final int rowAxis, final double poleDistanceX, final double poleDistanceY) {
		EnergyPanel.getInstance().clearRadiationHeatMap();
		final Class<?>[] clazz = new Class[] { Rack.class, SolarPanel.class };
		final AddArrayCommand command = new AddArrayCommand(removeChildrenOfClass(clazz), this, clazz);
		final double az = Math.toRadians(getAzimuth());
		if (!Util.isZero(az)) {
			rotate(az, null);
		}
		if (Util.isZero(tiltAngle - 90)) {
			tiltAngle = 89.999;
		} else if (Util.isZero(tiltAngle + 90)) {
			tiltAngle = -89.999;
		}
		final Vector3 p0 = getAbsPoint(0);
		final double a = p0.distance(getAbsPoint(2));
		final double b = p0.distance(getAbsPoint(1));
		double x0 = Math.min(Math.min(p0.getX(), getAbsPoint(1).getX()), getAbsPoint(2).getX());
		double y0 = Math.min(Math.min(p0.getY(), getAbsPoint(1).getY()), getAbsPoint(2).getY());
		final double x1 = Math.max(Math.max(p0.getX(), getAbsPoint(1).getX()), getAbsPoint(2).getX());
		final double y1 = Math.max(Math.max(p0.getY(), getAbsPoint(1).getY()), getAbsPoint(2).getY());
		final double panelHeight = panel.isRotated() ? panel.getPanelWidth() : panel.getPanelHeight();
		final double rackHeight = panelHeight * panelRowsPerRack;
		final double halfHeight = 0.5 * rackHeight / Scene.getInstance().getAnnotationScale();
		final double h = (rackHeight + rowSpacing) / Scene.getInstance().getAnnotationScale();
		double rackWidth, rows;
		final Vector3 center = new Vector3();
		final Vector3 v1 = new Vector3();
		final Vector3 v2 = new Vector3();
		final List<Point2D.Double> intersections = new ArrayList<Point2D.Double>();
		double[] bounds = null;
		switch (rowAxis) {
		case Trackable.EAST_WEST_AXIS:
			center.setX((x0 + x1) * 0.5);
			rackWidth = a * Scene.getInstance().getAnnotationScale() - panelHeight;
			rows = (int) Math.floor(b / h);
			for (int r = 0; r < rows; r++) {
				if (foundationPolygon != null && foundationPolygon.isVisible()) {
					if (bounds == null) {
						bounds = foundationPolygon.getBounds();
					}
					x0 = Math.max(x0, bounds[0]);
					y0 = Math.max(y0, bounds[2]);
					center.setY(y0 + halfHeight + h * r);
					v1.set(x0, center.getY(), 0);
					v2.set(x1, center.getY(), 0);
					intersections.clear();
					intersections.addAll(foundationPolygon.getIntersectingPoints(v1, v2));
					final int n = intersections.size();
					if (n >= 2) {
						for (int i = 0; i < n; i += 2) {
							final Point2D.Double pd1 = intersections.get(i);
							final Point2D.Double pd2 = intersections.get(i + 1);
							rackWidth = pd2.distance(pd1) * Scene.getInstance().getAnnotationScale();
							final Rack rack = addRack(panel, tiltAngle, rowAxis, poleDistanceX, poleDistanceY, new Vector3(0.5 * (pd1.getX() + pd2.getX()), 0.5 * (pd1.getY() + pd2.getY()), 0), rackWidth, rackHeight, false);
							rack.draw();
						}
					}
				} else {
					center.setY(y0 + h * (r + 0.5));
					addRack(panel, tiltAngle, rowAxis, poleDistanceX, poleDistanceY, center, rackWidth, rackHeight, false).draw();
				}
			}
			break;
		case Trackable.NORTH_SOUTH_AXIS:
			center.setY((y0 + y1) * 0.5);
			rackWidth = b * Scene.getInstance().getAnnotationScale() - panelHeight;
			rows = (int) Math.floor(a / h);
			for (int r = 0; r < rows; r++) {
				if (foundationPolygon != null && foundationPolygon.isVisible()) {
					if (bounds == null) {
						bounds = foundationPolygon.getBounds();
					}
					x0 = Math.max(x0, bounds[0]);
					y0 = Math.max(y0, bounds[2]);
					center.setX(x0 + halfHeight + h * r);
					v1.set(center.getX(), y0, 0);
					v2.set(center.getX(), y1, 0);
					intersections.clear();
					intersections.addAll(foundationPolygon.getIntersectingPoints(v1, v2));
					final int n = intersections.size();
					if (n >= 2) {
						for (int i = 0; i < n; i += 2) {
							final Point2D.Double pd1 = intersections.get(i);
							final Point2D.Double pd2 = intersections.get(i + 1);
							rackWidth = pd2.distance(pd1) * Scene.getInstance().getAnnotationScale();
							final Rack rack = addRack(panel, tiltAngle, rowAxis, poleDistanceX, poleDistanceY, new Vector3(0.5 * (pd1.getX() + pd2.getX()), 0.5 * (pd1.getY() + pd2.getY()), 0), rackWidth, rackHeight, true);
							rack.draw();
						}
					}
				} else {
					center.setX(x0 + h * (r + 0.5));
					addRack(panel, tiltAngle, rowAxis, poleDistanceX, poleDistanceY, center, rackWidth, rackHeight, true).draw();
				}
			}
			break;
		}
		if (!Util.isZero(az)) {
			rotate(-az, null);
		}
		Scene.getInstance().redrawAll();
		SceneManager.getInstance().getUndoManager().addEdit(command);
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				EnergyPanel.getInstance().updateProperties();
			}
		});
	}

	private Rack addRack(final SolarPanel panel, final double tiltAngle, final int rowAxis, final double poleDistanceX, final double poleDistanceY, final Vector3 center, final double rackWidth, final double rackHeight, final boolean rotate90) {
		final Rack rack = new Rack();
		rack.setContainer(this);
		rack.setSolarPanel((SolarPanel) panel.copy(false));
		rack.setMonolithic(true);
		rack.set(center, rackWidth, rackHeight);
		rack.points.get(0).setZ(height);
		rack.roundUpRackWidth();
		// rack.roundUpRackHeight();
		rack.setTiltAngle(tiltAngle);
		rack.setRotationAxis(rowAxis);
		rack.setPoleDistanceX(poleDistanceX);
		rack.setPoleDistanceY(poleDistanceY);
		Scene.getInstance().add(rack, false);
		rack.complete();
		if (rotate90) {
			rack.setRelativeAzimuth(90);
		}
		return rack;
	}

	public int getSupportingType() {
		for (final HousePart p : children) {
			if (p instanceof Wall) {
				return BUILDING;
			}
			if (p instanceof SolarPanel || p instanceof Rack) {
				return PV_STATION;
			}
			if (p instanceof Mirror) {
				return CSP_STATION;
			}
		}
		return -1;
	}

	public void setSolarReceiverEfficiency(final double solarReceiverEfficiency) {
		this.solarReceiverEfficiency = solarReceiverEfficiency;
	}

	public double getSolarReceiverEfficiency() {
		return solarReceiverEfficiency;
	}

	public void setChildGridSize(final double childGridSize) {
		this.childGridSize = childGridSize;
	}

	public double getChildGridSize() {
		return childGridSize;
	}

	public FoundationPolygon getPolygon() {
		return foundationPolygon;
	}

	public boolean containsPoint(final double x, final double y) {
		final Path2D foundationPoly = new Path2D.Double();
		foundationPoly.moveTo(points.get(0).getX(), points.get(0).getY());
		foundationPoly.lineTo(points.get(2).getX(), points.get(2).getY());
		foundationPoly.lineTo(points.get(3).getX(), points.get(3).getY());
		foundationPoly.lineTo(points.get(1).getX(), points.get(1).getY());
		foundationPoly.closePath();
		return foundationPoly.contains(x, y);
	}

	// change properties of all the solar panels on this foundation

	public void setCellNumbersForSolarPanels(final int nx, final int ny) {
		for (final HousePart p : Scene.getInstance().getParts()) { // don't just call children as a solar panel may not be a direct offspring of the foundation (e.g., it can be a child of a roof or wall)
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				final SolarPanel s = (SolarPanel) p;
				s.setNumberOfCellsInX(nx);
				s.setNumberOfCellsInY(ny);
				s.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setAzimuthForSolarPanels(final double angle) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setRelativeAzimuth(angle);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setTiltAngleForSolarPanels(final double angle) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setTiltAngle(angle);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setBaseHeightForSolarPanels(final double baseHeight) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setBaseHeight(baseHeight);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setSolarCellEfficiency(final double eff) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setCellEfficiency(eff);
			}
		}
	}

	public void setTemperatureCoefficientPmax(final double tcPmax) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setTemperatureCoefficientPmax(tcPmax);
			}
		}
	}

	public void setSolarPanelInverterEfficiency(final double eff) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setInverterEfficiency(eff);
			}
		}
	}

	public void setShadeToleranceForSolarPanels(final int cellWiring) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setShadeTolerance(cellWiring);
			}
		}
	}

	public void setTrackerForSolarPanels(final int tracker) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this && !(p.getContainer() instanceof Rack)) { // no tracker for solar panels on racks as they use rack trackers
				((SolarPanel) p).setTracker(tracker);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setColorForSolarPanels(final int colorOption) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof SolarPanel && p.getTopContainer() == this) {
				((SolarPanel) p).setColorOption(colorOption);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	// change properties of all the solar panel racks on this foundation

	public void setTiltAngleForRacks(final double angle) {
		for (final HousePart p : Scene.getInstance().getParts()) { // don't just call children as a solar panel may not be a direct offspring of the foundation (e.g., it can be a child of a roof)
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).setTiltAngle(angle);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setAzimuthForRacks(final double angle) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).setRelativeAzimuth(angle);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setBaseHeightForRacks(final double baseHeight) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).setBaseHeight(baseHeight);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setSizeForRacks(final double width, final double height) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				final Rack r = (Rack) p;
				r.setRackWidth(width);
				r.setRackHeight(height);
				r.ensureFullSolarPanels(false);
				r.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setSolarPanelSizeForRacks(final double width, final double height) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				final Rack r = (Rack) p;
				r.getSolarPanel().setPanelWidth(width);
				r.getSolarPanel().setPanelHeight(height);
				r.ensureFullSolarPanels(false);
				r.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setSolarPanelColorForRacks(final int colorOption) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				final Rack r = (Rack) p;
				r.getSolarPanel().setColorOption(colorOption);
				r.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setSolarPanelShadeToleranceForRacks(final int tolerance) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).getSolarPanel().setShadeTolerance(tolerance);
			}
		}
	}

	public void setSolarCellEfficiencyForRacks(final double eff) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).getSolarPanel().setCellEfficiency(eff);
			}
		}
	}

	public void setInverterEfficiencyForRacks(final double eff) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).getSolarPanel().setInverterEfficiency(eff);
			}
		}
	}

	public void setTemperatureCoefficientPmaxForRacks(final double pmax) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).getSolarPanel().setTemperatureCoefficientPmax(pmax);
			}
		}
	}

	public void rotateSolarPanelsOnRacks(final boolean rotated) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				final Rack r = (Rack) p;
				r.getSolarPanel().setRotated(rotated);
				r.ensureFullSolarPanels(false);
				r.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setPoleSpacingForRacks(final double dx, final double dy) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				final Rack r = (Rack) p;
				r.setPoleDistanceX(dx);
				r.setPoleDistanceY(dy);
				r.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setTrackerForRacks(final int tracker) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Rack && p.getTopContainer() == this) {
				((Rack) p).setTracker(tracker);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	// change properties of all the mirrors on this foundation

	public void setZenithAngleForMirrors(final double angle) {
		for (final HousePart p : children) {
			if (p instanceof Mirror) {
				((Mirror) p).setTiltAngle(angle);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setAzimuthForMirrors(final double angle) {
		for (final HousePart p : children) {
			if (p instanceof Mirror) {
				((Mirror) p).setRelativeAzimuth(angle);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setReflectivityForMirrors(final double reflectivity) {
		for (final HousePart p : children) {
			if (p instanceof Mirror) {
				((Mirror) p).setReflectivity(reflectivity);
			}
		}
	}

	public void setBaseHeightForMirrors(final double baseHeight) {
		for (final HousePart p : children) {
			if (p instanceof Mirror) {
				((Mirror) p).setBaseHeight(baseHeight);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setTargetForMirrors(final Foundation target) {
		final List<Foundation> oldTargets = new ArrayList<Foundation>();
		for (final HousePart p : children) {
			if (p instanceof Mirror) {
				final Mirror m = (Mirror) p;
				final Foundation t = m.getHeliostatTarget();
				if (t != null && !oldTargets.contains(t)) {
					oldTargets.add(t);
				}
				m.setHeliostatTarget(target);
				m.draw();
			}
		}
		if (target != null) {
			target.drawSolarReceiver();
		}
		if (!oldTargets.isEmpty()) {
			for (final Foundation t : oldTargets) {
				t.drawSolarReceiver();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setSizeForMirrors(final double width, final double height) {
		for (final HousePart p : children) {
			if (p instanceof Mirror) {
				final Mirror m = (Mirror) p;
				m.setMirrorWidth(width);
				m.setMirrorHeight(height);
				m.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	// change properties of all walls on this foundation

	public void setThicknessOfWalls(final double thickness) {
		for (final HousePart p : children) {
			if (p instanceof Wall) {
				((Wall) p).setThickness(thickness);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setHeightOfWalls(final double height) {
		for (final HousePart p : children) {
			if (p instanceof Wall) {
				((Wall) p).setHeight(height, true);
				p.draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	// change properties of all windows on this foundation

	public void setSizeForWindows(final double width, final double height) {
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Window && p.getTopContainer() == this) {
				final Window w = (Window) p;
				w.setWindowWidth(width);
				w.setWindowHeight(height);
				w.draw();
				w.getContainer().draw();
			}
		}
		SceneManager.getInstance().refresh();
	}

	public void setGroupMaster(final boolean groupMaster) {
		this.groupMaster = groupMaster;
	}

	public boolean isGroupMaster() {
		return groupMaster;
	}

	public boolean overlap(final Foundation another) {
		final Area a = getPathArea();
		a.intersect(another.getPathArea());
		return !a.isEmpty();
	}

	private Area getPathArea() {
		final Path2D.Double path = new Path2D.Double();
		Vector3 p = points.get(0);
		path.moveTo(p.getX(), p.getY());
		p = points.get(1);
		path.lineTo(p.getX(), p.getY());
		p = points.get(3);
		path.lineTo(p.getX(), p.getY());
		p = points.get(2);
		path.lineTo(p.getX(), p.getY());
		path.closePath();
		return new Area(path);
	}

	public List<Node> getImportedNodes() {
		return importedNodes;
	}

	public void deleteNode(final Node n) {
		n.getParent().detachChild(n);
		final int i = importedNodes.indexOf(n);
		importedNodes.remove(n);
		importedNodeStates.remove(i);
		clearSelectedMesh();
		draw();
	}

	public void addNode(final Node n, final NodeState ns) {
		root.attachChild(n);
		importedNodes.add(n);
		importedNodeStates.add(ns);
		draw();
	}

	public NodeState getNodeState(final Node n) {
		return importedNodeStates.get(importedNodes.indexOf(n));
	}

	public void translateImportedNode(final Node n, final double x, final double y, final double z) {
		final Vector3 v = new Vector3(x, y, z);
		getNodeState(n).getPosition().addLocal(v);
		final Vector3 d = n.getRotation().applyPost(v, null);
		for (final HousePart p : children) {
			if (p instanceof SolarPanel) {
				final SolarPanel s = (SolarPanel) p;
				final MeshLocator l = s.getMeshLocator();
				if (l != null && l.getFoundation() == this && l.getNodeIndex() == importedNodes.indexOf(n)) {
					s.points.get(0).addLocal(s.toRelativeVector(d));
					s.draw();
				}
			} else if (p instanceof Rack) {
				final Rack r = (Rack) p;
				final MeshLocator l = r.getMeshLocator();
				if (l != null && l.getFoundation() == this && l.getNodeIndex() == importedNodes.indexOf(n)) {
					r.points.get(0).addLocal(r.toRelativeVector(d));
					r.draw();
				}
			}
		}
	}

	public Node importCollada(final URL file, final Vector3 position) throws Exception {
		if (importedNodes == null) {
			importedNodes = new ArrayList<Node>();
		}
		if (importedNodeStates == null) {
			importedNodeStates = new ArrayList<NodeState>();
		}
		if (position != null) { // when position is null, the cursor is already set to be wait by the loading method
			SceneManager.getInstance().cursorWait(true);
		}
		File sourceFile = new File(file.toURI());
		if (!sourceFile.exists() && Scene.getURL() != null) {
			sourceFile = new File(new File(Scene.getURL().toURI()).getParentFile(), Util.getFileName(file.getPath()));
		}
		if (sourceFile.exists()) {
			final double scale = Scene.getInstance().getAnnotationScale() * 0.633; // 0.633 is determined by fitting the length in Energy3D to the length in SketchUp
			final ColladaStorage storage = new ColladaImporter().load(new URLResourceSource(sourceFile.toURI().toURL()));
			final Node originalNode = storage.getScene();
			originalNode.setScale(scale);
			if (position != null) { // when position is null, the node uses the position saved in the associated NodeState object
				final NodeState ns = new NodeState();
				importedNodeStates.add(ns);
				originalNode.setTranslation(position);
				final Vector3 relativePosition = position.subtract(getAbsCenter().multiplyLocal(1, 1, 0), null).addLocal(0, 0, height);
				final double az = Math.toRadians(getAzimuth());
				ns.setPosition(Util.isZero(az) ? relativePosition : new Matrix3().fromAngles(0, 0, az).applyPost(relativePosition, null)); // why not -getAzimuth()?
				ns.setSourceURL(file);
			}
			// now construct a new node that is a parent of all planar meshes
			final Node newNode = new Node(originalNode.getName());
			final String nodeString = "Node #" + importedNodes.size() + ", Foundation #" + id;
			final List<Mesh> meshes = new ArrayList<Mesh>();
			Util.getMeshes(originalNode, meshes);
			String warnInfo = null;
			final int nodeIndex = importedNodes.size();
			int meshIndex = 0;
			for (final Mesh m : meshes) {
				final ReadOnlyTransform t = m.getWorldTransform();
				final MeshData md = m.getMeshData();
				switch (md.getIndexMode(0)) {
				case Triangles:
					final List<Mesh> children = TriangleMeshLib.getPlanarMeshes(m);
					if (!children.isEmpty()) {
						for (final Mesh s : children) {
							s.setTransform(t);
							final UserData ud = new UserData(this, nodeIndex, meshIndex);
							ud.setNormal((Vector3) s.getUserData());
							ud.setRenderState(s.getLocalRenderState(StateType.Texture));
							ud.setTextureBuffer(s.getMeshData().getTextureBuffer(0));
							s.setUserData(ud);
							s.setName("Mesh #" + meshIndex + ", " + nodeString);
							newNode.attachChild(s);
							meshIndex++;
						}
					}
					break;
				case Lines:
					break;
				default:
					warnInfo = md.getIndexMode(0).name();
					break;
				}
			}
			if (warnInfo != null) {
				JOptionPane.showMessageDialog(MainFrame.getInstance(), "Non-triangular mesh " + warnInfo + " is found.", "Warning", JOptionPane.WARNING_MESSAGE);
			}
			if (newNode.getNumberOfChildren() > 0) {
				importedNodes.add(newNode);
				newNode.setScale(scale);
				newNode.updateWorldTransform(true);
				root.attachChild(newNode);
				new NodeWorker(newNode).findTwinMeshes();
				return newNode;
			}
			if (position != null) {
				SceneManager.getInstance().cursorWait(false);
			}
		} else {
			if (position != null) {
				// get rid of the dead nodes no longer linked to files
				for (final Iterator<NodeState> it = importedNodeStates.iterator(); it.hasNext();) {
					if (file.equals(it.next().getSourceURL())) {
						it.remove();
					}
				}
			}
		}
		return null;
	}

	// imported nodes often have a twin of meshes with identical vertex coordinates but opposite normal vectors, find these and offset them
	public void processImportedMeshes() {
		SceneManager.getInstance().cursorWait(true); // this could be a very compute-intensive task
		for (final Node node : importedNodes) {
			new NodeWorker(node).work(false);
		}
		clearSelectedMesh();
		SceneManager.getInstance().cursorWait(false);
	}

	public void removeAllImports() {
		if (importedNodes == null || importedNodes.isEmpty()) {
			JOptionPane.showMessageDialog(MainFrame.getInstance(), "There is no imported structure to remove.", "No Imported Structure", JOptionPane.INFORMATION_MESSAGE);
			return;
		}
		final int count = importedNodes.size();
		if (JOptionPane.showConfirmDialog(MainFrame.getInstance(), "Do you really want to remove all " + count + " imported structures?", "Confirm", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
			return;
		}
		if (importedNodes != null) {
			for (final Node n : importedNodes) {
				root.detachChild(n);
			}
			importedNodes.clear();
		}
		if (importedNodeStates != null) {
			importedNodeStates.clear();
		}
	}

	public void pickMesh(final int x, final int y) {
		selectedMesh = null;
		if (importedNodes != null) {
			final PickResults pickResults = new PrimitivePickResults();
			pickResults.setCheckDistance(true);
			final Ray3 pickRay = SceneManager.getInstance().getCamera().getPickRay(new Vector2(x, y), false, null);
			for (final Node node : importedNodes) {
				for (final Spatial s : node.getChildren()) {
					if (s instanceof Mesh) {
						PickingUtil.findPick(s, pickRay, pickResults, false);
					}
				}
			}
			if (pickResults.getNumber() > 0) {
				final Pickable pickable = pickResults.getPickData(0).getTarget();
				if (pickable instanceof Mesh) {
					selectedMesh = (Mesh) pickable;
					drawMeshSelection(selectedMesh);
				}
			} else {
				setMeshSelectionVisible(false);
			}
		}
	}

	public Mesh getSelectedMesh() {
		return selectedMesh;
	}

	public void removeEmptyNodes() {
		if (importedNodes != null) {
			for (final Iterator<Node> it = importedNodes.iterator(); it.hasNext();) {
				final Node n = it.next();
				if (n.getNumberOfChildren() == 0) {
					importedNodeStates.remove(importedNodes.indexOf(n));
					root.detachChild(n);
					it.remove();
				}
			}
		}
	}

	public void clearSelectedMesh() {
		selectedMesh = null;
		setMeshSelectionVisible(false);
	}

	public void setMeshSelectionVisible(final boolean b) {
		selectedMeshOutline.setVisible(b);
		selectedNodeBoundingBox.setVisible(b);
	}

	public void deleteMesh(final Mesh m) {
		final DeleteMeshCommand c = new DeleteMeshCommand(m, this);
		final Node n = m.getParent();
		n.detachChild(m);
		clearSelectedMesh();
		removeEmptyNodes();
		getNodeState(n).deleteMesh(((UserData) m.getUserData()).getMeshIndex());
		draw();
		SceneManager.getInstance().getUndoManager().addEdit(c);
	}

	public void restoreDeletedMeshes(final Node n) {
		final NodeState ns = getNodeState(n);
		if (ns.getDeletedMeshes() != null) {
			ns.getDeletedMeshes().clear();
		}
	}

	private void drawMeshSelection(final Mesh m) {
		final List<ReadOnlyVector3> outlinePoints = MeshLib.computeOutline(m.getMeshData().getVertexBuffer());
		if (outlinePoints == null || outlinePoints.isEmpty()) {
			return;
		}
		final int n = outlinePoints.size();
		FloatBuffer outlineVertexBuffer = selectedMeshOutline.getMeshData().getVertexBuffer();
		if (outlineVertexBuffer.capacity() != n * 6) {
			outlineVertexBuffer = BufferUtils.createFloatBuffer(n * 6);
			selectedMeshOutline.getMeshData().setVertexBuffer(outlineVertexBuffer);
		} else {
			outlineVertexBuffer.rewind();
			outlineVertexBuffer.limit(outlineVertexBuffer.capacity());
		}
		ReadOnlyVector3 p;
		for (int i = 0; i < n; i++) {
			p = outlinePoints.get(i);
			outlineVertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
			p = outlinePoints.get((i + 1) % n);
			outlineVertexBuffer.put(p.getXf()).put(p.getYf()).put(p.getZf());
		}
		selectedMeshOutline.setTransform(m.getWorldTransform());
		selectedMeshOutline.updateModelBound();
		selectedMeshOutline.setVisible(true);
		// Util.drawBoundingBox(m.getParent(), selectedNodeBoundingBox);
	}

	public void drawImports() {
		if (importedNodes != null) {
			final int n = importedNodes.size();
			if (n > 0) {
				Node ni;
				final Vector3 c = getAbsCenter();
				c.setZ(height); // the absolute center is lifted to the center of the bounding box that includes walls when there are
				final Matrix3 matrix = new Matrix3().fromAngles(0, 0, -Math.toRadians(getAzimuth())); // FIXME: Why negate?
				for (int i = 0; i < n; i++) {
					ni = importedNodes.get(i);
					if (root.getChildren().contains(ni)) {
						final Vector3 vi = matrix.applyPost(importedNodeStates.get(i).getPosition(), null);
						ni.setTranslation(c.add(vi, null));
						ni.setRotation(matrix);
						for (final Spatial s : ni.getChildren()) {
							if (s instanceof Mesh) {
								final Mesh m = (Mesh) s;
								m.updateModelBound();
							}
						}
					}
				}
			}
		}
	}

	private void setRotatedNormalsForImportedMeshes() {
		if (importedNodes != null) {
			drawImports();
			final boolean nonZeroAz = !Util.isZero(getAzimuth());
			if (nonZeroAz) { // if the foundation is rotated, rotate the imported meshes, too, but this doesn't alter their original normals
				for (final Node node : importedNodes) {
					for (final Spatial s : node.getChildren()) {
						final Mesh m = (Mesh) s;
						final UserData ud = (UserData) m.getUserData();
						ud.setRotatedNormal(node.getRotation().applyPost(ud.getNormal(), null));
					}
				}
			}
		}
	}

	public void showFloatingLabel(final boolean b) {
		floatingLabel.setVisible(b && SceneManager.getInstance().getSolarHeatMap());
	}

	public void setFloatingLabelText(final String text) {
		if (text == null) {
			floatingLabel.setVisible(false);
		} else {
			floatingLabel.setVisible(Scene.getInstance().areFloatingLabelsVisible());
			floatingLabel.setText(text);
		}
	}

	private void updateFloatingLabelPosition() {
		final ReadOnlyVector3 center = getCenter();
		floatingLabel.setTranslation(center.getX(), center.getY(), boundingHeight + height + 6);
	}

}