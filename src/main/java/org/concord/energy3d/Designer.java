package org.concord.energy3d;

import java.io.Serializable;

/**
 * @author Charles Xie
 *
 */
public class Designer implements Serializable {

	private static final long serialVersionUID = 1L;

	private String name;
	private String organization;

	public Designer() {
		name = "User";
	}

	public void setName(final String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setOrganization(final String organization) {
		this.organization = organization;
	}

	public String getOrganization() {
		return organization;
	}

}