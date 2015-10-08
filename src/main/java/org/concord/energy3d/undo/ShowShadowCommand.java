package org.concord.energy3d.undo;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.gui.MainPanel;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.util.Util;

public class ShowShadowCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private boolean oldValue, newValue;

	public ShowShadowCommand() {
		oldValue = SceneManager.getInstance().isShadowEnabled();
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		newValue = SceneManager.getInstance().isShadowEnabled();
		SceneManager.getInstance().setShadow(oldValue);
		Util.selectSilently(MainFrame.getInstance().getShadowMenuItem(), oldValue);
		Util.selectSilently(MainPanel.getInstance().getShadowButton(), oldValue);
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		SceneManager.getInstance().setShadow(newValue);
		Util.selectSilently(MainFrame.getInstance().getShadowMenuItem(), newValue);
		Util.selectSilently(MainPanel.getInstance().getShadowButton(), newValue);
	}

	@Override
	public String getPresentationName() {
		return "Show Shadow";
	}

}