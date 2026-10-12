package com.mixyn.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The mask clean-up: an unsure mask goes in, the mask of a solid object comes out. */
class CutoutServiceTest {

    @Test
    void tellsACleanCutoutFromOneTheModelWasUnsureAbout() {
        // Clean: background, a two-pixel soft edge, then a solid piece.
        int[] clean = {0, 0, 0, 90, 180, 255, 255, 255, 255, 255, 255, 255};
        // Unsure: half-seen stripes across most of the piece.
        int[] mushy = {0, 0, 255, 60, 80, 255, 40, 70, 120, 255, 90, 60};

        assertTrue(CutoutService.unsureShare(clean) < CutoutService.UNSURE_LIMIT);
        assertTrue(CutoutService.unsureShare(mushy) > CutoutService.UNSURE_LIMIT);
        assertEquals(0, CutoutService.unsureShare(new int[] {0, 0, 5}));
    }

    @Test
    void makesAHalfSeenPieceSolidAndClearsFaintBackground() {
        // One row: background the models barely noticed, a soft edge, then a piece they half believed in.
        int[] alpha = {5, 20, 40, 60, 90, 130, 255};

        CutoutService.solidify(alpha, alpha.length, 1);

        assertArrayEquals(new int[] {0, 0, 127, 255, 255, 255, 255}, alpha);
    }

    @Test
    void dropsASpeckFarFromThePieceButKeepsTheSecondShoe() {
        // 10 x 12: a big shoe on the left, a smaller second shoe on the right, one stray pixel between.
        int width = 12;
        int height = 10;
        int[] alpha = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 6; x++) alpha[y * width + x] = 255;      // 60 pixels
            if (y < 4) for (int x = 9; x < 12; x++) alpha[y * width + x] = 255; // 12 pixels
        }
        int speck = 7 * width + 8;
        alpha[speck] = 0;
        int[] expected = alpha.clone();
        alpha[speck] = 255; // 1 pixel: under 2% of the largest blob

        CutoutService.solidify(alpha, width, height);

        assertArrayEquals(expected, alpha);
    }

    @Test
    void dropsAFaintSmudgeEvenWhenItIsLarge() {
        // Left half: the piece. Right half, detached: a wide smudge that is only just above "background".
        int width = 10;
        int[] alpha = {255, 255, 255, 255, 0, 30, 30, 30, 30, 30};

        CutoutService.solidify(alpha, width, 1);

        assertArrayEquals(new int[] {255, 255, 255, 255, 0, 0, 0, 0, 0, 0}, alpha);
    }
}
