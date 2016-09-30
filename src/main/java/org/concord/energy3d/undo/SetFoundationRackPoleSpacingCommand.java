package org.concord.energy3d.undo;

import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.Rack;
import org.concord.energy3d.scene.SceneManager;

public class SetFoundationRackPoleSpacingCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final double[] oldDxs;
	private double[] newDxs;
	private final double[] oldDys;
	private double[] newDys;
	private final Foundation foundation;
	private final List<Rack> racks;

	public SetFoundationRackPoleSpacingCommand(final Foundation foundation) {
		this.foundation = foundation;
		racks = foundation.getRacks();
		final int n = racks.size();
		oldDxs = new double[n];
		oldDys = new double[n];
		for (int i = 0; i < n; i++) {
			final Rack r = racks.get(i);
			oldDxs[i] = r.getPoleDistanceX();
			oldDys[i] = r.getPoleDistanceY();
		}
	}

	public Foundation getFoundation() {
		return foundation;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		final int n = racks.size();
		newDxs = new double[n];
		newDys = new double[n];
		for (int i = 0; i < n; i++) {
			final Rack r = racks.get(i);
			newDxs[i] = r.getPoleDistanceX();
			newDys[i] = r.getPoleDistanceY();
			r.setPoleDistanceX(oldDxs[i]);
			r.setPoleDistanceY(oldDys[i]);
			r.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		final int n = racks.size();
		for (int i = 0; i < n; i++) {
			final Rack r = racks.get(i);
			r.setPoleDistanceX(newDxs[i]);
			r.setPoleDistanceY(newDys[i]);
			r.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public String getPresentationName() {
		return "Set Pole Spacing for All Racks on Selected Foundation";
	}

}