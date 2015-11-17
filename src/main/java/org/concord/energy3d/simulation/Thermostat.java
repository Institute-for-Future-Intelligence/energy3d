package org.concord.energy3d.simulation;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Calendar;

/**
 * @author Charles Xie
 *
 */

public class Thermostat implements Serializable {

	private static final long serialVersionUID = 1L;

	private int[] monthlyTemperatures;

	public Thermostat() {
		monthlyTemperatures = new int[12];
		Arrays.fill(monthlyTemperatures, 20);
	}

	public void setTemperature(int month, int temperature) {
		if (month < Calendar.JANUARY || month > Calendar.DECEMBER)
			return;
		monthlyTemperatures[month] = temperature;
	}

	public int getTemperature(int month) {
		if (month < Calendar.JANUARY || month > Calendar.DECEMBER)
			return 20;
		return monthlyTemperatures[month];
	}

	public void setMonthlyTemperature(int[] monthlyTemperature) {
		this.monthlyTemperatures = monthlyTemperature;
	}

	public int[] getMonthlyTemperature() {
		return monthlyTemperatures;
	}

}