package org.concord.energy3d.geneticalgorithms.applications;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.concord.energy3d.geneticalgorithms.Constraint;
import org.concord.energy3d.geneticalgorithms.Individual;
import org.concord.energy3d.geneticalgorithms.ObjectiveFunction;
import org.concord.energy3d.geneticalgorithms.Population;
import org.concord.energy3d.geneticalgorithms.RectangularBound;
import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainPanel;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.util.Util;

import com.ardor3d.math.Vector3;

/**
 * @author Charles Xie
 *
 */
public abstract class Optimizer {

	int maximumGeneration = 5;
	double mutationRate = 0.1;
	double crossoverRate = 0.5;
	double selectionRate = 0.5;
	Population population;
	int outsideGenerationCounter;
	int computeCounter;
	double[] mins, maxs;
	Foundation foundation;
	double cx, cy, lx, ly;
	List<Constraint> constraints;
	int populationSize;
	int chromosomeLength;
	int objectiveFunctionType = ObjectiveFunction.DAILY;
	volatile boolean converged;
	ObjectiveFunction objectiveFunction;

	public Optimizer(final int populationSize, final int chromosomeLength, final Foundation foundation, final int maximumGeneration, final int selectionMethod, final double convergenceThreshold, final int objectiveFunctionType) {
		this.populationSize = populationSize;
		this.foundation = foundation;
		this.maximumGeneration = maximumGeneration;
		this.objectiveFunctionType = objectiveFunctionType;
		this.chromosomeLength = chromosomeLength;
		population = new Population(populationSize, chromosomeLength, selectionMethod, convergenceThreshold);
		objectiveFunction = new SolarCollectorObjectiveFunction(objectiveFunctionType);
		constraints = new ArrayList<Constraint>();
	}

	public void setupFoundationConstraint() {
		final Vector3 v0 = foundation.getAbsPoint(0);
		final Vector3 v1 = foundation.getAbsPoint(1);
		final Vector3 v2 = foundation.getAbsPoint(2);
		final Vector3 v3 = foundation.getAbsPoint(3);
		cx = 0.25 * (v0.getX() + v1.getX() + v2.getX() + v3.getX()) * Scene.getInstance().getAnnotationScale();
		cy = 0.25 * (v0.getY() + v1.getY() + v2.getY() + v3.getY()) * Scene.getInstance().getAnnotationScale();
		lx = v0.distance(v2) * Scene.getInstance().getAnnotationScale();
		ly = v0.distance(v1) * Scene.getInstance().getAnnotationScale();
		mins = new double[chromosomeLength];
		maxs = new double[chromosomeLength];
		for (int i = 0; i < chromosomeLength; i += 2) {
			setMinMax(i, cx - lx * 0.5, cx + lx * 0.5);
			setMinMax(i + 1, cy - ly * 0.5, cy + ly * 0.5);
		}
	}

	abstract void computeIndividual(final int indexOfIndividual);

	public void evolve() {

		onStart();
		outsideGenerationCounter = 0;
		computeCounter = 0;

		while (!shouldTerminate()) { // the number of individuals to evaluate is maximumGeneration * population.size(), subject to the convergence criterion
			for (int i = 0; i < population.size(); i++) {
				computeIndividual(i);
			}
			outsideGenerationCounter++;
		}
		population.sort();
		computeIndividual(0);

		SceneManager.getTaskManager().update(new Callable<Object>() {
			@Override
			public Object call() {
				EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						onCompletion();
					}
				});
				return null;
			}
		});

	}

	// if anyone in the current population doesn't meed the constraints, the entire population dies and the algorithm reverts to the previous generation -- not efficient
	void detectViolations() {
		if (mins != null && maxs != null) {
			for (int i = 0; i < populationSize; i++) {
				final Individual individual = population.getIndividual(i);
				final double[] x = new double[chromosomeLength / 2];
				final double[] y = new double[chromosomeLength / 2];
				for (int j = 0; j < chromosomeLength; j++) {
					final double gene = individual.getGene(j);
					final int j2 = j / 2;
					if (j % 2 == 0) {
						x[j2] = (mins[j] + gene * (maxs[j] - mins[j]));
					} else {
						y[j2] = (mins[j] + gene * (maxs[j] - mins[j]));
					}
				}
				for (int j2 = 0; j2 < x.length; j2++) {
					for (final Constraint c : constraints) {
						if (c instanceof RectangularBound) {
							final RectangularBound rb = (RectangularBound) c;
							if (rb.contains(x[j2], y[j2])) {
								population.setViolation(i, true);
							}
						}
					}
				}
			}
		}
	}

	public void addConstraint(final Constraint c) {
		constraints.add(c);
	}

	boolean shouldTerminate() {
		return outsideGenerationCounter >= maximumGeneration;
	}

	public void setMinMax(final int i, final double min, final double max) {
		mins[i] = min;
		maxs[i] = max;
	}

	public Population getPopulation() {
		return population;
	}

	public void setCrossoverRate(final double crossoverRate) {
		this.crossoverRate = crossoverRate;
	}

	public double getCrossoverRate() {
		return crossoverRate;
	}

	public void setMutationRate(final double mutationRate) {
		this.mutationRate = mutationRate;
	}

	public double getMutationRate() {
		return mutationRate;
	}

	public void setSelectionRate(final double selectionRate) {
		this.selectionRate = selectionRate;
	}

	public double getSelectionRate() {
		return selectionRate;
	}

	void onCompletion() {
		EnergyPanel.getInstance().progress(0);
		EnergyPanel.getInstance().disableDateSpinner(false);
		SceneManager.setExecuteAllTask(true);
		EnergyPanel.getInstance().cancel();
	}

	void onStart() {
		EnergyPanel.getInstance().disableDateSpinner(true);
		SceneManager.getInstance().setHeatFluxDaily(true);
		Util.selectSilently(MainPanel.getInstance().getEnergyButton(), true);
		SceneManager.getInstance().setSolarHeatMapWithoutUpdate(true);
		SceneManager.getInstance().setHeatFluxVectorsVisible(true);
		SceneManager.getInstance().getSolarLand().setVisible(Scene.getInstance().getSolarMapForLand());
		SceneManager.setExecuteAllTask(false);
		Scene.getInstance().redrawAllNow();
	}

	abstract void updateInfo();

}
