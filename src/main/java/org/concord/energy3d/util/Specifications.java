package org.concord.energy3d.util;

import org.concord.energy3d.gui.EnergyPanel;

/**
 * This class defines the design specifications.
 * 
 * @author Charles Xie
 * 
 */
public class Specifications {

	private static Specifications instance = new Specifications();

	private int maximumBudget = 100000;
	private double minimumHeight = 8;
	private double maximumHeight = 10;
	private double minimumArea = 100;
	private double maximumArea = 150;

	private Specifications() {
	}

	public static Specifications getInstance() {
		return instance;
	}

	public void setMaximumBudget(int maximumBudget) {
		this.maximumBudget = maximumBudget;
		EnergyPanel.getInstance().updateCostBar();
	}

	public int getMaximumBudget() {
		return maximumBudget;
	}

	public void setMaximumHeight(double maximumHeight) {
		this.maximumHeight = maximumHeight;
		EnergyPanel.getInstance().updateHeightBar();
	}

	public double getMaximumHeight() {
		return maximumHeight;
	}

	public void setMinimumHeight(double minimumHeight) {
		this.minimumHeight = minimumHeight;
		EnergyPanel.getInstance().updateHeightBar();
	}

	public double getMinimumHeight() {
		return minimumHeight;
	}

	public void setMaximumArea(double maximumArea) {
		this.maximumArea = maximumArea;
		EnergyPanel.getInstance().updateAreaBar();
	}

	public double getMaximumArea() {
		return maximumArea;
	}

	public void setMinimumArea(double minimumArea) {
		this.minimumArea = minimumArea;
		EnergyPanel.getInstance().updateAreaBar();
	}

	public double getMinimumArea() {
		return minimumArea;
	}

}
