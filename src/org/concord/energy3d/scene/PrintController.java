package org.concord.energy3d.scene;

import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.ArrayList;

import org.concord.energy3d.gui.MainFrame;
import org.concord.energy3d.model.Floor;
import org.concord.energy3d.model.HousePart;
import org.concord.energy3d.model.Roof;
import org.concord.energy3d.model.UserData;
import org.concord.energy3d.scene.SceneManager.ViewMode;
import org.concord.energy3d.util.ObjectCloner;
import org.concord.energy3d.util.PrintExporter;
import org.concord.energy3d.util.Util;

import com.ardor3d.framework.CanvasRenderer;
import com.ardor3d.framework.Updater;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Matrix3;
import com.ardor3d.math.Vector3;
import com.ardor3d.renderer.Camera;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.Spatial;
import com.ardor3d.scenegraph.hint.CullHint;
import com.ardor3d.scenegraph.shape.Box;
import com.ardor3d.util.ReadOnlyTimer;
import com.ardor3d.util.screen.ScreenExporter;

public class PrintController implements Updater {
	private static PrintController instance = new PrintController();
	private static final int MARGIN = 5;
	private static final double PRINT_MARGIN = 0.5;
	private double pageWidth, pageHeight;
	private boolean isPrintPreview = false;
	private boolean init = false;
	private boolean finish = true;
	private int finishPhase = 100;
	private long startTime;
	private Scene sceneClone = null;
	private ArrayList<HousePart> printParts = new ArrayList<HousePart>();
	private double angle;
	private final ArrayList<Vector3> printCenters = new ArrayList<Vector3>();
	private boolean shadingSelected;
	private boolean shadowSelected;
	private Node pagesRoot = new Node();
	private int cols;
	private int rows;

	public static PrintController getInstance() {
		return instance;
	}

	private PrintController() {

	}

	public void init() {

	}

	public void update(ReadOnlyTimer timer) {
		if (isPrintPreview)
			rotate();

		if (isFinished())
			return;

		final long time = timer.getTime();
		final Spatial originalHouseRoot = Scene.getInstance().getOriginalHouseRoot();		
		if (init) {
			init = false;
			startTime = time;
//			HousePart.setFlatten(true);
//			final CanvasRenderer renderer = SceneManager.getInstance().getCanvas().getCanvasRenderer();
			if (!isPrintPreview) { // && !renderer.getBackgroundColor().equals(ColorRGBA.WHITE))
				Scene.getRoot().detachChild(pagesRoot);
				pagesRoot.detachAllChildren();
			} else {
//				renderer.makeCurrentContext();
//				// renderer.getRenderer().setBackgroundColor(ColorRGBA.WHITE);
//				renderer.releaseCurrentContext();				
				sceneClone = (Scene) ObjectCloner.deepCopy(Scene.getInstance());
				printParts.clear();
				for (int i = 0; i < sceneClone.getParts().size(); i++) {
					final HousePart newPart = sceneClone.getParts().get(i);
					Scene.getRoot().attachChild(newPart.getRoot());
					newPart.draw();
					newPart.setOriginal(Scene.getInstance().getParts().get(i));
					if (newPart.isPrintable() && newPart.isDrawCompleted())
						printParts.add(newPart);
				}
				final ArrayList<ArrayList<Spatial>> pages = new ArrayList<ArrayList<Spatial>>();
				computePageDimension();
				computePrintCenters(pages);
				arrangePrintPages(pages);
				System.out.println("Total # of Print Pages = " + pages.size());

				// applyPreviewScale();
				SceneManager.getInstance().updatePrintPreviewScene(true);

				originalHouseRoot.setScale(2);
				originalHouseRoot.setTranslation(0, 0, -Util.findExactHeight(printParts));
			}
			for (HousePart part : Scene.getInstance().getParts())
				part.getRoot().getSceneHints().setCullHint(CullHint.Always);
		}

		if (!finish) {
			final double t = (time - startTime) / 1.0 / timer.getResolution();
//			HousePart.setFlattenTime(isPrintPreview ? t : 1 - t);
			drawPrintParts(isPrintPreview ? t : 1 - t);

			finish = t > 1;
			finishPhase = 0;
		}

		if (finish) {
			if (isPrintPreview)
				Scene.getRoot().attachChild(pagesRoot);
//			if (!isPrintPreview)
//				HousePart.setFlatten(false);
			if (!isPrintPreview && finishPhase == 10) {
				originalHouseRoot.setRotation(new Matrix3().fromAngles(0, 0, 0));
				angle = 0;
				for (HousePart housePart : sceneClone.getParts())
					Scene.getRoot().detachChild(housePart.getRoot());
				printParts.clear();
				originalHouseRoot.setScale(1);
				originalHouseRoot.setTranslation(0, 0, 0);
				originalHouseRoot.updateGeometricState(timer.getTimePerFrame(), true);

				final CanvasRenderer renderer = SceneManager.getInstance().getCanvas().getCanvasRenderer();
				renderer.makeCurrentContext();
				renderer.getRenderer().setBackgroundColor(ColorRGBA.BLACK);
				renderer.releaseCurrentContext();

				final MainFrame frame = MainFrame.getInstance();
				frame.getLightingMenu().setSelected(shadingSelected);
				frame.getShadowMenu().setSelected(shadowSelected);

				SceneManager.getInstance().updatePrintPreviewScene(false);
			}

			if (finishPhase == 10) {
				int printSequence = 0;
				for (HousePart part : Scene.getInstance().getParts()) {
					part.getRoot().getSceneHints().setCullHint(CullHint.Inherit);
					printSequence = part.drawLabels(printSequence);
				}
			}

			finishPhase++;

			// if (finishPhase > 20) {
			// counter++;
			// if (Util.DEBUG)
			// System.out.println("PrintPreview Counter: " + counter);
			// isPrintPreview = !isPrintPreview;
			// init = true;
			// finish = false;
			// }
		}
	}

	public void drawPrintParts(double flattenTime) {
		if (sceneClone == null)
			return;
		if (flattenTime < 0)
			flattenTime = 0;
		if (flattenTime > 1)
			flattenTime = 1;			
		
		int printSequence = 0;
		for (HousePart part : sceneClone.getParts()) {
			// TODO If draw not completed then it shouldn't even exist at this point!
			if (part.isDrawCompleted() && part.isPrintable()) {
//				part.draw();
				part.flatten(flattenTime);
				printSequence = part.drawLabels(printSequence);
			}
			
		}
			
	}

	public void print() {
		Scene.getInstance().getOriginalHouseRoot().getSceneHints().setCullHint(CullHint.Always);
		final PrintExporter printExporter = new PrintExporter();
		final Camera camera = Camera.getCurrentCamera();
		SceneManager.getInstance().resetCamera(ViewMode.PRINT);
		for (Vector3 pos : printCenters) {
			camera.setLocation(pos.getX(), pos.getY() - pageWidth * 2, pos.getZ());
			camera.lookAt(pos.add(0, 1, 0, null), Vector3.UNIT_Z);
			SceneManager.getInstance().getCameraNode().updateFromCamera();
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			final CanvasRenderer canvasRenderer = SceneManager.getInstance().getCanvas().getCanvasRenderer();
			canvasRenderer.makeCurrentContext();
			ScreenExporter.exportCurrentScreen(canvasRenderer.getRenderer(), printExporter);
			canvasRenderer.releaseCurrentContext();
		}
		final PrinterJob job = PrinterJob.getPrinterJob();
		// job.setPrintable(printExporter);
		final PageFormat pageFormat = new PageFormat();
		final Paper paper = new Paper();
		paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
		pageFormat.setPaper(paper);

		job.setPageable(new Pageable() {

			@Override
			public Printable getPrintable(int arg0) throws IndexOutOfBoundsException {
				return printExporter;
			}

			@Override
			public PageFormat getPageFormat(int arg0) throws IndexOutOfBoundsException {
				return pageFormat;
			}

			@Override
			public int getNumberOfPages() {
				return printCenters.size();
			}
		});
		if (job.printDialog())
			try {
				job.print();
			} catch (PrinterException exc) {
				exc.printStackTrace();
			}
		Scene.getInstance().getOriginalHouseRoot().getSceneHints().setCullHint(CullHint.Inherit);
		SceneManager.getInstance().resetCamera(ViewMode.PRINT_PREVIEW);
	}

	public void setPrintPreview(final boolean printPreview) {
		if (printPreview == isPrintPreview)
			return;
		init = true;
		finish = false;
		isPrintPreview = printPreview;

		final MainFrame frame = MainFrame.getInstance();
		if (printPreview) {
			shadingSelected = frame.getLightingMenu().isSelected();
			shadowSelected = frame.getShadowMenu().isSelected();

			if (shadingSelected)
				frame.getLightingMenu().setSelected(false);
			if (shadowSelected)
				frame.getShadowMenu().setSelected(false);
		}

		// SceneManager.getInstance().setCompassVisible(!printPreview);
		// if (printPreview)
		// SceneManager.getInstance().updatePrintPreviewScene(true);
	}

	public boolean isPrintPreview() {
		return isPrintPreview;
	}

	public void rotate() {
		if (SceneManager.getInstance().isRotationAnimationOn()) {
			angle += 0.01;
			Scene.getInstance().getOriginalHouseRoot().setRotation(new Matrix3().fromAngles(0, 0, angle));
		}
	}

	public ArrayList<HousePart> getPrintParts() {
		return printParts;
	}

	public void addPrintCenters(Vector3 p) {
		printCenters.add(p);
	}

	public boolean isFinished() {
		return finish && finishPhase > 20;
	}

	private void computePageDimension() {
		double maxSize = 0;
		for (final HousePart printPart : printParts) {
			printPart.getRoot().updateWorldBound(true);
			if (printPart instanceof Roof) {
				for (final Spatial mesh : ((Roof)printPart).getFlattenedMeshesRoot().getChildren()) {
					double d = 2 + Util.findBoundLength(mesh.getWorldBound());
					if (!Double.isInfinite(d) && d > maxSize)
						maxSize = d;					
				}
			} else {
				double d = 2 + Util.findBoundLength(printPart.getMesh().getWorldBound());
				if (!Double.isInfinite(d) && d > maxSize)
					maxSize = d;
			}
		}
		pageWidth = maxSize;
		final Paper paper = new Paper();
		pageHeight = pageWidth * paper.getHeight() / paper.getWidth();
	}

	private void arrangePrintPages(final ArrayList<ArrayList<Spatial>> pages) {
		final double ratio = (double) Camera.getCurrentCamera().getWidth() / Camera.getCurrentCamera().getHeight();
		cols = (int) Math.round(Math.sqrt((pages.size() + 1) * ratio));
		if (cols % 2 == 0)
			cols++;
		rows = (int) Math.ceil((pages.size() + 1.0) / cols);

		int pageNum = 0;
		printCenters.clear();
		for (final ArrayList<Spatial> page : pages) {
			final Vector3 currentCorner = new Vector3();
			double x, y;
			do {
				x = (pageNum % cols - cols / 2) * (pageWidth + MARGIN);
				y = (pageNum / cols - (rows - 1) / 2) * (pageHeight + MARGIN);
				currentCorner.setX(x - pageWidth / 2);
				currentCorner.setZ(y + pageHeight / 2);
				pageNum++;
			} while (currentCorner.length() < pageWidth);

			printCenters.add(new Vector3(x, 0, y));

			for (final Spatial printSpatial : page)
				((UserData) printSpatial.getUserData()).getPrintCenter().addLocal(currentCorner);

			final Box box = new Box("Page Boundary");
			box.setData(currentCorner.add(0, 0.1, 0, null), currentCorner.add(pageWidth, 0.2, -pageHeight, null));
//			box.setDefaultColor(ColorRGBA.BLUE);
			box.setModelBound(null);
			pagesRoot.attachChild(box);
		}

	}

	private void computePrintCenters(final ArrayList<ArrayList<Spatial>> pages) {
		for (HousePart printPart : printParts) {
			if (printPart instanceof Roof) {
				for (Spatial roofPart : ((Roof) printPart).getFlattenedMeshesRoot().getChildren())
					computePrintCenterOf(roofPart, pages);
			} else 
				computePrintCenterOf(printPart.getMesh(), pages);
		}
	}

//	private void computePrintCentersForRoof(final ArrayList<ArrayList<Spatial>> pages) {
//		for (HousePart printPart : printParts) {
//			if (!(printPart instanceof Roof))
//				continue;
//			for (Spatial roofPart : ((Roof) printPart).getFlattenedMeshesRoot().getChildren()) {
//				computePrintCenterOf(roofPart, pages);
//			}
//		}
//	}

	public void computePrintCenterOf(final Spatial printPart, final ArrayList<ArrayList<Spatial>> pages) {
		printPart.updateWorldBound(true);
		boolean isFitted = false;
		for (int pageNum = 0; pageNum < pages.size() && !isFitted; pageNum++) {
			isFitted = fitInPage(printPart, pages.get(pageNum));
		}
		if (!isFitted) {
			final double radius = Util.findBoundLength(printPart.getWorldBound()) / 2;
			// printPart.setUserData(new Vector3(radius, 0, -radius));
			((UserData) printPart.getUserData()).setPrintCenter(new Vector3(radius + PRINT_MARGIN, 0, -radius - PRINT_MARGIN));
			final ArrayList<Spatial> page = new ArrayList<Spatial>();
			page.add(printPart);
			pages.add(page);
		}
	}

	private boolean fitInPage(final Spatial printPart, final ArrayList<Spatial> page) {
		final double printPartRadius = Util.findBoundLength(printPart.getWorldBound()) / 2;
		for (Spatial part : page) {
			// final Vector3 p = (Vector3) part.getUserData();
			final Vector3 p = ((UserData) part.getUserData()).getPrintCenter();
			final double r = Util.findBoundLength(part.getWorldBound()) / 2;
			final double dis = r + printPartRadius;

			final Vector3 disVector = new Vector3(dis, 0, 0);
			for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 4) {
				final Vector3 tryCenter = new Matrix3().fromAngles(0, angle, 0).applyPost(disVector, null);
				tryCenter.addLocal(p);
				boolean collision = false;
				if (!isCircleInsideRectangle(tryCenter, printPartRadius, new Vector3(PRINT_MARGIN, 0, -PRINT_MARGIN), new Vector3(pageWidth - PRINT_MARGIN, 0, -pageHeight + PRINT_MARGIN)))
					collision = true;
				else
					for (Spatial otherPart : page) {
						if (otherPart == part)
							continue;
						collision = tryCenter.subtract(((UserData) otherPart.getUserData()).getPrintCenter(), null).length() < printPartRadius + Util.findBoundLength(otherPart.getWorldBound()) / 2;
						if (collision)
							break;
					}
				if (!collision) {
					// printPart.setUserData(tryCenter);
					((UserData) printPart.getUserData()).setPrintCenter(tryCenter);
					page.add(printPart);
					return true;
				}
			}
		}
		return false;
	}

	private boolean isCircleInsideRectangle(Vector3 center, double r, Vector3 p1, Vector3 p2) {
		for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 2) {
			final Vector3 p = new Matrix3().fromAngles(0, angle, 0).applyPost(new Vector3(r, 0, 0), null);
			p.addLocal(center);
			final double x = p.getX();
			final double z = p.getZ();
			if (x < p1.getX() || x > p2.getX() || z > p1.getZ() || z < p2.getZ())
				return false;
		}
		return true;
	}

	public double getPageWidth() {
		return pageWidth;
	}

	public double getPageHeight() {
		return pageHeight;
	}

	public int getCols() {
		return cols;
	}

	public int getRows() {
		return rows;
	}

	public static int getMargin() {
		return MARGIN;
	}

}