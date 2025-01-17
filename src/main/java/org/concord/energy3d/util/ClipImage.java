package org.concord.energy3d.util;

import java.awt.Component;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

public class ClipImage implements ClipboardOwner {

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {
    }

    public void copyImageToClipboard(Component c) {
        BufferedImage bi = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_ARGB);
        c.paintAll(bi.createGraphics());
        try {
            TransferableImage trans = new TransferableImage(bi);
            Clipboard clip = Toolkit.getDefaultToolkit().getSystemClipboard();
            clip.setContents(trans, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class TransferableImage implements Transferable {

        private Image image;

        TransferableImage(Image image) {
            this.image = image;
        }

        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (flavor.equals(DataFlavor.imageFlavor) && image != null)
                return image;
            throw new UnsupportedFlavorException(flavor);
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        public boolean isDataFlavorSupported(DataFlavor flavor) {
            DataFlavor[] flavors = getTransferDataFlavors();
            for (DataFlavor x : flavors) {
                if (flavor.equals(x)) {
                    return true;
                }
            }
            return false;
        }

    }

}