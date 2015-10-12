package org.concord.energy3d.simulation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.model.Door;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Roof;
import org.concord.energy3d.model.SolarPanel;
import org.concord.energy3d.model.Tree;
import org.concord.energy3d.model.Wall;
import org.concord.energy3d.model.Window;
import org.concord.energy3d.scene.SceneManager;

/**
 * @author Charles Xie
 * 
 */
public class EnergyDailyAnalysis extends Analysis {

	public EnergyDailyAnalysis() {
		super();
		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		graph = selectedPart instanceof Foundation ? new BuildingEnergyDailyGraph() : new PartEnergyDailyGraph();
		graph.setPreferredSize(new Dimension(600, 400));
		graph.setBackground(Color.WHITE);
	}

	private void runAnalysis(final JDialog parent) {
		super.runAnalysis(new Runnable() {
			@Override
			public void run() {
				final Throwable t = compute();
				if (t != null) {
					EventQueue.invokeLater(new Runnable() {
						public void run() {
							JOptionPane.showMessageDialog(parent, "Daily analysis failed. Please restart the program.\n" + t.getMessage(), "Analysis Error", JOptionPane.ERROR_MESSAGE);
						}
					});
				}
				EventQueue.invokeLater(new Runnable() {
					@Override
					public void run() {
						onCompletion();
						if (graph instanceof BuildingEnergyDailyGraph) {
							int net = (int) Math.round(getResult("Net"));
							String previousRuns = "";
							Map<String, Double> recordedResults = getRecordedResults("Net");
							int n = recordedResults.size();
							if (n > 0) {
								Object[] keys = recordedResults.keySet().toArray();
								for (int i = n - 1; i >= 0; i--) {
									previousRuns += keys[i] + " : " + Math.round(recordedResults.get(keys[i])) + " kWh<br>";
								}
							}
							JOptionPane.showMessageDialog(parent, "<html>The calculated daily net energy is <b>" + net + " kWh</b>." + (previousRuns.equals("") ? "" : "<br>For details, look at the graph.<br><br><hr>Results from all previously recorded tests:<br>" + previousRuns) + "</html>", "Daily Net Energy", JOptionPane.INFORMATION_MESSAGE);
						}
					}
				});
			}
		});
	}

	@Override
	void updateGraph() {
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
				Window window = (Window) selectedPart;
				final double solar = selectedPart.getSolarPotentialNow() * window.getSolarHeatGainCoefficientNotPercentage();
				graph.addData("Solar", solar);
				final double[] loss = selectedPart.getHeatLoss();
				double sum = 0;
				for (final double x : loss)
					sum += x;
				graph.addData("Heat Gain", -sum);
			} else if (selectedPart instanceof Wall || selectedPart instanceof Roof || selectedPart instanceof Door) {
				final double[] loss = selectedPart.getHeatLoss();
				double sum = 0;
				for (final double x : loss)
					sum += x;
				graph.addData("Heat Gain", -sum);
			} else if (selectedPart instanceof SolarPanel) {
				final SolarPanel solarPanel = (SolarPanel) selectedPart;
				final double solar = solarPanel.getSolarPotentialNow() * solarPanel.getEfficiencyNotPercentage();
				graph.addData("Solar", solar);
			}
		}
		graph.repaint();
	}

	public void show(String title) {

		HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		String s = null;
		if (selectedPart != null) {
			if (graph.type == Graph.SENSOR) {
				SceneManager.getInstance().setSelectedPart(null);
			} else {
				s = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
				if (selectedPart instanceof Foundation) {
					s = s.replaceAll("Foundation", "Building");
					if (selectedPart.getChildren().isEmpty()) {
						JOptionPane.showMessageDialog(MainFrame.getInstance(), "There is no building on this platform.", "No Building", JOptionPane.INFORMATION_MESSAGE);
						return;
					}
					if (!isBuildingComplete((Foundation) selectedPart)) {
						if (JOptionPane.showConfirmDialog(MainFrame.getInstance(), "The selected building has not been completed.\nAre you sure to continue?", "Incomplete Building", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
							return;
					}
				} else if (selectedPart instanceof Tree) {
					JOptionPane.showMessageDialog(MainFrame.getInstance(), "Energy analysis is not applicable to a tree.", "Not Applicable", JOptionPane.INFORMATION_MESSAGE);
					return;
				}
			}
		}
		final JDialog dialog = new JDialog(MainFrame.getInstance(), s == null ? title : title + ": " + s, true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		final JMenuBar menuBar = new JMenuBar();
		dialog.setJMenuBar(menuBar);

		final JMenuItem miClear = new JMenuItem("Clear Previous Results");
		final JMenuItem miView = new JMenuItem("View Raw Data");

		final JMenu menu = new JMenu("Options");
		menu.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				miClear.setEnabled(graph.hasRecords());
				miView.setEnabled(graph.hasData());
			}

			@Override
			public void menuDeselected(MenuEvent e) {
			}

			@Override
			public void menuCanceled(MenuEvent e) {
			}
		});
		menuBar.add(menu);

		miClear.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				final int i = JOptionPane.showConfirmDialog(dialog, "Are you sure that you want to clear all the previous results\nrelated to the selected object?", "Confirmation", JOptionPane.YES_NO_OPTION);
				if (i != JOptionPane.YES_OPTION)
					return;
				graph.clearRecords();
				graph.repaint();
			}
		});
		menu.add(miClear);

		miView.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				DataViewer.viewRawData(dialog, graph);
			}
		});
		menu.add(miView);

		final JMenu showTypeMenu = new JMenu("Types");
		showTypeMenu.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				showTypeMenu.removeAll();
				final Set<String> dataNames = graph.getDataNames();
				if (!dataNames.isEmpty()) {
					JMenuItem mi = new JMenuItem("Show All");
					mi.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							for (String name : dataNames)
								graph.hideData(name, false);
							graph.repaint();
						}
					});
					showTypeMenu.add(mi);
					mi = new JMenuItem("Hide All");
					mi.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							for (String name : dataNames)
								graph.hideData(name, true);
							graph.repaint();
						}
					});
					showTypeMenu.add(mi);
					showTypeMenu.addSeparator();
					for (final String name : dataNames) {
						final JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem(name, !graph.isDataHidden(name));
						cbmi.addItemListener(new ItemListener() {
							@Override
							public void itemStateChanged(final ItemEvent e) {
								graph.hideData(name, !cbmi.isSelected());
								graph.repaint();
							}
						});
						showTypeMenu.add(cbmi);
					}
				}
			}

			@Override
			public void menuDeselected(MenuEvent e) {
			}

			@Override
			public void menuCanceled(MenuEvent e) {
			}
		});
		menuBar.add(showTypeMenu);

		final JMenu showRunsMenu = new JMenu("Runs");
		showRunsMenu.addMenuListener(new MenuListener() {
			@Override
			public void menuSelected(MenuEvent e) {
				showRunsMenu.removeAll();
				if (!DailyGraph.records.isEmpty()) {
					JMenuItem mi = new JMenuItem("Show All");
					mi.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							for (Results r : DailyGraph.records)
								graph.hideRun(r.getID(), false);
							graph.repaint();
						}
					});
					showRunsMenu.add(mi);
					mi = new JMenuItem("Hide All");
					mi.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent e) {
							for (Results r : DailyGraph.records)
								graph.hideRun(r.getID(), true);
							graph.repaint();
						}
					});
					showRunsMenu.add(mi);
					showRunsMenu.addSeparator();
					Map<String, Double> recordedResults = getRecordedResults("Net");
					for (final Results r : DailyGraph.records) {
						String key = r.getID() + (r.getFileName() == null ? "" : " (file: " + r.getFileName() + ")");
						Double result = recordedResults.get(key);
						final JCheckBoxMenuItem cbmi = new JCheckBoxMenuItem(r.getID() + ":" + r.getFileName() + (result == null ? "" : " - " + Math.round(recordedResults.get(key)) + " kWh"), !graph.isRunHidden(r.getID()));
						cbmi.addItemListener(new ItemListener() {
							@Override
							public void itemStateChanged(final ItemEvent e) {
								graph.hideRun(r.getID(), !cbmi.isSelected());
								graph.repaint();
							}
						});
						showRunsMenu.add(cbmi);
					}
				}
			}

			@Override
			public void menuDeselected(MenuEvent e) {
			}

			@Override
			public void menuCanceled(MenuEvent e) {
			}
		});
		menuBar.add(showRunsMenu);

		final JPanel contentPane = new JPanel(new BorderLayout());
		dialog.setContentPane(contentPane);

		final JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEtchedBorder());
		contentPane.add(panel, BorderLayout.CENTER);

		panel.add(graph, BorderLayout.CENTER);

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		contentPane.add(buttonPanel, BorderLayout.SOUTH);

		runButton = new JButton("Run");
		runButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				runButton.setEnabled(false);
				runAnalysis(dialog);
			}
		});
		buttonPanel.add(runButton);

		JButton button = new JButton("Close");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				stopAnalysis();
				if (graph.hasData()) {
					final Object[] options = { "Yes", "No" };
					if (JOptionPane.showOptionDialog(dialog, "Do you want to keep the results of this run?", "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]) == JOptionPane.YES_OPTION)
						graph.keepResults();
				}
				windowLocation.setLocation(dialog.getLocationOnScreen());
				dialog.dispose();
			}
		});
		buttonPanel.add(button);

		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				stopAnalysis();
				windowLocation.setLocation(dialog.getLocationOnScreen());
				dialog.dispose();
			}
		});

		dialog.pack();
		if (windowLocation.x > 0 && windowLocation.y > 0)
			dialog.setLocation(windowLocation);
		else
			dialog.setLocationRelativeTo(MainFrame.getInstance());
		dialog.setVisible(true);

	}

}