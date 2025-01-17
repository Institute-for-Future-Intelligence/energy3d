package org.concord.energy3d.shapes;

import java.awt.EventQueue;
import java.nio.FloatBuffer;
import java.util.Calendar;
import java.util.Date;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.undo.ChangeTimeAndDateWithHeliodonCommand;
import org.concord.energy3d.util.FontManager;
import org.concord.energy3d.util.Util;

import com.ardor3d.bounding.BoundingVolume;
import com.ardor3d.extension.effect.bloom.BloomRenderPass;
import com.ardor3d.input.Key;
import com.ardor3d.input.MouseButton;
import com.ardor3d.input.MouseState;
import com.ardor3d.input.logical.InputTrigger;
import com.ardor3d.input.logical.KeyPressedCondition;
import com.ardor3d.input.logical.LogicalLayer;
import com.ardor3d.input.logical.MouseButtonPressedCondition;
import com.ardor3d.input.logical.MouseButtonReleasedCondition;
import com.ardor3d.input.logical.MouseMovedCondition;
import com.ardor3d.intersection.IntersectionRecord;
import com.ardor3d.intersection.PickResults;
import com.ardor3d.intersection.PickingUtil;
import com.ardor3d.intersection.PrimitivePickResults;
import com.ardor3d.light.DirectionalLight;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.MathUtils;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Ray3;
import com.ardor3d.math.Vector2;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyTransform;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.renderer.IndexMode;
import com.ardor3d.renderer.pass.BasicPassManager;
import com.ardor3d.renderer.pass.RenderPass;
import com.ardor3d.renderer.queue.RenderBucketType;
import com.ardor3d.renderer.state.BlendState;
import com.ardor3d.renderer.state.ClipState;
import com.ardor3d.renderer.state.MaterialState;
import com.ardor3d.renderer.state.OffsetState;
import com.ardor3d.renderer.state.OffsetState.OffsetType;
import com.ardor3d.renderer.state.ShadingState;
import com.ardor3d.renderer.state.ShadingState.ShadingMode;
import com.ardor3d.renderer.state.WireframeState;
import com.ardor3d.scenegraph.Line;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.Spatial;
import com.ardor3d.scenegraph.hint.CullHint;
import com.ardor3d.scenegraph.hint.LightCombineMode;
import com.ardor3d.scenegraph.hint.TransparencyType;
import com.ardor3d.scenegraph.shape.Sphere;
import com.ardor3d.ui.text.BMText;
import com.ardor3d.ui.text.BMText.Align;
import com.ardor3d.ui.text.BMText.AutoFade;
import com.ardor3d.ui.text.BMText.AutoScale;
import com.ardor3d.util.geom.BufferUtils;

public class Heliodon {

    public static final double DEFAULT_LATITUDE = 42.34396;
    private static final int BASE_DIVISIONS = 72;
    private static final int DECLINATION_DIVISIONS = 12;
    private static final int HOUR_DIVISIONS = 96;
    private static final int BASE_VERTICES = 72 * 2;
    private static final int SUN_REGION_VERTICES = 8064 / 3;
    private static final int SUN_PATH_VERTICES = 291 / 3;
    private static final double TILT_ANGLE = 23.45 / 180.0 * Math.PI;
    private static Heliodon instance;
    private final Node root = new Node("Heliodon Root");
    private final Mesh sun = new Sphere("Sun", 20, 20, 0.3);
    private final DirectionalLight light;
    private final Line sunPath;
    private final Line sunTriangle;
    private final Node angles;
    private final Mesh sunRegion;
    private final PickResults pickResults;
    private final Mesh base;
    private final Mesh baseTicks;
    private final Calendar calendar = Calendar.getInstance();
    private double hourAngle;
    private double declinationAngle;
    private double latitude = DEFAULT_LATITUDE / 180.0 * Math.PI;
    private boolean sunGrabbed = false;
    private boolean selectDifferentDeclinationWithMouse = false;
    private boolean dirtySunRegion = false;
    private boolean dirtySunPath = false;
    private boolean forceSunRegionOn = true;
    private BloomRenderPass bloomRenderPass;
    private final BasicPassManager passManager;
    private boolean visible;
    private double oldHourAngle;
    private ChangeTimeAndDateWithHeliodonCommand changeTimeAndDateCommand;

    public static Heliodon getInstance() {
        return instance;
    }

    public Heliodon(final Node scene, final DirectionalLight light, final BasicPassManager passManager, final LogicalLayer logicalLayer, final Date timeAndDate) {
        this.light = light;
        this.passManager = passManager;
        pickResults = new PrimitivePickResults();
        pickResults.setCheckDistance(true);

        // Sun
        final MaterialState material = new MaterialState();
        material.setEmissive(ColorRGBA.WHITE);
        sun.setRenderState(material);
        sun.updateModelBound();
        sun.setTranslation(0, 0, 5);
        root.attachChild(sun);

        // Sun Path
        sunPath = new Line("Sun Path", BufferUtils.createVector3Buffer(SUN_PATH_VERTICES), null, null, null);
        sunPath.setLineWidth(3);
        sunPath.setDefaultColor(ColorRGBA.YELLOW);
        sunPath.getMeshData().setIndexMode(IndexMode.LineStrip);
        Util.disablePickShadowLight(sunPath);
        root.attachChild(sunPath);

        // Sun Triangle
        sunTriangle = new Line("Sun Triangle", BufferUtils.createVector3Buffer(SUN_PATH_VERTICES), null, null, null);
        sunTriangle.setLineWidth(1);
        // sunTriangle.setStipplePattern((short) 0xcccc);
        sunTriangle.setDefaultColor(ColorRGBA.WHITE);
        sunTriangle.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(12));
        Util.disablePickShadowLight(sunTriangle);
        root.attachChild(sunTriangle);

        angles = new Node("Sun Angles");
        angles.getSceneHints().setAllPickingHints(false);
        Util.disablePickShadowLight(angles);
        root.attachChild(angles);

        final AngleAnnotation zenith = new AngleAnnotation();
        zenith.setColor(ColorRGBA.WHITE);
        zenith.setLineWidth(1);
        // zenith.setStipplePattern((short) 0xcccc);
        zenith.setFontSize(2.5);
        zenith.setCustomRadius(1.5);
        zenith.setCustomText("Z");
        angles.attachChild(zenith);

        final AngleAnnotation elevation = new AngleAnnotation();
        elevation.setColor(ColorRGBA.WHITE);
        elevation.setLineWidth(1);
        // elevation.setStipplePattern((short) 0xcccc);
        elevation.setFontSize(2.5);
        elevation.setCustomRadius(1.75);
        elevation.setCustomText("h");
        angles.attachChild(elevation);

        final AngleAnnotation azimuth = new AngleAnnotation();
        azimuth.setColor(ColorRGBA.WHITE);
        azimuth.setLineWidth(1);
        // azimuth.setStipplePattern((short) 0xcccc);
        azimuth.setFontSize(3);
        azimuth.setCustomRadius(1);
        azimuth.setCustomText("A");
        angles.attachChild(azimuth);

        // Sun Region
        sunRegion = new Mesh("Sun Region");
        sunRegion.setTranslation(0, 0, 0.001); // to avoid flickering
        if (!forceSunRegionOn) {
            sunRegion.getSceneHints().setCullHint(CullHint.Always);
        }
        sunRegion.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(SUN_REGION_VERTICES));
        sunRegion.getMeshData().setIndexMode(IndexMode.Quads);
        sunRegion.setDefaultColor(new ColorRGBA(1f, 1f, 0f, 0.5f));
        final BlendState blendState = new BlendState();
        blendState.setBlendEnabled(true);
        sunRegion.setRenderState(blendState);
        sunRegion.getSceneHints().setRenderBucketType(RenderBucketType.Transparent);
        sunRegion.getSceneHints().setTransparencyType(TransparencyType.TwoPass);
        sunRegion.getSceneHints().setLightCombineMode(LightCombineMode.Off);
        root.attachChild(sunRegion);

        // Sun Region Wireframe
        final RenderPass wireframePass = new RenderPass();
        wireframePass.setPassState(new WireframeState());
        wireframePass.add(sunRegion);
        passManager.add(wireframePass);

        // Base
        base = new Mesh("Base");
        base.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(BASE_VERTICES + 2));
        base.getMeshData().setColorBuffer(BufferUtils.createColorBuffer(BASE_VERTICES + 2));
        base.getMeshData().setIndexMode(IndexMode.QuadStrip);
        final OffsetState offsetState = new OffsetState();
        offsetState.setTypeEnabled(OffsetType.Fill, true);
        offsetState.setFactor(0.1f);
        offsetState.setUnits(0.1f);
        base.setRenderState(offsetState);
        final ShadingState shadingState = new ShadingState();
        shadingState.setShadingMode(ShadingMode.Flat);
        base.setRenderState(shadingState);
        base.getSceneHints().setLightCombineMode(LightCombineMode.Off);
        root.attachChild(base);

        // Base Ticks
        baseTicks = new Mesh("Base Ticks");
        baseTicks.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(BASE_VERTICES));
        baseTicks.getMeshData().setIndexMode(IndexMode.Lines);
        baseTicks.setDefaultColor(ColorRGBA.BLACK);
        baseTicks.getSceneHints().setLightCombineMode(LightCombineMode.Off);
        root.attachChild(baseTicks);

        // Azimuth: N = 0, E = 90, S = 180, W = 270
        final double radius = 6;
        final BMText northLabel = createText("0\u00B0");
        northLabel.setRotation(new Matrix3().fromAngles(-MathUtils.HALF_PI, 0, 0));
        northLabel.setTranslation(0, radius, 0);
        final BMText southLabel = createText("180\u00B0");
        southLabel.setRotation(new Matrix3().fromAngles(-MathUtils.HALF_PI, 0, Math.PI));
        southLabel.setTranslation(0, -radius, 0);
        final BMText eastLabel = createText("90\u00B0");
        eastLabel.setRotation(new Matrix3().fromAngles(-MathUtils.HALF_PI, 0, -MathUtils.HALF_PI));
        eastLabel.setTranslation(radius, 0, 0);
        final BMText westLabel = createText("270\u00B0");
        westLabel.setRotation(new Matrix3().fromAngles(-MathUtils.HALF_PI, 0, MathUtils.HALF_PI));
        westLabel.setTranslation(-radius, 0, 0);

        // TODO: Not exactly working at they do not lie on the ground
        // NE = 45, SE = 135, NW = 315, SW = 225
        final BMText northEastLabel = createText("45\u00B0");
        northEastLabel.setRotation(new Matrix3().fromAngles(-MathUtils.QUARTER_PI, 0, -MathUtils.QUARTER_PI));
        northEastLabel.setTranslation(radius * Math.cos(MathUtils.QUARTER_PI), radius * Math.sin(MathUtils.QUARTER_PI), 0);
        final BMText southEastLabel = createText("135\u00B0");
        southEastLabel.setRotation(new Matrix3().fromAngles(-MathUtils.QUARTER_PI, 0, Math.PI + MathUtils.QUARTER_PI));
        southEastLabel.setTranslation(radius * Math.cos(MathUtils.QUARTER_PI), radius * Math.sin(-MathUtils.QUARTER_PI), 0);
        final BMText northWestLabel = createText("315\u00B0");
        northWestLabel.setRotation(new Matrix3().fromAngles(-MathUtils.QUARTER_PI, 0, MathUtils.QUARTER_PI));
        northWestLabel.setTranslation(radius * Math.cos(MathUtils.QUARTER_PI + MathUtils.HALF_PI), radius * Math.sin(MathUtils.QUARTER_PI + MathUtils.HALF_PI), 0);
        final BMText southWestLabel = createText("225\u00B0");
        southWestLabel.setRotation(new Matrix3().fromAngles(-MathUtils.QUARTER_PI, 0, Math.PI - MathUtils.QUARTER_PI));
        southWestLabel.setTranslation(radius * Math.cos(MathUtils.QUARTER_PI + MathUtils.PI), radius * Math.sin(MathUtils.QUARTER_PI + MathUtils.PI), 0);

        // Clip
        final ClipState cs = new ClipState();
        cs.setEnableClipPlane(0, true);
        cs.setClipPlaneEquation(0, 0, 0, 1, 0);
        sunPath.setRenderState(cs);
        sunRegion.setRenderState(cs);

        initMouse(logicalLayer);

        root.getSceneHints().setCullHint(CullHint.Always);
        scene.attachChild(root);

        setDate(timeAndDate);
        setTime(timeAndDate);
        Util.setSilently(EnergyPanel.getInstance().getDateSpinner(), timeAndDate);

        if (isNightTime()) {
            final Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR, 0);
            calendar.set(Calendar.AM_PM, Calendar.PM);
            calendar.set(Calendar.MINUTE, 0);
            setTime(calendar.getTime());
            Util.setSilently(EnergyPanel.getInstance().getTimeSpinner(), calendar.getTime());
        } else {
            Util.setSilently(EnergyPanel.getInstance().getTimeSpinner(), timeAndDate);
        }

        draw();

        instance = this;
    }

    private BMText createText(final String text) {
        final BMText label = new BMText(text, text, FontManager.getInstance().getAnnotationFont(), Align.Center);
        label.setAutoRotate(false);
        label.setAutoScale(AutoScale.FixedScreenSize);
        label.setAutoFade(AutoFade.Off);
        label.setFontScale(0.75);
        root.attachChild(label);
        return label;
    }

    private void initMouse(final LogicalLayer logicalLayer) {
        logicalLayer.registerTrigger(new InputTrigger(new KeyPressedCondition(Key.F), (source, inputStates, tpf) -> setSunRegionAlwaysVisible(!forceSunRegionOn)));
        logicalLayer.registerTrigger(new InputTrigger(new MouseButtonPressedCondition(MouseButton.LEFT), (source, inputStates, tpf) -> {
            oldHourAngle = hourAngle;
            changeTimeAndDateCommand = new ChangeTimeAndDateWithHeliodonCommand(calendar.getTime());
            final int x = inputStates.getCurrent().getMouseState().getX();
            final int y = inputStates.getCurrent().getMouseState().getY();
            final Ray3 pickRay = SceneManager.getInstance().getCanvas().getCanvasRenderer().getCamera().getPickRay(new Vector2(x, y), false, null);
            pickResults.clear();
            PickingUtil.findPick(sun, pickRay, pickResults);
            if (pickResults.getNumber() != 0) {
                sunGrabbed = true;
            } else {
                sunGrabbed = false;
            }
            if (forceSunRegionOn) {
                selectDifferentDeclinationWithMouse = true;
            } else {
                selectDifferentDeclinationWithMouse = false;
            }
            SceneManager.getInstance().setMouseControlEnabled(!sunGrabbed);
        }));

        logicalLayer.registerTrigger(new InputTrigger(new MouseButtonReleasedCondition(MouseButton.LEFT), (source, inputStates, tpf) -> {
            sunGrabbed = false;
            if (!forceSunRegionOn) {
                sunRegion.getSceneHints().setCullHint(CullHint.Always);
            }
            SceneManager.getInstance().setMouseControlEnabled(true);
            if (!Util.isEqual(oldHourAngle, hourAngle) && changeTimeAndDateCommand != null) {
                SceneManager.getInstance().getUndoManager().addEdit(changeTimeAndDateCommand);
            }
        }));

        logicalLayer.registerTrigger(new InputTrigger(new MouseMovedCondition(), (source, inputStates, tpf) -> {
            if (Scene.getInstance().isDateFixed()) {
                return;
            }
            if (!sunGrabbed) {
                return;
            }
            final MouseState mouse = inputStates.getCurrent().getMouseState();
            final Ray3 pickRay = SceneManager.getInstance().getCamera().getPickRay(new Vector2(mouse.getX(), mouse.getY()), false, null);

            pickResults.clear();
            PickingUtil.findPick(sunRegion, pickRay, pickResults);
            final Vector3 intersectionPoint;
            if (pickResults.getNumber() > 0) {
                final IntersectionRecord intersectionRecord = pickResults.getPickData(0).getIntersectionRecord();
                intersectionPoint = intersectionRecord.getIntersectionPoint(intersectionRecord.getClosestIntersection());
            } else {
                intersectionPoint = null;
            }

            double smallestDistance = Double.MAX_VALUE;
            int hourVertex = -1;
            int totalHourVertices = 0;
            final Vector3 newSunLocation = new Vector3();
            final Vector3 p = new Vector3();
            final Vector3 p_abs = new Vector3();
            final ReadOnlyTransform rootTansform = root.getTransform();
            if (!selectDifferentDeclinationWithMouse) {
                final FloatBuffer buf = sunPath.getMeshData().getVertexBuffer();
                buf.rewind();
                while (buf.hasRemaining()) {
                    p.set(buf.get(), buf.get(), buf.get());
                    rootTansform.applyForward(p, p_abs);
                    final double d;
                    d = pickRay.distanceSquared(p_abs, null);
                    if (d < smallestDistance) {
                        smallestDistance = d;
                        hourVertex = buf.position() / 3 - 1;
                        newSunLocation.set(p);
                    }
                }
                totalHourVertices = buf.limit() / 3;
            }

            if (smallestDistance > 5.0 * root.getTransform().getScale().getX() * root.getTransform().getScale().getX()) {
                selectDifferentDeclinationWithMouse = true;
            }

            boolean declinationChanged = false;

            if (selectDifferentDeclinationWithMouse) {
                sunRegion.getSceneHints().setCullHint(CullHint.Inherit);
                int rowCounter = 0;
                int resultRow = -1;
                final FloatBuffer buf = sunRegion.getMeshData().getVertexBuffer();
                buf.rewind();
                final double r = 5.0 / 2.0;
                final Vector3 prev = new Vector3();
                int quadVertexCounter = 0;
                final double maxVertexInRow = HOUR_DIVISIONS * 4.0;
                int rowVertexCounter = 0;
                boolean foundInThisRow = false;
                while (buf.hasRemaining()) {
                    p.set(buf.get(), buf.get(), buf.get());
                    rootTansform.applyForward(p, p_abs);
                    final double d;
                    if (intersectionPoint != null) {
                        d = intersectionPoint.distanceSquared(p_abs);
                    } else {
                        d = pickRay.distanceSquared(p_abs, null);
                    }
                    if (d < smallestDistance && p.getZ() >= -MathUtils.ZERO_TOLERANCE) {
                        smallestDistance = d;
                        newSunLocation.set(p);
                        resultRow = rowCounter + (quadVertexCounter >= 2 ? 1 : 0);
                        hourVertex = rowVertexCounter / 4 + (quadVertexCounter == 1 || quadVertexCounter == 2 ? 1 : 0);
                        foundInThisRow = true;
                    }
                    if (prev.lengthSquared() != 0 && (prev.distance(p) > r || rowVertexCounter >= maxVertexInRow)) {
                        rowCounter++;
                        if (foundInThisRow) {
                            totalHourVertices = rowVertexCounter / 4;
                        }
                        foundInThisRow = false;
                        rowVertexCounter = 0;
                    }
                    prev.set(p);
                    quadVertexCounter = (quadVertexCounter + 1) % 4;
                    rowVertexCounter++;
                }
                rowCounter++;
                if (resultRow != -1) {
                    if (rowCounter < DECLINATION_DIVISIONS && latitude > 0) {
                        resultRow += DECLINATION_DIVISIONS - rowCounter;
                    }
                    final double newDeclinationAngle = -TILT_ANGLE + (2.0 * TILT_ANGLE * resultRow / DECLINATION_DIVISIONS);
                    declinationChanged = !Util.isEqual(newDeclinationAngle, declinationAngle);
                    if (declinationChanged) {
                        setDeclinationAngle(newDeclinationAngle, false, true);
                        dirtySunPath = true;
                    }
                }
            }
            final double newHourAngle = (hourVertex - Math.floor(totalHourVertices / 2.0)) * Math.PI / 48.0;
            final boolean hourAngleChanged = !Util.isEqual(newHourAngle, hourAngle);
            if (hourAngleChanged) {
                setHourAngle(newHourAngle, false, true, false);
            }
            if (declinationChanged || hourAngleChanged) {
                setSunLocation(newSunLocation);
                drawSunTriangle();
                EnergyPanel.getInstance().updateRadiationHeatMap();
            }
        }));
    }

    public Node getRoot() {
        return root;
    }

    public double getHourAngle() {
        return hourAngle;
    }

    public void setHourAngle(final double hourAngle, final boolean redrawHeliodon, final boolean updateGUI, final boolean undoable) {
        this.hourAngle = toPlusMinusPIRange(hourAngle, -Math.PI, Math.PI);

        final int minutes = (int) Math.round(this.hourAngle / Math.PI * 12 * 60 + 12 * 60);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, minutes);

        if (updateGUI) {
            EventQueue.invokeLater(() -> {
                final Date d = calendar.getTime();
                EnergyPanel.getInstance().updateWeatherData();
                EnergyPanel.getInstance().updateThermostat();
                if (undoable) {
                    EnergyPanel.getInstance().getTimeSpinner().setValue(d);
                    EnergyPanel.getInstance().getDateSpinner().setValue(d);
                } else {
                    Util.setSilently(EnergyPanel.getInstance().getTimeSpinner(), d);
                    Util.setSilently(EnergyPanel.getInstance().getDateSpinner(), d);
                }
            });
        }

        if (redrawHeliodon) {
            drawSun();
            drawSunTriangle();
        }

        if (SceneManager.getInstance() != null) {
            SceneManager.getInstance().refresh();
        }
    }

    public double getDeclinationAngle() {
        return declinationAngle;
    }

    private void setDeclinationAngle(final double declinationAngle, final boolean redrawHeliodon, final boolean updateGUI) {
        this.declinationAngle = toPlusMinusPIRange(declinationAngle, -TILT_ANGLE, TILT_ANGLE);

        if (updateGUI) {
            // calendar must not be updated if this method is called from setDate() because this method can only computer 6 months of the year from declination angle
            final double days = MathUtils.asin(this.declinationAngle / TILT_ANGLE) / MathUtils.TWO_PI * 365.25 - 284.0;
            calendar.set(calendar.get(Calendar.YEAR), 0, (int) Math.round(days)); //
            Util.setSilently(EnergyPanel.getInstance().getDateSpinner(), calendar.getTime());
        }

        if (redrawHeliodon) {
            dirtySunPath = true;
        }

        if (SceneManager.getInstance() != null) {
            SceneManager.getInstance().refresh();
        }
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(final double latitude) {
        this.latitude = toPlusMinusPIRange(latitude, -MathUtils.HALF_PI, MathUtils.HALF_PI);

        dirtySunRegion = true;
        dirtySunPath = true;

        if (SceneManager.getInstance() != null) {
            SceneManager.getInstance().refresh();
        }
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(final boolean visible) {
        this.visible = visible;
        getBloomRenderPass();
        if (!bloomRenderPass.contains(sun)) {
            bloomRenderPass.add(sun);
        }
        // bloomRenderPass.setEnabled(visible);
        root.getSceneHints().setCullHint(visible ? CullHint.Inherit : CullHint.Always);

        if (visible) {
            updateSize();
        }

        if (SceneManager.getInstance() != null) {
            SceneManager.getInstance().refresh();
        }
    }

    public void updateSize() {
        Scene.getRoot().updateWorldBound(true);
        final BoundingVolume bounds = Scene.getRoot().getWorldBound();
        if (bounds == null) {
            root.setScale(1);
        } else {
            final double scale = (Util.findBoundLength(bounds) / 2.0 + bounds.getCenter().length()) / 5.0;
            System.out.println("Heliodon scale = " + scale);
            if (!Double.isInfinite(scale)) {
                root.setScale(scale);
            }
        }
        root.updateGeometricState(0);
        if (SceneManager.getInstance() != null) {
            SceneManager.getInstance().refresh();
        }
    }

    public double getRadius() {
        return root.getScale().getX() * 5.0;
    }

    private Vector3 computeSunLocation(final double hourAngle, final double declinationAngle, final double observerLatitude) {
        // cache the expensive trig calculations
        final double cosHourAngle = MathUtils.cos(hourAngle);
        final double cosDeclinationAngle = MathUtils.cos(declinationAngle);
        final double sinDeclinationAngle = MathUtils.sin(declinationAngle);
        final double cosObserverLatitude = MathUtils.cos(observerLatitude);
        final double sinObserverLatitude = MathUtils.sin(observerLatitude);

        final double altitudeAngle = MathUtils.asin(sinDeclinationAngle * sinObserverLatitude + cosDeclinationAngle * cosHourAngle * cosObserverLatitude);
        final double x_azm = MathUtils.sin(hourAngle) * cosDeclinationAngle;
        final double y_azm = cosObserverLatitude * sinDeclinationAngle - cosHourAngle * cosDeclinationAngle * sinObserverLatitude;
        final double azimuthAngle = Math.atan2(y_azm, x_azm);

        final Vector3 coords = new Vector3(5, azimuthAngle, altitudeAngle);
        MathUtils.sphericalToCartesianZ(coords, coords);
        coords.setX(-coords.getX()); // reverse the x so that sun moves from east to west
        return coords;
    }

    private static double toPlusMinusPIRange(final double radian, final double min, final double max) {
        double result = radian - (int) (radian / MathUtils.TWO_PI) * MathUtils.TWO_PI;
        if (Math.abs(result) > Math.PI) {
            result = -Math.signum(result) * (MathUtils.TWO_PI - Math.abs(result));
        }
        if (result < min) {
            result = min;
        } else if (result > max) {
            result = max;
        }
        return result;
    }

    private void drawBase() {
        final FloatBuffer buf = base.getMeshData().getVertexBuffer();
        buf.rewind();
        final FloatBuffer cbuf = base.getMeshData().getColorBuffer();
        cbuf.rewind();
        final FloatBuffer lbuf = baseTicks.getMeshData().getVertexBuffer();
        lbuf.rewind();
        final Vector3 p = new Vector3();
        final double r = 5.0;
        final double step = MathUtils.TWO_PI / BASE_DIVISIONS;
        int counter = 0;
        for (double angle = 0; angle < MathUtils.TWO_PI + step / 2.0; angle += step) {
            final double trimedAngle;
            if (angle > MathUtils.TWO_PI) {
                trimedAngle = MathUtils.TWO_PI;
            } else {
                trimedAngle = angle;
            }
            float width = 0.3f;
            final float zoffset = 0f;

            MathUtils.sphericalToCartesianZ(p.set(r, trimedAngle, 0), p);
            buf.put(p.getXf()).put(p.getYf()).put(p.getZf() + zoffset);
            MathUtils.sphericalToCartesianZ(p.set(r + width, trimedAngle, 0), p);
            buf.put(p.getXf()).put(p.getYf()).put(p.getZf() + zoffset);

            final float c = ((counter - 1) / 3) % 2 == 0 ? 0.5f : 1.0f;
            cbuf.put(c).put(c).put(c).put(c);
            cbuf.put(c).put(c).put(c).put(c);

            if (MathUtils.TWO_PI - trimedAngle > MathUtils.ZERO_TOLERANCE) {
                width = counter % 3 == 0 ? 0.5f : 0.3f;
                MathUtils.sphericalToCartesianZ(p.set(r, trimedAngle, 0), p);
                lbuf.put(p.getXf()).put(p.getYf()).put(p.getZf() + zoffset);
                MathUtils.sphericalToCartesianZ(p.set(r + width, trimedAngle, 0), p);
                lbuf.put(p.getXf()).put(p.getYf()).put(p.getZf() + zoffset);
            }
            counter++;
        }
        base.updateModelBound();
        baseTicks.updateModelBound();
    }

    private void drawSunRegion() {
        final FloatBuffer buf = sunRegion.getMeshData().getVertexBuffer();
        buf.limit(buf.capacity());
        buf.rewind();
        final double declinationStep = 2.0 * TILT_ANGLE / DECLINATION_DIVISIONS;
        final double hourStep = MathUtils.TWO_PI / HOUR_DIVISIONS;
        int limit = 0;
        for (double declinationAngle = -TILT_ANGLE; declinationAngle < TILT_ANGLE - declinationStep / 2.0; declinationAngle += declinationStep) {
            for (double hourAngle = -Math.PI; hourAngle < Math.PI - hourStep / 2.0; hourAngle += hourStep) {
                double hourAngle2 = hourAngle + hourStep;
                double declinationAngle2 = declinationAngle + declinationStep;
                if (hourAngle2 > Math.PI) {
                    hourAngle2 = Math.PI;
                }
                if (declinationAngle2 > TILT_ANGLE) {
                    declinationAngle2 = TILT_ANGLE;
                }
                final Vector3 v1 = computeSunLocation(hourAngle, declinationAngle, latitude);
                final Vector3 v2 = computeSunLocation(hourAngle2, declinationAngle, latitude);
                final Vector3 v3 = computeSunLocation(hourAngle2, declinationAngle2, latitude);
                final Vector3 v4 = computeSunLocation(hourAngle, declinationAngle2, latitude);
                if (v1.getZ() >= 0 || v2.getZ() >= 0 || v3.getZ() >= 0 || v4.getZ() >= 0) {
                    buf.put(v1.getXf()).put(v1.getYf()).put(v1.getZf())
                            .put(v2.getXf()).put(v2.getYf()).put(v2.getZf())
                            .put(v3.getXf()).put(v3.getYf()).put(v3.getZf())
                            .put(v4.getXf()).put(v4.getYf()).put(v4.getZf());
                    limit += 12;
                }
            }
        }
        buf.limit(limit);
        sunRegion.getMeshData().updateVertexCount();
        sunRegion.updateModelBound();
        sunRegion.updateGeometricState(0);
        dirtySunRegion = false;
    }

    private void drawSunPath() {
        final FloatBuffer buf = sunPath.getMeshData().getVertexBuffer();
        buf.limit(buf.capacity());
        buf.rewind();
        final double step = MathUtils.TWO_PI / HOUR_DIVISIONS;
        int limit = 0;
        for (double hourAngle = -Math.PI; hourAngle < Math.PI + step / 2.0; hourAngle += step) {
            final Vector3 v = computeSunLocation(hourAngle, declinationAngle, latitude);
            if (v.getZ() > -0.3) {
                buf.put(v.getXf()).put(v.getYf()).put(v.getZf());
                limit += 3;
            }
        }
        buf.limit(limit);
        sunPath.updateModelBound();
        sunPath.getSceneHints().setCullHint(limit == 0 ? CullHint.Always : CullHint.Inherit);
        dirtySunPath = false;
    }

    private void showSunTriangle(final boolean b) {
        sunTriangle.setVisible(b);
        for (final Spatial s : angles.getChildren()) {
            final AngleAnnotation a = (AngleAnnotation) s;
            a.mesh.setVisible(b);
            a.label.setVisible(b);
        }
    }

    public void drawSunTriangle() {
        if (isNightTime() || ((SceneManager.getInstance() != null
                && !Scene.getInstance().isZenithAngleVisible() && !Scene.getInstance().isElevationAngleVisible() && !Scene.getInstance().isAzimuthAngleVisible()))) {
            showSunTriangle(false);
            return;
        }
        showSunTriangle(true);
        final FloatBuffer buf = sunTriangle.getMeshData().getVertexBuffer();
        buf.rewind();
        final Vector3 o = new Vector3();
        buf.put(o.getXf()).put(o.getYf()).put(o.getZf());
        final ReadOnlyVector3 s = getSunLocation();
        buf.put(s.getXf()).put(s.getYf()).put(s.getZf());
        buf.put(s.getXf()).put(s.getYf()).put(s.getZf());
        final Vector3 t = new Vector3(s.getX(), s.getY(), 0);
        buf.put(t.getXf()).put(t.getYf()).put(t.getZf());
        buf.put(t.getXf()).put(t.getYf()).put(t.getZf());
        buf.put(o.getXf()).put(o.getYf()).put(o.getZf());
        sunTriangle.updateModelBound();
        sunTriangle.getSceneHints().setCullHint(CullHint.Inherit);
        AngleAnnotation a = (AngleAnnotation) angles.getChild(0); // draw zenith angle
        if (SceneManager.getInstance() != null) {
            a.setVisible(Scene.getInstance().isZenithAngleVisible());
        }
        final Vector3 n = s.cross(Vector3.UNIT_Z, null);
        final Vector3 p = s.normalize(null);
        a.setRange(o, p, Vector3.UNIT_Z, n);
        a = (AngleAnnotation) angles.getChild(1); // draw elevation angle
        if (SceneManager.getInstance() != null) {
            a.setVisible(Scene.getInstance().isElevationAngleVisible());
        }
        a.setRange(o, p, t, n);
        a = (AngleAnnotation) angles.getChild(2); // draw azimuth angle
        a.setRange(o, Vector3.UNIT_Y, t, Vector3.UNIT_Z);
        if (SceneManager.getInstance() != null) {
            a.setVisible(Scene.getInstance().isAzimuthAngleVisible());
        }
    }

    public void drawSun() {
        final Vector3 sunLocation = computeSunLocation(hourAngle, declinationAngle, latitude);
        setSunLocation(sunLocation);
    }

    private void setSunLocation(final ReadOnlyVector3 sunLocation) {
        sun.setTranslation(sunLocation);
        final boolean enabled = !isNightTime();
        if (enabled) {
            light.setDirection(sunLocation.negate(null));
        } else {
            light.setDirection(Vector3.ZERO);
        }
        if (bloomRenderPass != null) {
            bloomRenderPass.setEnabled(enabled);
        }
    }

    public Vector3 computeSunLocation(final Calendar calendar) {
        return computeSunLocation(computeHourAngle(calendar), computeDeclinationAngle(calendar), latitude);
    }

    private ReadOnlyVector3 getSunLocation() {
        return sun.getTranslation();
    }

    public boolean isNightTime() {
        return sun.getTranslation().getZ() < 0;
    }

    private void draw() {
        drawBase();
        drawSunRegion();
        drawSunPath();
        drawSun();
        drawSunTriangle();
    }

    public void updateBloom() {
        if (bloomRenderPass != null) {
            bloomRenderPass.markNeedsRefresh();
        }
    }

    private BloomRenderPass getBloomRenderPass() {
        if (bloomRenderPass == null) {
            bloomRenderPass = new BloomRenderPass(SceneManager.getInstance().getCamera(), 4);
            // bloomRenderPass.setBlurIntensityMultiplier(0.8f);
            passManager.add(bloomRenderPass);
        }
        return bloomRenderPass;
    }

    public void setTime(final Date time) {
        final Calendar timeCalendar = Calendar.getInstance();
        timeCalendar.setTime(time);
        calendar.set(Calendar.HOUR_OF_DAY, timeCalendar.get(Calendar.HOUR_OF_DAY));
        calendar.set(Calendar.MINUTE, timeCalendar.get(Calendar.MINUTE));
        setHourAngle(computeHourAngle(calendar), true, false, false);
    }

    private double computeHourAngle(final Calendar calendar) {
        final int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE) - 720;
        return minutes / 720.0 * Math.PI;
    }

    public void setDate(final Date date) {
        final Calendar dateCalendar = Calendar.getInstance();
        final int year = dateCalendar.get(Calendar.YEAR); // keep the current year in case the year of date is wrong (1970)
        dateCalendar.setTime(date);
        calendar.set(year, dateCalendar.get(Calendar.MONTH), dateCalendar.get(Calendar.DAY_OF_MONTH));
        setDeclinationAngle(computeDeclinationAngle(calendar), true, false);
        dirtySunPath = true;
        if (SceneManager.getInstance() != null) {
            SceneManager.getInstance().refresh();
        }
    }

    private double computeDeclinationAngle(final Calendar calendar) {
        return TILT_ANGLE * MathUtils.sin(MathUtils.TWO_PI * (284.0 + calendar.get(Calendar.DAY_OF_YEAR)) / 365.25);
    }

    public void update() {
        if (dirtySunRegion) {
            drawSunRegion();
        }
        if (dirtySunPath) {
            drawSunPath();
            drawSun();
            drawSunTriangle();
        }
    }

    private void setSunRegionAlwaysVisible(final boolean forceSunRegionOn) {
        this.forceSunRegionOn = forceSunRegionOn;
        if (forceSunRegionOn) {
            sunRegion.getSceneHints().setCullHint(CullHint.Inherit);
        } else {
            sunRegion.getSceneHints().setCullHint(CullHint.Always);
        }
    }

    public Calendar getCalendar() {
        return calendar;
    }

}