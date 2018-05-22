package org.concord.energy3d.geneticalgorithms.applications;

import java.awt.EventQueue;
import java.util.Calendar;
import java.util.List;

import org.concord.energy3d.geneticalgorithms.Individual;
import org.concord.energy3d.geneticalgorithms.ObjectiveFunction;
import org.concord.energy3d.gui.BuildingDailyEnergyGraph;
import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Wall;
import org.concord.energy3d.model.Window;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.shapes.Heliodon;

/**
 * Chromosome of an individual is encoded as follows:
 * 
 * window[0].width, window[0].height, ..., window[n].width, window[n].height
 * 
 * @author Charles Xie
 *
 */
public class WindowOptimizer extends NetEnergyOptimizer {

	private double maximumWidthRelative = 0.15;
	private double minimumWidthRelative = 0.05;
	private double maximumHeightRelative = 0.4;
	private double minimumHeightRelative = 0.05;
	private boolean optimizeIndividualWindows;

	public WindowOptimizer(final int populationSize, final int chromosomeLength, final int selectionMethod, final double convergenceThreshold, final int discretizationSteps) {
		super(populationSize, chromosomeLength, selectionMethod, convergenceThreshold, discretizationSteps);
	}

	public void setOptimizeIndividualWindows(final boolean optimizeIndividualWindows) {
		this.optimizeIndividualWindows = optimizeIndividualWindows;
	}

	public boolean getOptimizeIndividualWindows() {
		return optimizeIndividualWindows;
	}

	public void setMinimumWidthRelative(final double minimumWidthRelative) {
		this.minimumWidthRelative = minimumWidthRelative;
	}

	public void setMaximumWidthRelative(final double maximumWidthRelative) {
		this.maximumWidthRelative = maximumWidthRelative;
	}

	public void setMinimumHeightRelative(final double minimumHeightRelative) {
		this.minimumHeightRelative = minimumHeightRelative;
	}

	public void setMaximumHeightRelative(final double maximumHeightRelative) {
		this.maximumHeightRelative = maximumHeightRelative;
	}

	@Override
	public void setFoundation(final Foundation foundation) {
		super.setFoundation(foundation);
		// initialize the population with the first-born being the current design
		final Individual firstBorn = population.getIndividual(0);
		if (optimizeIndividualWindows) {
			final List<Window> windows = foundation.getWindows();
			int i = 0;
			for (final Window w : windows) {
				if (w.getContainer() instanceof Wall) {
					final Wall wall = (Wall) w.getContainer();
					final double wallWidth = wall.getWallWidth();
					final double wallHeight = wall.getWallHeight();
					final double minWidth = minimumWidthRelative * wallWidth;
					final double maxWidth = maximumWidthRelative * wallWidth;
					final double minHeight = minimumHeightRelative * wallHeight;
					final double maxHeight = maximumHeightRelative * wallHeight;
					double val = (w.getWindowWidth() - minWidth) / (maxWidth - minWidth);
					if (val < 0) {
						val = 0;
					} else if (val > 1) {
						val = 1;
					}
					firstBorn.setGene(i++, val);
					val = (w.getWindowHeight() - minHeight) / (maxHeight - minHeight);
					if (val < 0) {
						val = 0;
					} else if (val > 1) {
						val = 1;
					}
					firstBorn.setGene(i++, val);
				} else {
					throw new RuntimeException("Windows must be on walls!");
				}
			}
		} else {
			final List<Wall> walls = foundation.getWalls();
			int i = 0;
			for (final Wall wall : walls) {
				final List<Window> windows = wall.getWindows();
				if (!windows.isEmpty()) {
					final double wallWidth = wall.getWallWidth();
					final double wallHeight = wall.getWallHeight();
					final double minWidth = minimumWidthRelative * wallWidth;
					final double maxWidth = maximumWidthRelative * wallWidth;
					final double minHeight = minimumHeightRelative * wallHeight;
					final double maxHeight = maximumHeightRelative * wallHeight;
					final Window w = windows.get(0);
					double val = (w.getWindowWidth() - minWidth) / (maxWidth - minWidth);
					if (val < 0) {
						val = 0;
					} else if (val > 1) {
						val = 1;
					}
					firstBorn.setGene(i++, val);
					val = (w.getWindowHeight() - minHeight) / (maxHeight - minHeight);
					if (val < 0) {
						val = 0;
					} else if (val > 1) {
						val = 1;
					}
					firstBorn.setGene(i++, val);
				}
			}
		}
	}

	@Override
	void computeIndividualFitness(final Individual individual) {
		if (optimizeIndividualWindows) {
			final List<Window> windows = foundation.getWindows();
			for (int i = 0; i < individual.getChromosomeLength(); i++) {
				final double gene = individual.getGene(i);
				final Window w = windows.get(i / 2);
				final Wall wall = (Wall) w.getContainer();
				final double wallWidth = wall.getWallWidth();
				final double wallHeight = wall.getWallHeight();
				final double minWidth = minimumWidthRelative * wallWidth;
				final double maxWidth = maximumWidthRelative * wallWidth;
				final double minHeight = minimumHeightRelative * wallHeight;
				final double maxHeight = maximumHeightRelative * wallHeight;
				switch (i % 2) {
				case 0:
					w.setWindowWidth(minWidth + gene * (maxWidth - minWidth));
					break;
				case 1:
					w.setWindowHeight(minHeight + gene * (maxHeight - minHeight));
					break;
				}
				w.draw();
				wall.draw();
			}
		} else {
			int i = 0;
			final List<Wall> walls = foundation.getWalls();
			for (final Wall wall : walls) {
				final List<Window> windows = wall.getWindows();
				if (!windows.isEmpty()) {
					final double geneWidth = individual.getGene(i * 2);
					final double geneHeight = individual.getGene(i * 2 + 1);
					final double wallWidth = wall.getWallWidth();
					final double wallHeight = wall.getWallHeight();
					final double minWidth = minimumWidthRelative * wallWidth;
					final double maxWidth = maximumWidthRelative * wallWidth;
					final double minHeight = minimumHeightRelative * wallHeight;
					final double maxHeight = maximumHeightRelative * wallHeight;
					for (final Window w : windows) {
						w.setWindowWidth(minWidth + geneWidth * (maxWidth - minWidth));
						w.setWindowHeight(minHeight + geneHeight * (maxHeight - minHeight));
						w.draw();
					}
					wall.draw();
					i++;
				}
			}
		}
		individual.setFitness(objectiveFunction.compute());
	}

	@Override
	public void applyFittest() {
		final Individual best = population.getFittest();
		if (optimizeIndividualWindows) {
			final List<Window> windows = foundation.getWindows();
			for (int i = 0; i < best.getChromosomeLength(); i++) {
				final double gene = best.getGene(i);
				final Window w = windows.get(i / 2);
				final Wall wall = (Wall) w.getContainer();
				final double wallWidth = wall.getWallWidth();
				final double wallHeight = wall.getWallHeight();
				final double minWidth = minimumWidthRelative * wallWidth;
				final double maxWidth = maximumWidthRelative * wallWidth;
				final double minHeight = minimumHeightRelative * wallHeight;
				final double maxHeight = maximumHeightRelative * wallHeight;
				switch (i % 2) {
				case 0:
					w.setWindowWidth(minWidth + gene * (maxWidth - minWidth));
					break;
				case 1:
					w.setWindowHeight(minHeight + gene * (maxHeight - minHeight));
					break;
				}
				w.draw();
				wall.draw();
			}
		} else {
			int i = 0;
			final List<Wall> walls = foundation.getWalls();
			for (final Wall wall : walls) {
				final List<Window> windows = wall.getWindows();
				if (!windows.isEmpty()) {
					final double geneWidth = best.getGene(2 * i);
					final double geneHeight = best.getGene(2 * i + 1);
					final double wallWidth = wall.getWallWidth();
					final double wallHeight = wall.getWallHeight();
					final double minWidth = minimumWidthRelative * wallWidth;
					final double maxWidth = maximumWidthRelative * wallWidth;
					final double minHeight = minimumHeightRelative * wallHeight;
					final double maxHeight = maximumHeightRelative * wallHeight;
					for (final Window w : windows) {
						w.setWindowWidth(minWidth + geneWidth * (maxWidth - minWidth));
						w.setWindowHeight(minHeight + geneHeight * (maxHeight - minHeight));
						w.draw();
					}
					wall.draw();
					i++;
				}
			}
		}
		System.out.println("Fittest: " + individualToString(best));
	}

	@Override
	String individualToString(final Individual individual) {
		String s = "(";
		if (optimizeIndividualWindows) {
			final List<Window> windows = foundation.getWindows();
			for (int i = 0; i < individual.getChromosomeLength(); i++) {
				final double gene = individual.getGene(i);
				final Window w = windows.get(i / 2);
				final Wall wall = (Wall) w.getContainer();
				final double wallWidth = wall.getWallWidth();
				final double wallHeight = wall.getWallHeight();
				final double minWidth = minimumWidthRelative * wallWidth;
				final double maxWidth = maximumWidthRelative * wallWidth;
				final double minHeight = minimumHeightRelative * wallHeight;
				final double maxHeight = maximumHeightRelative * wallHeight;
				switch (i % 2) {
				case 0:
					s += (minWidth + gene * (maxWidth - minWidth)) + ", ";
					break;
				case 1:
					s += (minHeight + gene * (maxHeight - minHeight)) + " | ";
					break;
				}
			}
		} else {
			int i = 0;
			final List<Wall> walls = foundation.getWalls();
			for (final Wall wall : walls) {
				final List<Window> windows = wall.getWindows();
				if (!windows.isEmpty()) {
					final double geneWidth = individual.getGene(2 * i);
					final double geneHeight = individual.getGene(2 * i + 1);
					final double wallWidth = wall.getWallWidth();
					final double wallHeight = wall.getWallHeight();
					final double minWidth = minimumWidthRelative * wallWidth;
					final double maxWidth = maximumWidthRelative * wallWidth;
					final double minHeight = minimumHeightRelative * wallHeight;
					final double maxHeight = maximumHeightRelative * wallHeight;
					s += (minWidth + geneWidth * (maxWidth - minWidth)) + ", ";
					s += (minHeight + geneHeight * (maxHeight - minHeight)) + " | ";
					i++;
				}
			}
		}
		return s.substring(0, s.length() - 3) + ") = " + individual.getFitness();
	}

	@Override
	void updateInfo() {
		if (foundation != null) {
			switch (objectiveFunction.getType()) {
			case ObjectiveFunction.DAILY:
				foundation.setLabelCustomText("Daily Energy Use = " + EnergyPanel.ONE_DECIMAL.format(population.getIndividual(0).getFitness()));
				break;
			case ObjectiveFunction.ANNUAl:
				foundation.setLabelCustomText("Annual Energy Use = " + EnergyPanel.ONE_DECIMAL.format(population.getIndividual(0).getFitness() * 365.0 / 12.0));
				break;
			case ObjectiveFunction.RANDOM:
				foundation.setLabelCustomText(null);
				break;
			}
			foundation.draw();
		}
		EventQueue.invokeLater(new Runnable() {
			@Override
			public void run() {
				final Calendar today = Heliodon.getInstance().getCalendar();
				EnergyPanel.getInstance().getDateSpinner().setValue(today.getTime());
				final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
				if (selectedPart instanceof Foundation) {
					final BuildingDailyEnergyGraph g = EnergyPanel.getInstance().getBuildingDailyEnergyGraph();
					g.setCalendar(today);
					EnergyPanel.getInstance().getBuildingTabbedPane().setSelectedComponent(g);
					if (g.hasGraph()) {
						g.updateGraph();
					} else {
						g.addGraph((Foundation) selectedPart);
					}
				}
			}
		});
	}

}