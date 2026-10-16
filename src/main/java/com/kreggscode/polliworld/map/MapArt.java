package com.kreggscode.polliworld.map;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Pure image helpers: decode what Pollinations returned and fit it into the
 * square 128x128 a Minecraft map item needs.
 */
public final class MapArt {

    public static final int SIZE = 128;

    private MapArt() {}

    public static BufferedImage decode(byte[] imageBytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (img == null) {
            throw new IOException("The image could not be decoded.");
        }
        return img;
    }

    /** Center-crop to a square, then resize to SIZE x SIZE. */
    public static BufferedImage fit(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int side = Math.min(w, h);
        int x = (w - side) / 2;
        int y = (h - side) / 2;
        BufferedImage square = src.getSubimage(x, y, side, side);
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(square.getScaledInstance(SIZE, SIZE, Image.SCALE_SMOOTH), 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
