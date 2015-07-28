package org.concord.energy3d;

import javax.swing.JOptionPane;
import javax.swing.UIManager;

import org.concord.energy3d.gui.EnergyPanel;
import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.logger.SnapshotLogger;
import org.concord.energy3d.logger.TimeSeriesLogger;
import org.concord.energy3d.scene.Scene;
import org.concord.energy3d.scene.SceneManager;
import org.concord.energy3d.util.Config;
import org.concord.energy3d.util.Config.RenderMode;
import org.concord.energy3d.util.UpdateAnnouncer;

public class MainApplication {
	/**
	 * @wbp.parser.entryPoint
	 */
	public static void main(final String[] args) {
		final long t = System.nanoTime();
		System.setProperty("jogamp.gluegen.UseTempJarCache", "false");
		final String version = System.getProperty("java.version");
		if (version.compareTo("1.6") < 0) {
			JOptionPane.showMessageDialog(null, "Your current Java version is " + version + ". Version 1.6 or higher is required.");
			System.exit(0);
		}

		System.setProperty("jogl.gljpanel.noglsl", "true");
		System.setProperty("direct", "true");

		if (System.getProperty("os.name").startsWith("Mac")) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");
			System.setProperty("com.apple.mrj.application.apple.menu.about.name", "Energy3D");
		}
		Config.setWebStart(System.getProperty("javawebstart.version", null) != null);
		// if (Config.isWebStart())
		// System.out.println("Application is lauched by webstart.");
		// else
		// setupLibraryPath();

		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (final Exception e) {
			e.printStackTrace();
		}
		final SceneManager sceneManager = SceneManager.getInstance();
		final MainFrame mainFrame = MainFrame.getInstance();
		mainFrame.updateTitleBar();
		mainFrame.setVisible(true);
		Scene.getInstance();
		new Thread(sceneManager, "Energy 3D Application").start();

		// if (args.length > 1 && !args[args.length - 1].startsWith("-"))
		// mainFrame.open(args[args.length - 1]);

		if (args.length > 0)
			mainFrame.open(args[0]);

		/* initialize data logging */
		final TimeSeriesLogger logger = new TimeSeriesLogger(1);
		sceneManager.addShutdownHook(new Runnable() {
			@Override
			public void run() {
				logger.closeLog();
			}
		});
		Scene.getInstance().addPropertyChangeListener(logger);
		EnergyPanel.getInstance().addPropertyChangeListener(logger);
		logger.start();
		SnapshotLogger.start(20, logger);

		// detect if the app is launched via webstart just checking its class loader: SystemClassLoader or JnlpClassLoader.
		if (!Config.isRestrictMode()) {
			final ClassLoader cl = Thread.currentThread().getContextClassLoader();
			if (cl.equals(ClassLoader.getSystemClassLoader()))
				UpdateAnnouncer.showMessage();
		}

		JOptionPane.showMessageDialog(null, (System.nanoTime() - t) / 1000000 / 1000.0);

	}

	public static void setupLibraryPath() {
		System.out.println(System.getProperty("java.version") + ", " + System.getProperty("os.arch"));
		final String orgLibraryPath = System.getProperty("java.library.path");
		final String sep = System.getProperty("file.separator");
		final String rendererNativePath = "." + sep + "lib" + sep + (Config.RENDER_MODE == RenderMode.LWJGL ? "lwjgl" : "jogl") + sep + "native";
		final String OSPath;
		final String os = System.getProperty("os.name").toLowerCase();
		if (os.startsWith("windows")) {
			final String sunArch = System.getProperty("sun.arch.data.model");
			if (sunArch != null && sunArch.startsWith("64"))
				OSPath = "windows-64";
			else
				OSPath = "windows-32";
		} else if (os.startsWith("mac")) {
			OSPath = "mac-universal";
		} else if (os.startsWith("linux")) {
			final String sunArch = System.getProperty("sun.arch.data.model");
			if (sunArch != null && sunArch.startsWith("64"))
				OSPath = "linux-64";
			else
				OSPath = "linux-32";
		} else
			throw new RuntimeException("Unknown OS: " + os);

		final String pathSep = System.getProperty("path.separator");
		final String newLibraryPath = "." + pathSep + rendererNativePath + sep + OSPath + pathSep + orgLibraryPath;
		System.setProperty("java.library.path", newLibraryPath);
		System.out.println("Path = " + System.getProperty("java.library.path"));
		// The following code is to empty the library path cache in order to force JVM to use the new library path above
		java.lang.reflect.Field fieldSysPath;
		try {
			fieldSysPath = ClassLoader.class.getDeclaredField("sys_paths");
			fieldSysPath.setAccessible(true);
			fieldSysPath.set(null, null);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

}
