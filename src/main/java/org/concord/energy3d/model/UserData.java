package org.concord.energy3d.model;

import org.concord.energy3d.util.Util;

import com.ardor3d.math.Vector3;

public class UserData {
	private final HousePart housePart;
	private final int index;
	private final boolean isEditPoint; 
	private Vector3 printCenter = new Vector3();
	
	public UserData(final HousePart housePart) {
		this(housePart, -1, false);
	}
	
	public UserData(final HousePart housePart, final int index, final boolean isEditPoint) {
		this.housePart = housePart;
		this.index = index;
		this.isEditPoint = isEditPoint;
	}

	public HousePart getHousePart() {
		return housePart;
	}

	public int getIndex() {
		return index;
	}

	public boolean isEditPoint() {
		return isEditPoint;
	}

	public void setPrintCenter(final Vector3 printCenter) {
		this.printCenter = printCenter;
	}
	
	public Vector3 getPrintCenter() {
		return printCenter;
	}

	@Override
	public String toString() {
		return housePart + " index = " + index + " PrintCenter = " + Util.toString(printCenter);
	}
}