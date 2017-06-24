package org.concord.energy3d.undo;

import java.util.List;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.ParabolicTrough;
import org.concord.energy3d.scene.SceneManager;

public class SetSizeForParabolicTroughsOnFoundationCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final double[] oldWidths;
	private double[] newWidths;
	private final double[] oldHeights;
	private double[] newHeights;
	private final Foundation foundation;
	private final List<ParabolicTrough> troughs;

	public SetSizeForParabolicTroughsOnFoundationCommand(final Foundation foundation) {
		this.foundation = foundation;
		troughs = foundation.getParabolicTroughs();
		final int n = troughs.size();
		oldWidths = new double[n];
		oldHeights = new double[n];
		for (int i = 0; i < n; i++) {
			final ParabolicTrough t = troughs.get(i);
			oldWidths[i] = t.getTroughWidth();
			oldHeights[i] = t.getTroughHeight();
		}
	}

	public Foundation getFoundation() {
		return foundation;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		final int n = troughs.size();
		newWidths = new double[n];
		newHeights = new double[n];
		for (int i = 0; i < n; i++) {
			final ParabolicTrough t = troughs.get(i);
			newWidths[i] = t.getTroughWidth();
			newHeights[i] = t.getTroughHeight();
			t.setTroughWidth(oldWidths[i]);
			t.setTroughHeight(oldHeights[i]);
			t.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		final int n = troughs.size();
		for (int i = 0; i < n; i++) {
			final ParabolicTrough t = troughs.get(i);
			t.setTroughWidth(newWidths[i]);
			t.setTroughHeight(newHeights[i]);
			t.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public String getPresentationName() {
		return "Set Size for All Parabolic Troughs on Selected Foundation";
	}

}
