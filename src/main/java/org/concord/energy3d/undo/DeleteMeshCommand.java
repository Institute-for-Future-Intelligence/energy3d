package org.concord.energy3d.undo;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.NodeState;
import org.concord.energy3d.model.UserData;

import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.Node;

public class DeleteMeshCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final Mesh mesh;
	private final Node parent;
	private final Foundation foundation;

	public DeleteMeshCommand(final Mesh mesh, final Foundation foundation) {
		this.mesh = mesh;
		this.foundation = foundation;
		parent = mesh.getParent();
	}

	public Mesh getMesh() {
		return mesh;
	}

	public Foundation getFoundation() {
		return foundation;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		parent.attachChild(mesh);
		foundation.draw();
		final int meshIndex = ((UserData) mesh.getUserData()).getMeshIndex();
		final NodeState ns = foundation.getNodeState(parent);
		ns.getDeletedMeshes().remove(Integer.valueOf(meshIndex));
		EnergyPanel.getInstance().clearRadiationHeatMap();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		parent.detachChild(mesh);
		foundation.draw();
		foundation.getNodeState(parent).deleteMesh(((UserData) mesh.getUserData()).getMeshIndex());
		EnergyPanel.getInstance().clearRadiationHeatMap();
	}

	@Override
	public String getPresentationName() {
		return "Delete Mesh";
	}

}
