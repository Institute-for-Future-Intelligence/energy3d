package org.concord.energy3d.simulation;

import java.io.Serializable;
import java.util.Arrays;

/**
 * @author Charles Xie
 */

public class Thermostat implements Serializable {

    private static final long serialVersionUID = 1L;

    private int[][][] temperatures;

    public Thermostat() {
        init();
    }

    private void init() {
        temperatures = new int[12][7][25]; // the last one of 25 is the default temperature of the whole day
        for (int i = 0; i < 7; i++) {
            Arrays.fill(temperatures[0][i], 20);
            Arrays.fill(temperatures[1][i], 20);
            Arrays.fill(temperatures[2][i], 20);
            Arrays.fill(temperatures[3][i], 21);
            Arrays.fill(temperatures[4][i], 21);
            Arrays.fill(temperatures[5][i], 22);
            Arrays.fill(temperatures[6][i], 22);
            Arrays.fill(temperatures[7][i], 22);
            Arrays.fill(temperatures[8][i], 21);
            Arrays.fill(temperatures[9][i], 21);
            Arrays.fill(temperatures[10][i], 20);
            Arrays.fill(temperatures[11][i], 20);
        }
    }

    public int[][][] getTemperatures() {
        return temperatures;
    }

    public void setTemperatures(int[][][] values) {
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 7; j++) {
                for (int k = 0; k < 25; k++) {
                    temperatures[i][j][k] = values[i][j][k];
                }
            }
        }
    }

    /**
     * monthOfYear, dayOfWeek, and hourOfDay all starts from zero.
     */
    public void setTemperature(int monthOfYear, int dayOfWeek, int hourOfDay, int temperature) {
        temperatures[monthOfYear][dayOfWeek][hourOfDay] = temperature;
    }

    /**
     * monthOfYear, dayOfWeek, and hourOfDay all starts from zero.
     */
    public int getTemperature(int monthOfYear, int dayOfWeek, int hourOfDay) {
        if (temperatures == null) // backward compatibility with object serialization
            init();
        return temperatures[monthOfYear][dayOfWeek][hourOfDay];
    }

}