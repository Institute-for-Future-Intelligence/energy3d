package org.concord.energy3d.gui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.ParabolicDish;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.simulation.ParabolicDishAnnualAnalysis;
import org.concord.energy3d.simulation.ParabolicDishDailyAnalysis;
import org.concord.energy3d.undo.ChangeBaseHeightCommand;
import org.concord.energy3d.undo.ChangeBaseHeightForAllParabolicDishesCommand;
import org.concord.energy3d.undo.ChangeFoundationParabolicDishBaseHeightCommand;
import org.concord.energy3d.undo.ChangeFoundationParabolicDishStructureTypeCommand;
import org.concord.energy3d.undo.ChangeStructureTypeForAllParabolicDishesCommand;
import org.concord.energy3d.undo.SetFocalLengthForAllParabolicDishesCommand;
import org.concord.energy3d.undo.SetFocalLengthForParabolicDishesOnFoundationCommand;
import org.concord.energy3d.undo.SetParabolicDishFocalLengthCommand;
import org.concord.energy3d.undo.SetParabolicDishLabelCommand;
import org.concord.energy3d.undo.SetParabolicDishStructureTypeCommand;
import org.concord.energy3d.undo.SetPartSizeCommand;
import org.concord.energy3d.undo.SetRimRadiusForAllParabolicDishesCommand;
import org.concord.energy3d.undo.SetRimRadiusForParabolicDishesOnFoundationCommand;
import org.concord.energy3d.util.Util;

class PopupMenuForParabolicDish extends PopupMenuFactory {

	private static JPopupMenu popupMenuForParabolicDish;

	static JPopupMenu getPopupMenu() {

		if (popupMenuForParabolicDish == null) {

			final JMenuItem miMesh = new JMenuItem("Mesh...");
			miMesh.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final Foundation foundation = d.getTopContainer();
					final String partInfo = d.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
					gui.add(inputPanel, BorderLayout.CENTER);
					inputPanel.add(new JLabel("Radial Direction: "));
					final JTextField nRadialField = new JTextField("" + d.getNRadialSections());
					inputPanel.add(nRadialField);
					inputPanel.add(new JLabel("Axial Direction: "));
					final JTextField nAxialField = new JTextField("" + d.getNAxialSections());
					inputPanel.add(nAxialField);
					inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
					final JPanel scopePanel = new JPanel();
					scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));
					scopePanel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					scopePanel.add(rb1);
					scopePanel.add(rb2);
					scopePanel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					gui.add(scopePanel, BorderLayout.NORTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { "Set mesh for " + partInfo, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Mesh");

					while (true) {
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							int nRadialSections = 0, nAxialSections = 0;
							boolean ok = true;
							try {
								nRadialSections = Integer.parseInt(nRadialField.getText());
								nAxialSections = Integer.parseInt(nAxialField.getText());
							} catch (final NumberFormatException nfe) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (nRadialSections < 4) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Number of radial sections must be at least 4.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else if (nAxialSections < 4) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Number of axial sections mesh must be at least 4.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else if (!Util.isPowerOfTwo(nRadialSections) || !Util.isPowerOfTwo(nAxialSections)) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Number of parabolic dish mesh sections in x or y direction must be power of two.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										// final SetPartSizeCommand c = new SetPartSizeCommand(t);
										d.setNRadialSections(nRadialSections);
										d.setNAxialSections(nAxialSections);
										d.draw();
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										// final SetShapeForParabolicTroughsOnFoundationCommand c = new SetShapeForParabolicTroughsOnFoundationCommand(foundation);
										foundation.setSectionsForParabolicDishes(nRadialSections, nAxialSections);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										// final SetShapeForAllParabolicTroughsCommand c = new SetShapeForAllParabolicTroughsCommand();
										Scene.getInstance().setSectionsForAllParabolicDishes(nRadialSections, nAxialSections);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JMenuItem miRib = new JMenuItem("Ribs...");
			miRib.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final Foundation foundation = d.getTopContainer();
					final String partInfo = d.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel inputPanel = new JPanel(new GridLayout(2, 2, 5, 5));
					gui.add(inputPanel, BorderLayout.CENTER);
					inputPanel.add(new JLabel("Rib Lines: "));
					final JTextField nribField = new JTextField("" + d.getNumberOfRibs());
					inputPanel.add(nribField);
					inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
					final JPanel scopePanel = new JPanel();
					scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));
					scopePanel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					scopePanel.add(rb1);
					scopePanel.add(rb2);
					scopePanel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					gui.add(scopePanel, BorderLayout.NORTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { "Set rib lines for " + partInfo, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Ribs");

					while (true) {
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							int nrib = 0;
							boolean ok = true;
							try {
								nrib = Integer.parseInt(nribField.getText());
							} catch (final NumberFormatException nfe) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (nrib < 0) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Number of ribs cannot be negative.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										// final SetPartSizeCommand c = new SetPartSizeCommand(t);
										d.setNumberOfRibs(nrib);
										d.draw();
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										// final SetShapeForParabolicTroughsOnFoundationCommand c = new SetShapeForParabolicTroughsOnFoundationCommand(foundation);
										foundation.setNumberOfRibsForParabolicDishes(nrib);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										// final SetShapeForAllParabolicTroughsCommand c = new SetShapeForAllParabolicTroughsCommand();
										Scene.getInstance().setNumberOfRibsForAllParabolicDishes(nrib);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JCheckBoxMenuItem cbmiDrawSunBeams = new JCheckBoxMenuItem("Draw Sun Beams");
			cbmiDrawSunBeams.addItemListener(new ItemListener() {
				@Override
				public void itemStateChanged(final ItemEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final ParabolicDish d = (ParabolicDish) selectedPart;
					d.setBeamsVisible(cbmiDrawSunBeams.isSelected());
					d.drawLightBeams();
					d.draw();
					Scene.getInstance().setEdited(true);
				}
			});

			final JMenuItem miRimRadius = new JMenuItem("Rim Radius...");
			miRimRadius.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final Foundation foundation = d.getTopContainer();
					final String partInfo = d.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel inputPanel = new JPanel(new GridLayout(1, 2, 5, 5));
					gui.add(inputPanel, BorderLayout.CENTER);
					inputPanel.add(new JLabel("Rim Radius (m): "));
					final JTextField apertureRadiusField = new JTextField(threeDecimalsFormat.format(d.getRimRadius()));
					inputPanel.add(apertureRadiusField);
					inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
					final JPanel scopePanel = new JPanel();
					scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));
					scopePanel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					scopePanel.add(rb1);
					scopePanel.add(rb2);
					scopePanel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					gui.add(scopePanel, BorderLayout.NORTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { "Set rim radius for " + partInfo, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Rim Radius");

					while (true) {
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double r = 0;
							boolean ok = true;
							try {
								r = Double.parseDouble(apertureRadiusField.getText());
							} catch (final NumberFormatException x) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (r < 1 || r > 10) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Parabolic dish rim radius must be between 1 and 10 m.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										final SetPartSizeCommand c = new SetPartSizeCommand(d);
										d.setRimRadius(r);
										d.draw();
										SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										final SetRimRadiusForParabolicDishesOnFoundationCommand c = new SetRimRadiusForParabolicDishesOnFoundationCommand(foundation);
										foundation.setRimRadiusForParabolicDishes(r);
										SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										final SetRimRadiusForAllParabolicDishesCommand c = new SetRimRadiusForAllParabolicDishesCommand();
										Scene.getInstance().setRimRadiusForAllParabolicDishes(r);
										SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JMenuItem miFocalLength = new JMenuItem("Focal Length...");
			miFocalLength.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final Foundation foundation = d.getTopContainer();
					final String partInfo = d.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel inputPanel = new JPanel(new GridLayout(1, 2, 5, 5));
					gui.add(inputPanel, BorderLayout.CENTER);
					inputPanel.add(new JLabel("Focal Length (m): "));
					final JTextField focalLengthField = new JTextField(threeDecimalsFormat.format(d.getFocalLength()));
					inputPanel.add(focalLengthField);
					inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
					final JPanel scopePanel = new JPanel();
					scopePanel.setLayout(new BoxLayout(scopePanel, BoxLayout.Y_AXIS));
					scopePanel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					scopePanel.add(rb1);
					scopePanel.add(rb2);
					scopePanel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					gui.add(scopePanel, BorderLayout.NORTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { "Set focal length for " + partInfo, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Focal Length");

					while (true) {
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double fl = 0;
							boolean ok = true;
							try {
								fl = Double.parseDouble(focalLengthField.getText());
							} catch (final NumberFormatException nfe) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), "Invalid input!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (fl < 0.5 || fl > 10) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Focal length must be between 0.5 and 10 m.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										final SetParabolicDishFocalLengthCommand c = new SetParabolicDishFocalLengthCommand(d);
										d.setFocalLength(fl);
										d.draw();
										SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										final SetFocalLengthForParabolicDishesOnFoundationCommand c = new SetFocalLengthForParabolicDishesOnFoundationCommand(foundation);
										foundation.setFocalLengthForParabolicDishes(fl);
										SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										final SetFocalLengthForAllParabolicDishesCommand c = new SetFocalLengthForAllParabolicDishesCommand();
										Scene.getInstance().setFocalLengthForAllParabolicDishes(fl);
										SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JMenuItem miBaseHeight = new JMenuItem("Base Height...");
			miBaseHeight.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final String partInfo = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final Foundation foundation = d.getTopContainer();
					final String title = "<html>Base Height (m) of " + partInfo + "</html>";
					final String footnote = "<html><hr><font size=2></html>";
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel panel = new JPanel();
					gui.add(panel, BorderLayout.CENTER);
					panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
					panel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					panel.add(rb1);
					panel.add(rb2);
					panel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					final JTextField inputField = new JTextField(EnergyPanel.TWO_DECIMALS.format(d.getBaseHeight() * Scene.getInstance().getAnnotationScale()));
					gui.add(inputField, BorderLayout.SOUTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { title, footnote, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Base Height");

					while (true) {
						inputField.selectAll();
						inputField.requestFocusInWindow();
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double val = 0;
							boolean ok = true;
							try {
								val = Double.parseDouble(inputField.getText()) / Scene.getInstance().getAnnotationScale();
							} catch (final NumberFormatException exception) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), inputField.getText() + " is an invalid value!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (rb1.isSelected()) {
									final ChangeBaseHeightCommand c = new ChangeBaseHeightCommand(d);
									d.setBaseHeight(val);
									d.draw();
									SceneManager.getInstance().getUndoManager().addEdit(c);
									selectedScopeIndex = 0;
								} else if (rb2.isSelected()) {
									final ChangeFoundationParabolicDishBaseHeightCommand c = new ChangeFoundationParabolicDishBaseHeightCommand(foundation);
									foundation.setBaseHeightForParabolicDishes(val);
									SceneManager.getInstance().getUndoManager().addEdit(c);
									selectedScopeIndex = 1;
								} else if (rb3.isSelected()) {
									final ChangeBaseHeightForAllParabolicDishesCommand c = new ChangeBaseHeightForAllParabolicDishesCommand();
									Scene.getInstance().setBaseHeightForAllParabolicDishes(val);
									SceneManager.getInstance().getUndoManager().addEdit(c);
									selectedScopeIndex = 2;
								}
								updateAfterEdit();
								if (choice == options[0]) {
									break;
								}
							}
						}
					}
				}
			});

			final JMenuItem miStructureType = new JMenuItem("Structure Type...");
			miStructureType.addActionListener(new ActionListener() {
				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final String partInfo = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final Foundation foundation = d.getTopContainer();
					final String title = "<html>Structure Type of " + partInfo + "</html>";
					final String footnote = "<html><hr><font size=2></html>";
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel panel = new JPanel();
					gui.add(panel, BorderLayout.CENTER);
					panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
					panel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					panel.add(rb1);
					panel.add(rb2);
					panel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					final JComboBox<String> comboBox = new JComboBox<String>(new String[] { "Central Pole", "Tripod" });
					comboBox.setSelectedIndex(d.getStructureType());
					gui.add(comboBox, BorderLayout.SOUTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { title, footnote, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Structure Type");

					while (true) {
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							final int structureType = comboBox.getSelectedIndex();
							if (rb1.isSelected()) {
								final SetParabolicDishStructureTypeCommand c = new SetParabolicDishStructureTypeCommand(d);
								d.setStructureType(structureType);
								d.draw();
								SceneManager.getInstance().getUndoManager().addEdit(c);
								selectedScopeIndex = 0;
							} else if (rb2.isSelected()) {
								final ChangeFoundationParabolicDishStructureTypeCommand c = new ChangeFoundationParabolicDishStructureTypeCommand(foundation);
								foundation.setStructureTypeForParabolicDishes(structureType);
								SceneManager.getInstance().getUndoManager().addEdit(c);
								selectedScopeIndex = 1;
							} else if (rb3.isSelected()) {
								final ChangeStructureTypeForAllParabolicDishesCommand c = new ChangeStructureTypeForAllParabolicDishesCommand();
								Scene.getInstance().setStructureTypeForAllParabolicDishes(structureType);
								SceneManager.getInstance().getUndoManager().addEdit(c);
								selectedScopeIndex = 2;
							}
							updateAfterEdit();
							if (choice == options[0]) {
								break;
							}
						}
					}
				}
			});

			final JMenu labelMenu = new JMenu("Label");

			final JCheckBoxMenuItem miLabelNone = new JCheckBoxMenuItem("None", true);
			miLabelNone.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					if (miLabelNone.isSelected()) {
						final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
						if (selectedPart instanceof ParabolicDish) {
							final ParabolicDish d = (ParabolicDish) selectedPart;
							final SetParabolicDishLabelCommand c = new SetParabolicDishLabelCommand(d);
							d.clearLabels();
							d.draw();
							SceneManager.getInstance().getUndoManager().addEdit(c);
							Scene.getInstance().setEdited(true);
							SceneManager.getInstance().refresh();
						}
					}
				}
			});
			labelMenu.add(miLabelNone);

			final JCheckBoxMenuItem miLabelCustom = new JCheckBoxMenuItem("Custom");
			miLabelCustom.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (selectedPart instanceof ParabolicDish) {
						final ParabolicDish d = (ParabolicDish) selectedPart;
						final SetParabolicDishLabelCommand c = new SetParabolicDishLabelCommand(d);
						d.setLabelCustom(miLabelCustom.isSelected());
						if (d.getLabelCustom()) {
							d.setLabelCustomText(JOptionPane.showInputDialog(MainFrame.getInstance(), "Custom Text", d.getLabelCustomText()));
						}
						d.draw();
						SceneManager.getInstance().getUndoManager().addEdit(c);
						Scene.getInstance().setEdited(true);
						SceneManager.getInstance().refresh();
					}
				}
			});
			labelMenu.add(miLabelCustom);

			final JCheckBoxMenuItem miLabelId = new JCheckBoxMenuItem("ID");
			miLabelId.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (selectedPart instanceof ParabolicDish) {
						final ParabolicDish d = (ParabolicDish) selectedPart;
						final SetParabolicDishLabelCommand c = new SetParabolicDishLabelCommand(d);
						d.setLabelId(miLabelId.isSelected());
						d.draw();
						SceneManager.getInstance().getUndoManager().addEdit(c);
						Scene.getInstance().setEdited(true);
						SceneManager.getInstance().refresh();
					}
				}
			});
			labelMenu.add(miLabelId);

			final JCheckBoxMenuItem miLabelEnergyOutput = new JCheckBoxMenuItem("Energy Output");
			miLabelEnergyOutput.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (selectedPart instanceof ParabolicDish) {
						final ParabolicDish d = (ParabolicDish) selectedPart;
						final SetParabolicDishLabelCommand c = new SetParabolicDishLabelCommand(d);
						d.setLabelEnergyOutput(miLabelEnergyOutput.isSelected());
						d.draw();
						SceneManager.getInstance().getUndoManager().addEdit(c);
						Scene.getInstance().setEdited(true);
						SceneManager.getInstance().refresh();
					}
				}
			});
			labelMenu.add(miLabelEnergyOutput);

			popupMenuForParabolicDish = createPopupMenu(true, true, new Runnable() {
				@Override
				public void run() {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final ParabolicDish d = (ParabolicDish) selectedPart;
					Util.selectSilently(miLabelNone, !d.isLabelVisible());
					Util.selectSilently(miLabelCustom, d.getLabelCustom());
					Util.selectSilently(miLabelId, d.getLabelId());
					Util.selectSilently(miLabelEnergyOutput, d.getLabelEnergyOutput());
					Util.selectSilently(cbmiDrawSunBeams, d.areBeamsVisible());
				}
			});

			final JMenuItem miReflectance = new JMenuItem("Mirror Reflectance...");
			miReflectance.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final String partInfo = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final String title = "<html>Reflectance (%) of " + partInfo + "</html>";
					final String footnote = "<html><hr><font size=2>Reflectance can be affected by pollen and dust.<hr></html>";
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel panel = new JPanel();
					gui.add(panel, BorderLayout.CENTER);
					panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
					panel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					panel.add(rb1);
					panel.add(rb2);
					panel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					final JTextField inputField = new JTextField(EnergyPanel.TWO_DECIMALS.format(d.getReflectance() * 100));
					gui.add(inputField, BorderLayout.SOUTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { title, footnote, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Mirror Reflectance");

					while (true) {
						inputField.selectAll();
						inputField.requestFocusInWindow();
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double val = 0;
							boolean ok = true;
							try {
								val = Double.parseDouble(inputField.getText());
							} catch (final NumberFormatException exception) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), inputField.getText() + " is an invalid value!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (val < 50 || val > 99) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Parabolic dish reflectance must be between 50% and 99%.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										// final ChangeParabolicTroughReflectanceCommand c = new ChangeParabolicTroughReflectanceCommand(t);
										d.setReflectance(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										// final Foundation foundation = t.getTopContainer();
										// final ChangeFoundationParabolicTroughReflectanceCommand c = new ChangeFoundationParabolicTroughReflectanceCommand(foundation);
										// foundation.setReflectanceForParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										// final ChangeReflectanceForAllParabolicTroughsCommand c = new ChangeReflectanceForAllParabolicTroughsCommand();
										// Scene.getInstance().setReflectanceForAllParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JMenuItem miAbsorptance = new JMenuItem("Receiver Absorptance...");
			miAbsorptance.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final String partInfo = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final String title = "<html>Absorptance (%) of " + partInfo + "</html>";
					final String footnote = "<html><hr><font size=2><hr></html>";
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel panel = new JPanel();
					gui.add(panel, BorderLayout.CENTER);
					panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
					panel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					panel.add(rb1);
					panel.add(rb2);
					panel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					final JTextField inputField = new JTextField(EnergyPanel.TWO_DECIMALS.format(d.getAbsorptance() * 100));
					gui.add(inputField, BorderLayout.SOUTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { title, footnote, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Receiver Absorptance");

					while (true) {
						inputField.selectAll();
						inputField.requestFocusInWindow();
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double val = 0;
							boolean ok = true;
							try {
								val = Double.parseDouble(inputField.getText());
							} catch (final NumberFormatException exception) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), inputField.getText() + " is an invalid value!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (val < 50 || val > 99) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Parabolic dish absorptance must be between 50% and 99%.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										// final ChangeParabolicTroughAbsorptanceCommand c = new ChangeParabolicTroughAbsorptanceCommand(t);
										d.setAbsorptance(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										// final Foundation foundation = t.getTopContainer();
										// final ChangeFoundationParabolicTroughAbsorptanceCommand c = new ChangeFoundationParabolicTroughAbsorptanceCommand(foundation);
										// foundation.setAbsorptanceForParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										// final ChangeAbsorptanceForAllParabolicTroughsCommand c = new ChangeAbsorptanceForAllParabolicTroughsCommand();
										// Scene.getInstance().setAbsorptanceForAllParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JMenuItem miOpticalEfficiency = new JMenuItem("Optical Efficiency...");
			miOpticalEfficiency.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final String partInfo = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final String title = "<html>Opitical efficiency (%) of " + partInfo + "</html>";
					final String footnote = "<html><hr><font size=2><hr></html>";
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel panel = new JPanel();
					gui.add(panel, BorderLayout.CENTER);
					panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
					panel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					panel.add(rb1);
					panel.add(rb2);
					panel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					final JTextField inputField = new JTextField(EnergyPanel.TWO_DECIMALS.format(d.getOpticalEfficiency() * 100));
					gui.add(inputField, BorderLayout.SOUTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { title, footnote, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Optical Efficiency");

					while (true) {
						inputField.selectAll();
						inputField.requestFocusInWindow();
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double val = 0;
							boolean ok = true;
							try {
								val = Double.parseDouble(inputField.getText());
							} catch (final NumberFormatException exception) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), inputField.getText() + " is an invalid value!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (val < 20 || val > 80) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Parabolic dish optical efficiency must be between 20% and 80%.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										// final ChangeParabolicTroughOpticalEfficiencyCommand c = new ChangeParabolicTroughOpticalEfficiencyCommand(t);
										d.setOpticalEfficiency(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										// final Foundation foundation = t.getTopContainer();
										// final ChangeFoundationParabolicTroughOpticalEfficiencyCommand c = new ChangeFoundationParabolicTroughOpticalEfficiencyCommand(foundation);
										// foundation.setOpticalEfficiencyForParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										// final ChangeOpticalEfficiencyForAllParabolicTroughsCommand c = new ChangeOpticalEfficiencyForAllParabolicTroughsCommand();
										// Scene.getInstance().setOpticalEfficiencyForAllParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			final JMenuItem miThermalEfficiency = new JMenuItem("Thermal Efficiency...");
			miThermalEfficiency.addActionListener(new ActionListener() {

				private int selectedScopeIndex = 0; // remember the scope selection as the next action will likely be applied to the same scope

				@Override
				public void actionPerformed(final ActionEvent e) {
					final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
					if (!(selectedPart instanceof ParabolicDish)) {
						return;
					}
					final String partInfo = selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1);
					final ParabolicDish d = (ParabolicDish) selectedPart;
					final String title = "<html>Thermal efficiency (%) of " + partInfo + "</html>";
					final String footnote = "<html><hr><font size=2><hr></html>";
					final JPanel gui = new JPanel(new BorderLayout());
					final JPanel panel = new JPanel();
					gui.add(panel, BorderLayout.CENTER);
					panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
					panel.setBorder(BorderFactory.createTitledBorder("Apply to:"));
					final JRadioButton rb1 = new JRadioButton("Only this Parabolic Dish", true);
					final JRadioButton rb2 = new JRadioButton("All Parabolic Dishes on this Foundation");
					final JRadioButton rb3 = new JRadioButton("All Parabolic Dishes");
					panel.add(rb1);
					panel.add(rb2);
					panel.add(rb3);
					final ButtonGroup bg = new ButtonGroup();
					bg.add(rb1);
					bg.add(rb2);
					bg.add(rb3);
					switch (selectedScopeIndex) {
					case 0:
						rb1.setSelected(true);
						break;
					case 1:
						rb2.setSelected(true);
						break;
					case 2:
						rb3.setSelected(true);
						break;
					}
					final JTextField inputField = new JTextField(EnergyPanel.TWO_DECIMALS.format(d.getThermalEfficiency() * 100));
					gui.add(inputField, BorderLayout.SOUTH);

					final Object[] options = new Object[] { "OK", "Cancel", "Apply" };
					final JOptionPane optionPane = new JOptionPane(new Object[] { title, footnote, gui }, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[2]);
					final JDialog dialog = optionPane.createDialog(MainFrame.getInstance(), "Parabolic Dish Thermal Efficiency");

					while (true) {
						inputField.selectAll();
						inputField.requestFocusInWindow();
						dialog.setVisible(true);
						final Object choice = optionPane.getValue();
						if (choice == options[1]) {
							break;
						} else {
							double val = 0;
							boolean ok = true;
							try {
								val = Double.parseDouble(inputField.getText());
							} catch (final NumberFormatException exception) {
								JOptionPane.showMessageDialog(MainFrame.getInstance(), inputField.getText() + " is an invalid value!", "Error", JOptionPane.ERROR_MESSAGE);
								ok = false;
							}
							if (ok) {
								if (val < 20 || val > 80) {
									JOptionPane.showMessageDialog(MainFrame.getInstance(), "Parabolic dish thermal efficiency must be between 20% and 80%.", "Range Error", JOptionPane.ERROR_MESSAGE);
								} else {
									if (rb1.isSelected()) {
										// final ChangeParabolicTroughThermalEfficiencyCommand c = new ChangeParabolicTroughThermalEfficiencyCommand(t);
										d.setThermalEfficiency(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 0;
									} else if (rb2.isSelected()) {
										/// final Foundation foundation = t.getTopContainer();
										// final ChangeFoundationParabolicTroughThermalEfficiencyCommand c = new ChangeFoundationParabolicTroughThermalEfficiencyCommand(foundation);
										// foundation.setThermalEfficiencyForParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 1;
									} else if (rb3.isSelected()) {
										// final ChangeThermalEfficiencyForAllParabolicTroughsCommand c = new ChangeThermalEfficiencyForAllParabolicTroughsCommand();
										// Scene.getInstance().setThermalEfficiencyForAllParabolicTroughs(val * 0.01);
										// SceneManager.getInstance().getUndoManager().addEdit(c);
										selectedScopeIndex = 2;
									}
									updateAfterEdit();
									if (choice == options[0]) {
										break;
									}
								}
							}
						}
					}
				}
			});

			popupMenuForParabolicDish.addSeparator();
			popupMenuForParabolicDish.add(cbmiDrawSunBeams);
			popupMenuForParabolicDish.add(labelMenu);
			popupMenuForParabolicDish.addSeparator();
			popupMenuForParabolicDish.add(miRimRadius);
			popupMenuForParabolicDish.add(miFocalLength);
			popupMenuForParabolicDish.add(miBaseHeight);
			popupMenuForParabolicDish.add(miStructureType);
			popupMenuForParabolicDish.addSeparator();
			popupMenuForParabolicDish.add(miReflectance);
			popupMenuForParabolicDish.add(miAbsorptance);
			popupMenuForParabolicDish.add(miOpticalEfficiency);
			popupMenuForParabolicDish.add(miThermalEfficiency);
			popupMenuForParabolicDish.addSeparator();
			popupMenuForParabolicDish.add(miMesh);
			popupMenuForParabolicDish.add(miRib);
			popupMenuForParabolicDish.addSeparator();

			JMenuItem mi = new JMenuItem("Daily Yield Analysis...");
			mi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					if (SceneManager.getInstance().getSelectedPart() instanceof ParabolicDish) {
						new ParabolicDishDailyAnalysis().show();
					}
				}
			});
			popupMenuForParabolicDish.add(mi);

			mi = new JMenuItem("Annual Yield Analysis...");
			mi.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					if (SceneManager.getInstance().getSelectedPart() instanceof ParabolicDish) {
						new ParabolicDishAnnualAnalysis().show();
					}
				}
			});
			popupMenuForParabolicDish.add(mi);

		}

		return popupMenuForParabolicDish;

	}

}