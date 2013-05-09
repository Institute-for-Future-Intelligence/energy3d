package org.concord.energy3d.undo;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.scene.Scene;

@SuppressWarnings("serial")
public class AddHousePartCommand extends AbstractUndoableEdit {
	private final HousePart housePart;
	
	public AddHousePartCommand(final HousePart housePart) {
		this.housePart = housePart;
	}
	
	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		Scene.getInstance().remove(housePart);
	}
	
	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		Scene.getInstance().add(housePart);
	}
	
	@Override
	public String getPresentationName() {
		return "Add " + housePart.getClass().getSimpleName();
	}
}