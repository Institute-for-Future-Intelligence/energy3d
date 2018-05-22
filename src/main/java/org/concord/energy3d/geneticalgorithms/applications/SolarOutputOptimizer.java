package org.concord.energy3d.geneticalgorithms.applications;

/**
 * @author Charles Xie
 *
 */
public abstract class SolarOutputOptimizer extends Optimizer {

	public SolarOutputOptimizer(final int populationSize, final int chromosomeLength, final int selectionMethod, final double convergenceThreshold, final int discretizationSteps) {
		super(populationSize, chromosomeLength, selectionMethod, convergenceThreshold, discretizationSteps);
	}

	@Override
	public void setOjectiveFunction(final int objectiveFunctionType) {
		objectiveFunction = new SolarOutputObjectiveFunction(objectiveFunctionType);
	}

}