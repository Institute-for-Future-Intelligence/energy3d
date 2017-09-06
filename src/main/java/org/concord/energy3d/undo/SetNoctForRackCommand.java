package org.concord.energy3d.undo;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.Rack;

public class SetNoctForRackCommand extends MyAbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final double oldValue;
	private double newValue;
	private final Rack rack;

	public SetNoctForRackCommand(final Rack rack) {
		this.rack = rack;
		oldValue = rack.getSolarPanel().getNominalOperatingCellTemperature();
	}

	public Rack getRack() {
		return rack;
	}

	public double getOldValue() {
		return oldValue;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		newValue = rack.getSolarPanel().getNominalOperatingCellTemperature();
		rack.getSolarPanel().setNominalOperatingCellTemperature(oldValue);
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		rack.getSolarPanel().setNominalOperatingCellTemperature(newValue);
	}

	@Override
	public String getPresentationName() {
		return "Set Nominal Operating Cell Temperature for Selected Rack";
	}

}
