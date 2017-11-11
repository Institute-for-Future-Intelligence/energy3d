package org.concord.energy3d.simulation;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.concord.energy3d.gui.CspStationDailyEnergyGraph;
import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.ParabolicDish;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.shapes.Heliodon;
import org.concord.energy3d.util.Util;

/**
 * For fast feedback, only 12 days are calculated.
 *
 * @author Charles Xie
 *
 */
public class ParabolicDishAnnualAnalysis extends AnnualAnalysis {

	public ParabolicDishAnnualAnalysis() {
		super();
		graph = new PartEnergyAnnualGraph();
		graph.setPreferredSize(new Dimension(600, 400));
		graph.setBackground(Color.WHITE);
	}

	private void runAnalysis(final JDialog parent) {
		graph.info = "Calculating...";
		graph.repaint();
		onStart();
		final EnergyPanel e = EnergyPanel.getInstance();
		for (final int m : MONTHS) {
			SceneManager.getTaskManager().update(new Callable<Object>() {
				@Override
				public Object call() {
					if (!analysisStopped) {
						final Calendar c = Heliodon.getInstance().getCalendar();
						c.set(Calendar.MONTH, m);
						final Calendar today = (Calendar) c.clone();
						Scene.getInstance().updateTrackables();
						final Throwable t = compute();
						if (t != null) {
							stopAnalysis();
							EventQueue.invokeLater(new Runnable() {
								@Override
								public void run() {
									Util.reportError(t);
								}
							});
						}
						final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
						if (selectedPart instanceof Foundation) { // synchronize with daily graph
							final CspStationDailyEnergyGraph g = e.getCspStationDailyEnergyGraph();
							if (g.hasGraph()) {
								g.setCalendar(today);
								g.updateGraph();
							}
						}
						final Calendar today2 = today;
						EventQueue.invokeLater(new Runnable() {
							@Override
							public void run() {
								e.getDateSpinner().setValue(c.getTime());
								if (selectedPart instanceof Foundation) {
									final CspStationDailyEnergyGraph g = e.getCspStationDailyEnergyGraph();
									e.getCspStationTabbedPane().setSelectedComponent(g);
									if (!g.hasGraph()) {
										g.setCalendar(today2);
										g.addGraph((Foundation) selectedPart);
									}
								}
							}
						});
					}
					return null;
				}
			});

		}

		SceneManager.getTaskManager().update(new Callable<Object>() {
			@Override
			public Object call() {
				EventQueue.invokeLater(new Runnable() {

					@Override
					public void run() {
						onCompletion();
						if (Heliodon.getInstance().getCalendar().get(Calendar.MONTH) != Calendar.DECEMBER) {
							return; // annual calculation aborted
						}
						final String current = Graph.TWO_DECIMALS.format(getResult("Solar"));
						final Map<String, Double> recordedResults = getRecordedResults("Solar");
						final int n = recordedResults.size();
						if (n > 0) {
							String previousRuns = "";
							final Object[] keys = recordedResults.keySet().toArray();
							for (int i = n - 1; i >= 0; i--) {
								previousRuns += keys[i] + " : " + Graph.TWO_DECIMALS.format(recordedResults.get(keys[i]) * 365.0 / 12.0) + " kWh<br>";
							}
							final Object[] options = new Object[] { "OK", "Copy Data" };
							final String msg = "<html>The calculated annual output is <b>" + current + " kWh</b>.<br><hr>Results from previously recorded tests:<br>" + previousRuns + "</html>";
							final JOptionPane optionPane = new JOptionPane(msg, JOptionPane.INFORMATION_MESSAGE, JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
							final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Annual Output");
							dialog.setVisible(true);
							final Object choice = optionPane.getValue();
							if (choice == options[1]) {
								String output = "";
								for (int i = 0; i < n; i++) {
									output += Graph.TWO_DECIMALS.format(recordedResults.get(keys[i]) * 365.0 / 12.0) + "\n";
								}
								output += current;
								final Clipboard clpbrd = Toolkit.getDefaultToolkit().getSystemClipboard();
								clpbrd.setContents(new StringSelection(output), null);
								JOptionPane.showMessageDialog(MainFrame.getInstance(), "<html>" + (n + 1) + " data points copied to system clipboard.<br><hr>" + output, "Confirmation", JOptionPane.INFORMATION_MESSAGE);
							}
						} else {
							JOptionPane.showMessageDialog(parent, "<html>The calculated annual output is <b>" + current + " kWh</b>.</html>", "Annual Output", JOptionPane.INFORMATION_MESSAGE);
						}
					}

				});
				return null;
			}
		});

	}

	@Override
	public void updateGraph() {
		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		if (selectedPart != null) {
			if (selectedPart instanceof ParabolicDish) {
				final ParabolicDish d = (ParabolicDish) selectedPart;
				graph.addData("Solar", d.getSolarPotentialToday() * d.getSystemEfficiency());
			} else if (selectedPart instanceof Foundation) {
				double output = 0;
				for (final HousePart p : Scene.getInstance().getParts()) {
					if (p instanceof ParabolicDish && p.getTopContainer() == selectedPart) {
						final ParabolicDish d = (ParabolicDish) p;
						output += d.getSolarPotentialToday() * d.getSystemEfficiency();
					}
				}
				graph.addData("Solar", output);
			} else if (selectedPart.getTopContainer() instanceof Foundation) {
				double output = 0;
				for (final HousePart p : Scene.getInstance().getParts()) {
					if (p instanceof ParabolicDish && p.getTopContainer() == selectedPart.getTopContainer()) {
						final ParabolicDish d = (ParabolicDish) p;
						output += d.getSolarPotentialToday() * d.getSystemEfficiency();
					}
				}
				graph.addData("Solar", output);
			}
		} else {
			double output = 0;
			for (final HousePart p : Scene.getInstance().getParts()) {
				if (p instanceof ParabolicDish) {
					final ParabolicDish d = (ParabolicDish) p;
					output += d.getSolarPotentialToday() * d.getSystemEfficiency();
				}
			}
			graph.addData("Solar", output);
		}
		graph.repaint();

	}

	public void show() {

		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		String s = null;
		int cost = -1;
		String title = "Annual Yield of All Parabolic Dishes";
		if (selectedPart != null) {
			if (selectedPart instanceof ParabolicDish) {
				cost = (int) CspProjectCost.getPartCost(selectedPart);
				s = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
				title = "Annual Yield";
			} else if (selectedPart instanceof Foundation || selectedPart.getTopContainer() instanceof Foundation) {
				title = "Annual Yield of Selected Foundation";
			}
		}
		final JDialog dialog = new JDialog(MainFrame.getInstance(), s == null ? title : title + ": " + s + " (Cost: $" + cost + ")", true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		graph.parent = dialog;

		final JMenuBar menuBar = new JMenuBar();
		dialog.setJMenuBar(menuBar);

		menuBar.add(createOptionsMenu(dialog, null, true));
		menuBar.add(createRunsMenu());

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

		final JButton button = new JButton("Close");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				stopAnalysis();
				if (graph.hasData()) {
					final Object[] options = { "Yes", "No", "Cancel" };
					final int i = JOptionPane.showOptionDialog(dialog, "Do you want to keep the results of this run?", "Confirmation", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
					if (i == JOptionPane.CANCEL_OPTION) {
						return;
					}
					if (i == JOptionPane.YES_OPTION) {
						graph.keepResults();
					}
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
		if (windowLocation.x > 0 && windowLocation.y > 0) {
			dialog.setLocation(windowLocation);
		} else {
			dialog.setLocationRelativeTo(MainFrame.getInstance());
		}
		dialog.setVisible(true);

	}

	@Override
	public String toJson() {
		String s = "{\"Months\": " + getNumberOfDataPoints();
		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		if (selectedPart != null) {
			if (selectedPart instanceof ParabolicDish) {
				s += ", \"Parabolic Dish\": \"" + selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1) + "\"";
			} else if (selectedPart instanceof Foundation) {
				s += ", \"Foundation\": \"" + selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1) + "\"";
			} else if (selectedPart.getTopContainer() instanceof Foundation) {
				s += ", \"Foundation\": \"" + selectedPart.getTopContainer().toString().substring(0, selectedPart.getTopContainer().toString().indexOf(')') + 1) + "\"";
			}
		} else {
			s += ", \"Parabolic Dish\": \"All\"";
		}
		final String name = "Solar";
		final List<Double> data = graph.getData(name);
		s += ", \"" + name + "\": {";
		s += "\"Monthly\": [";
		for (final Double x : data) {
			s += Graph.ENERGY_FORMAT.format(x) + ",";
		}
		s = s.substring(0, s.length() - 1);
		s += "]\n";
		s += ", \"Total\": " + Graph.ENERGY_FORMAT.format(getResult(name));
		s += "}";
		s += "}";
		return s;
	}

}
