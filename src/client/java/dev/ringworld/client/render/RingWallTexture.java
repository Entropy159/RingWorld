package dev.ringworld.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ringworld.config.WallSettings;
import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSurfaceLod;
import dev.ringworld.world.atlas.RingTerrainAtlas;
import dev.ringworld.world.wall.RingGenerationBoundary;
import dev.ringworld.world.wall.RingWallPattern;
import net.minecraft.client.Minecraft;

import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Worker-only wall colour atlas. Four strips: two inner faces, then two outer faces.
 */
final class RingWallTexture {
    record ContentKey(RingGeometry geometry, WallSettings style, long seed, int bottom, int top, int columns, long terrainHash) {
    }

    /**
     * Only inner industrial motifs depend on Atlas heights at the rims.
     */
    static ContentKey contentKey(RingTerrainAtlas atlas, WallSettings style, long seed, int bottom, int top, int columns) {
        long terrainHash = 0L;
        return new ContentKey(atlas.geometry(), style, seed, bottom, top, columns, terrainHash);
    }

    /**
     * Four independent strips; stop before a mip would merge different wall faces.
     */
    static NativeImage[] buildMipmapped(RingTerrainAtlas atlas, WallSettings style, long seed, int bottom, int top, int columns, BooleanSupplier cancelled) {
        int rows = 1;
        while (rows < Math.clamp(top - bottom, 1, 512)) rows <<= 1;
        NativeImage[] levels = new NativeImage[Integer.numberOfTrailingZeros(rows) + 1];
        try {
            levels[0] = build(atlas, style, seed, bottom, top, columns, cancelled, rows);
            int width = columns;
            int height = rows * 4;
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) pixels[y * width + x] = levels[0].getPixel(x, y);
            }
            for (int level = 1; level < levels.length; level++) {
                if (cancelled.getAsBoolean())
                    throw new CancellationException("obsolete wall texture");
                // Power-of-two strip heights keep every 2x2 filter inside one face.
                // Alpha-weighted RGB prevents decay holes from darkening the wall.
                pixels = RingSurfaceLod.buildNextMipArgb(pixels, width, height);
                width = Math.max(1, width >> 1);
                height >>= 1;
                NativeImage image = new NativeImage(width, height, false);
                levels[level] = image;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) image.setPixel(x, y, pixels[y * width + x]);
                }
            }
            return levels;
        } catch (RuntimeException | Error failure) {
            for (NativeImage image : levels) if (image != null) image.close();
            throw failure;
        }
    }

    private static NativeImage build(RingTerrainAtlas atlas, WallSettings style, long seed, int bottom, int top, int columns, BooleanSupplier cancelled, int rows) {
        int height = Math.max(1, top - bottom);
        NativeImage image = new NativeImage(columns, rows * 4, false);
        try {
            var geometry = atlas.geometry();
            for (int strip = 0; strip < 4; strip++) {
                boolean inner = strip < 2;
                int depth = inner ? style.thickness() - 1 : 0;
                for (int col = 0; col < columns; col++) {
                    if ((col & 63) == 0 && cancelled.getAsBoolean())
                        throw new java.util.concurrent.CancellationException("obsolete wall texture");
                    int x = (int) ((col + 0.5) * geometry.circumference() / columns);
                    for (int row = 0; row < rows; row++) {
                        int y = bottom + (int) ((row + 0.5) * height / rows);
                        int roll = RingWallPattern.materialRoll(style, x, y, depth, geometry.circumference(), seed);
                        float shade = 1.0F;
                        int rgb = RingWallMaterialColors.color(RingGenerationBoundary.styledRimBlockForRoll(style, roll), Minecraft.getInstance().level);
                        int color = 0xFF000000 | (int) ((rgb >>> 16 & 255) * shade) << 16 | (int) ((rgb >>> 8 & 255) * shade) << 8 | (int) ((rgb & 255) * shade);
                        image.setPixel(col, strip * rows + row, color);
                    }
                }
            }
            return image;
        } catch (RuntimeException | Error failure) {
            image.close();
            throw failure;
        }
    }

}
