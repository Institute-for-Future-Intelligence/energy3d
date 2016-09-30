package org.concord.energy3d.undo;

import javax.swing.undo.AbstractUndoableEdit;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;

import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Mirror;
import org.concord.energy3d.model.Rack;

public class SetPartSizeCommand extends AbstractUndoableEdit {

	private static final long serialVersionUID = 1L;
	private double oldWidth, newWidth;
	private double oldHeight, newHeight;
	private final HousePart part;

	public SetPartSizeCommand(final HousePart part) {
		this.part = part;
		if (part instanceof Mirror) {
			final Mirror m = (Mirror) part;
			oldWidth = m.getMirrorWidth();
			oldHeight = m.getMirrorHeight();
		} else if (part instanceof Rack) {
			final Rack r = (Rack) part;
			oldWidth = r.getRackWidth();
			oldHeight = r.getRackHeight();
		}
	}

	public HousePart getPart() {
		return part;
	}

	public double getOldWidth() {
		return oldWidth;
	}

	public double getOldHeight() {
		return oldHeight;
	}

	@Override
	public void undo() throws CannotUndoException {
		super.undo();
		if (part instanceof Mirror) {
			final Mirror m = (Mirror) part;
			newWidth = m.getMirrorWidth();
			newHeight = m.getMirrorHeight();
			m.setMirrorWidth(oldWidth);
			m.setMirrorHeight(oldHeight);
		} else if (part instanceof Rack) {
			final Rack r = (Rack) part;
			newWidth = r.getRackWidth();
			newHeight = r.getRackHeight();
			r.setRackWidth(oldWidth);
			r.setRackHeight(oldHeight);
		}
		part.draw();
	}

	@Override
	public void redo() throws CannotRedoException {
		super.redo();
		if (part instanceof Mirror) {
			final Mirror m = (Mirror) part;
			m.setMirrorWidth(newWidth);
			m.setMirrorHeight(newHeight);
		} else if (part instanceof Rack) {
			final Rack r = (Rack) part;
			r.setRackWidth(newWidth);
			r.setRackHeight(newHeight);
		}
		part.draw();
	}

	@Override
	public String getPresentationName() {
		if (part instanceof Mirror) {
			return "Set Size for Selected Mirror";
		}
		if (part instanceof Rack) {
			return "Set Size for Selected Rack";
		}
		return "Set Size";
	}

}