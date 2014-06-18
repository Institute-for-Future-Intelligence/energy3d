package org.concord.energy3d.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.HierarchyBoundsAdapter;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.concord.energy3d.model.Door;
import org.concord.energy3d.model.Floor;
import org.concord.energy3d.model.Foundation;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Roof;
import org.concord.energy3d.model.Sensor;
import org.concord.energy3d.model.SolarPanel;
import org.concord.energy3d.model.Tree;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.shapes.Heliodon;
import org.concord.energy3d.simulation.CityData;
import org.concord.energy3d.simulation.Cost;
import org.concord.energy3d.simulation.HeatLoad;
import org.concord.energy3d.simulation.SolarIrradiation;
import org.concord.energy3d.util.Specifications;
import org.concord.energy3d.util.Util;

import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.type.ReadOnlyColorRGBA;
import com.ardor3d.math.type.ReadOnlyVector3;

public class EnergyPanel extends JPanel {

	public static final ReadOnlyColorRGBA[] solarColors = { ColorRGBA.BLUE, ColorRGBA.GREEN, ColorRGBA.YELLOW, ColorRGBA.RED };
	private static final long serialVersionUID = 1L;
	private static final EnergyPanel instance = new EnergyPanel();
	private final DecimalFormat twoDecimals = new DecimalFormat();
	private final DecimalFormat noDecimals = new DecimalFormat();
	private static boolean keepHeatmapOn = false;
	private Thread thread;
	private boolean computeRequest;
	private boolean cancel;
	private Object disableActionsRequester;
	private boolean alreadyRenderedHeatmap = false;
	private UpdateRadiation updateRadiation;
	private boolean computeEnabled = true;
	private final List<PropertyChangeListener> propertyChangeListeners = Collections.synchronizedList(new ArrayList<PropertyChangeListener>());

	public enum UpdateRadiation {
		ALWAYS, ONLY_IF_SLECTED_IN_GUI
	};

	private final JComboBox<String> wallsComboBox;
	private final JComboBox<String> doorsComboBox;
	private final JComboBox<String> windowsComboBox;
	private final JComboBox<String> roofsComboBox;
	private final JComboBox<String> cityComboBox;
	private final JComboBox<String> solarPanelEfficiencyComboBox;
	private final JComboBox<String> windowSHGCComboBox;
	private final JTextField heatingTextField;
	private final JTextField coolingTextField;
	private final JTextField netEnergyTextField;
	private final JSpinner insideTemperatureSpinner;
	private final JSpinner outsideTemperatureSpinner;
	private final JLabel dateLabel;
	private final JLabel timeLabel;
	private final JSpinner dateSpinner;
	private final JSpinner timeSpinner;
	private final JLabel latitudeLabel;
	private final JSpinner latitudeSpinner;
	private final JPanel heatMapPanel;
	private final JSlider colorMapSlider;
	private final JProgressBar progressBar;
	private final ColorBar budgetBar, heightBar, areaBar;
	private final JPanel budgetPanel, heightPanel, areaPanel;
	private JPanel partPanel;
	private JPanel buildingPanel;
	private JTextField windowTextField;
	private JTextField solarPanelTextField;
	private JPanel partPropertiesPanel;
	private JLabel partProperty1Label;
	private JLabel partProperty2Label;
	private JLabel partProperty3Label;
	private JLabel partProperty4Label;
	private JTextField partProperty1TextField;
	private JTextField partProperty2TextField;
	private JTextField partProperty3TextField;
	private JTextField partProperty4TextField;

	public static EnergyPanel getInstance() {
		return instance;
	}

	private EnergyPanel() {

		twoDecimals.setMaximumFractionDigits(2);
		noDecimals.setMaximumFractionDigits(0);

		setLayout(new BorderLayout());
		final JPanel dataPanel = new JPanel();
		dataPanel.setLayout(new BoxLayout(dataPanel, BoxLayout.Y_AXIS));
		add(new JScrollPane(dataPanel), BorderLayout.CENTER);

		final JPanel timeAndLocationPanel = new JPanel();
		timeAndLocationPanel.setToolTipText("<html>The outside temperature and the sun path<br>differ from time to time and from location to location.</html>");
		timeAndLocationPanel.setBorder(new TitledBorder(null, "Time & Location", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(timeAndLocationPanel);
		final GridBagLayout gbl_panel_3 = new GridBagLayout();
		timeAndLocationPanel.setLayout(gbl_panel_3);

		dateLabel = new JLabel("Date: ");
		final GridBagConstraints gbc_dateLabel = new GridBagConstraints();
		gbc_dateLabel.gridx = 0;
		gbc_dateLabel.gridy = 0;
		timeAndLocationPanel.add(dateLabel, gbc_dateLabel);

		dateSpinner = new JSpinner();
		dateSpinner.setModel(new SpinnerDateModel(new Date(1380427200000L), null, null, Calendar.MONTH));
		dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "MMMM dd"));
		dateSpinner.addHierarchyBoundsListener(new HierarchyBoundsAdapter() {
			@Override
			public void ancestorResized(final HierarchyEvent e) {
				dateSpinner.setMinimumSize(dateSpinner.getPreferredSize());
				dateSpinner.setPreferredSize(dateSpinner.getPreferredSize());
				dateSpinner.removeHierarchyBoundsListener(this);
			}
		});
		dateSpinner.addChangeListener(new ChangeListener() {
			boolean firstCall = true;

			@Override
			public void stateChanged(final ChangeEvent e) {
				if (firstCall) {
					firstCall = false;
					return;
				}
				if (disableActionsRequester == null) {
					final Heliodon heliodon = Heliodon.getInstance();
					if (heliodon != null)
						heliodon.setDate((Date) dateSpinner.getValue());
					compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
					Scene.getInstance().setEdited(true);
				}
			}
		});
		final GridBagConstraints gbc_dateSpinner = new GridBagConstraints();
		gbc_dateSpinner.insets = new Insets(0, 0, 1, 1);
		gbc_dateSpinner.gridx = 1;
		gbc_dateSpinner.gridy = 0;
		timeAndLocationPanel.add(dateSpinner, gbc_dateSpinner);

		cityComboBox = new JComboBox<String>();
		cityComboBox.setModel(new DefaultComboBoxModel<String>(CityData.getInstance().getCities()));
		cityComboBox.setSelectedItem("Boston");
		cityComboBox.setMaximumRowCount(15);
		cityComboBox.addActionListener(new java.awt.event.ActionListener() {
			@Override
			public void actionPerformed(final java.awt.event.ActionEvent e) {
				if (cityComboBox.getSelectedItem().equals(""))
					compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
				else {
					final Integer newLatitude = CityData.getInstance().getCityLatitutes().get(cityComboBox.getSelectedItem());
					if (newLatitude.equals(latitudeSpinner.getValue()))
						compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
					else
						latitudeSpinner.setValue(newLatitude);
				}
				Scene.getInstance().setEdited(true);
			}
		});

		final GridBagConstraints gbc_cityComboBox = new GridBagConstraints();
		gbc_cityComboBox.gridwidth = 2;
		gbc_cityComboBox.fill = GridBagConstraints.HORIZONTAL;
		gbc_cityComboBox.gridx = 2;
		gbc_cityComboBox.gridy = 0;
		timeAndLocationPanel.add(cityComboBox, gbc_cityComboBox);

		timeLabel = new JLabel("Time: ");
		final GridBagConstraints gbc_timeLabel = new GridBagConstraints();
		gbc_timeLabel.gridx = 0;
		gbc_timeLabel.gridy = 1;
		timeAndLocationPanel.add(timeLabel, gbc_timeLabel);

		timeSpinner = new JSpinner(new SpinnerDateModel());
		timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "H:mm"));
		timeSpinner.addChangeListener(new ChangeListener() {
			private boolean firstCall = true;

			@Override
			public void stateChanged(final ChangeEvent e) {
				// ignore the first event
				if (firstCall) {
					firstCall = false;
					return;
				}
				final Heliodon heliodon = Heliodon.getInstance();
				if (heliodon != null)
					heliodon.setTime((Date) timeSpinner.getValue());
				updateOutsideTemperature();
				Scene.getInstance().setEdited(true);
				SceneManager.getInstance().changeSkyTexture();
			}
		});
		final GridBagConstraints gbc_timeSpinner = new GridBagConstraints();
		gbc_timeSpinner.insets = new Insets(0, 0, 0, 1);
		gbc_timeSpinner.fill = GridBagConstraints.HORIZONTAL;
		gbc_timeSpinner.gridx = 1;
		gbc_timeSpinner.gridy = 1;
		timeAndLocationPanel.add(timeSpinner, gbc_timeSpinner);

		latitudeLabel = new JLabel("Latitude: ");
		final GridBagConstraints gbc_altitudeLabel = new GridBagConstraints();
		gbc_altitudeLabel.insets = new Insets(0, 1, 0, 0);
		gbc_altitudeLabel.gridx = 2;
		gbc_altitudeLabel.gridy = 1;
		timeAndLocationPanel.add(latitudeLabel, gbc_altitudeLabel);

		latitudeSpinner = new JSpinner();
		latitudeSpinner.setModel(new SpinnerNumberModel(Heliodon.DEFAULT_LATITUDE, -90, 90, 1));
		latitudeSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				if (!cityComboBox.getSelectedItem().equals("") && !CityData.getInstance().getCityLatitutes().values().contains(latitudeSpinner.getValue()))
					cityComboBox.setSelectedItem("");
				Heliodon.getInstance().setLatitude(((Integer) latitudeSpinner.getValue()) / 180.0 * Math.PI);
				compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
				Scene.getInstance().setEdited(true);
			}
		});
		final GridBagConstraints gbc_latitudeSpinner = new GridBagConstraints();
		gbc_latitudeSpinner.fill = GridBagConstraints.HORIZONTAL;
		gbc_latitudeSpinner.gridx = 3;
		gbc_latitudeSpinner.gridy = 1;
		timeAndLocationPanel.add(latitudeSpinner, gbc_latitudeSpinner);

		timeAndLocationPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, timeAndLocationPanel.getPreferredSize().height));

		final JPanel temperaturePanel = new JPanel();
		temperaturePanel.setToolTipText("<html>Temperature difference drives heat transfer<br>between inside and outside.</html>");
		temperaturePanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Temperature \u00B0C", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(temperaturePanel);
		final GridBagLayout gbl_temperaturePanel = new GridBagLayout();
		temperaturePanel.setLayout(gbl_temperaturePanel);

		final JLabel insideTemperatureLabel = new JLabel("Inside: ");
		insideTemperatureLabel.setToolTipText("");
		final GridBagConstraints gbc_insideTemperatureLabel = new GridBagConstraints();
		gbc_insideTemperatureLabel.gridx = 1;
		gbc_insideTemperatureLabel.gridy = 0;
		temperaturePanel.add(insideTemperatureLabel, gbc_insideTemperatureLabel);

		insideTemperatureSpinner = new JSpinner();
		insideTemperatureSpinner.setToolTipText("Thermostat temperature setting for the inside of the house");
		insideTemperatureSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				if (disableActionsRequester == null)
					compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
			}
		});
		insideTemperatureSpinner.setModel(new SpinnerNumberModel(20, -70, 60, 1));
		final GridBagConstraints gbc_insideTemperatureSpinner = new GridBagConstraints();
		gbc_insideTemperatureSpinner.gridx = 2;
		gbc_insideTemperatureSpinner.gridy = 0;
		temperaturePanel.add(insideTemperatureSpinner, gbc_insideTemperatureSpinner);

		final JLabel outsideTemperatureLabel = new JLabel(" Outside: ");
		outsideTemperatureLabel.setToolTipText("");
		final GridBagConstraints gbc_outsideTemperatureLabel = new GridBagConstraints();
		gbc_outsideTemperatureLabel.gridx = 3;
		gbc_outsideTemperatureLabel.gridy = 0;
		temperaturePanel.add(outsideTemperatureLabel, gbc_outsideTemperatureLabel);

		temperaturePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, temperaturePanel.getPreferredSize().height));

		outsideTemperatureSpinner = new JSpinner();
		outsideTemperatureSpinner.setToolTipText("Outside temperature at this time and day");
		outsideTemperatureSpinner.setEnabled(false);
		outsideTemperatureSpinner.setModel(new SpinnerNumberModel(10, -70, 60, 1));
		outsideTemperatureSpinner.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				if (disableActionsRequester == null)
					compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
			}
		});
		final GridBagConstraints gbc_outsideTemperatureSpinner = new GridBagConstraints();
		gbc_outsideTemperatureSpinner.gridx = 4;
		gbc_outsideTemperatureSpinner.gridy = 0;
		temperaturePanel.add(outsideTemperatureSpinner, gbc_outsideTemperatureSpinner);

		final JPanel uFactorPanel = new JPanel();
		uFactorPanel.setToolTipText("<html><b>U-factor</b><br>measures how well a building element conducts heat.</html>");
		uFactorPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "U-Factor W/(m\u00B2.\u00B0C)", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(uFactorPanel);
		final GridBagLayout gbl_uFactorPanel = new GridBagLayout();
		uFactorPanel.setLayout(gbl_uFactorPanel);

		final JLabel wallsLabel = new JLabel("Walls:");
		final GridBagConstraints gbc_wallsLabel = new GridBagConstraints();
		gbc_wallsLabel.anchor = GridBagConstraints.EAST;
		gbc_wallsLabel.insets = new Insets(0, 0, 5, 5);
		gbc_wallsLabel.gridx = 0;
		gbc_wallsLabel.gridy = 0;
		uFactorPanel.add(wallsLabel, gbc_wallsLabel);

		wallsComboBox = new WideComboBox();
		wallsComboBox.setEditable(true);
		wallsComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "0.28 ", "0.67 (Concrete)", "0.41 (Masonary Brick)", "0.04 (Flat Metal Fiberglass Insulation)" }));
		wallsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
				updateCost();
			}
		});
		final GridBagConstraints gbc_wallsComboBox = new GridBagConstraints();
		gbc_wallsComboBox.insets = new Insets(0, 0, 5, 5);
		gbc_wallsComboBox.gridx = 1;
		gbc_wallsComboBox.gridy = 0;
		uFactorPanel.add(wallsComboBox, gbc_wallsComboBox);

		final JLabel doorsLabel = new JLabel("Doors:");
		final GridBagConstraints gbc_doorsLabel = new GridBagConstraints();
		gbc_doorsLabel.anchor = GridBagConstraints.EAST;
		gbc_doorsLabel.insets = new Insets(0, 0, 5, 5);
		gbc_doorsLabel.gridx = 2;
		gbc_doorsLabel.gridy = 0;
		uFactorPanel.add(doorsLabel, gbc_doorsLabel);

		doorsComboBox = new WideComboBox();
		doorsComboBox.setEditable(true);
		doorsComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "0.8 ", "1.2 (Steel)", "0.4 (Wood)" }));
		doorsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
				updateCost();
			}
		});
		final GridBagConstraints gbc_doorsComboBox = new GridBagConstraints();
		gbc_doorsComboBox.insets = new Insets(0, 0, 5, 0);
		gbc_doorsComboBox.gridx = 3;
		gbc_doorsComboBox.gridy = 0;
		uFactorPanel.add(doorsComboBox, gbc_doorsComboBox);

		final JLabel windowsLabel = new JLabel("Windows:");
		final GridBagConstraints gbc_windowsLabel = new GridBagConstraints();
		gbc_windowsLabel.anchor = GridBagConstraints.EAST;
		gbc_windowsLabel.insets = new Insets(0, 0, 0, 5);
		gbc_windowsLabel.gridx = 0;
		gbc_windowsLabel.gridy = 1;
		uFactorPanel.add(windowsLabel, gbc_windowsLabel);

		windowsComboBox = new WideComboBox();
		windowsComboBox.setEditable(true);
		windowsComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "1.0", "0.35 (Double Pane)", "0.15 (Triple Pane)" }));
		windowsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
				updateCost();
			}
		});
		final GridBagConstraints gbc_windowsComboBox = new GridBagConstraints();
		gbc_windowsComboBox.insets = new Insets(0, 0, 0, 5);
		gbc_windowsComboBox.gridx = 1;
		gbc_windowsComboBox.gridy = 1;
		uFactorPanel.add(windowsComboBox, gbc_windowsComboBox);

		final JLabel roofsLabel = new JLabel("Roofs:");
		final GridBagConstraints gbc_roofsLabel = new GridBagConstraints();
		gbc_roofsLabel.anchor = GridBagConstraints.EAST;
		gbc_roofsLabel.insets = new Insets(0, 0, 0, 5);
		gbc_roofsLabel.gridx = 2;
		gbc_roofsLabel.gridy = 1;
		uFactorPanel.add(roofsLabel, gbc_roofsLabel);

		roofsComboBox = new WideComboBox();
		roofsComboBox.setEditable(true);
		roofsComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "0.14 ", "0.23 (Concrete)", "0.11 (Flat Metal Fiberglass Insulation)", "0.10 (Wood Fiberglass Insulation)" }));
		roofsComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
				updateCost();
			}
		});
		final GridBagConstraints gbc_roofsComboBox = new GridBagConstraints();
		gbc_roofsComboBox.gridx = 3;
		gbc_roofsComboBox.gridy = 1;
		uFactorPanel.add(roofsComboBox, gbc_roofsComboBox);

		uFactorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, uFactorPanel.getPreferredSize().height));

		final JPanel solarConversionPercentagePanel = new JPanel();
		solarConversionPercentagePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, solarConversionPercentagePanel.getPreferredSize().height));
		solarConversionPercentagePanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Solar Conversion (%)", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(solarConversionPercentagePanel);

		final JLabel labelSHGC = new JLabel("Window (SHGC): ");
		labelSHGC.setToolTipText("<html><b>SHGC - Solar heat gain coefficient</b><br>measures the fraction of solar energy transmitted through a window.</html>");
		solarConversionPercentagePanel.add(labelSHGC);

		windowSHGCComboBox = new WideComboBox();
		windowSHGCComboBox.setEditable(true);
		windowSHGCComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "25.0", "50.0", "80.0" }));
		windowSHGCComboBox.setSelectedIndex(1);
		windowSHGCComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				// validate the input
				final String s = (String) windowSHGCComboBox.getSelectedItem();
				double eff = 50;
				try {
					eff = Float.parseFloat(s);
				} catch (final NumberFormatException ex) {
					JOptionPane.showMessageDialog(MainFrame.getInstance(), "Wrong format: must be 25-80.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (eff < 25 || eff > 80) {
					JOptionPane.showMessageDialog(MainFrame.getInstance(), "Wrong range: must be 25-80.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				Scene.getInstance().setWindowSolarHeatGainCoefficient(eff);
				updateCost();
			}
		});
		solarConversionPercentagePanel.add(windowSHGCComboBox);

		final JLabel labelPV = new JLabel("Solar Panel: ");
		labelPV.setToolTipText("<html><b>Solar photovoltaic efficiency</b><br>measures the fraction of solar energy converted into electricity by a solar panel.</html>");
		solarConversionPercentagePanel.add(labelPV);

		solarPanelEfficiencyComboBox = new WideComboBox();
		solarPanelEfficiencyComboBox.setEditable(true);
		solarPanelEfficiencyComboBox.setModel(new DefaultComboBoxModel<String>(new String[] { "10.0", "15.0", "20.0" }));
		solarPanelEfficiencyComboBox.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				// validate the input
				final String s = (String) solarPanelEfficiencyComboBox.getSelectedItem();
				double eff = 10;
				try {
					eff = Float.parseFloat(s);
				} catch (final NumberFormatException ex) {
					JOptionPane.showMessageDialog(MainFrame.getInstance(), "Wrong format: must be 10-20.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				if (eff < 10 || eff > 30) {
					JOptionPane.showMessageDialog(MainFrame.getInstance(), "Wrong range: must be 10-30.", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
				Scene.getInstance().setSolarPanelEfficiency(eff);
				updateCost();
			}
		});
		solarConversionPercentagePanel.add(solarPanelEfficiencyComboBox);

		heatMapPanel = new JPanel(new BorderLayout());
		heatMapPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Heat Map Contrast", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(heatMapPanel);

		colorMapSlider = new MySlider();
		colorMapSlider.setToolTipText("<html>Increase or decrease the color contrast of the heat map.</html>");
		colorMapSlider.setMinimum(10);
		colorMapSlider.setMaximum(90);
		colorMapSlider.setMinimumSize(colorMapSlider.getPreferredSize());
		colorMapSlider.addChangeListener(new ChangeListener() {
			@Override
			public void stateChanged(final ChangeEvent e) {
				if (!colorMapSlider.getValueIsAdjusting()) {
					compute(SceneManager.getInstance().isSolarColorMap() ? UpdateRadiation.ALWAYS : UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
					Scene.getInstance().setEdited(true, false);
				}
			}
		});
		colorMapSlider.setSnapToTicks(true);
		colorMapSlider.setMinorTickSpacing(1);
		colorMapSlider.setMajorTickSpacing(5);
		heatMapPanel.add(colorMapSlider, BorderLayout.CENTER);
		heatMapPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, heatMapPanel.getPreferredSize().height));

		partPanel = new JPanel();
		partPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Part", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(partPanel);

		buildingPanel = new JPanel();
		buildingPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Building", TitledBorder.LEADING, TitledBorder.TOP));
		dataPanel.add(buildingPanel);
		buildingPanel.setLayout(new BoxLayout(buildingPanel, BoxLayout.Y_AXIS));

		final JPanel buildingSizePanel = new JPanel(new GridLayout(1, 2, 0, 0));
		buildingPanel.add(buildingSizePanel);

		// area for the selected building

		areaPanel = new JPanel(new BorderLayout());
		areaPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Area (\u33A1)", TitledBorder.LEADING, TitledBorder.TOP));
		areaPanel.setToolTipText("<html>The area of the selected building<br><b>Must be within the specified range (if any).</b></html>");
		buildingSizePanel.add(areaPanel);
		areaBar = new ColorBar(Color.WHITE, Color.LIGHT_GRAY);
		areaBar.setUnit("");
		areaBar.setUnitPrefix(false);
		areaBar.setVerticalLineRepresentation(false);
		areaBar.setDecimalDigits(1);
		areaBar.setToolTipText(areaPanel.getToolTipText());
		areaBar.setPreferredSize(new Dimension(100, 16));
		areaBar.setMaximum(Specifications.getInstance().getMaximumArea());
		areaPanel.add(areaBar, BorderLayout.CENTER);

		// height for the selected building

		heightPanel = new JPanel(new BorderLayout());
		heightPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Height (m)", TitledBorder.LEADING, TitledBorder.TOP));
		heightPanel.setToolTipText("<html>The height of the selected building<br><b>Must be within the specified range (if any).</b></html>");
		buildingSizePanel.add(heightPanel);
		heightBar = new ColorBar(Color.WHITE, Color.LIGHT_GRAY);
		heightBar.setUnit("");
		heightBar.setUnitPrefix(false);
		heightBar.setVerticalLineRepresentation(false);
		heightBar.setDecimalDigits(1);
		heightBar.setToolTipText(heightPanel.getToolTipText());
		heightBar.setPreferredSize(new Dimension(100, 16));
		heightBar.setMaximum(Specifications.getInstance().getMaximumHeight());
		heightPanel.add(heightBar, BorderLayout.CENTER);

		// cost for the selected building

		budgetPanel = new JPanel(new BorderLayout());
		budgetPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Cost ($)", TitledBorder.LEADING, TitledBorder.TOP));
		budgetPanel.setToolTipText("<html>The total material cost for the selected building<br><b>Must not exceed the limit (if specified).</b></html>");
		buildingPanel.add(budgetPanel);
		budgetBar = new ColorBar(Color.WHITE, Color.LIGHT_GRAY);
		budgetBar.setToolTipText(budgetPanel.getToolTipText());
		budgetBar.setPreferredSize(new Dimension(200, 16));
		budgetBar.setMaximum(Specifications.getInstance().getMaximumBudget());
		budgetBar.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				if (e.getClickCount() > 1)
					Cost.getInstance().showGraph();
			}
		});
		budgetPanel.add(budgetBar, BorderLayout.CENTER);

		final Component verticalGlue = Box.createVerticalGlue();
		dataPanel.add(verticalGlue);

		progressBar = new JProgressBar();
		add(progressBar, BorderLayout.SOUTH);

		JPanel target = buildingPanel;
		target.setMaximumSize(new Dimension(target.getMaximumSize().width, target.getPreferredSize().height));

		final JPanel energyTodayPanel = new JPanel();
		buildingPanel.add(energyTodayPanel);
		energyTodayPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Energy Today (kWh)", TitledBorder.LEADING, TitledBorder.TOP));
		final GridBagLayout gbl_panel_1 = new GridBagLayout();
		energyTodayPanel.setLayout(gbl_panel_1);

		final JLabel windowLabel = new JLabel("Windows");
		windowLabel.setToolTipText("Renewable energy gained through windows");
		windowLabel.setHorizontalAlignment(SwingConstants.CENTER);
		final GridBagConstraints gbc_windowLabel = new GridBagConstraints();
		gbc_windowLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_windowLabel.insets = new Insets(0, 0, 5, 5);
		gbc_windowLabel.gridx = 0;
		gbc_windowLabel.gridy = 0;
		energyTodayPanel.add(windowLabel, gbc_windowLabel);

		final JLabel solarPanelLabel = new JLabel("Solar Panels");
		solarPanelLabel.setToolTipText("Renewable energy harvested from solar panels");
		solarPanelLabel.setHorizontalAlignment(SwingConstants.CENTER);
		final GridBagConstraints gbc_solarPanelLabel = new GridBagConstraints();
		gbc_solarPanelLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_solarPanelLabel.insets = new Insets(0, 0, 5, 5);
		gbc_solarPanelLabel.gridx = 1;
		gbc_solarPanelLabel.gridy = 0;
		energyTodayPanel.add(solarPanelLabel, gbc_solarPanelLabel);

		final JLabel heatingLabel = new JLabel("Heater");
		heatingLabel.setToolTipText("Nonrenewable energy for heating the building");
		heatingLabel.setHorizontalAlignment(SwingConstants.CENTER);
		final GridBagConstraints gbc_heatingLabel = new GridBagConstraints();
		gbc_heatingLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_heatingLabel.insets = new Insets(0, 0, 5, 5);
		gbc_heatingLabel.gridx = 2;
		gbc_heatingLabel.gridy = 0;
		energyTodayPanel.add(heatingLabel, gbc_heatingLabel);

		final JLabel coolingLabel = new JLabel("AC");
		coolingLabel.setToolTipText("Nonrenewable energy for cooling the building");
		coolingLabel.setHorizontalAlignment(SwingConstants.CENTER);
		final GridBagConstraints gbc_coolingLabel = new GridBagConstraints();
		gbc_coolingLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_coolingLabel.insets = new Insets(0, 0, 5, 5);
		gbc_coolingLabel.gridx = 3;
		gbc_coolingLabel.gridy = 0;
		energyTodayPanel.add(coolingLabel, gbc_coolingLabel);

		final JLabel netEnergyLabel = new JLabel("Net");
		netEnergyLabel.setToolTipText("<html><b>Net energy cost for this building</b><br>Negative if the energy it generates exceeds the energy it consumes.</html>");
		netEnergyLabel.setHorizontalAlignment(SwingConstants.CENTER);
		final GridBagConstraints gbc_netEnergyLabel = new GridBagConstraints();
		gbc_netEnergyLabel.fill = GridBagConstraints.HORIZONTAL;
		gbc_netEnergyLabel.insets = new Insets(0, 0, 5, 0);
		gbc_netEnergyLabel.gridx = 4;
		gbc_netEnergyLabel.gridy = 0;
		energyTodayPanel.add(netEnergyLabel, gbc_netEnergyLabel);

		windowTextField = new JTextField();
		windowTextField.setToolTipText(windowLabel.getToolTipText());
		final GridBagConstraints gbc_windowTextField = new GridBagConstraints();
		gbc_windowTextField.weightx = 1.0;
		gbc_windowTextField.fill = GridBagConstraints.HORIZONTAL;
		gbc_windowTextField.insets = new Insets(0, 0, 0, 5);
		gbc_windowTextField.gridx = 0;
		gbc_windowTextField.gridy = 1;
		energyTodayPanel.add(windowTextField, gbc_windowTextField);
		windowTextField.setEditable(false);
		windowTextField.setColumns(5);

		solarPanelTextField = new JTextField();
		solarPanelTextField.setToolTipText(solarPanelLabel.getToolTipText());
		final GridBagConstraints gbc_solarPanelTextField = new GridBagConstraints();
		gbc_solarPanelTextField.weightx = 1.0;
		gbc_solarPanelTextField.fill = GridBagConstraints.HORIZONTAL;
		gbc_solarPanelTextField.insets = new Insets(0, 0, 0, 5);
		gbc_solarPanelTextField.gridx = 1;
		gbc_solarPanelTextField.gridy = 1;
		energyTodayPanel.add(solarPanelTextField, gbc_solarPanelTextField);
		solarPanelTextField.setEditable(false);
		solarPanelTextField.setColumns(5);

		heatingTextField = new JTextField();
		heatingTextField.setToolTipText(heatingLabel.getToolTipText());
		heatingTextField.setEditable(false);
		final GridBagConstraints gbc_heatingTextField = new GridBagConstraints();
		gbc_heatingTextField.weightx = 1.0;
		gbc_heatingTextField.insets = new Insets(0, 0, 0, 5);
		gbc_heatingTextField.fill = GridBagConstraints.HORIZONTAL;
		gbc_heatingTextField.gridx = 2;
		gbc_heatingTextField.gridy = 1;
		energyTodayPanel.add(heatingTextField, gbc_heatingTextField);
		heatingTextField.setColumns(5);

		coolingTextField = new JTextField();
		coolingTextField.setToolTipText(coolingLabel.getToolTipText());
		coolingTextField.setEditable(false);
		final GridBagConstraints gbc_coolingTextField = new GridBagConstraints();
		gbc_coolingTextField.weightx = 1.0;
		gbc_coolingTextField.insets = new Insets(0, 0, 0, 5);
		gbc_coolingTextField.fill = GridBagConstraints.HORIZONTAL;
		gbc_coolingTextField.gridx = 3;
		gbc_coolingTextField.gridy = 1;
		energyTodayPanel.add(coolingTextField, gbc_coolingTextField);
		coolingTextField.setColumns(5);

		netEnergyTextField = new JTextField();
		netEnergyTextField.setToolTipText(netEnergyLabel.getToolTipText());
		netEnergyTextField.setEditable(false);
		final GridBagConstraints gbc_netEnergyTextField = new GridBagConstraints();
		gbc_netEnergyTextField.weightx = 1.0;
		gbc_netEnergyTextField.fill = GridBagConstraints.HORIZONTAL;
		gbc_netEnergyTextField.gridx = 4;
		gbc_netEnergyTextField.gridy = 1;
		energyTodayPanel.add(netEnergyTextField, gbc_netEnergyTextField);
		netEnergyTextField.setColumns(5);

		energyTodayPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, energyTodayPanel.getPreferredSize().height));

		final Dimension size = heatingLabel.getMinimumSize();
		windowLabel.setMinimumSize(size);
		solarPanelLabel.setMinimumSize(size);
		coolingLabel.setMinimumSize(size);
		netEnergyLabel.setMinimumSize(size);
		target = partPanel;
		target.setMaximumSize(new Dimension(target.getMaximumSize().width, target.getPreferredSize().height));
		partPanel.setLayout(new BoxLayout(partPanel, BoxLayout.Y_AXIS));

		partPropertiesPanel = new JPanel();
		partPanel.add(partPropertiesPanel);

		partProperty1Label = new JLabel("Width:");
		partPropertiesPanel.add(partProperty1Label);

		partProperty1TextField = new JTextField();
		partProperty1TextField.setEditable(false);
		partPropertiesPanel.add(partProperty1TextField);
		partProperty1TextField.setColumns(4);

		partProperty2Label = new JLabel("Height:");
		partPropertiesPanel.add(partProperty2Label);

		partProperty2TextField = new JTextField();
		partProperty2TextField.setEditable(false);
		partPropertiesPanel.add(partProperty2TextField);
		partProperty2TextField.setColumns(4);

		partProperty3Label = new JLabel("Insolation:");
		partPropertiesPanel.add(partProperty3Label);

		partProperty3TextField = new JTextField();
		partProperty3TextField.setEditable(false);
		partPropertiesPanel.add(partProperty3TextField);
		partProperty3TextField.setColumns(4);

		partProperty4Label = new JLabel();
		partProperty4TextField = new JTextField();
		partProperty4TextField.setEditable(false);
		partProperty4TextField.setColumns(4);

	}

	public void compute(final UpdateRadiation updateRadiation) {
		if (!computeEnabled)
			return;
		updateOutsideTemperature(); // TODO: There got to be a better way to do this
		this.updateRadiation = updateRadiation;
		if (thread != null && thread.isAlive())
			computeRequest = true;
		else {
			thread = new Thread("Energy Computer") {
				@Override
				public void run() {
					do {
						computeRequest = false;
						cancel = false;
						/* since this thread can accept multiple computeRequest, cannot use updateRadiationColorMap parameter directly */
						try {
							final boolean doCompute = EnergyPanel.this.updateRadiation == UpdateRadiation.ALWAYS || (SceneManager.getInstance().isSolarColorMap() && (!alreadyRenderedHeatmap || keepHeatmapOn));
							if (doCompute) {
								alreadyRenderedHeatmap = true;
								computeNow();
								if (!cancel) {
									SceneManager.getInstance().getSolarLand().setVisible(true);
									SceneManager.getInstance().refresh();
								} else if (!keepHeatmapOn)
									turnOffCompute();
							} else
								turnOffCompute();
						} catch (final Throwable e) {
							e.printStackTrace();
							Util.reportError(e);
						}
						progress(0);
					} while (computeRequest);
					thread = null;
				}
			};
			thread.start();
		}
	}

	public void computeNow() {
		try {
			System.out.println("EnergyPanel.computeNow()");
			progressBar.setValue(0);
			progressBar.setStringPainted(false);

			updateOutsideTemperature();
			HeatLoad.getInstance().computeEnergyToday((Calendar) Heliodon.getInstance().getCalender().clone(), (Integer) insideTemperatureSpinner.getValue());

			SolarIrradiation.getInstance().compute();
			notifyPropertyChangeListeners(new PropertyChangeEvent(EnergyPanel.this, "Solar energy calculation completed", 0, 1));
			updatePartEnergy();

			// XIE: This needs to be called for trees to change texture when the month changes
			synchronized (Scene.getInstance().getParts()) {
				for (final HousePart p : Scene.getInstance().getParts())
					if (p instanceof Tree)
						p.updateTextureAndColor();
			}
//			SceneManager.getInstance().refresh();

			progressBar.setValue(100);

		} catch (final CancellationException e) {
			System.out.println("Energy compute cancelled.");
		}

	}

	// TODO: There should be a better way to do this.
	public void clearIrradiationHeatMap() {
		compute(UpdateRadiation.ONLY_IF_SLECTED_IN_GUI);
	}

	private void updateOutsideTemperature() {
		if (cityComboBox.getSelectedItem().equals(""))
			outsideTemperatureSpinner.setValue(15);
		else
			outsideTemperatureSpinner.setValue(Math.round(CityData.getInstance().computeOutsideTemperature(Heliodon.getInstance().getCalender())));
	}

	public JSpinner getDateSpinner() {
		return dateSpinner;
	}

	public JSpinner getTimeSpinner() {
		return timeSpinner;
	}

	public JSpinner getInsideTemperatureSpinner() {
		return insideTemperatureSpinner;
	}

	public void progress(final int percentage) {
		if (percentage == 0) {
			progressBar.setValue(0);
			progressBar.setStringPainted(false);
		} else {
			progressBar.setValue(percentage);
			progressBar.setStringPainted(true);
		}
	}

	public void setLatitude(final int latitude) {
		latitudeSpinner.setValue(latitude);
	}

	public int getLatitude() {
		return (Integer) latitudeSpinner.getValue();
	}

	public JSlider getColorMapSlider() {
		return colorMapSlider;
	}

	public void clearAlreadyRendered() {
		alreadyRenderedHeatmap = false;
	}

	public static void setKeepHeatmapOn(final boolean on) {
		keepHeatmapOn = on;
	}

	public void setComputeEnabled(final boolean computeEnabled) {
		this.computeEnabled = computeEnabled;
	}

	@Override
	public void addPropertyChangeListener(final PropertyChangeListener pcl) {
		propertyChangeListeners.add(pcl);
	}

	@Override
	public void removePropertyChangeListener(final PropertyChangeListener pcl) {
		propertyChangeListeners.remove(pcl);
	}

	private void notifyPropertyChangeListeners(final PropertyChangeEvent evt) {
		if (!propertyChangeListeners.isEmpty()) {
			synchronized (propertyChangeListeners) {
				for (final PropertyChangeListener x : propertyChangeListeners) {
					x.propertyChange(evt);
				}
			}
		}
	}

	public void updatePartEnergy() {
		final boolean iradiationEnabled = MainPanel.getInstance().getSolarButton().isSelected();
		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();

		if (selectedPart instanceof Foundation) {
			partProperty1Label.setText("Width:");
			partProperty2Label.setText("Length:");
			partProperty3Label.setText("Insolation:");
			partPropertiesPanel.remove(partProperty4Label);
			partPropertiesPanel.remove(partProperty4TextField);
		} else if (selectedPart instanceof Sensor) {
			partProperty1Label.setText("X:");
			partProperty2Label.setText("Y:");
			partProperty3Label.setText("Z:");
			partProperty4Label.setText("Data:");
			partPropertiesPanel.add(partProperty4Label);
			partPropertiesPanel.add(partProperty4TextField);
		} else {
			partProperty1Label.setText("Width:");
			partProperty2Label.setText("Height:");
			partProperty3Label.setText("Insolation:");
			partPropertiesPanel.remove(partProperty4Label);
			partPropertiesPanel.remove(partProperty4TextField);
		}
		partPropertiesPanel.revalidate();

		((TitledBorder) partPanel.getBorder()).setTitle("Part" + (selectedPart == null ? "" : (" - " + selectedPart.toString().substring(0, selectedPart.toString().indexOf(')') + 1))));
		partPanel.repaint();

		if (!iradiationEnabled || selectedPart == null || selectedPart instanceof Door || selectedPart instanceof Foundation)
			partProperty3TextField.setText("");
		else {
			if (selectedPart instanceof Sensor) {
				final String light = twoDecimals.format(selectedPart.getSolarPotentialToday() / selectedPart.computeArea());
				final String heatFlux = twoDecimals.format(selectedPart.getTotalHeatLoss() / selectedPart.computeArea());
				partProperty4TextField.setText(light + ", " + heatFlux);
				partProperty4TextField.setToolTipText("Light sensor: " + light + ", heat flux sensor: " + heatFlux);
			} else
				partProperty3TextField.setText(twoDecimals.format(selectedPart.getSolarPotentialToday()));
		}

		if (selectedPart != null && !(selectedPart instanceof Roof || selectedPart instanceof Floor || selectedPart instanceof Tree)) {
			if (selectedPart instanceof SolarPanel) {
				partProperty1TextField.setText(twoDecimals.format(SolarPanel.WIDTH));
				partProperty2TextField.setText(twoDecimals.format(SolarPanel.HEIGHT));
			} else if (selectedPart instanceof Sensor) {
				final ReadOnlyVector3 v = ((Sensor) selectedPart).getAbsPoint(0);
				partProperty1TextField.setText(twoDecimals.format(v.getX() * Scene.getInstance().getAnnotationScale()));
				partProperty2TextField.setText(twoDecimals.format(v.getY() * Scene.getInstance().getAnnotationScale()));
				partProperty3TextField.setText(twoDecimals.format(v.getZ() * Scene.getInstance().getAnnotationScale()));
			} else {
				partProperty1TextField.setText(twoDecimals.format(selectedPart.getAbsPoint(0).distance(selectedPart.getAbsPoint(2)) * Scene.getInstance().getAnnotationScale()));
				partProperty2TextField.setText(twoDecimals.format(selectedPart.getAbsPoint(0).distance(selectedPart.getAbsPoint(1)) * Scene.getInstance().getAnnotationScale()));
			}
		} else {
			partProperty1TextField.setText("");
			partProperty2TextField.setText("");
		}

		final Foundation selectedBuilding;
		if (selectedPart == null)
			selectedBuilding = null;
		else if (selectedPart instanceof Foundation)
			selectedBuilding = (Foundation) selectedPart;
		else
			selectedBuilding = selectedPart.getTopContainer();

		if (selectedBuilding != null) {
			if (iradiationEnabled) {
				windowTextField.setText(twoDecimals.format(selectedBuilding.getPassiveSolarToday()));
				solarPanelTextField.setText(twoDecimals.format(selectedBuilding.getPhotovoltaicToday()));
				heatingTextField.setText(twoDecimals.format(selectedBuilding.getHeatingToday()));
				coolingTextField.setText(twoDecimals.format(selectedBuilding.getCoolingToday()));
				netEnergyTextField.setText(twoDecimals.format(selectedBuilding.getTotalEnergyToday()));
			} else {
				windowTextField.setText("");
				solarPanelTextField.setText("");
				heatingTextField.setText("");
				coolingTextField.setText("");
				netEnergyTextField.setText("");
			}
			final double[] buildingGeometry = selectedBuilding.getBuildingGeometry();
			if (buildingGeometry != null) {
				heightBar.setValue((float) buildingGeometry[0]);
				areaBar.setValue((float) buildingGeometry[1]);
			} else {
				heightBar.setValue(0);
				areaBar.setValue(0);
			}
		} else {
			windowTextField.setText("");
			solarPanelTextField.setText("");
			heatingTextField.setText("");
			coolingTextField.setText("");
			netEnergyTextField.setText("");
			heightBar.setValue(0);
			areaBar.setValue(0);
		}

		heightBar.repaint();
		areaBar.repaint();

	}

	public void updateCost() {
		final HousePart selectedPart = SceneManager.getInstance().getSelectedPart();
		final Foundation selectedBuilding;
		if (selectedPart == null)
			selectedBuilding = null;
		else if (selectedPart instanceof Foundation)
			selectedBuilding = (Foundation) selectedPart;
		else
			selectedBuilding = selectedPart.getTopContainer();
		int n = 0;
		if (selectedBuilding != null)
			n = Cost.getInstance().getBuildingCost(selectedBuilding);
		budgetBar.setValue(n);
		budgetBar.repaint();
	}

	public void update() {
		updatePartEnergy();
		updateCost();
	}

	/** Apply this when the UI is set programmatically (not by the user) */
	public void requestDisableActions(final Object requester) {
		disableActionsRequester = requester;
	}

	public Object getDisableActionsRequester() {
		return disableActionsRequester;
	}

	public boolean isCancelled() {
		return cancel || computeRequest;
	}

	public void cancel() {
		cancel = true;
	}

	public JComboBox<String> getWallsComboBox() {
		return wallsComboBox;
	}

	public JComboBox<String> getDoorsComboBox() {
		return doorsComboBox;
	}

	public JComboBox<String> getWindowsComboBox() {
		return windowsComboBox;
	}

	public JComboBox<String> getRoofsComboBox() {
		return roofsComboBox;
	}

	public JComboBox<String> getCityComboBox() {
		return cityComboBox;
	}

	public JComboBox<String> getSolarPanelEfficiencyComboBox() {
		return solarPanelEfficiencyComboBox;
	}

	public JComboBox<String> getWindowSHGCComboBox() {
		return windowSHGCComboBox;
	}

	public void updateBudgetBar() {
		String t = "Cost (";
		t += Specifications.getInstance().isBudgetEnabled() ? "\u2264 $" + noDecimals.format(Specifications.getInstance().getMaximumBudget()) : "$";
		t += ")";
		budgetPanel.setBorder(BorderFactory.createTitledBorder(UIManager.getBorder("TitledBorder.border"), t, TitledBorder.LEADING, TitledBorder.TOP));
		budgetBar.setEnabled(Specifications.getInstance().isBudgetEnabled());
		budgetBar.setMaximum(Specifications.getInstance().getMaximumBudget());
		budgetBar.repaint();
	}

	public void updateAreaBar() {
		String t = "Area (";
		if (Specifications.getInstance().isAreaEnabled())
			t += twoDecimals.format(Specifications.getInstance().getMinimumArea()) + " - " + twoDecimals.format(Specifications.getInstance().getMaximumArea());
		t += "\u33A1)";
		areaPanel.setBorder(BorderFactory.createTitledBorder(UIManager.getBorder("TitledBorder.border"), t, TitledBorder.LEADING, TitledBorder.TOP));
		areaBar.setEnabled(Specifications.getInstance().isAreaEnabled());
		areaBar.setMinimum(Specifications.getInstance().getMinimumArea());
		areaBar.setMaximum(Specifications.getInstance().getMaximumArea());
		areaBar.repaint();
	}

	public void updateHeightBar() {
		String t = "Height (";
		if (Specifications.getInstance().isHeightEnabled())
			t += twoDecimals.format(Specifications.getInstance().getMinimumHeight()) + " - " + twoDecimals.format(Specifications.getInstance().getMaximumHeight());
		t += "m)";
		heightPanel.setBorder(BorderFactory.createTitledBorder(UIManager.getBorder("TitledBorder.border"), t, TitledBorder.LEADING, TitledBorder.TOP));
		heightBar.setEnabled(Specifications.getInstance().isHeightEnabled());
		heightBar.setMinimum(Specifications.getInstance().getMinimumHeight());
		heightBar.setMaximum(Specifications.getInstance().getMaximumHeight());
		heightBar.repaint();
	}

	public void turnOffCompute() {
		if (SceneManager.getInstance().isSolarColorMap())
			MainPanel.getInstance().getSolarButton().setSelected(false);

		int numberOfHouses = 0;
		synchronized (Scene.getInstance().getParts()) { // XIE: This needs to be synchronized to avoid concurrent modification exceptions
			for (final HousePart part : Scene.getInstance().getParts()) {
				if (part instanceof Foundation && !part.getChildren().isEmpty() && !part.isFrozen())
					numberOfHouses++;
				if (numberOfHouses >= 2)
					break;
			}
			for (final HousePart part : Scene.getInstance().getParts())
				if (part instanceof Foundation)
					((Foundation) part).setSolarLabelValue(numberOfHouses >= 2 && !part.getChildren().isEmpty() && !part.isFrozen() ? -1 : -2);
		}
		SceneManager.getInstance().getSolarLand().setVisible(false);
		Scene.getInstance().redrawAll();
	}

}
