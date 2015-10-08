package org.concord.energy3d.undo;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.gui.MainPanel;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.util.Util;

public class ShowAnnotationCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private boolean oldValue, newValue;

	public ShowAnnotationCommand() {
		oldValue = Scene.getInstance().areAnnotationsVisible();
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		newValue = Scene.getInstance().areAnnotationsVisible();
		Scene.getInstance().setAnnotationsVisible(oldValue);
		Util.selectSilently(MainPanel.getInstance().getAnnotationToggleButton(), oldValue);
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		Scene.getInstance().setAnnotationsVisible(newValue);
		Util.selectSilently(MainPanel.getInstance().getAnnotationToggleButton(), newValue);
	}

	@Override
	public String getPresentationName() {
		return "Show Annotation";
	}

}