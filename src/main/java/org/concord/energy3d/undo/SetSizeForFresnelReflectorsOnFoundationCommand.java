package org.concord.energy3d.undo;

import java.util.List;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.FresnelReflector;
import org.concord.energy3d.scene.SceneManager;

public class SetSizeForFresnelReflectorsOnFoundationCommand extends MyAbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final double[] oldLengths;
	private double[] newLengths;
	private final double[] oldWidths;
	private double[] newWidths;
	private final double[] oldModuleLengths;
	private double[] newModuleLengths;
	private final Foundation foundation;
	private final List<FresnelReflector> reflectors;

	public SetSizeForFresnelReflectorsOnFoundationCommand(final Foundation foundation) {
		this.foundation = foundation;
		reflectors = foundation.getFresnelReflectors();
		final int n = reflectors.size();
		oldLengths = new double[n];
		oldWidths = new double[n];
		oldModuleLengths = new double[n];
		for (int i = 0; i < n; i++) {
			final FresnelReflector r = reflectors.get(i);
			oldLengths[i] = r.getLength();
			oldWidths[i] = r.getModuleWidth();
			oldModuleLengths[i] = r.getModuleLength();
		}
	}

	public Foundation getFoundation() {
		return foundation;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		final int n = reflectors.size();
		newLengths = new double[n];
		newWidths = new double[n];
		newModuleLengths = new double[n];
		for (int i = 0; i < n; i++) {
			final FresnelReflector r = reflectors.get(i);
			newLengths[i] = r.getLength();
			newWidths[i] = r.getModuleWidth();
			newModuleLengths[i] = r.getModuleLength();
			r.setLength(oldLengths[i]);
			r.setModuleWidth(oldWidths[i]);
			r.setModuleLength(oldModuleLengths[i]);
			r.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		final int n = reflectors.size();
		for (int i = 0; i < n; i++) {
			final FresnelReflector r = reflectors.get(i);
			r.setLength(newLengths[i]);
			r.setModuleWidth(newWidths[i]);
			r.setModuleLength(newModuleLengths[i]);
			r.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public String getPresentationName() {
		return "Set Size for All Fresnel Reflectors on Selected Foundation";
	}

}
