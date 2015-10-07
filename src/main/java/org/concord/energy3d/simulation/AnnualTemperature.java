package org.concord.energy3d.simulation;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.util.Calendar;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.shapes.Heliodon;

/**
 * This graph shows the annual variation of air and ground temperatures for the current location.
 * 
 * @author Charles Xie
 * 
 */

public class AnnualTemperature extends JPanel {

	private static final long serialVersionUID = 1L;
	private static final int CIRCLE = 0;
	private static final int DIAMOND = 1;

	private int top = 50, right = 50, bottom = 80, left = 90;
	private double xmin = 0;
	private double xmax = 11;
	private double ymin = 1000;
	private double ymax = -1000;
	private double dx;
	private double dy;
	private int symbolSize = 8;
	private int numberOfTicks = 12;
	private String xAxisLabel = "Month";
	private String yAxisLabel = "Temperature (\u00b0C)";
	private BasicStroke dashed = new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[] { 2f }, 0.0f);
	private BasicStroke thin = new BasicStroke(1);
	private BasicStroke thick = new BasicStroke(2);
	private String city;

	private double[] lowestAirTemperature;
	private double[] highestAirTemperature;
	private double[] lowestGroundTemperatureSoil;
	private double[] highestGroundTemperatureSoil;
	private double[] lowestGroundTemperatureDeep;
	private double[] highestGroundTemperatureDeep;

	public AnnualTemperature() {

		super();
		setPreferredSize(new Dimension(600, 400));
		setBackground(Color.WHITE);

		int n = AnnualAnalysis.MONTHS.length;
		lowestAirTemperature = new double[n];
		highestAirTemperature = new double[n];
		lowestGroundTemperatureSoil = new double[n];
		highestGroundTemperatureSoil = new double[n];
		lowestGroundTemperatureDeep = new double[n];
		highestGroundTemperatureDeep = new double[n];

		city = (String) EnergyPanel.getInstance().getCityComboBox().getSelectedItem();
		Calendar today = (Calendar) Heliodon.getInstance().getCalender().clone();
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.DAY_OF_MONTH, 1);
		int count = 0;
		int lag = Ground.getInstance().getDailyLagInMinutes();
		for (int m : AnnualAnalysis.MONTHS) {
			today.set(Calendar.MONTH, m);
			double[] r = Weather.computeOutsideTemperature(today, city);
			lowestAirTemperature[count] = r[0];
			highestAirTemperature[count] = r[1];
			double amp = 0.5 * (r[1] - r[0]);
			int day = today.get(Calendar.DAY_OF_YEAR);
			lowestGroundTemperatureSoil[count] = Ground.getInstance().getFloorTemperature(day, lag, amp, 1); // (12 am + lag) is the coldest time
			highestGroundTemperatureSoil[count] = Ground.getInstance().getFloorTemperature(day, lag + 720, amp, 1); // (12 pm + lag) is the hottest time
			lowestGroundTemperatureDeep[count] = Ground.getInstance().getFloorTemperature(day, lag, amp, 50);
			highestGroundTemperatureDeep[count] = Ground.getInstance().getFloorTemperature(day, lag + 720, amp, 50);
			count++;
		}

	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		update(g);
	}

	public void update(Graphics g) {

		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

		Dimension dim = getSize();
		int width = dim.width;
		int height = dim.height;
		g2.setColor(getBackground());
		g2.fillRect(0, 0, width, height);
		g2.setColor(Color.GRAY);
		g2.drawRect(left / 2, top / 2, width - (left + right) / 2, height - (top + bottom) / 2);

		g2.setColor(Color.BLACK);
		g2.setFont(new Font("Arial", Font.PLAIN, 8));
		float tickWidth = (float) (width - left - right) / (float) (numberOfTicks - 1);
		float xTick;
		for (int i = 0; i < numberOfTicks; i++) {
			String s = AnnualGraph.THREE_LETTER_MONTH[i];
			int sWidth = g2.getFontMetrics().stringWidth(s);
			xTick = left + tickWidth * i;
			g2.drawString(s, xTick - sWidth / 2, height - bottom / 2 + 16);
			g2.drawLine((int) xTick, height - bottom / 2, (int) xTick, height - bottom / 2 - 4);
		}
		g2.setFont(new Font("Arial", Font.PLAIN, 10));
		int xAxisLabelWidth = g2.getFontMetrics().stringWidth(xAxisLabel);
		int yAxisLabelWidth = g2.getFontMetrics().stringWidth(yAxisLabel);
		g2.drawString(xAxisLabel, (width - xAxisLabelWidth) / 2, height - 8);
		g2.rotate(-Math.PI / 2, 16, (height + yAxisLabelWidth) / 2);
		g2.drawString(yAxisLabel, 16, (height + yAxisLabelWidth) / 2);
		g2.rotate(Math.PI / 2, 16, (height + yAxisLabelWidth) / 2);

		calculateBounds();
		int digits = String.valueOf(Math.round(ymax - ymin)).length() - 1;
		digits = (int) Math.pow(10, digits);
		int i1 = (int) Math.round(ymin / digits) - 2;
		int i2 = (int) Math.round(ymax / digits) + 2;
		int hVal, hPos;
		for (int i = i1; i <= i2; i++) {
			hVal = i * digits;
			hPos = (int) (getHeight() - top - (hVal - ymin) * dy);
			if (hPos > top / 2 && hPos < getHeight() - bottom / 2)
				drawHorizontalLine(g2, hPos, Integer.toString(hVal));
		}

		drawCurve(g2, lowestAirTemperature, Color.BLUE, CIRCLE, thick);
		drawCurve(g2, highestAirTemperature, Color.RED, CIRCLE, thick);
		drawCurve(g2, lowestGroundTemperatureSoil, Color.BLUE, DIAMOND, thick);
		drawCurve(g2, highestGroundTemperatureSoil, Color.RED, DIAMOND, thick);
		drawCurve(g2, lowestGroundTemperatureDeep, Color.GRAY, -1, dashed);
		drawCurve(g2, highestGroundTemperatureDeep, Color.GRAY, -1, dashed);

		drawLegends(g2);

		g2.setFont(new Font("Arial", Font.BOLD, 14));
		FontMetrics fm = g2.getFontMetrics();
		g2.drawString(city, (width - fm.stringWidth(city)) / 2, 20);

	}

	void drawLegends(Graphics2D g2) {

		g2.setFont(new Font("Arial", Font.PLAIN, 10));
		g2.setStroke(thin);
		int x0 = left / 2 + 20;
		int y0 = top - 10;

		g2.setColor(Color.RED);
		g2.setStroke(thick);
		g2.drawLine(x0 - 7, y0 + 3, x0 + 13, y0 + 3);
		g2.setStroke(thin);
		Graph.drawCircle(g2, x0, y0, 6, Color.WHITE);
		g2.drawString("Air (Highest)", x0 + 20, y0 + 8);

		y0 += 14;
		g2.setColor(Color.BLUE);
		g2.setStroke(thick);
		g2.drawLine(x0 - 7, y0 + 3, x0 + 13, y0 + 3);
		g2.setStroke(thin);
		Graph.drawCircle(g2, x0, y0, 6, Color.WHITE);
		g2.drawString("Air (Lowest)", x0 + 20, y0 + 8);

		y0 += 14;
		g2.setColor(Color.RED);
		g2.setStroke(thick);
		g2.drawLine(x0 - 7, y0 + 4, x0 + 13, y0 + 4);
		g2.setStroke(thin);
		Graph.drawDiamond(g2, x0 + 3, y0 + 4, 4, Color.WHITE);
		g2.drawString("Ground (1m deep, Highest)", x0 + 20, y0 + 8);

		y0 += 14;
		g2.setColor(Color.BLUE);
		g2.setStroke(thick);
		g2.drawLine(x0 - 7, y0 + 4, x0 + 13, y0 + 4);
		g2.setStroke(thin);
		Graph.drawDiamond(g2, x0 + 3, y0 + 4, 4, Color.WHITE);
		g2.drawString("Ground (1m deep, Lowest)", x0 + 20, y0 + 8);

		y0 += 14;
		g2.setColor(Color.GRAY);
		g2.setStroke(dashed);
		g2.drawLine(x0 - 7, y0 + 4, x0 + 13, y0 + 4);
		g2.setColor(Color.BLACK);
		g2.drawString("Ground (50m deep)", x0 + 20, y0 + 8);

	}

	private void drawCurve(Graphics2D g2, double[] data, Color color, int symbol, BasicStroke stroke) {

		double dataX, dataY;
		Path2D.Float path = new Path2D.Float();
		for (int i = 0; i < data.length; i++) {
			dataX = left + dx * i;
			dataY = getHeight() - top - (data[i] - ymin) * dy;
			if (i == 0) {
				path.moveTo(dataX, dataY);
			} else {
				path.lineTo(dataX, dataY);
			}
		}
		g2.setColor(color);
		g2.setStroke(stroke);
		g2.draw(path);

		if (symbol != -1) {
			g2.setStroke(thin);
			for (int i = 0; i < data.length; i++) {
				dataX = left + dx * i;
				dataY = getHeight() - top - (data[i] - ymin) * dy;
				switch (symbol) {
				case CIRCLE:
					Graph.drawCircle(g2, (int) Math.round(dataX - symbolSize / 2 + 1), (int) Math.round(dataY - symbolSize / 2 + 1), symbolSize - 2, Color.WHITE);
					break;
				case DIAMOND:
					Graph.drawDiamond(g2, (int) dataX, (int) dataY, symbolSize / 2, Color.WHITE);
					break;
				}
			}
		}

	}

	private void drawHorizontalLine(Graphics2D g2, int yValue, String yLabel) {
		g2.setStroke(thin);
		g2.setColor(Color.LIGHT_GRAY);
		g2.drawLine(left / 2, yValue, getWidth() - right / 2, yValue);
		g2.setColor(Color.BLACK);
		int yLabelWidth = g2.getFontMetrics().stringWidth(yLabel);
		g2.drawString(yLabel, left / 2 - 5 - yLabelWidth, yValue + 4);
	}

	private void calculateBounds() {
		for (double t : lowestAirTemperature) {
			if (t < ymin)
				ymin = t;
		}
		for (double t : highestAirTemperature) {
			if (t > ymax)
				ymax = t;
		}
		dx = (double) (getWidth() - left - right) / (xmax - xmin);
		dy = (double) (getHeight() - top - bottom) / (ymax - ymin);
	}

	public void show() {

		final JDialog dialog = new JDialog(MainFrame.getInstance(), "Temperature", true);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		final JPanel contentPane = new JPanel(new BorderLayout());
		dialog.setContentPane(contentPane);

		final JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createEtchedBorder());
		contentPane.add(panel, BorderLayout.CENTER);

		panel.add(this, BorderLayout.CENTER);

		final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
		contentPane.add(buttonPanel, BorderLayout.SOUTH);

		JButton button = new JButton("Close");
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(final ActionEvent e) {
				dialog.dispose();
			}
		});
		buttonPanel.add(button);

		dialog.addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(final WindowEvent e) {
				dialog.dispose();
			}
		});

		dialog.pack();
		dialog.setLocationRelativeTo(MainFrame.getInstance());
		dialog.setVisible(true);

	}

}
