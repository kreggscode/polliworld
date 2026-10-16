package com.kreggscode.polliworld.map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapView;

import java.awt.image.BufferedImage;

/**
 * Paints a fitted image onto a real in-game map item. The renderer draws once,
 * then stops drawing.
 */
public final class MapPainter {

    private MapPainter() {}

    public static ItemStack paint(World world, BufferedImage image) {
        MapView view = Bukkit.createMap(world);
        for (org.bukkit.map.MapRenderer old : view.getRenderers()) {
            view.removeRenderer(old);
        }
        final byte[] pixels = toPalette(image);
        org.bukkit.map.MapRenderer once = new org.bukkit.map.MapRenderer() {
            private boolean drawn;

            @Override
            public void render(MapView map, MapCanvas canvas, Player player) {
                if (drawn) {
                    return;
                }
                drawn = true;
                for (int y = 0; y < MapArt.SIZE; y++) {
                    for (int x = 0; x < MapArt.SIZE; x++) {
                        canvas.setPixel(x, y, pixels[y * MapArt.SIZE + x]);
                    }
                }
            }
        };
        view.addRenderer(once);

        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setMapView(view);
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation")
    static byte[] toPalette(BufferedImage image) {
        byte[] pixels = new byte[MapArt.SIZE * MapArt.SIZE];
        for (int y = 0; y < MapArt.SIZE; y++) {
            for (int x = 0; x < MapArt.SIZE; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                pixels[y * MapArt.SIZE + x] = MapPalette.matchColor(r, g, b);
            }
        }
        return pixels;
    }
}
