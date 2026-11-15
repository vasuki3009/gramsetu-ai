package com.gramsetu.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** Shrinks big photos before they are sent to the vision model (smaller upload, faster answer). */
public final class ImageResizer {

    /** Longest side, in pixels, sent to the vision model. */
    public static final int MAX_DIMENSION = 1024;

    private ImageResizer() {}

    /**
     * The image scaled down so its longest side is at most {@code maxDimension} (re-encoded as JPEG).
     * An image that is already small enough, or bytes that cannot be decoded as an image, come back unchanged.
     */
    public static byte[] limitDimension(byte[] imageBytes, int maxDimension) {
        if (imageBytes == null || maxDimension <= 0) return imageBytes;
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (original == null) return imageBytes;   // not an image format Java can read
            int width = original.getWidth();
            int height = original.getHeight();
            if (width <= maxDimension && height <= maxDimension) return imageBytes;

            double scale = (double) maxDimension / Math.max(width, height);
            int newWidth = Math.max(1, (int) (width * scale));
            int newHeight = Math.max(1, (int) (height * scale));

            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(original, 0, 0, newWidth, newHeight, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(resized, "jpg", out)) return imageBytes;
            return out.toByteArray();
        } catch (Exception e) {
            return imageBytes;   // never fail the request because of the resize; the caller sends the original
        }
    }
}
