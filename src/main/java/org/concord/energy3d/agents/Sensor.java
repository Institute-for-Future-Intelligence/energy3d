package org.concord.energy3d.agents;

/**
 * @author Charles Xie
 *
 */
public interface Sensor {

	public void sense(MyEvent e);

	public String getName();

}
