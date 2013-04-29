package org.concord.energy3d.gui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Hashtable;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.FocusManager;
import javax.swing.ImageIcon;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSplitPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;
import javax.swing.ToolTipManager;

import org.concord.energy3d.scene.PrintController;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.scene.SceneManager.Operation;
import org.concord.energy3d.util.Config;

public class MainPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final MainPanel instance = new MainPanel();
	private MainFrame mainFrame;
	private JToolBar appToolbar = null;
	private JToggleButton selectButton = null;
	private JToggleButton wallButton = null;
	private JToggleButton doorButton = null;
	private JToggleButton roofButton = null;
	private JToggleButton windowButton = null;
	private JToggleButton platformButton = null;
	private JToggleButton lightButton = null;
	private JToggleButton rotAnimButton = null;
	private JToggleButton floorButton = null;
	private JToggleButton roofHipButton = null;
	private JToggleButton resizeButton = null;
	private JToggleButton heliodonButton = null;
	private JToggleButton sunAnimButton = null;
	private JToggleButton annotationToggleButton;
	private JToggleButton previewButton = null;
	private JToggleButton roofCustomButton = null;
	private JToggleButton zoomButton = null;
	private JToggleButton roofGableButton = null;
	private JSplitPane splitPane;
	private EnergyPanel energyPanel;
	private JPanel canvasPanel;
	private JToggleButton energyToggleButton;

	final static Map<String, Integer> cityLatitute = new Hashtable<String, Integer>();
	static {
		cityLatitute.put("Moscow", 55);
		cityLatitute.put("Ottawa", 45);
		cityLatitute.put("Boston", 42);
		cityLatitute.put("Beijing", 39);
		cityLatitute.put("Washington DC", 38);
		cityLatitute.put("Tehran", 35);
		cityLatitute.put("Los Angeles", 34);
		cityLatitute.put("Miami", 25);
		cityLatitute.put("Mexico City", 19);
		cityLatitute.put("Singapore", 1);
		cityLatitute.put("Sydney", -33);
		cityLatitute.put("Buenos Aires", -34);
	}

	public static MainPanel getInstance() {
		return instance;
	}

	public void setMainFrame(final MainFrame mainFrame) {
		this.mainFrame = mainFrame;
	}

	public MainFrame getMainFrame() {
		return mainFrame;
	}

	/**
	 * This is the default constructor
	 */
	private MainPanel() {
		super();
		System.out.println("Version: " + Config.VERSION);
		System.out.print("Initiating GUI Panel...");
		final long time = System.nanoTime();
		ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
		ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);
		initialize();
		System.out.println("done");
		System.out.println("time = " + (System.nanoTime() - time) / 1000000000.0);
	}

	/**
	 * This method initializes this
	 *
	 * @return void
	 */
	private void initialize() {
		JPopupMenu.setDefaultLightWeightPopupEnabled(false);
		this.setSize(1000, 300);
		setLayout(new BorderLayout());
		this.add(getAppToolbar(), BorderLayout.NORTH);
		if (Config.EXPERIMENT)
			add(getSplitPane(), BorderLayout.CENTER);
		else
			add(getCanvasPanel(), BorderLayout.CENTER);
	}

	/**
	 * This method initializes appToolbar
	 *
	 * @return javax.swing.JToolBar
	 */
	private JToolBar getAppToolbar() {
		if (appToolbar == null) {
			appToolbar = new JToolBar();
			final boolean showEditTools = !Config.isHeliodonMode();
			appToolbar.add(getSelectButton());
			if (showEditTools)
				appToolbar.add(getResizeButton());
			appToolbar.add(getZoomButton());
			if (showEditTools) {
				appToolbar.addSeparator();
				appToolbar.add(getPlatformButton());
				appToolbar.add(getWallButton());
				appToolbar.add(getDoorButton());
				appToolbar.add(getWindowButton());
				appToolbar.add(getRoofButton());
				appToolbar.add(getRoofHipButton());
				appToolbar.add(getRoofCustomButton());
				appToolbar.add(getRoofGableButton());
				appToolbar.add(getFloorButton());
			}
			appToolbar.addSeparator();
			appToolbar.add(getLightButton());
			appToolbar.add(getHeliodonButton());
			appToolbar.add(getSunAnimButton());
			if (showEditTools) {
				appToolbar.addSeparator();
				appToolbar.add(getRotAnimButton());
				appToolbar.addSeparator();
			} else
				appToolbar.add(getRotAnimButton());

			appToolbar.add(getAnnotationToggleButton());
			if (showEditTools)
				appToolbar.add(getPreviewButton());

			final ButtonGroup bg = new ButtonGroup();
			bg.add(selectButton);
			bg.add(zoomButton);
			appToolbar.add(getEnergyToggleButton());
			if (showEditTools) {
				bg.add(resizeButton);
				bg.add(platformButton);
				bg.add(wallButton);
				bg.add(doorButton);
				bg.add(windowButton);
				bg.add(roofButton);
				bg.add(roofHipButton);
				bg.add(roofCustomButton);
				bg.add(floorButton);
				bg.add(roofGableButton);
			}
		}
		return appToolbar;
	}

	/**
	 * This method initializes selectButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	public JToggleButton getSelectButton() {
		if (selectButton == null) {
			selectButton = new JToggleButton();
			selectButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			selectButton.setSelected(true);
			selectButton.setToolTipText("Select");
			selectButton.setIcon(new ImageIcon(MainPanel.class.getResource("icons/select.png")));
			selectButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.SELECT);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return selectButton;
	}

	/**
	 * This method initializes wallButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getWallButton() {
		if (wallButton == null) {
			wallButton = new JToggleButton();
			wallButton.setIcon(new ImageIcon(getClass().getResource("icons/wall.png")));
			wallButton.setToolTipText("Draw wall");
			wallButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_WALL);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			wallButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return wallButton;
	}

	/**
	 * This method initializes doorButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getDoorButton() {
		if (doorButton == null) {
			doorButton = new JToggleButton();
			doorButton.setText("");
			doorButton.setToolTipText("Draw door");
			doorButton.setIcon(new ImageIcon(getClass().getResource("icons/door.png")));
			doorButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_DOOR);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			doorButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return doorButton;
	}

	/**
	 * This method initializes roofButton
	 *
	 * @return javax.swing.JButton
	 */
	private JToggleButton getRoofButton() {
		if (roofButton == null) {
			roofButton = new JToggleButton();
			roofButton.setIcon(new ImageIcon(getClass().getResource("icons/roof_pyramid.png")));
			roofButton.setToolTipText("Draw pyramid roof");
			roofButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_ROOF);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			roofButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return roofButton;
	}

	/**
	 * This method initializes windowButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getWindowButton() {
		if (windowButton == null) {
			windowButton = new JToggleButton();
			windowButton.setIcon(new ImageIcon(getClass().getResource("icons/window.png")));
			windowButton.setToolTipText("Draw window");
			windowButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_WINDOW);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			windowButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return windowButton;
	}

	/**
	 * This method initializes platformButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getPlatformButton() {
		if (platformButton == null) {
			platformButton = new JToggleButton();
			platformButton.setIcon(new ImageIcon(getClass().getResource("icons/foundation.png")));
			platformButton.setToolTipText("Draw platform");
			platformButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_FOUNDATION);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			platformButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return platformButton;
	}

	/**
	 * This method initializes lightButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getLightButton() {
		if (lightButton == null) {
			lightButton = new JToggleButton();
			lightButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			lightButton.setIcon(new ImageIcon(getClass().getResource("icons/shadow.png")));
			lightButton.setToolTipText("Show shadows");
			lightButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					if (mainFrame != null) {
						mainFrame.getShadeMenu().setSelected(lightButton.isSelected());
						mainFrame.getShadowMenu().setSelected(lightButton.isSelected());
					} else {
						SceneManager.getInstance().setShading(lightButton.isSelected());
						SceneManager.getInstance().setShadow(lightButton.isSelected());
					}
					final boolean showSunTools = lightButton.isSelected() || heliodonButton.isSelected();
					sunAnimButton.setEnabled(showSunTools);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return lightButton;
	}

	/**
	 * This method initializes rotAnimButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getRotAnimButton() {
		if (rotAnimButton == null) {
			rotAnimButton = new JToggleButton();
			rotAnimButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			rotAnimButton.setIcon(new ImageIcon(getClass().getResource("icons/rotate.png")));
			rotAnimButton.setToolTipText("Animate scene rotation");
			rotAnimButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().toggleRotation();
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return rotAnimButton;
	}

	/**
	 * This method initializes floorButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getFloorButton() {
		if (floorButton == null) {
			floorButton = new JToggleButton();
			floorButton.setIcon(new ImageIcon(getClass().getResource("icons/floor.png")));
			floorButton.setToolTipText("Draw floor");
			floorButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_FLOOR);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			floorButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return floorButton;
	}

	/**
	 * This method initializes roofHipButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getRoofHipButton() {
		if (roofHipButton == null) {
			roofHipButton = new JToggleButton();
			roofHipButton.setIcon(new ImageIcon(getClass().getResource("icons/roof_hip.png")));
			roofHipButton.setToolTipText("Draw hip roof");
			roofHipButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_ROOF_HIP);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			roofHipButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return roofHipButton;
	}

	/**
	 * This method initializes resizeButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getResizeButton() {
		if (resizeButton == null) {
			resizeButton = new JToggleButton();
			resizeButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			resizeButton.setIcon(new ImageIcon(getClass().getResource("icons/resize.png")));
			resizeButton.setToolTipText("Resize house");
			resizeButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(Operation.RESIZE);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return resizeButton;
	}

	/**
	 * This method initializes heliodonButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	public JToggleButton getHeliodonButton() {
		if (heliodonButton == null) {
			heliodonButton = new JToggleButton();
			heliodonButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			heliodonButton.setIcon(new ImageIcon(getClass().getResource("icons/sun_heliodon.png")));
			heliodonButton.setToolTipText("Show sun heliodon");
			heliodonButton.addItemListener(new java.awt.event.ItemListener() {
				@Override
				public void itemStateChanged(final java.awt.event.ItemEvent e) {
					SceneManager.getInstance().setHeliodonControl(heliodonButton.isSelected());
					final boolean showSunTools = lightButton.isSelected() || heliodonButton.isSelected();
					sunAnimButton.setEnabled(showSunTools);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return heliodonButton;
	}

	/**
	 * This method initializes sunAnimButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getSunAnimButton() {
		if (sunAnimButton == null) {
			sunAnimButton = new JToggleButton();
			sunAnimButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			sunAnimButton.setIcon(new ImageIcon(getClass().getResource("icons/sun_anim.png")));
			sunAnimButton.setEnabled(false);
			sunAnimButton.setToolTipText("Animate sun");
			sunAnimButton.addItemListener(new java.awt.event.ItemListener() {
				@Override
				public void itemStateChanged(final java.awt.event.ItemEvent e) {
					SceneManager.getInstance().setSunAnim(sunAnimButton.isSelected());
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return sunAnimButton;
	}

	/**
	 * This method initializes previewButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	public JToggleButton getPreviewButton() {
		if (previewButton == null) {
			previewButton = new JToggleButton();
			previewButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			previewButton.setIcon(new ImageIcon(getClass().getResource("icons/print_preview.png")));
			previewButton.setToolTipText("Preview printable parts");
			// must be ItemListner to be triggered when selection is changed by code
			previewButton.addItemListener(new java.awt.event.ItemListener() {
				@Override
				public void itemStateChanged(final java.awt.event.ItemEvent e) {
					if (mainFrame != null) {
						mainFrame.getPreviewMenuItem().setSelected(previewButton.isSelected());
						mainFrame.getEditMenu().setEnabled(!previewButton.isSelected());
						mainFrame.getCameraMenu().setEnabled(!previewButton.isSelected());
					}
					deselect();
					PrintController.getInstance().setPrintPreview(previewButton.isSelected());
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return previewButton;
	}

	/**
	 * This method initializes roofCustomButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getRoofCustomButton() {
		if (roofCustomButton == null) {
			roofCustomButton = new JToggleButton();
			roofCustomButton.setIcon(new ImageIcon(getClass().getResource("icons/roof_custom.png")));
			roofCustomButton.setToolTipText("Draw custom roof");
			roofCustomButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.DRAW_ROOF_CUSTOM);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			roofCustomButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return roofCustomButton;
	}

	public void deselect() {
		getSelectButton().setSelected(true);
		SceneManager.getInstance().setOperation(Operation.SELECT);
	}

	public JToggleButton getAnnotationToggleButton() {
		if (annotationToggleButton == null) {
			annotationToggleButton = new JToggleButton();
			annotationToggleButton.setSelected(Config.isApplet() ? false : true);
			annotationToggleButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			annotationToggleButton.setIcon(new ImageIcon(getClass().getResource("icons/annotation.png")));
			annotationToggleButton.setToolTipText("Show annotations");
			annotationToggleButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					Scene.getInstance().setAnnotationsVisible(annotationToggleButton.isSelected());
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return annotationToggleButton;
	}

	/**
	 * This method initializes zoomButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getZoomButton() {
		if (zoomButton == null) {
			zoomButton = new JToggleButton();
			zoomButton.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
			zoomButton.setIcon(new ImageIcon(getClass().getResource("icons/zoom.png")));
			zoomButton.setToolTipText("Zoom");
			zoomButton.addItemListener(new java.awt.event.ItemListener() {
				@Override
				public void itemStateChanged(final java.awt.event.ItemEvent e) {
					SceneManager.getInstance().setOperation(SceneManager.Operation.SELECT);
					SceneManager.getInstance().setZoomLock(zoomButton.isSelected());
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
		}
		return zoomButton;
	}

	/**
	 * This method initializes roofGableButton
	 *
	 * @return javax.swing.JToggleButton
	 */
	private JToggleButton getRoofGableButton() {
		if (roofGableButton == null) {
			roofGableButton = new JToggleButton();
			roofGableButton.setIcon(new ImageIcon(getClass().getResource("icons/roof_gable.png")));
			roofGableButton.setToolTipText("Convert to gable roof");
			roofGableButton.addActionListener(new java.awt.event.ActionListener() {
				@Override
				public void actionPerformed(final java.awt.event.ActionEvent e) {
					SceneManager.getInstance().setOperation(Operation.DRAW_ROOF_GABLE);
					FocusManager.getCurrentManager().clearGlobalFocusOwner();
				}
			});
			roofGableButton.addMouseListener(new java.awt.event.MouseAdapter() {
				@Override
				public void mouseClicked(final java.awt.event.MouseEvent e) {
					if (e.getClickCount() > 1)
						SceneManager.getInstance().setOperationStick(true);
				}

				@Override
				public void mouseExited(final MouseEvent e) {
					SceneManager.getInstance().refresh();
				}
			});
		}
		return roofGableButton;
	}

	public void setToolbarEnabled(final boolean enabled) {
		for (final Component c : getAppToolbar().getComponents()) {
			if (c != getPreviewButton() && c != getSelectButton() && c != getAnnotationToggleButton() && c != getZoomButton() && c != getRotAnimButton()) {
				if (!enabled || c != getSunAnimButton() || getLightButton().isSelected() || getHeliodonButton().isSelected())
					c.setEnabled(enabled);
			}
		}
	}

	private JSplitPane getSplitPane() {
		if (splitPane == null) {
			splitPane = new JSplitPane();
			splitPane.setOneTouchExpandable(true);
			splitPane.setResizeWeight(1.0);
			splitPane.setRightComponent(getEnergyPanel());
			splitPane.setLeftComponent(getCanvasPanel());
		}
		return splitPane;
	}

	private EnergyPanel getEnergyPanel() {
		if (energyPanel == null) {
			energyPanel = EnergyPanel.getInstance();
		}
		return energyPanel;
	}

	public JPanel getCanvasPanel() {
		if (canvasPanel == null) {
			canvasPanel = new JPanel();
			canvasPanel.setLayout(new BorderLayout(0, 0));
		}
		return canvasPanel;
	}

	private JToggleButton getEnergyToggleButton() {
		if (energyToggleButton == null) {
			energyToggleButton = new JToggleButton("");
			energyToggleButton.setToolTipText("Show energy analysis");
			energyToggleButton.setSelected(true);
			energyToggleButton.setIcon(new ImageIcon(getClass().getResource("icons/chart.png")));
			energyToggleButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(final ActionEvent e) {
					EnergyPanel.getInstance().setVisible(energyToggleButton.isSelected());
					splitPane.resetToPreferredSizes();
				}
			});
		}
		return energyToggleButton;
	}
}
