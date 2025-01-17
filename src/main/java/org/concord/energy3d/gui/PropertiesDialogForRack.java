package org.concord.energy3d.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SpringLayout;
import javax.swing.border.EmptyBorder;

import org.concord.energy3d.model.Rack;
import org.concord.energy3d.model.SolarPanel;
import org.concord.energy3d.simulation.PvModuleSpecs;
import org.concord.energy3d.util.SpringUtilities;

/**
 * @author Charles Xie
 */
class PropertiesDialogForRack extends PropertiesDialogFactory {

    static JDialog getDialog(final Rack rack) {

        final SolarPanel solarPanel = rack.getSolarPanel();

        final JDialog dialog = new JDialog(MainFrame.getInstance(), "Solar Panel", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        final String info = rack.toString().substring(0, rack.toString().indexOf(')') + 1);
        dialog.setTitle("Properties - " + info);

        dialog.getContentPane().setLayout(new BorderLayout());
        final JPanel panel = new JPanel(new SpringLayout());
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        final JScrollPane scroller = new JScrollPane(panel);
        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.setPreferredSize(new Dimension(400, 300));
        dialog.getContentPane().add(scroller, BorderLayout.CENTER);

        final PvModuleSpecs specs = solarPanel.getPvModuleSpecs();

        int i = 0;

        panel.add(new JLabel("Solar Panel Manufacturer: "));
        final JTextField brandField = new JTextField(specs.getBrand());
        brandField.setEditable(false);
        panel.add(brandField);
        i++;

        panel.add(new JLabel("Solar Panel Model: "));
        final JTextField modelField = new JTextField(specs.getModel());
        modelField.setEditable(false);
        panel.add(modelField);
        i++;

        panel.add(new JLabel("Solar Panels: "));
        final int[] nxny = rack.getSolarPanelRowAndColumnNumbers();
        final JTextField layoutField = new JTextField(nxny[0] + "\u00D7" + nxny[1] + " = " + rack.getNumberOfSolarPanels());
        layoutField.setEditable(false);
        panel.add(layoutField);
        i++;

        panel.add(new JLabel("Solar Panel Color: "));
        final JTextField colorField = new JTextField(specs.getColor());
        colorField.setEditable(false);
        panel.add(colorField);
        i++;

        panel.add(new JLabel("Solar Cell Type: "));
        final JTextField cellTypeField = new JTextField(specs.getCellType());
        cellTypeField.setEditable(false);
        panel.add(cellTypeField);
        i++;

        panel.add(new JLabel("Solar Cell Efficiency: "));
        final JTextField cellEfficiencyField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getCelLEfficiency() * 100) + "%");
        cellEfficiencyField.setEditable(false);
        panel.add(cellEfficiencyField);
        i++;

        panel.add(new JLabel("Solar Panel Dimension: "));
        final JTextField dimensionField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getLength()) + "\u00D7" +
                PopupMenuFactory.threeDecimalsFormat.format(specs.getWidth()) + "\u00D7" + PopupMenuFactory.threeDecimalsFormat.format(specs.getThickness()) + " m");
        dimensionField.setEditable(false);
        panel.add(dimensionField);
        i++;

        panel.add(new JLabel("Solar Cells on Each Panel: "));
        final JTextField cellsField = new JTextField(specs.getLayout().width + "\u00D7" + specs.getLayout().height);
        cellsField.setEditable(false);
        panel.add(cellsField);
        i++;

        panel.add(new JLabel("Solar Panel Maximal Power (Pmax): "));
        final JTextField pmaxField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getPmax()) + " W");
        pmaxField.setEditable(false);
        panel.add(pmaxField);
        i++;

        panel.add(new JLabel("Voltage at Maximal Power Point (Vmpp): "));
        final JTextField vmppField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getVmpp()) + " V");
        vmppField.setEditable(false);
        panel.add(vmppField);
        i++;

        panel.add(new JLabel("Current at Maximal Power Point (Impp): "));
        final JTextField imppField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getImpp()) + " A");
        imppField.setEditable(false);
        panel.add(imppField);
        i++;

        panel.add(new JLabel("Voltage at Open Circuit (Voc): "));
        final JTextField vocField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getVoc()) + " V");
        vocField.setEditable(false);
        panel.add(vocField);
        i++;

        panel.add(new JLabel("Current at Short Circuit (Isc): "));
        final JTextField iscField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getIsc()) + " A");
        iscField.setEditable(false);
        panel.add(iscField);
        i++;

        panel.add(new JLabel("Nominal Operating Cell Temperature (NOCT): "));
        final JTextField noctField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getNoct()) + " \u00B0C");
        noctField.setEditable(false);
        panel.add(noctField);
        i++;

        panel.add(new JLabel("Temperature Coefficient of Power: "));
        final JTextField pmaxTcField = new JTextField(PopupMenuFactory.sixDecimalsFormat.format(specs.getPmaxTc()) + "%/\u00B0C");
        pmaxTcField.setEditable(false);
        panel.add(pmaxTcField);
        i++;

        panel.add(new JLabel("Single Solar Panel Weight: "));
        final JTextField weightField = new JTextField(PopupMenuFactory.threeDecimalsFormat.format(specs.getWeight()) + " kg");
        weightField.setEditable(false);
        panel.add(weightField);
        i++;

        panel.add(new JLabel("Tracker: "));
        final JTextField trackerField = new JTextField(rack.getTrackerName() == null ? "None" : rack.getTrackerName());
        trackerField.setEditable(false);
        panel.add(trackerField);
        i++;

        SpringUtilities.makeCompactGrid(panel, i, 2, 4, 4, 4, 4);

        final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        final JButton button = new JButton("Close");
        button.addActionListener(e -> dialog.dispose());
        buttonPanel.add(button);

        dialog.pack();

        return dialog;

    }

}