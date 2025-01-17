package org.concord.energy3d.model;

import java.nio.FloatBuffer;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.util.FontManager;
import org.concord.energy3d.util.Util;

import com.ardor3d.bounding.OrientedBoundingBox;
import com.ardor3d.intersection.PickData;
import com.ardor3d.intersection.PickResults;
import com.ardor3d.intersection.PickingUtil;
import com.ardor3d.intersection.PrimitivePickResults;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Ray3;
import com.ardor3d.math.Vector3;
import com.ardor3d.math.type.ReadOnlyVector3;
import com.ardor3d.renderer.state.OffsetState;
import com.ardor3d.scenegraph.Line;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.Spatial;
import com.ardor3d.scenegraph.shape.Box;
import com.ardor3d.ui.text.BMText;
import com.ardor3d.ui.text.BMText.Align;
import com.ardor3d.ui.text.BMText.Justify;
import com.ardor3d.util.geom.BufferUtils;

public class Sensor extends HousePart implements SolarCollector, Labelable {

    private static final long serialVersionUID = 1L;

    public static final double WIDTH = 0.15;
    public static final double HEIGHT = 0.1;
    private transient ReadOnlyVector3 normal;
    private transient Mesh outlineMesh;
    private transient Box surround;
    private transient BMText label;
    private boolean lightOff;
    private boolean heatFluxOff;
    private boolean labelLightOutput;
    private boolean labelHeatFluxOutput;

    public Sensor() {
        super(1, 1, 0.0);
    }

    @Override
    protected void init() {
        super.init();

        mesh = new Mesh("Sensor");
        mesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(6));
        mesh.getMeshData().setTextureBuffer(BufferUtils.createVector2Buffer(6), 0);
        mesh.setModelBound(new OrientedBoundingBox());
        mesh.setUserData(new UserData(this));
        root.attachChild(mesh);

        surround = new Box("Sensor (Surround)");
        surround.setModelBound(new OrientedBoundingBox());
        final OffsetState offset = new OffsetState();
        offset.setFactor(1);
        offset.setUnits(1);
        surround.setRenderState(offset);
        root.attachChild(surround);

        outlineMesh = new Line("Sensor (Outline)");
        outlineMesh.getMeshData().setVertexBuffer(BufferUtils.createVector3Buffer(8));
        outlineMesh.setDefaultColor(ColorRGBA.BLACK);
        outlineMesh.setModelBound(new OrientedBoundingBox());
        root.attachChild(outlineMesh);

        label = new BMText("" + getId(), "0", FontManager.getInstance().getPartNumberFont(), Align.Center, Justify.Center);
        Util.initHousePartLabel(label);
        label.setFontScale(0.6);
        label.setVisible(true);
        root.attachChild(label);

        updateTextureAndColor();

    }

    @Override
    public void setPreviewPoint(final int x, final int y) {
        final PickedHousePart picked = pickContainer(x, y, new Class<?>[]{Roof.class, Wall.class, Foundation.class});
        if (picked != null && picked.getUserData() != null) { // when the user data is null, it picks the land
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
    protected void drawMesh() {
        if (container == null) {
            return;
        }

        if (container instanceof Roof) {
            final PickResults pickResults = new PrimitivePickResults();
            final Ray3 ray = new Ray3(getAbsPoint(0).addLocal(0, 0, 1000), Vector3.NEG_UNIT_Z);
            PickingUtil.findPick(container.getRoot(), ray, pickResults, false);
            if (pickResults.getNumber() != 0) {
                final PickData pickData = pickResults.getPickData(0);
                final Vector3 p = pickData.getIntersectionRecord().getIntersectionPoint(0);
                points.get(0).setZ(p.getZ());
                final UserData userData = (UserData) ((Spatial) pickData.getTarget()).getUserData();
                final int roofPartIndex = userData.getEditPointIndex();
                normal = (ReadOnlyVector3) ((Roof) container).getRoofPartsRoot().getChild(roofPartIndex).getUserData();
            }
        } else {
            normal = container.getNormal();
        }
        updateEditShapes();

        final double sceneScale = Scene.getInstance().getScale();
        surround.setData(Vector3.ZERO, WIDTH / 2.0 / sceneScale, HEIGHT / 2.0 / sceneScale, 0.02); // last arg sets close to zero so the sensor doesn't cast shadow
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

        mesh.setTranslation(getAbsPoint(0));
        if (normal != null) { // FIXME: Sometimes normal is null
            if (Util.isEqual(normal, Vector3.UNIT_Z)) {
                mesh.setRotation(new Matrix3());
            } else {
                mesh.setRotation(new Matrix3().lookAt(normal, Vector3.UNIT_Z));
            }
        }

        surround.setTranslation(mesh.getTranslation());
        surround.setRotation(mesh.getRotation());

        outlineMesh.setTranslation(mesh.getTranslation());
        outlineMesh.setRotation(mesh.getRotation());

        updateLabel();

    }

    @Override
    public boolean isDrawable() {
        if (container == null) {
            return true;
        }
        if (mesh.getWorldBound() == null) {
            return true;
        }
        final OrientedBoundingBox bound = (OrientedBoundingBox) mesh.getWorldBound().clone(null);
        bound.setExtent(bound.getExtent().divide(1.1, null));
        for (final HousePart child : container.getChildren()) {
            if (child != this && child instanceof Sensor && bound.intersects(child.mesh.getWorldBound())) {
                return false;
            }
        }
        return true;
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

    @Override
    public void updateTextureAndColor() {
        updateTextureAndColor(mesh, null);
    }

    @Override
    protected String getTextureFileName() {
        return "sensor.png";
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
        return WIDTH / Scene.getInstance().getScale();
    }

    @Override
    protected void computeArea() {
        area = WIDTH * HEIGHT;
    }

    @Override
    public double getArea() { // FIXME: Sometimes computeArea is not called because this sensor might not be drawable, so we temporarily override this method to work around the problem
        return WIDTH * HEIGHT;
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
        return false;
    }

    public void setLightOff(final boolean lightOff) {
        this.lightOff = lightOff;
    }

    public boolean isLightOff() {
        return lightOff;
    }

    public void setHeatFluxOff(final boolean heatFluxOff) {
        this.heatFluxOff = heatFluxOff;
    }

    public boolean isHeatFluxOff() {
        return heatFluxOff;
    }

    // Not implemented
    @Override
    public void setPoleHeight(final double poleHeight) {
    }

    // Not implemented
    @Override
    public double getPoleHeight() {
        return 0;
    }

    // Not implemented
    @Override
    public double getYieldNow() {
        return 0;
    }

    // Not implemented
    @Override
    public void setYieldNow(final double yieldNow) {
    }

    // Not implemented
    @Override
    public double getYieldToday() {
        return 0;
    }

    // Not implemented
    @Override
    public void setYieldToday(final double yieldToday) {
    }

    // Not implemented
    @Override
    public void drawSunBeam() {
    }

    // Not implemented
    @Override
    public void setSunBeamVisible(final boolean visible) {
    }

    // Not implemented
    @Override
    public boolean isSunBeamVisible() {
        return false;
    }

    public boolean isLabelVisible() {
        return label.isVisible();
    }

    public String getLabelText() {
        return label.getText();
    }

    @Override
    public void updateLabel() {
        String text = "";
        if (labelCustom && labelCustomText != null) {
            text += labelCustomText;
        }
        if (labelId) {
            text += (text.equals("") ? "" : "\n") + "#" + id;
        }
        if (labelLightOutput) {
            double totalLightToday = getSolarPotentialToday() / getArea();
            text += (text.equals("") ? "" : "\n") + (Util.isZero(totalLightToday) ? "Light" : EnergyPanel.ONE_DECIMAL.format(totalLightToday) + " kWh/m^2");
        }
        if (labelHeatFluxOutput) {
            double totalHeatFluxToday = -getTotalHeatLoss() / getArea();
            text += (text.equals("") ? "" : "\n") + (Util.isZero(totalHeatFluxToday) ? "Heat Flux" : EnergyPanel.ONE_DECIMAL.format(totalHeatFluxToday) + " kWh/m^2");
        }
        if (!text.equals("")) {
            label.setText(text);
            label.setVisible(true);
            final ReadOnlyVector3 translation = mesh.getTranslation();
            if (normal != null) {
                final double labelOffset = 1.0;
                label.setTranslation(translation.getX() + labelOffset * normal.getX(), translation.getY() + labelOffset * normal.getY(), translation.getZ() + labelOffset * normal.getZ());
            }
        } else {
            label.setVisible(false);
        }
    }

    @Override
    public void clearLabels() {
        super.clearLabels();
        labelLightOutput = false;
        labelHeatFluxOutput = false;
    }

    public void setLabelLightOutput(final boolean labelLightOutput) {
        this.labelLightOutput = labelLightOutput;
    }

    public boolean getLabelLightOutput() {
        return labelLightOutput;
    }

    public void setLabelHeatFluxOutput(final boolean labelHeatFluxOutput) {
        this.labelHeatFluxOutput = labelHeatFluxOutput;
    }

    public boolean getLabelHeatFluxOutput() {
        return labelHeatFluxOutput;
    }

}