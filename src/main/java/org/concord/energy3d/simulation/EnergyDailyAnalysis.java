package org.concord.energy3d.simulation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;

import javax.swing.JDialog;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.model.Door;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Rack;
import org.concord.energy3d.model.Roof;
import org.concord.energy3d.model.SolarPanel;
import org.concord.energy3d.model.Tree;
import org.concord.energy3d.model.Wall;
import org.concord.energy3d.model.Window;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.util.BugReporter;

/**
 * @author Charles Xie
 */
public class EnergyDailyAnalysis extends DailyAnalysis {

    public EnergyDailyAnalysis() {
        super();
        final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
        graph = selectedPart instanceof Foundation ? new BuildingEnergyDailyGraph() : new PartEnergyDailyGraph();
        graph.setPreferredSize(new Dimension(600, 400));
        graph.setBackground(Color.WHITE);
    }

    @Override
    void runAnalysis(final JDialog parent) {
        graph.info = "Calculating...";
        graph.repaint();
        onStart();
        SceneManager.getTaskManager().update(() -> {
            final Throwable t = compute();
            if (t != null) {
                EventQueue.invokeLater(() -> BugReporter.report(t));
            }
            EventQueue.invokeLater(() -> {
                onCompletion();
                if (graph instanceof BuildingEnergyDailyGraph) {
                    final int net = (int) Math.round(getResult("Net"));
                    final Map<String, Double> recordedResults = getRecordedResults("Net");
                    final int n = recordedResults.size();
                    if (n > 0) {
                        String previousRuns = "";
                        final Object[] keys = recordedResults.keySet().toArray();
                        for (int i = n - 1; i >= 0; i--) {
                            previousRuns += keys[i] + " : " + Graph.TWO_DECIMALS.format(recordedResults.get(keys[i])) + " kWh<br>";
                        }
                        final Object[] options = new Object[]{"OK", "Copy Data"};
                        final String msg = "<html>The calculated daily net energy is <b>" + net + " kWh</b>.<br><hr>Results from previously recorded tests:<br>" + previousRuns + "</html>";
                        final JOptionPane optionPane = new JOptionPane(msg, JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
                        final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Daily Net Energy");
                        dialog.setVisible(true);
                        final Object choice = optionPane.getValue();
                        if (choice == options[1]) {
                            String output = "";
                            for (int i = 0; i < n; i++) {
                                output += Graph.TWO_DECIMALS.format(recordedResults.get(keys[i])) + "\n";
                            }
                            output += Graph.TWO_DECIMALS.format(getResult("Net"));
                            final Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
                            clpbrd.setContents(new StringSelection(output), null);
                            JOptionPane.showMessageDialog(MainFrame.getInstance(), "<html>" + (n + 1) + " data points copied to system clipboard.<br><hr>" + output,
                                    "Confirmation", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } else {
                        JOptionPane.showMessageDialog(parent, "<html>The calculated daily net energy is <b>" + net + " kWh</b>.</html>", "Daily Net Energy", JOptionPane.INFORMATION_MESSAGE);
                    }
                    final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
                    if (selectedPart instanceof Foundation) {
                        EnergyPanel.getInstance().getBuildingDailyEnergyGraph().addGraph((Foundation) selectedPart);
                    }
                }
            });
            return null;
        });
    }

    @Override
    public void updateGraph() {
        final int n = (int) Math.round(60.0 / Scene.getInstance().getTimeStep());
        for (int i = 0; i < 24; i++) {
            SolarRadiation.getInstance().computeEnergyAtHour(i);
            final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
            if (selectedPart instanceof Foundation) {
                if (graph instanceof BuildingEnergyDailyGraph) {
                    final Foundation selectedBuilding = (Foundation) selectedPart;
                    final double window = selectedBuilding.getPassiveSolarNow();
                    final double solarPanel = selectedBuilding.getPhotovoltaicNow();
                    final double heater = selectedBuilding.getHeatingNow();
                    final double ac = selectedBuilding.getCoolingNow();
                    final double net = selectedBuilding.getTotalEnergyNow();
                    graph.addData("Windows", window);
                    graph.addData("Solar Panels", solarPanel);
                    graph.addData("Heater", heater);
                    graph.addData("AC", ac);
                    graph.addData("Net", net);
                } else {
                    graph.addData("Solar", selectedPart.getSolarPotentialNow());
                }
            } else if (selectedPart instanceof Window) {
                final Window window = (Window) selectedPart;
                final double solar = selectedPart.getSolarPotentialNow() * window.getSolarHeatGainCoefficient();
                graph.addData("Solar", solar);
                final double[] loss = selectedPart.getHeatLoss();
                final int t0 = n * i;
                double sum = 0;
                for (int k = t0; k < t0 + n; k++) {
                    sum += loss[k];
                }
                graph.addData("Heat Gain", -sum);
            } else if (selectedPart instanceof Wall || selectedPart instanceof Roof || selectedPart instanceof Door) {
                final double solar = selectedPart.getSolarPotentialNow();
                graph.addData("Solar", solar);
                final double[] loss = selectedPart.getHeatLoss();
                final int t0 = n * i;
                double sum = 0;
                for (int k = t0; k < t0 + n; k++) {
                    sum += loss[k];
                }
                graph.addData("Heat Gain", -sum);
            } else if (selectedPart instanceof SolarPanel) {
                graph.addData("Solar", ((SolarPanel) selectedPart).getYieldNow());
            } else if (selectedPart instanceof Rack) {
                graph.addData("Solar", ((Rack) selectedPart).getYieldNow());
            }
        }
        graph.repaint();
    }

    public void show(final String title) {
        final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
        String s = null;
        int cost = -1;
        if (selectedPart != null) {
            cost = (int) BuildingCost.getPartCost(selectedPart);
            if (graph.instrumentType == Graph.SENSOR) {
                SceneManager.getInstance().setSelectedPart(null);
            } else {
                s = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
                if (selectedPart instanceof Foundation) {
                    cost = (int) BuildingCost.getInstance().getCostByFoundation((Foundation) selectedPart);
                    s = s.replaceAll("Foundation", "Building");
                    if (selectedPart.getChildren().isEmpty()) {
                        JOptionPane.showMessageDialog(MainFrame.getInstance(), "There is no building on this foundation.", "No Building", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (!isBuildingAcceptable((Foundation) selectedPart)) {
                        if (JOptionPane.showConfirmDialog(MainFrame.getInstance(), "<html>The selected building cannot be accepted for this analysis.<br>Are you sure to continue?</html>",
                                "Unacceptable Building", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION) {
                            return;
                        }
                    }
                } else if (selectedPart instanceof Tree) {
                    JOptionPane.showMessageDialog(MainFrame.getInstance(), "Energy analysis is not applicable to a tree.", "Not Applicable", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        }
        final JDialog dialog = createDialog(s == null ? title : title + ": " + s + " (Construction cost: $" + cost + ")");
        final JMenuBar menuBar = new JMenuBar();
        dialog.setJMenuBar(menuBar);
        menuBar.add(createOptionsMenu(dialog, null, false));
        menuBar.add(createTypesMenu());
        menuBar.add(createRunsMenu());
        dialog.setVisible(true);
    }

    @Override
    public String toJson() {
        final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
        String s = "{";
        String[] names;
        if (selectedPart instanceof Foundation) {
            s += "\"Building\": " + selectedPart.getId();
            names = new String[]{"Net", "AC", "Heater", "Windows", "Solar Panels"};
        } else {
            s += "\"Part\": \"" + selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1) + "\"";
            names = new String[]{"Solar", "Heat Gain"};
        }
        for (final String name : names) {
            final List<Double> data = graph.getData(name);
            if (data == null) {
                continue;
            }
            s += ", \"" + name + "\": {";
            s += "\"Hourly\": [";
            for (final Double x : data) {
                s += Graph.FIVE_DECIMALS.format(x) + ",";
            }
            s = s.substring(0, s.length() - 1);
            s += "]\n";
            s += ", \"Total\": " + Graph.ENERGY_FORMAT.format(getResult(name));
            s += "}";
        }
        s += "}";
        return s;
    }

}