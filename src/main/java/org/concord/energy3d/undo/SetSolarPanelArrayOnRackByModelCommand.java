package org.concord.energy3d.undo;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.Rack;
import org.concord.energy3d.model.SolarPanel;
import org.concord.energy3d.simulation.PvModuleSpecs;

public class SetSolarPanelArrayOnRackByModelCommand extends MyAbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final PvModuleSpecs oldModel;
	private final double oldRackWidth, oldRackHeight;
	private PvModuleSpecs newModel;
	private double newRackWidth, newRackHeight;
	private final Rack rack;

	public SetSolarPanelArrayOnRackByModelCommand(final Rack rack) {
		this.rack = rack;
		oldModel = rack.getSolarPanel().getPvModuleSpecs();
		oldRackWidth = rack.getRackWidth();
		oldRackHeight = rack.getRackHeight();
	}

	public Rack getRack() {
		return rack;
	}

	public PvModuleSpecs getOldModel() {
		return oldModel;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		final SolarPanel s = rack.getSolarPanel();
		newModel = s.getPvModuleSpecs();
		newRackWidth = rack.getRackWidth();
		newRackHeight = rack.getRackHeight();
		s.setPvModuleSpecs(oldModel);
		rack.setRackWidth(oldRackWidth);
		rack.setRackHeight(oldRackHeight);
		rack.ensureFullSolarPanels(false);
		rack.draw();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		final SolarPanel s = rack.getSolarPanel();
		s.setPvModuleSpecs(newModel);
		rack.setRackWidth(newRackWidth);
		rack.setRackHeight(newRackHeight);
		rack.ensureFullSolarPanels(false);
		rack.draw();
	}

	@Override
	public String getPresentationName() {
		return "Set Solar Panel Array on Rack by Model";
	}

}
