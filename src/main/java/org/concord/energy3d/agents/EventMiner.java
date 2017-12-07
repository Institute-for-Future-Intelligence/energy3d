package org.concord.energy3d.agents;

import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.undo.ChangeDateCommand;
import org.concord.energy3d.undo.ChangePartUValueCommand;
import org.concord.energy3d.util.Util;

/**
 * @author Charles Xie
 *
 */
public class EventMiner implements Agent {

	final String name;
	String eventString;
	String conformanceRegex = "(U.*?A)+?";
	String violationRegex = "(U.*?[CY]+?.*?A)+?";

	TreeMap<String, FeedbackPool> singleIndicatorFeedbackMap;
	FeedbackPool feedbackOnDailyAnalysis;
	FeedbackPool feedbackOnUValueChange;
	FeedbackPool feedbackOnDataCollector;
	FeedbackPool feedbackOnTargetBehavior;
	FeedbackPool feedbackOnTargetViolation;

	public EventMiner(final String name) {
		this.name = name;

		singleIndicatorFeedbackMap = new TreeMap<String, FeedbackPool>();

		feedbackOnDailyAnalysis = new FeedbackPool(1, 2);
		feedbackOnDailyAnalysis.setItem(0, 0, "Try analyzing the energy use of the house using the menu<br>Analysis > Buildings > Dail Energy Analysis for Selected Building...");
		feedbackOnDailyAnalysis.setItem(0, 1, "Did you forget to run daily energy analysis?");
		singleIndicatorFeedbackMap.put("A+?", feedbackOnDailyAnalysis);

		feedbackOnDataCollector = new FeedbackPool(1, 1);
		feedbackOnDataCollector.setItem(0, 0, "Have you input enough data in the table?");
		singleIndicatorFeedbackMap.put("D{2,}?", feedbackOnDataCollector);

		feedbackOnUValueChange = new FeedbackPool(1, 2);
		feedbackOnUValueChange.setItem(0, 0, "Have you selected a wall and changed its U-value?<br>Try right-clicking a wall and select \"Insulation...\" from the popup menu.");
		feedbackOnUValueChange.setItem(0, 1, "Your task is to investigate how changing U-value of a wall affects the energy use<br>of the house. But you haven't adjusted the U-value.");
		singleIndicatorFeedbackMap.put("U+?", feedbackOnUValueChange);

		feedbackOnTargetBehavior = new FeedbackPool(4, 1);
		feedbackOnTargetBehavior.setItem(0, 0, "You should run a daily energy analysis after changing U-value.");
		feedbackOnTargetBehavior.setItem(1, 0, "You only analyzed U-value change once.<br>Is it sufficient to draw a conclusion?");
		feedbackOnTargetBehavior.setItem(2, 0, "You have run two analyses after changing U-value.<br>Did you compare the results to find the relationship<br>between the difference of energy use and the change<br>of the U-value?");
		feedbackOnTargetBehavior.setItem(3, 0, "You have run {COUNT_PATTERN} analyses after changing U-value.<br>What relationship between the energy use of the house<br>and the U-value of the wall did you find?");

		feedbackOnTargetViolation = new FeedbackPool(1, 1);
		feedbackOnTargetViolation.setItem(0, 0, "You changed other variables than U-value that may invalidate your result.");

	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void sense(final MyEvent e) {
		eventString = EventUtil.eventsToString(new Class[] { AnalysisEvent.class, DataCollectionEvent.class, ChangePartUValueCommand.class, ChangeDateCommand.class }, 10000, null);
		System.out.println(this + " Sensing:" + e.getName() + ">>> " + eventString);
	}

	private int countMatch(final String regex) {
		return Util.countMatch(Pattern.compile(regex).matcher(eventString));
	}

	public String checkSingleIndicators() {
		String s = "";
		for (final String regex : singleIndicatorFeedbackMap.keySet()) {
			if (countMatch(regex) == 0) {
				final FeedbackPool f = singleIndicatorFeedbackMap.get(regex);
				s += f.getCurrentItem(0);
				f.forward(0);
				break;
			}
		}
		return s;
	}

	private String lastMatch(final String regex, final String string) {
		final Matcher matcher = Pattern.compile(regex).matcher(string);
		String lastMatch = null;
		while (matcher.find()) {
			lastMatch = matcher.group();
		}
		return lastMatch;
	}

	@Override
	public void actuate() {
		System.out.println(this + " Actuating: " + eventString);
		String msg = checkSingleIndicators();
		boolean violated = false;
		if (msg.equals("")) {
			String s = "";
			final int c = countMatch(conformanceRegex);
			if (c != 0) {
				final String v = lastMatch(conformanceRegex, eventString);
				if (v != null) {
					if (Pattern.compile(violationRegex).matcher(v).find()) {
						violated = true;
					}
				}
				if (violated) {
					s = feedbackOnTargetViolation.getCurrentItem(0);
				} else {
					s = feedbackOnTargetBehavior.getCurrentItem(c).replaceAll("\\{COUNT_PATTERN\\}", c + "");
				}
			} else {
				s = feedbackOnTargetBehavior.getCurrentItem(0);
			}
			msg += s;
		}
		if (violated) {
			JOptionPane.showMessageDialog(MainFrame.getInstance(), "<html>" + msg + "</html>", "Warning", JOptionPane.WARNING_MESSAGE);
		} else {
			JOptionPane.showMessageDialog(MainFrame.getInstance(), "<html>" + msg + "</html>", "Advice", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	MyEvent idChangeEvent() {
		final List<MyEvent> u = EventUtil.getEvents(ChangePartUValueCommand.class);
		if (u.size() < 2) {
			return null;
		}
		long oldId = -1;
		long newId = -1;
		for (final MyEvent x : u) {
			if (x instanceof ChangePartUValueCommand) {
				final ChangePartUValueCommand command = (ChangePartUValueCommand) x;
				newId = command.getPart().getId();
				if (oldId == -1) { // first
					oldId = newId;
				} else {
					if (newId != oldId) {
						return x;
					}
				}
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return name;
	}

}
