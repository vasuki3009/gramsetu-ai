package com.gramsetu.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ImageResizerTest {

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void bigImage_isScaledDown_keepingAspectRatio() throws Exception {
        byte[] result = ImageResizer.limitDimension(png(3000, 1500), 1024);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(1024, img.getWidth());
        assertEquals(512, img.getHeight());
    }

    @Test
    void tallImage_isLimitedByHeight() throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(ImageResizer.limitDimension(png(800, 2000), 1024)));
        assertEquals(1024, img.getHeight());
        assertEquals(409, img.getWidth());
    }

    @Test
    void smallImage_isReturnedUnchanged() throws Exception {
        byte[] small = png(640, 480);
        assertSame(small, ImageResizer.limitDimension(small, 1024));
    }

    @Test
    void notAnImage_isReturnedUnchanged() {
        byte[] junk = {1, 2, 3, 4};
        assertSame(junk, ImageResizer.limitDimension(junk, 1024));
    }

    @Test
    void nullAndEmpty_doNotThrow() {
        assertNull(ImageResizer.limitDimension(null, 1024));
        byte[] empty = new byte[0];
        assertSame(empty, ImageResizer.limitDimension(empty, 1024));
    }
}
