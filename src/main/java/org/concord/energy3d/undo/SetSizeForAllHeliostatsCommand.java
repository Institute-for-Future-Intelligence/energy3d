package org.concord.energy3d.undo;

import java.util.List;

import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.Mirror;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;

public class SetSizeForAllHeliostatsCommand extends MyAbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private final double[] oldWidths;
	private double[] newWidths;
	private final double[] oldHeights;
	private double[] newHeights;
	private final List<Mirror> mirrors;

	public SetSizeForAllHeliostatsCommand() {
		mirrors = Scene.getInstance().getAllHeliostats();
		final int n = mirrors.size();
		oldWidths = new double[n];
		oldHeights = new double[n];
		for (int i = 0; i < n; i++) {
			final Mirror m = mirrors.get(i);
			oldWidths[i] = m.getApertureWidth();
			oldHeights[i] = m.getApertureHeight();
		}
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		final int n = mirrors.size();
		newWidths = new double[n];
		newHeights = new double[n];
		for (int i = 0; i < n; i++) {
			final Mirror m = mirrors.get(i);
			newWidths[i] = m.getApertureWidth();
			m.setApertureWidth(oldWidths[i]);
			newHeights[i] = m.getApertureHeight();
			m.seApertureHeight(oldHeights[i]);
			m.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		final int n = mirrors.size();
		for (int i = 0; i < n; i++) {
			final Mirror m = mirrors.get(i);
			m.setApertureWidth(newWidths[i]);
			m.seApertureHeight(newHeights[i]);
			m.draw();
		}
		SceneManager.getInstance().refresh();
	}

	@Override
	public String getPresentationName() {
		return "Set Size for All Mirrors";
	}

}
