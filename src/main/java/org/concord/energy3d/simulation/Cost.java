package org.concord.energy3d.simulation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.model.Door;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Human;
import org.concord.energy3d.model.Roof;
import org.concord.energy3d.model.SolarPanel;
import org.concord.energy3d.model.Tree;
import org.concord.energy3d.model.Wall;
import org.concord.energy3d.model.Window;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;

/**
 * Calculate the cost. The material and installation costs are partly based on http://www.homewyse.com, but should be considered as largely fictitious.
 * 
 * @author Charles Xie
 * 
 */
public class Cost {

	private static Cost instance = new Cost();

	private Cost() {
	}

	public static Cost getInstance() {
		return instance;
	}

	public int getTotalCost() {
		int sum = 0;
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p.isFrozen())
				continue;
			sum += getPartCost(p);
		}
		return sum;
	}

	public int getBuildingCost(final Foundation foundation) {
		if (foundation == null)
			return 0;
		int buildingCount = 0;
		for (final HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Foundation)
				buildingCount++;
		}
		int sum = 0;
		if (buildingCount == 1) {
			for (final HousePart p : Scene.getInstance().getParts()) { // if there is only one building, trees are included in its cost
				if (p.isFrozen() && p instanceof Tree)
					continue;
				sum += getPartCost(p);
			}
		} else {
			sum = getPartCost(foundation);
			for (final HousePart p : Scene.getInstance().getParts()) {
				if (p.getTopContainer() == foundation) {
					sum += getPartCost(p);
				}
			}
		}
		return sum;
	}

	public int getPartCost(final HousePart part) {
		if (part instanceof Wall) {
			double uFactor = part.getUFactor();
			if (uFactor == 0)
				uFactor = HeatLoad.parseValue(EnergyPanel.getInstance().getWallsComboBox());
			// According to http://www.homewyse.com/services/cost_to_insulate_your_home.html
			// As of 2015, a 1000 square feet wall insulation will cost as high as $1500 to insulate in Boston.
			// This translates into $16/m^2. We don't know what R-value this insulation will be. But let's assume it is R13 material that has a U-value of 0.44 W/m^2/C.
			// Let's also assume that the insulation cost is inversely proportional to the U-value.
			// The baseline cost for a wall is set to be $300/m^2, close to homewyse's estimates of masonry walls, interior framing, etc.
			double unitPrice = 300 + 7 / uFactor;
			return (int) (part.getArea() * unitPrice);
		}
		if (part instanceof Window) {
			double uFactor = part.getUFactor();
			if (uFactor == 0)
				uFactor = HeatLoad.parseValue(EnergyPanel.getInstance().getWindowsComboBox());
			// According to http://www.homewyse.com/costs/cost_of_double_pane_windows.html
			// A storm window of about 1 m^2 costs about $500. A double-pane window of about 1 m^2 costs about $700.
			double unitPrice = 500 + 600 / uFactor;
			return (int) (part.getArea() * unitPrice);
		}
		if (part instanceof Roof) {
			double uFactor = part.getUFactor();
			if (uFactor == 0)
				uFactor = HeatLoad.parseValue(EnergyPanel.getInstance().getRoofsComboBox());
			// According to http://www.homewyse.com/services/cost_to_insulate_attic.html
			// As of 2015, a 1000 square feet of attic area costs as high as $3200 to insulate in Boston.
			// This translates into $34/m^2. We don't know the R-value of this insulation. But let's assume it is R22 material that has a U-value of 0.26 W/m^2/C.
			// Let's also assume that the insulation cost is inversely proportional to the U-value.
			// The baseline (that is, the structure without insulation) cost for a roof is set to be $100/m^2.
			double unitPrice = 100 + 9 / uFactor;
			return (int) (part.getArea() * unitPrice);
		}
		if (part instanceof Foundation) {
			Foundation foundation = (Foundation) part;
			double uFactor = foundation.getUFactor();
			if (uFactor == 0)
				uFactor = HeatLoad.parseValue(EnergyPanel.getInstance().getFloorsComboBox());
			// http://www.homewyse.com/costs/cost_of_floor_insulation.html
			// As of 2015, a 1000 square feet of floor area costs as high as $3000 to insulate in Boston. This translates into $32/m^2.
			// Now, we don't know what R-value this insulation is. But let's assume it is R25 material (minimum insulation recommended
			// for zone 5 by energystar.gov) that has a U-value of 0.23 W/m^2/C.
			// Let's also assume that the insulation cost is inversely proportional to the U-value.
			// The baseline cost (that is, the structure without insulation) for floor is set to be $100/m^2.
			double unitPrice = 100 + 8 / uFactor;
			final double[] buildingGeometry = foundation.getBuildingGeometry();
			if (buildingGeometry != null)
				return (int) (buildingGeometry[1] * unitPrice);
			return 0; // the building is incomplete yet, so we can assume the floor insulation isn't there yet
		}
		if (part instanceof Door) {
			double uFactor = part.getUFactor();
			if (uFactor == 0)
				uFactor = HeatLoad.parseValue(EnergyPanel.getInstance().getDoorsComboBox());
			// According to http://www.homewyse.com/costs/cost_of_exterior_doors.html
			double unitPrice = 500 + 100 / uFactor;
			return (int) (part.getArea() * unitPrice);
		}
		if (part instanceof SolarPanel) {
			return (int) ((SolarPanel) part).getEfficiency() * 50;
		}
		if (part instanceof Tree) {
			Tree tree = (Tree) part;
			int price;
			switch (tree.getTreeType()) {
			case Tree.OAK:
				price = 2000;
			case Tree.PINE:
				price = 1500;
			case Tree.MAPLE:
				price = 1000;
			default:
				price = 500;
			}
			return price;
		}
		return 0;
	}

	public void showGraph() {
		EnergyPanel.getInstance().requestDisableActions(this);
		show();
		EnergyPanel.getInstance().requestDisableActions(null);
	}

	private void show() {

		String details = "";
		int count = 0;
		for (HousePart p : Scene.getInstance().getParts()) {
			if (p instanceof Foundation) {
				count++;
				Foundation foundation = (Foundation) p;
				details += "#" + foundation.getId() + ":$" + getBuildingCost(foundation) + "/";
			}
		}
		if (count > 0)
			details = details.substring(0, details.length() - 1);

		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		final Foundation selectedBuilding;
		if (selectedPart == null || selectedPart instanceof Tree || selectedPart instanceof Human) {
			selectedBuilding = null;
		} else if (selectedPart instanceof Foundation) {
			selectedBuilding = (Foundation) selectedPart;
		} else {
			selectedBuilding = selectedPart.getTopContainer();
			selectedPart.setEditPointsVisible(false);
			SceneManager.getInstance().setSelectedPart(selectedBuilding);
		}
		int wallSum = 0;
		int windowSum = 0;
		int roofSum = 0;
		int foundationSum = 0;
		int doorSum = 0;
		int solarPanelSum = 0;
		int treeSum = 0;
		String info;
		if (selectedBuilding != null) {
			info = "Building #" + selectedBuilding.getId();
			foundationSum = getPartCost(selectedBuilding);
			for (final HousePart p : Scene.getInstance().getParts()) {
				if (p.getTopContainer() == selectedBuilding) {
					if (p instanceof Wall)
						wallSum += getPartCost(p);
					else if (p instanceof Window)
						windowSum += getPartCost(p);
					else if (p instanceof Roof)
						roofSum += getPartCost(p);
					else if (p instanceof Door)
						doorSum += getPartCost(p);
					else if (p instanceof SolarPanel)
						solarPanelSum += getPartCost(p);
				}
				if (count <= 1) {
					if (p instanceof Tree && !p.isFrozen())
						treeSum += getPartCost(p);
				}
			}
		} else {
			info = count + " buildings";
			for (final HousePart p : Scene.getInstance().getParts()) {
				if (p instanceof Wall)
					wallSum += getPartCost(p);
				else if (p instanceof Window)
					windowSum += getPartCost(p);
				else if (p instanceof Roof)
					roofSum += getPartCost(p);
				else if (p instanceof Foundation)
					foundationSum += getPartCost(p);
				else if (p instanceof Door)
					doorSum += getPartCost(p);
				else if (p instanceof SolarPanel)
					solarPanelSum += getPartCost(p);
				else if (p instanceof Tree && !p.isFrozen())
					treeSum += getPartCost(p);
			}
		}

		final float[] data = new float[] { wallSum, windowSum, roofSum, foundationSum, doorSum, solarPanelSum, treeSum };
		final String[] legends = new String[] { "Walls", "Windows", "Roof", "Foundation Floor", "Doors", "Solar Panels", "Trees" };
		final Color[] colors = new Color[] { Color.RED, Color.BLUE, Color.GRAY, Color.MAGENTA, Color.PINK, Color.YELLOW, Color.GREEN };

		// show them in a popup window
		final PieChart pie = new PieChart(data, colors, legends, "$", info, count > 1 ? details : null);
		pie.setBackground(Color.WHITE);
		pie.setBorder(BorderFactory.createEtchedBorder());
		final JDialog dialog = new JDialog(MainFrame.getInstance(), "Costs by Category", true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.getContentPane().add(pie, BorderLayout.CENTER);
		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		final JButton button = new JButton("Close");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				dialog.dispose();
			}
		});
		buttonPanel.add(button);
		dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
		dialog.pack();
		dialog.setLocationRelativeTo(MainFrame.getInstance());
		dialog.setVisible(true);

	}

}
