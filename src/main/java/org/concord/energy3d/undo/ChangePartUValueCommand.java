package org.concord.energy3d.undo;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Thermalizable;

public class ChangePartUValueCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private double oldValue, newValue;
	private HousePart part;

	public ChangePartUValueCommand(HousePart part) {
		this.part = part;
		if (part instanceof Thermalizable)
			oldValue = ((Thermalizable) part).getUValue();
	}

	public double getOldValue() {
		return oldValue;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		if (part instanceof Thermalizable) {
			newValue = ((Thermalizable) part).getUValue();
			((Thermalizable) part).setUValue(oldValue);
		}
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		if (part instanceof Thermalizable)
			((Thermalizable) part).setUValue(newValue);
	}

	public HousePart getPart() {
		return part;
	}

	@Override
	public String getPresentationName() {
		return "U-Value Change for Selected Part";
	}

}
