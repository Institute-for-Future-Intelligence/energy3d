package org.concord.energy3d.model;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import javax.swing.JOptionPane;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.shapes.AngleAnnotation;
import org.concord.energy3d.shapes.Heliodon;
import org.concord.energy3d.simulation.Atmosphere;
import org.concord.energy3d.simulation.PvModuleSpecs;
import org.concord.energy3d.util.FontManager;
import org.concord.energy3d.util.Util;

import com.ardor3d.bounding.BoundingBox;
import com.ardor3d.bounding.OrientedBoundingBox;
import com.ardor3d.extension.effect.bloom.BloomRenderPass;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Transform;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyTransform;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.renderer.Camera;
import com.ardor3d.renderer.Camera.ProjectionMode;
import com.ardor3d.renderer.IndexMode;
import com.ardor3d.renderer.state.MaterialState;
import com.ardor3d.renderer.state.OffsetState;
import com.ardor3d.scenegraph.Line;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.hint.CullHint;
import com.ardor3d.scenegraph.shape.Box;
import com.ardor3d.ui.text.BMText;
import com.ardor3d.ui.text.BMText.Align;
import com.ardor3d.ui.text.BMText.Justify;
import com.ardor3d.util.geom.BufferUtils;

public class SolarPanel extends HousePart implements Trackable, Meshable, Labelable {

    private static final long serialVersionUID = 1L;

    public static final int PARTIAL_SHADE_TOLERANCE = 0;
    public static final int HIGH_SHADE_TOLERANCE = 1;
    public static final int NO_SHADE_TOLERANCE = 2;
    public static final int MIN_SOLAR_CELL_EFFICIENCY_PERCENTAGE = 10;
    public static final int MAX_SOLAR_CELL_EFFICIENCY_PERCENTAGE = 30;
    public static final int MIN_INVERTER_EFFICIENCY_PERCENTAGE = 80;
    public static final int MAX_INVERTER_EFFICIENCY_PERCENTAGE = 100;
    public static final int COLOR_OPTION_BLUE = 0;
    public static final int COLOR_OPTION_BLACK = 1;
    public static final int COLOR_OPTION_GRAY = 2;
    public static final int POLYCRYSTALLINE = 0;
    public static final int MONOCRYSTALLINE = 1;
    public static final int THIN_FILM = 2;

    private transient ReadOnlyVector3 normal;
    private transient Mesh outlineMesh;
    private transient Box surround;
    private transient Mesh supportFrame;
    private transient Line sunBeam;
    private transient Line normalVector;
    private transient Node angles;
    private transient AngleAnnotation sunAngle;
    private transient BMText label;
    private transient Line solarCellOutlines;
    private static double normalVectorLength = 5;
    private transient double yieldNow; // solar output at current hour
    private transient double yieldToday;
    private double efficiency = 0.15; // a number in (0, 1) (backward compatibility, should use pvModuleSpecs.getCellEfficiency)
    private double temperatureCoefficientPmax = -0.005; // backward compatibility, should use pvModuleSpecs.getPmaxTc
    private double nominalOperatingCellTemperature = 48; // backward compatibility, should use pvModuleSpecs.getNoct
    private double panelWidth = 0.99; // 39" (backward compatibility, should use pvModuleSpecs.getWidth)
    private double panelHeight = 1.65; // 65" (backward compatibility, should use pvModuleSpecs.getLength)
    private int cellType = POLYCRYSTALLINE; // backward compatibility, should use pvModuleSpecs.getCellType
    private int numberOfCellsInX = 6; // backward compatibility, should use pvModuleSpecs.getLayout
    private int numberOfCellsInY = 10; // backward compatibility, should use pvModuleSpecs.getLayout
    private int colorOption = COLOR_OPTION_BLUE;
    private double inverterEfficiency = 0.95;
    private boolean rotated = false; // rotation around the normal usually takes only two angles: 0 or 90, so we use a boolean here
    private double relativeAzimuth;
    private double tiltAngle;
    private int trackerType = NO_TRACKER;
    private double baseHeight = 5;
    private boolean drawSunBeam;
    private int shadeTolerance = PARTIAL_SHADE_TOLERANCE;
    private boolean labelModelName;
    private boolean labelCellEfficiency;
    private boolean labelTiltAngle;
    private boolean labelTracker;
    private boolean labelEnergyOutput;
    private transient double layoutGap = 0.01;
    private static transient BloomRenderPass bloomRenderPass;
    private MeshLocator meshLocator; // if the mesh that this solar panel rests on is a vertical surface of unknown type (e.g., an imported mesh), store its info for finding it later
    private PvModuleSpecs pvModuleSpecs;

    public SolarPanel() {
        super(1, 1, 0);
    }

    @Override
    protected void init() {
        super.init();

        if (Util.isZero(panelWidth)) {
            panelWidth = 0.99;
        }
        if (Util.isZero(panelHeight)) {
            panelHeight = 1.65;
        }
        if (Util.isZero(efficiency)) {
            efficiency = 0.1833; // make it the same as the default one in PvModuleSpecs
        }
        if (Util.isZero(temperatureCoefficientPmax)) {
            temperatureCoefficientPmax = -0.005;
        }
        if (Util.isZero(nominalOperatingCellTemperature)) {
            nominalOperatingCellTemperature = 48;
        }
        if (Util.isZero(inverterEfficiency)) {
            inverterEfficiency = 0.95;
        }
        if (Util.isZero(baseHeight)) {
            baseHeight = 5;
        }
        if (Util.isZero(numberOfCellsInX)) {
            numberOfCellsInX = 6;
        }
        if (Util.isZero(numberOfCellsInY)) {
            numberOfCellsInY = 10;
        }

        if (pvModuleSpecs == null) { // backward compatibility
            pvModuleSpecs = new PvModuleSpecs("Custom");
            pvModuleSpecs.setCellEfficiency(efficiency);
            pvModuleSpecs.setWidth(panelWidth);
            pvModuleSpecs.setLength(panelHeight);
            pvModuleSpecs.setNoct(nominalOperatingCellTemperature);
            pvModuleSpecs.setPmaxTc(temperatureCoefficientPmax);
            pvModuleSpecs.setLayout(numberOfCellsInX, numberOfCellsInY);
            switch (cellType) {
                case POLYCRYSTALLINE:
                    pvModuleSpecs.setCellType("Polycrystalline");
                    colorOption = COLOR_OPTION_BLUE;
                    break;
                case MONOCRYSTALLINE:
                    pvModuleSpecs.setCellType("Monocrystalline");
                    colorOption = COLOR_OPTION_BLACK;
                    break;
                case THIN_FILM:
                    pvModuleSpecs.setCellType("Thin Film");
                    colorOption = COLOR_OPTION_BLACK;
                    break;
            }
        } else {
            convertStringPropertiesToIntegerProperties();
        }

        mesh = new Mesh("SolarPanel");
        mesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
        mesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(6), 0);
        mesh.setModelBound(new OrientedBoundingBox());
        mesh.setUserData(new UserData(this));
        root.attachChild(mesh);

        surround = new Box("SolarPanel (Surround)");
        surround.setModelBound(new OrientedBoundingBox());

        final OffsetState offset = new OffsetState();
        offset.setFactor(1);
        offset.setUnits(1);
        surround.setRenderState(offset);
        root.attachChild(surround);

        outlineMesh = new Line("SolarPanel (Outline)");
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

        sunBeam = new Line("Sun Beam");
        sunBeam.setLineWidth(1f);
        sunBeam.setStipplePattern((short) 0xffff);
        sunBeam.setModelBound(null);
        Util.disablePickShadowLight(sunBeam);
        sunBeam.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(4));
        sunBeam.setDefaultColor(new ColorRGBA(1f, 1f, 1f, 1f));
        root.attachChild(sunBeam);

        normalVector = new Line("Normal Vector");
        normalVector.setLineWidth(1f);
        normalVector.setStipplePattern((short) 0xffff);
        normalVector.setModelBound(null);
        Util.disablePickShadowLight(normalVector);
        normalVector.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
        normalVector.setDefaultColor(new ColorRGBA(1f, 1f, 0f, 1f));
        root.attachChild(normalVector);

        angles = new Node("Angles");
        angles.getSceneHints().setAllPickingHints(false);
        Util.disablePickShadowLight(angles);
        root.attachChild(angles);

        sunAngle = new AngleAnnotation(); // the angle between the sun beam and the normal vector
        sunAngle.setColor(ColorRGBA.WHITE);
        sunAngle.setLineWidth(1);
        sunAngle.setFontSize(1);
        sunAngle.setCustomRadius(normalVectorLength * 0.8);
        angles.attachChild(sunAngle);

        label = new BMText("Label", "# " + id, FontManager.getInstance().getPartNumberFont(), Align.Center, Justify.Center);
        Util.initHousePartLabel(label);
        label.setFontScale(0.5);
        label.setVisible(false);
        root.attachChild(label);

        solarCellOutlines = new Line("Solar Cell Outlines");
        solarCellOutlines.setLineWidth(1f);
        solarCellOutlines.setStipplePattern((short) 0xffff);
        solarCellOutlines.setModelBound(null);
        Util.disablePickShadowLight(solarCellOutlines);
        solarCellOutlines.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(1));
        solarCellOutlines.setDefaultColor(new ColorRGBA(0f, 0f, 0f, 1f));
        root.attachChild(solarCellOutlines);

        updateTextureAndColor();

    }

    @Override
    public void setPreviewPoint(final int x, final int y) {
        if (lockEdit) {
            return;
        }
        final PickedHousePart picked = pickContainer(x, y, new Class<?>[]{Roof.class, Wall.class, Foundation.class, Rack.class});
        if (picked != null && picked.getUserData() != null) { // when the user data is null, it picks the land
            final Vector3 p = picked.getPoint().clone();
            final UserData ud = picked.getUserData();
            pickedNormal = ud.getRotatedNormal() == null ? ud.getNormal() : ud.getRotatedNormal();
            if (ud.getHousePart() instanceof Foundation && ud.isImported() && ud.getNodeIndex() >= 0 && ud.getMeshIndex() >= 0) {
                // if this solar panel rests on an imported mesh, store its info and don't snap to grid as imported meshes do not sit on grid
                meshLocator = new MeshLocator((Foundation) ud.getHousePart(), ud.getNodeIndex(), ud.getMeshIndex());
            } else {
                snapToGrid(p, getAbsPoint(0), getGridSize(), container instanceof Wall);
                meshLocator = null;
            }
            points.get(0).set(toRelative(p));
        } else {
            pickedNormal = null;
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
                if (onFlatSurface()) {
                    p.setZ(p.getZ() + baseHeight);
                }
                getEditPointShape(i).setTranslation(p);
            }
        } finally {
            Vector3.releaseTempInstance(p);
        }
        /* remove remaining edit shapes */
        for (int i = points.size(); i < pointsRoot.getNumberOfChildren(); i++) {
            pointsRoot.detachChildAt(points.size());
        }
    }

    public boolean checkContainerIntersection() {
        final double z0 = container.getAbsCenter().getZ() + container.height + surround.getZExtent() * 2;
        final FloatBuffer buf = mesh.getMeshData().getVertexBuffer();
        final ReadOnlyTransform trans = mesh.getWorldTransform();
        final Vector3 v1 = new Vector3();
        final Vector3 v2 = new Vector3();
        BufferUtils.populateFromBuffer(v1, buf, 0);
        BufferUtils.populateFromBuffer(v2, buf, 1);
        final Vector3 p1 = trans.applyForward(v1).add(trans.applyForward(v2), null).multiplyLocal(0.5);
        if (p1.getZ() < z0) {
            return true;
        }
        BufferUtils.populateFromBuffer(v1, buf, 1);
        BufferUtils.populateFromBuffer(v2, buf, 2);
        final Vector3 p2 = trans.applyForward(v1).add(trans.applyForward(v2), null).multiplyLocal(0.5);
        if (p2.getZ() < z0) {
            return true;
        }
        BufferUtils.populateFromBuffer(v1, buf, 2);
        BufferUtils.populateFromBuffer(v2, buf, 4);
        final Vector3 p3 = trans.applyForward(v1).add(trans.applyForward(v2), null).multiplyLocal(0.5);
        if (p3.getZ() < z0) {
            return true;
        }
        BufferUtils.populateFromBuffer(v1, buf, 4);
        BufferUtils.populateFromBuffer(v2, buf, 0);
        final Vector3 p4 = trans.applyForward(v1).add(trans.applyForward(v2), null).multiplyLocal(0.5);
        return p4.getZ() < z0;
    }

    private Vector3 getVertex(final int i) {
        final Vector3 v = new Vector3();
        BufferUtils.populateFromBuffer(v, mesh.getMeshData().getVertexBuffer(), i);
        return mesh.getWorldTransform().applyForward(v);
    }

    public boolean outOfBound() {
        drawMesh();
        if (container instanceof Foundation) {
            final Foundation foundation = (Foundation) container;
            final int n = Math.round(mesh.getMeshData().getVertexBuffer().limit() / 3f);
            for (int i = 0; i < n; i++) {
                final Vector3 a = getVertex(i);
                if (a.getZ() < foundation.getHeight() * 1.1) { // left a 10% margin above the foundation
                    return true;
                }
                if (!foundation.containsPoint(a.getX(), a.getY())) {
                    return true;
                }
            }
        } else if (container instanceof Roof) {
            final Roof roof = (Roof) container;
            final int n = Math.round(mesh.getMeshData().getVertexBuffer().limit() / 3f);
            boolean init = true;
            for (int i = 0; i < n; i++) {
                final Vector3 a = getVertex(i);
                if (!roof.insideWalls(a.getX(), a.getY(), init)) {
                    return true;
                }
                if (init) {
                    init = false;
                }
            }
        }
        return false;
    }

    private boolean onFlatSurface() {
        if (meshLocator != null) { // if this solar panel rests on an imported mesh, treat it differently
            return false;
        }
        if (container instanceof Roof) {
            return Util.isZero(container.getHeight());
        } else if (container instanceof Foundation) {
            if (pickedNormal != null) {
                return Util.isEqualFaster(pickedNormal, Vector3.UNIT_Z);
            }
            return true;
        }
        return false;
    }

    @Override
    protected void drawMesh() {

        if (container == null) {
            return;
        }

        final double az = Math.toRadians(relativeAzimuth);
        final boolean heatMap = SceneManager.getInstance().getSolarHeatMap();
        boolean onFlatSurface = onFlatSurface();
        final Mesh host = meshLocator == null ? null : meshLocator.find(); // if this solar panel rests on an imported mesh or not?
        if (host == null) {
            normal = pickedNormal != null ? pickedNormal : computeNormalAndKeepOnSurface();
        } else {
            final UserData ud = (UserData) host.getUserData();
            normal = ud.getRotatedNormal() == null ? ud.getNormal() : ud.getRotatedNormal();
            onFlatSurface = Util.isEqual(normal, Vector3.UNIT_Z);
        }
        updateEditShapes();

        final double sceneScaleFactor = 0.5 / Scene.getInstance().getScale();
        if (rotated) {
            surround.setData(new Vector3(), panelHeight * sceneScaleFactor, panelWidth * sceneScaleFactor, 0.15);
        } else {
            surround.setData(new Vector3(), panelWidth * sceneScaleFactor, panelHeight * sceneScaleFactor, 0.15);
        }
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

        switch (trackerType) {
            case ALTAZIMUTH_DUAL_AXIS_TRACKER:
                normal = Heliodon.getInstance().computeSunLocation(Heliodon.getInstance().getCalendar()).normalizeLocal();
                break;
            case HORIZONTAL_SINGLE_AXIS_TRACKER:
                final Vector3 sunDirection = Heliodon.getInstance().computeSunLocation(Heliodon.getInstance().getCalendar()).normalizeLocal();
                final Vector3 rotationAxis = new Vector3(Math.sin(az), Math.cos(az), 0);
                final double axisSunDot = sunDirection.dot(rotationAxis);
                rotationAxis.multiplyLocal(Util.isZero(axisSunDot) ? 0.001 : axisSunDot); // avoid singularity when the direction of the sun is perpendicular to the rotation axis
                normal = sunDirection.subtractLocal(rotationAxis).normalizeLocal();
                break;
            case VERTICAL_SINGLE_AXIS_TRACKER:
                final Vector3 a = Heliodon.getInstance().computeSunLocation(Heliodon.getInstance().getCalendar()).multiplyLocal(1, 1, 0).normalizeLocal();
                final Vector3 b = Vector3.UNIT_Z.cross(a, null);
                final Matrix3 m = new Matrix3().applyRotation(Math.toRadians(90 - tiltAngle), b.getX(), b.getY(), b.getZ());
                normal = m.applyPost(a, null);
                if (normal.getZ() < 0) {
                    normal = normal.negate(null);
                }
                break;
            default:
                if (onFlatSurface) {
                    setNormal(Util.isZero(tiltAngle) ? Math.PI / 2 * 0.9999 : Math.toRadians(90 - tiltAngle), Math.toRadians(relativeAzimuth)); // exactly 90 degrees will cause the solar panel to disappear
                }
        }
        if (Util.isEqual(normal, Vector3.UNIT_Z)) {
            normal = new Vector3(-0.001, 0, 1).normalizeLocal();
        }
        mesh.setRotation(new Matrix3().lookAt(normal, normal.getX() > 0 ? Vector3.UNIT_Z : Vector3.NEG_UNIT_Z));
        mesh.setTranslation(onFlatSurface ? getAbsPoint(0).addLocal(0, 0, baseHeight) : getAbsPoint(0));

        surround.setTranslation(mesh.getTranslation());
        surround.setRotation(mesh.getRotation());

        outlineMesh.setTranslation(mesh.getTranslation());
        outlineMesh.setRotation(mesh.getRotation());

        if (onFlatSurface) {
            supportFrame.getSceneHints().setCullHint(CullHint.Inherit);
            drawSupporFrame();
        } else {
            supportFrame.getSceneHints().setCullHint(CullHint.Always);
        }

        if (drawSunBeam) {
            drawSunBeam();
        }

        if (heatMap) {
            drawSolarCellOutlines();
        } else {
            solarCellOutlines.setVisible(false);
        }

        drawFloatingLabel(onFlatSurface);

    }

    @Override
    public void updateLabel() {
        drawFloatingLabel(onFlatSurface());
    }

    private void drawFloatingLabel(final boolean onFlatSurface) {
        String text = "";
        if (labelCustom && labelCustomText != null) {
            text += labelCustomText;
        }
        if (labelId) {
            text += (text.equals("") ? "" : "\n") + "#" + id;
        }
        if (labelModelName) {
            text += (text.equals("") ? "" : "\n") + pvModuleSpecs.getModel();
        }
        if (labelCellEfficiency) {
            text += (text.equals("") ? "" : "\n") + EnergyPanel.TWO_DECIMALS.format(100 * getCellEfficiency()) + "%";
        }
        if (labelTiltAngle) {
            text += (text.equals("") ? "" : "\n") + EnergyPanel.ONE_DECIMAL.format(onFlatSurface ? tiltAngle : Math.toDegrees(Math.asin(normal.getY()))) + " \u00B0";
        }
        if (labelTracker) {
            final String name = getTrackerName();
            if (name != null) {
                text += (text.equals("") ? "" : "\n") + name;
            }
        }
        if (labelEnergyOutput) {
            text += (text.equals("") ? "" : "\n") + (Util.isZero(solarPotentialToday) ? "Output" : EnergyPanel.TWO_DECIMALS.format(solarPotentialToday) + " kWh");
        }
        if (!text.equals("")) {
            label.setText(text);
            final double shift = 0.5 * (rotated ? panelHeight : panelWidth) / Scene.getInstance().getScale();
            label.setTranslation((onFlatSurface ? getAbsCenter().addLocal(0, 0, baseHeight) : getAbsCenter()).addLocal(normal.multiply(shift, null)));
            label.setVisible(true);
        } else {
            label.setVisible(false);
        }
    }

    public String getTrackerName() {
        String name = null;
        switch (trackerType) {
            case HORIZONTAL_SINGLE_AXIS_TRACKER:
                name = "HSAT";
                break;
            case TILTED_SINGLE_AXIS_TRACKER:
                name = "TSAT";
                break;
            case VERTICAL_SINGLE_AXIS_TRACKER:
                name = "VSAT";
                break;
            case ALTAZIMUTH_DUAL_AXIS_TRACKER:
                name = "AADAT";
                break;
        }
        return name;
    }

    // ensure that a solar panel in special cases (on a flat roof or at a tilt angle) will have correct orientation
    private void setNormal(final double angle, final double azimuth) {
        final Foundation foundation = getTopContainer();
        Vector3 v = foundation.getAbsPoint(0);
        final Vector3 vx = foundation.getAbsPoint(2).subtractLocal(v); // x direction
        final Vector3 vy = foundation.getAbsPoint(1).subtractLocal(v); // y direction
        final Matrix3 m = new Matrix3().applyRotationZ(-azimuth);
        final Vector3 v1 = m.applyPost(vx, null);
        final Vector3 v2 = m.applyPost(vy, null);
        v = new Matrix3().fromAngleAxis(angle, v1).applyPost(v2, null);
        if (v.getZ() < 0) {
            v.negateLocal();
        }
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
        Vector3 dir;
        Vector3 p;
        if (trackerType == NO_TRACKER && Util.isZero(tiltAngle)) {
            dir = new Vector3(0.5, 0, 0);
            p = o.add(0, 0, baseHeight, null);
        } else {
            dir = Util.isEqualFaster(normal, Vector3.UNIT_Z, 0.001) ? new Vector3(0, 1, 0) : normal.cross(Vector3.UNIT_Z, null); // special case when normal is z-axis
            dir = dir.multiplyLocal(0.5);
            p = o.add(0, 0, baseHeight, null);
        }
        Util.addPointToQuad(normal, o, p, dir, vertexBuffer, normalBuffer);
        final double w = (rotated ? panelHeight : panelWidth) / Scene.getInstance().getScale();
        dir.normalizeLocal().multiplyLocal(w * 0.5);
        final Vector3 v1 = p.add(dir, null);
        dir.negateLocal();
        final Vector3 v2 = p.add(dir, null);
        dir = new Vector3(normal).multiplyLocal(0.2);
        Util.addPointToQuad(normal, v1, v2, dir, vertexBuffer, normalBuffer);

        vertexBuffer.limit(vertexBuffer.position());
        normalBuffer.limit(normalBuffer.position());
        supportFrame.getMeshData().updateVertexCount();
        supportFrame.updateModelBound();
    }

    @Override
    public void drawSunBeam() {
        if (Heliodon.getInstance().isNightTime() || !drawSunBeam) {
            sunBeam.setVisible(false);
            normalVector.setVisible(false);
            sunAngle.setVisible(false);
            return;
        }
        final Vector3 o = (!onFlatSurface() || container instanceof Rack) ? getAbsPoint(0) : getAbsPoint(0).addLocal(0, 0, baseHeight);
        final Vector3 sunLocation = Heliodon.getInstance().computeSunLocation(Heliodon.getInstance().getCalendar()).normalizeLocal();
        final FloatBuffer beamsVertices = sunBeam.getMeshData().getVertexBuffer();
        beamsVertices.rewind();
        Vector3 r = o.clone(); // draw sun vector
        r.addLocal(sunLocation.multiply(5000, null));
        beamsVertices.put(o.getXf()).put(o.getYf()).put(o.getZf());
        beamsVertices.put(r.getXf()).put(r.getYf()).put(r.getZf());
        sunBeam.updateModelBound();
        sunBeam.setVisible(true);
        if (bloomRenderPass == null) {
            bloomRenderPass = new BloomRenderPass(SceneManager.getInstance().getCamera(), 10);
            bloomRenderPass.setBlurIntensityMultiplier(0.5f);
            bloomRenderPass.setNrBlurPasses(2);
            SceneManager.getInstance().getPassManager().add(bloomRenderPass);
        }
        if (!bloomRenderPass.contains(sunBeam)) {
            bloomRenderPass.add(sunBeam);
        }
        final FloatBuffer normalVertices = normalVector.getMeshData().getVertexBuffer();
        normalVertices.rewind();
        r = o.clone(); // draw normal vector
        r.addLocal(normal.multiply(normalVectorLength, null));
        normalVertices.put(o.getXf()).put(o.getYf()).put(o.getZf());
        normalVertices.put(r.getXf()).put(r.getYf()).put(r.getZf());

        // draw arrows of the normal vector
        final double arrowLength = 0.75;
        final double arrowAngle = Math.toRadians(20);
        final Matrix3 matrix = new Matrix3();
        final FloatBuffer buf = mesh.getMeshData().getVertexBuffer();
        final ReadOnlyTransform trans = mesh.getWorldTransform();
        final Vector3 v1 = new Vector3();
        final Vector3 v2 = new Vector3();
        BufferUtils.populateFromBuffer(v1, buf, 1);
        BufferUtils.populateFromBuffer(v2, buf, 2);
        Vector3 a = trans.applyForward(v1).subtract(trans.applyForward(v2), null).normalizeLocal();
        a = a.crossLocal(normal);
        Vector3 s = normal.clone();
        s = matrix.fromAngleNormalAxis(arrowAngle, a).applyPost(s, null).multiplyLocal(arrowLength);
        s = r.subtract(s, null);
        normalVertices.put(r.getXf()).put(r.getYf()).put(r.getZf());
        normalVertices.put(s.getXf()).put(s.getYf()).put(s.getZf());
        s = normal.clone();
        s = matrix.fromAngleNormalAxis(-arrowAngle, a).applyPost(s, null).multiplyLocal(arrowLength);
        s = r.subtract(s, null);
        normalVertices.put(r.getXf()).put(r.getYf()).put(r.getZf());
        normalVertices.put(s.getXf()).put(s.getYf()).put(s.getZf());

        // draw the angle between the sun beam and the normal vector
        normal.cross(sunLocation, a);
        sunAngle.setRange(o, o.add(sunLocation, null), o.add(normal, null), a);
        sunAngle.setVisible(true);

        normalVector.updateModelBound();
        normalVector.setVisible(true);
    }

    // draw solar cell outlines when in heat map mode
    private void drawSolarCellOutlines() {
        final FloatBuffer vertexBuffer = mesh.getMeshData().getVertexBuffer();
        final ReadOnlyTransform trans = mesh.getTransform(); // do not use WorldTransform
        final Vector3 p0 = trans.applyForward(new Vector3(vertexBuffer.get(3), vertexBuffer.get(4), vertexBuffer.get(5))); // (0, 0)
        final Vector3 p1 = trans.applyForward(new Vector3(vertexBuffer.get(6), vertexBuffer.get(7), vertexBuffer.get(8))); // (1, 0)
        final Vector3 p2 = trans.applyForward(new Vector3(vertexBuffer.get(0), vertexBuffer.get(1), vertexBuffer.get(2))); // (0, 1)
        final int bufferSize = (getNumberOfCellsInX() + getNumberOfCellsInY() + 2) * 6;
        FloatBuffer vertices = solarCellOutlines.getMeshData().getVertexBuffer();
        if (vertices.capacity() != bufferSize) {
            vertices = BufferUtils.createFloatBuffer(bufferSize);
            solarCellOutlines.getMeshData().setVertexBuffer(vertices);
        } else {
            vertices.rewind();
            vertices.limit(vertices.capacity());
        }
        final int nx = rotated ? getNumberOfCellsInY() : getNumberOfCellsInX();
        final int ny = rotated ? getNumberOfCellsInX() : getNumberOfCellsInY();
        final Vector3 u = p1.subtract(p0, null).normalizeLocal();
        final Vector3 v = p2.subtract(p0, null).normalizeLocal();
        final double margin = 0.3;
        final double dx = (p1.distance(p0) - margin * 2) / ny;
        final double dy = (p2.distance(p0) - margin * 2) / nx;
        final Vector3 ud = u.multiply(dx, null);
        final Vector3 vd = v.multiply(dy, null);
        final Vector3 um = u.multiply(margin, null);
        final Vector3 vm = v.multiply(margin, null);
        Vector3 p, q;

        // draw x-lines
        for (int i = 0; i <= nx; i++) {
            q = vm.add(vd.multiply(i, null), null);
            p = p0.add(um, null).addLocal(q);
            vertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
            p = p1.subtract(um, null).addLocal(q);
            vertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
        }

        // draw y-lines
        for (int i = 0; i <= ny; i++) {
            q = um.add(ud.multiply(i, null), null);
            p = p0.add(vm, null).addLocal(q);
            vertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
            p = p2.subtract(vm, null).addLocal(q);
            vertices.put(p.getXf()).put(p.getYf()).put(p.getZf());
        }

        solarCellOutlines.updateModelBound();
        solarCellOutlines.setVisible(true);
    }

    @Override
    public boolean isDrawable() {
        if (container == null) {
            return true;
        }
        if (mesh.getWorldBound() == null) {
            return true;
        }
        final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
        if (selectedPart == null || selectedPart.isDrawCompleted()) { // if nothing is really selected, skip overlap check
            return true;
        }
        final OrientedBoundingBox bound = (OrientedBoundingBox) mesh.getWorldBound().clone(null);
        bound.setExtent(bound.getExtent().divide(1.5, null).addLocal(0, 0, 1));
        for (final HousePart child : container.getChildren()) {
            if (child != this && child instanceof SolarPanel && bound.intersects(child.mesh.getWorldBound())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void updateTextureAndColor() {
        updateTextureAndColor(mesh, null);
    }

    @Override
    protected String getTextureFileName() {
        switch (colorOption) {
            case COLOR_OPTION_BLACK:
                switch (cellType) {
                    case MONOCRYSTALLINE:
                        return rotated ? "solarpanel-black-landscape.png" : "solarpanel-black-portrait.png";
                    case POLYCRYSTALLINE:
                    case THIN_FILM:
                        return rotated ? "polycrystal-solarpanel-black-landscape.png" : "polycrystal-solarpanel-black-portrait.png";
                }
            case COLOR_OPTION_GRAY:
                switch (cellType) {
                    case MONOCRYSTALLINE:
                        return rotated ? "solarpanel-gray-landscape.png" : "solarpanel-gray-portrait.png";
                    case POLYCRYSTALLINE:
                    case THIN_FILM:
                        return rotated ? "polycrystal-solarpanel-gray-landscape.png" : "polycrystal-solarpanel-gray-portrait.png";
                }
            default:
                switch (cellType) {
                    case POLYCRYSTALLINE:
                    case THIN_FILM:
                        return rotated ? "polycrystal-solarpanel-blue-landscape.png" : "polycrystal-solarpanel-blue-portrait.png";
                    default:
                        return rotated ? "solarpanel-blue-landscape.png" : "solarpanel-blue-portrait.png";
                }
        }
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
        return Math.min(panelWidth, panelHeight) / Scene.getInstance().getScale() / (SceneManager.getInstance().isFineGrid() ? 50.0 : 10.0);
    }

    @Override
    protected void computeArea() {
        area = panelWidth * panelHeight;
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
        final double w1 = (rotated ? panelHeight : panelWidth) / Scene.getInstance().getScale();
        final Vector3 center = getAbsCenter();
        for (final HousePart p : Scene.getInstance().getParts()) {
            if (p.container == container && p != this) {
                if (p instanceof SolarPanel) {
                    final SolarPanel s2 = (SolarPanel) p;
                    final double w2 = (s2.rotated ? s2.panelHeight : s2.panelWidth) / Scene.getInstance().getScale();
                    final double distance = p.getAbsCenter().distance(center);
                    if (distance < (w1 + w2) * 0.499) {
                        return distance;
                    }
                }
            }
        }
        return -1;
    }

    @Override
    public HousePart copy(final boolean check) {
        final SolarPanel c = (SolarPanel) super.copy(false);
        c.meshLocator = meshLocator; // deepy copy creates a copy of the foundation, we don't want that
        if (check) {
            // normal = c.computeNormalAndKeepOnSurface();
            final double sceneScale = Scene.getInstance().getScale();
            if (container instanceof Roof || container instanceof Rack) {
                if (normal == null) {
                    // don't remove this error message just in case this happens again
                    JOptionPane.showMessageDialog(MainFrame.getInstance(), "Normal of solar panel [" + c + "] is null. Please try again.", "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
                if (Util.isEqual(normal, Vector3.UNIT_Z)) { // flat roof
                    final Foundation foundation = getTopContainer();
                    final Vector3 p0 = foundation.getAbsPoint(0);
                    final Vector3 p1 = foundation.getAbsPoint(1);
                    final Vector3 p2 = foundation.getAbsPoint(2);
                    final double a = -Math.toRadians(relativeAzimuth) * Math.signum(p2.subtract(p0, null).getX() * p1.subtract(p0, null).getY());
                    final Vector3 v = new Vector3(Math.cos(a), Math.sin(a), 0);
                    final double length = (1 + layoutGap) * (rotated ? panelHeight : panelWidth) / sceneScale;
                    final double s = Math.signum(container.getAbsCenter().subtractLocal(Scene.getInstance().getOriginalCopy().getAbsCenter()).dot(v));
                    final double tx = length / p0.distance(p2);
                    final double ty = length / p0.distance(p1);
                    final double lx = s * v.getX() * tx;
                    final double ly = s * v.getY() * ty;
                    c.points.get(0).setX(points.get(0).getX() + lx);
                    c.points.get(0).setY(points.get(0).getY() + ly);
                } else {
                    final Vector3 d = normal.cross(Vector3.UNIT_Z, null);
                    d.normalizeLocal();
                    if (Util.isZero(d.length())) {
                        d.set(1, 0, 0);
                    }
                    final double s = Math.signum(container.getAbsCenter().subtractLocal(Scene.getInstance().getOriginalCopy().getAbsCenter()).dot(d));
                    d.multiplyLocal((1 + layoutGap) * (rotated ? panelHeight : panelWidth) / sceneScale);
                    d.addLocal(getContainerRelative().getPoints().get(0));
                    final Vector3 v = toRelative(d);
                    c.points.get(0).setX(points.get(0).getX() + s * v.getX());
                    c.points.get(0).setY(points.get(0).getY() + s * v.getY());
                    c.points.get(0).setZ(points.get(0).getZ() + s * v.getZ());
                }
                final boolean isOutside;
                if (container instanceof Roof) {
                    isOutside = !((Roof) c.container).insideWallsPolygon(c.getAbsCenter());
                } else if (container instanceof Rack) {
                    final double panelDx = (rotated ? panelHeight : panelWidth) / 2 / sceneScale;
                    final double rackDx = ((Rack) container).getRackWidth() / 2 / sceneScale;
                    isOutside = c.getAbsPoint(0).multiplyLocal(1, 1, 0).distance(container.getAbsPoint(0).multiplyLocal(1, 1, 0)) > rackDx - panelDx;
                } else {
                    isOutside = false;
                }
                if (isOutside) {
                    JOptionPane.showMessageDialog(MainFrame.getInstance(), "Sorry, you are not allowed to paste a solar panel outside a roof or rack.", "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
                final double o = c.overlap();
                if (o >= 0) {
                    JOptionPane.showMessageDialog(MainFrame.getInstance(), "Sorry, your new solar panel is too close to an existing one (" + o + ").", "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            } else if (container instanceof Foundation) {
                final Vector3 p0 = container.getAbsPoint(0);
                final Vector3 p1 = container.getAbsPoint(1);
                final Vector3 p2 = container.getAbsPoint(2);
                final double a = -Math.toRadians(relativeAzimuth) * Math.signum(p2.subtract(p0, null).getX() * p1.subtract(p0, null).getY());
                final Vector3 v = new Vector3(Math.cos(a), Math.sin(a), 0);
                final double length = (1 + layoutGap) * (rotated ? panelHeight : panelWidth) / sceneScale;
                final double s = Math.signum(container.getAbsCenter().subtractLocal(Scene.getInstance().getOriginalCopy().getAbsCenter()).dot(v));
                final double tx = length / p0.distance(p2);
                final double ty = length / p0.distance(p1);
                final double lx = s * v.getX() * tx;
                final double ly = s * v.getY() * ty;
                final double newX = points.get(0).getX() + lx;
                if (newX > 1 - tx || newX < tx) {
                    return null;
                }
                final double newY = points.get(0).getY() + ly;
                if (newY > 1 - ty || newY < ty) {
                    return null;
                }
                c.points.get(0).setX(newX);
                c.points.get(0).setY(newY);
                final double o = c.overlap();
                if (o >= 0) {
                    JOptionPane.showMessageDialog(MainFrame.getInstance(), "Sorry, your new solar panel is too close to an existing one (" + o + ").", "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            } else if (container instanceof Wall) {
                final double s = Math.signum(toRelative(container.getAbsCenter()).subtractLocal(toRelative(Scene.getInstance().getOriginalCopy().getAbsCenter())).dot(Vector3.UNIT_X));
                final double shift = (1 + layoutGap) * (rotated ? panelHeight : panelWidth) / (container.getAbsPoint(0).distance(container.getAbsPoint(2)) * sceneScale);
                final double newX = points.get(0).getX() + s * shift;
                if (newX > 1 - shift / 2 || newX < shift / 2) {
                    return null;
                }
                c.points.get(0).setX(newX);
                if (c.overlap() >= 0) {
                    JOptionPane.showMessageDialog(MainFrame.getInstance(), "Sorry, your new solar panel is too close to an existing one.", "Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }
        }
        return c;
    }

    @Override
    public double getYieldNow() {
        return yieldNow;
    }

    @Override
    public void setYieldNow(final double yieldNow) {
        this.yieldNow = yieldNow;
    }

    @Override
    public double getYieldToday() {
        return yieldToday;
    }

    @Override
    public void setYieldToday(final double yieldToday) {
        this.yieldToday = yieldToday;
    }

    /**
     * a number between 0 and 1
     */
    public void setCellEfficiency(final double efficiency) {
        this.efficiency = efficiency;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setCellEfficiency(efficiency);
        }
    }

    /**
     * a number between 0 and 1
     */
    public double getCellEfficiency() {
        if (pvModuleSpecs == null) {
            return efficiency;
        }
        return pvModuleSpecs.getCelLEfficiency();
    }

    /**
     * a number between 0 and 1 to specify power output change with respect to STC temperature (25C)
     */
    public void setTemperatureCoefficientPmax(final double temperatureCoefficientPmax) {
        this.temperatureCoefficientPmax = temperatureCoefficientPmax;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setPmaxTc(temperatureCoefficientPmax);
        }
    }

    /**
     * a number between 0 and 1 to specify power output change with respect to STC temperature (25C)
     */
    public double getTemperatureCoefficientPmax() {
        if (pvModuleSpecs == null) {
            return temperatureCoefficientPmax;
        }
        return pvModuleSpecs.getPmaxTc();
    }

    /**
     * Nominal Operating Cell Temperature (http://pveducation.org/pvcdrom/modules/nominal-operating-cell-temperature)
     */
    public void setNominalOperatingCellTemperature(final double nominalOperatingCellTemperature) {
        this.nominalOperatingCellTemperature = nominalOperatingCellTemperature;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setNoct(nominalOperatingCellTemperature);
        }
    }

    /**
     * Nominal Operating Cell Temperature (http://pveducation.org/pvcdrom/modules/nominal-operating-cell-temperature)
     */
    public double getNominalOperatingCellTemperature() {
        if (pvModuleSpecs == null) {
            return nominalOperatingCellTemperature;
        }
        return pvModuleSpecs.getNoct();
    }

    /**
     * a number between 0 and 1, typically 0.95
     */
    public void setInverterEfficiency(final double inverterEfficiency) {
        this.inverterEfficiency = inverterEfficiency;
    }

    /**
     * a number between 0 and 1, typically 0.95
     */
    public double getInverterEfficiency() {
        return inverterEfficiency;
    }

    public void setModelName(final String modelName) {
        pvModuleSpecs.setModel(modelName);
    }

    public String getModelName() {
        return pvModuleSpecs.getModel();
    }

    public void setBrandName(final String brandName) {
        pvModuleSpecs.setBrand(brandName);
    }

    public String getBrandName() {
        return pvModuleSpecs.getBrand();
    }

    public void setPanelWidth(final double panelWidth) {
        this.panelWidth = panelWidth;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setWidth(panelWidth);
        }
    }

    public double getPanelWidth() {
        return panelWidth;
    }

    public void setPanelHeight(final double panelHeight) {
        this.panelHeight = panelHeight;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setLength(panelHeight);
        }
    }

    public double getPanelHeight() {
        return panelHeight;
    }

    @Override
    public void setPoleHeight(final double poleHeight) {
        baseHeight = poleHeight;
    }

    @Override
    public double getPoleHeight() {
        return baseHeight;
    }

    public void setRotated(final boolean b) {
        rotated = b;
    }

    public boolean isRotated() {
        return rotated;
    }

    public void setRelativeAzimuth(double relativeAzimuth) {
        if (relativeAzimuth < 0) {
            relativeAzimuth += 360;
        } else if (relativeAzimuth > 360) {
            relativeAzimuth -= 360;
        }
        this.relativeAzimuth = relativeAzimuth;
    }

    public double getRelativeAzimuth() {
        return relativeAzimuth;
    }

    public void setTiltAngle(final double tiltAngle) {
        this.tiltAngle = tiltAngle;
    }

    public double getTiltAngle() {
        return tiltAngle;
    }

    @Override
    public void setTracker(final int tracker) {
        this.trackerType = tracker;
    }

    @Override
    public int getTracker() {
        return trackerType;
    }

    public void setNumberOfCellsInX(final int numberOfCellsInX) {
        this.numberOfCellsInX = numberOfCellsInX;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setLayout(numberOfCellsInX, numberOfCellsInY);
        }
    }

    public int getNumberOfCellsInX() {
        if (pvModuleSpecs == null) {
            return numberOfCellsInX;
        }
        return pvModuleSpecs.getLayout().width;
    }

    public void setNumberOfCellsInY(final int numberOfCellsInY) {
        this.numberOfCellsInY = numberOfCellsInY;
        if (pvModuleSpecs != null) {
            pvModuleSpecs.setLayout(numberOfCellsInX, numberOfCellsInY);
        }
    }

    public int getNumberOfCellsInY() {
        if (pvModuleSpecs == null) {
            return numberOfCellsInY;
        }
        return pvModuleSpecs.getLayout().height;
    }

    public void setShadeTolerance(final int shadeTolerance) {
        this.shadeTolerance = shadeTolerance;
        if (pvModuleSpecs != null) {
            switch (shadeTolerance) {
                case PARTIAL_SHADE_TOLERANCE:
                    pvModuleSpecs.setShadeTolerance("Partial");
                    break;
                case HIGH_SHADE_TOLERANCE:
                    pvModuleSpecs.setShadeTolerance("High");
                    break;
                case NO_SHADE_TOLERANCE:
                    pvModuleSpecs.setShadeTolerance("None");
                    break;
            }
        }
    }

    public int getShadeTolerance() {
        if (pvModuleSpecs == null) {
            return shadeTolerance;
        }
        if ("Partial".equals(pvModuleSpecs.getShadeTolerance())) {
            return PARTIAL_SHADE_TOLERANCE;
        }
        if ("High".equals(pvModuleSpecs.getShadeTolerance())) {
            return HIGH_SHADE_TOLERANCE;
        }
        return NO_SHADE_TOLERANCE;
    }

    @Override
    public void setSunBeamVisible(final boolean drawSunBeam) {
        this.drawSunBeam = drawSunBeam;
    }

    @Override
    public boolean isSunBeamVisible() {
        return drawSunBeam;
    }

    @Override
    public void delete() {
        super.delete();
        if (bloomRenderPass != null) {
            if (bloomRenderPass.contains(sunBeam)) {
                bloomRenderPass.remove(sunBeam);
            }
        }
    }

    @Override
    public void move(final Vector3 v, final double steplength) {
        if (lockEdit) {
            return;
        }
        v.normalizeLocal().multiplyLocal(steplength);
        final Vector3 p = getAbsPoint(0).addLocal(v);
        points.get(0).set(toRelative(p));
    }

    public double getSystemEfficiency(final double temperature) {
        double e = getCellEfficiency() * inverterEfficiency;
        if (cellType == MONOCRYSTALLINE) {
            e *= 0.95; // assuming that the packing density factor of semi-round cells is 0.95
        }
        final Atmosphere atm = Scene.getInstance().getAtmosphere();
        if (atm != null) {
            e *= 1 - atm.getDustLoss(Heliodon.getInstance().getCalendar().get(Calendar.MONTH));
        }
        return e * (1 + temperatureCoefficientPmax * (temperature - 25));
    }

    public List<SolarPanel> getRow() {
        final double minDistance = (rotated ? panelHeight : panelWidth) / Scene.getInstance().getScale() * 1.05;
        final List<SolarPanel> panels = getTopContainer().getSolarPanels();
        final List<SolarPanel> row = new ArrayList<>();
        row.add(this);
        final ArrayList<SolarPanel> oldNeighbors = new ArrayList<>();
        oldNeighbors.add(this);
        final ArrayList<SolarPanel> newNeighbors = new ArrayList<>();
        do {
            newNeighbors.clear();
            for (final HousePart oldNeighbor : oldNeighbors) {
                final Vector3 c = oldNeighbor.getAbsCenter();
                for (final SolarPanel x : panels) {
                    if (x != oldNeighbor && !row.contains(x)) {
                        if (x.getAbsCenter().distance(c) < minDistance) {
                            newNeighbors.add(x);
                        }
                    }
                }
            }
            row.addAll(newNeighbors);
            oldNeighbors.clear();
            oldNeighbors.addAll(newNeighbors);
        } while (!newNeighbors.isEmpty());
        return row;
    }

    public void setColorOption(final int colorOption) {
        this.colorOption = colorOption;
        if (pvModuleSpecs != null) {
            switch (colorOption) {
                case COLOR_OPTION_BLUE:
                    pvModuleSpecs.setColor("Blue");
                    break;
                case COLOR_OPTION_BLACK:
                    pvModuleSpecs.setColor("Black");
                    break;
                case COLOR_OPTION_GRAY:
                    pvModuleSpecs.setColor("Gray");
                    break;
            }
        }
    }

    public int getColorOption() {
        if (pvModuleSpecs == null) {
            return colorOption;
        }
        if ("Black".equals(pvModuleSpecs.getColor())) {
            return COLOR_OPTION_BLACK;
        }
        if ("Gray".equals(pvModuleSpecs.getColor())) {
            return COLOR_OPTION_GRAY;
        }
        return COLOR_OPTION_BLUE;
    }

    public void setCellType(final int cellType) {
        this.cellType = cellType;
        if (pvModuleSpecs != null) {
            switch (cellType) {
                case POLYCRYSTALLINE:
                    pvModuleSpecs.setCellType("Polycrystalline");
                    break;
                case MONOCRYSTALLINE:
                    pvModuleSpecs.setCellType("Monocrystalline");
                    break;
                case THIN_FILM:
                    pvModuleSpecs.setCellType("Thin Film");
                    break;
            }
        }
    }

    public int getCellType() {
        if (pvModuleSpecs == null) {
            return cellType;
        }
        if ("Polycrystalline".equals(pvModuleSpecs.getCellType())) {
            return POLYCRYSTALLINE;
        }
        if ("Thin Film".equals(pvModuleSpecs.getCellType())) {
            return THIN_FILM;
        }
        return MONOCRYSTALLINE;
    }

    @Override
    public void setEditPointsVisible(final boolean visible) {
        super.setEditPointsVisible(visible);
        if (container instanceof Rack) {
            container.setEditPointsVisible(visible);
        }

    }

    @Override
    public MeshLocator getMeshLocator() {
        return meshLocator;
    }

    @Override
    public void setMeshLocator(final MeshLocator meshLocator) {
        this.meshLocator = meshLocator;
    }

    @Override
    public void clearLabels() {
        super.clearLabels();
        labelModelName = false;
        labelCellEfficiency = false;
        labelTiltAngle = false;
        labelTracker = false;
        labelEnergyOutput = false;
    }

    public boolean isLabelVisible() {
        return label.isVisible();
    }

    public void setLabelModelName(final boolean labelModelName) {
        this.labelModelName = labelModelName;
    }

    public boolean getLabelModelName() {
        return labelModelName;
    }

    public void setLabelTracker(final boolean labelTracker) {
        this.labelTracker = labelTracker;
    }

    public boolean getLabelTracker() {
        return labelTracker;
    }

    public void setLabelCellEfficiency(final boolean labelCellEfficiency) {
        this.labelCellEfficiency = labelCellEfficiency;
    }

    public boolean getLabelCellEfficiency() {
        return labelCellEfficiency;
    }

    public void setLabelTiltAngle(final boolean labelTiltAngle) {
        this.labelTiltAngle = labelTiltAngle;
    }

    public boolean getLabelTiltAngle() {
        return labelTiltAngle;
    }

    public void setLabelEnergyOutput(final boolean labelEnergyOutput) {
        this.labelEnergyOutput = labelEnergyOutput;
    }

    public boolean getLabelEnergyOutput() {
        return labelEnergyOutput;
    }

    @Override
    protected void addPrintMesh(final List<Mesh> list, final Mesh mesh) {
        if (mesh.getSceneHints().getCullHint() != CullHint.Always) {
            final Mesh newMesh = mesh.makeCopy(false);
            final MaterialState material = new MaterialState();
            switch (colorOption) {
                case COLOR_OPTION_BLACK:
                    material.setDiffuse(ColorRGBA.BLACK);
                    break;
                case COLOR_OPTION_BLUE:
                    material.setDiffuse(ColorRGBA.BLUE);
                    break;
                case COLOR_OPTION_GRAY:
                    material.setDiffuse(ColorRGBA.GRAY);
                    break;
                default:
                    material.setDiffuse(mesh.getDefaultColor());
            }
            newMesh.setRenderState(material);
            newMesh.getMeshData().transformVertices((Transform) mesh.getWorldTransform());
            newMesh.getMeshData().transformNormals((Transform) mesh.getWorldTransform(), true);
            list.add(newMesh);
        }
    }

    @Override
    public void addPrintMeshes(final List<Mesh> list) {
        addPrintMesh(list, surround);
        addPrintMesh(list, supportFrame);
    }

    public void setPvModuleSpecs(final PvModuleSpecs pvModuleSpecs) {
        this.pvModuleSpecs = pvModuleSpecs;
        // backward compatibility
        panelWidth = pvModuleSpecs.getWidth();
        panelHeight = pvModuleSpecs.getLength();
        efficiency = pvModuleSpecs.getCelLEfficiency();
        temperatureCoefficientPmax = pvModuleSpecs.getPmaxTc();
        nominalOperatingCellTemperature = pvModuleSpecs.getNoct();
        numberOfCellsInX = pvModuleSpecs.getLayout().width;
        numberOfCellsInY = pvModuleSpecs.getLayout().height;
        convertStringPropertiesToIntegerProperties();
    }

    private void convertStringPropertiesToIntegerProperties() {
        if ("Polycrystalline".equals(pvModuleSpecs.getCellType())) {
            cellType = POLYCRYSTALLINE;
        } else if ("Monocrystalline".equals(pvModuleSpecs.getCellType())) {
            cellType = MONOCRYSTALLINE;
        } else if ("Thin Film".equals(pvModuleSpecs.getCellType())) {
            cellType = THIN_FILM;
        }
        if ("Blue".equals(pvModuleSpecs.getColor())) {
            colorOption = COLOR_OPTION_BLUE;
        } else if ("Black".equals(pvModuleSpecs.getColor())) {
            colorOption = COLOR_OPTION_BLACK;
        } else if ("Gray".equals(pvModuleSpecs.getColor())) {
            colorOption = COLOR_OPTION_GRAY;
        }
        if ("Partial".equals(pvModuleSpecs.getShadeTolerance())) {
            shadeTolerance = PARTIAL_SHADE_TOLERANCE;
        } else if ("High".equals(pvModuleSpecs.getColor())) {
            shadeTolerance = HIGH_SHADE_TOLERANCE;
        } else if ("None".equals(pvModuleSpecs.getColor())) {
            shadeTolerance = NO_SHADE_TOLERANCE;
        }
    }

    public PvModuleSpecs getPvModuleSpecs() {
        return pvModuleSpecs;
    }

}