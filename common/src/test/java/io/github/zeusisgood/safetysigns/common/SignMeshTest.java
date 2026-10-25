package io.github.zeusisgood.safetysigns.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class SignMeshTest {

    private static final SignOutline TRIANGLE = SignOutline.of(4, 4, new boolean[] {
            false, true, true, false,
            false, true, true, false,
            true, true, true, true,
            true, true, true, true,
    });

    @Test
    public void everyQuadIsCounterClockwiseSeenFromOutside() {
        for (boolean base : new boolean[] {false, true}) {
            for (boolean backPattern : new boolean[] {false, true}) {
                for (SignMesh.Quad quad : SignMesh.build(TRIANGLE, 2, 2, 0, 1 - SignMesh.THICKNESS, base, backPattern)) {
                    float[] p = quad.positions;
                    float ax = p[3] - p[0], ay = p[4] - p[1], az = p[5] - p[2];
                    float bx = p[6] - p[0], by = p[7] - p[1], bz = p[8] - p[2];
                    float dot = (ay * bz - az * by) * quad.nx + (az * bx - ax * bz) * quad.ny + (ax * by - ay * bx) * quad.nz;
                    assertTrue("quad faces " + quad.nx + "," + quad.ny + "," + quad.nz, dot > 0);
                }
            }
        }
    }

    @Test
    public void frontPatternComesLast() {
        List<SignMesh.Quad> quads = SignMesh.build(SignOutline.square(16), 1, 1, 0, 0.25, true, true);
        SignMesh.Quad last = quads.get(quads.size() - 1);
        assertTrue(last.front);
        assertEquals(SignMesh.Texture.PATTERN, last.texture);
        assertEquals(1, quads.stream().filter(q -> q.front).count());
    }

    @Test
    public void frontTopLeftOfTheImageIsTopLeftSeenFromTheNorth() {
        List<SignMesh.Quad> quads = SignMesh.build(SignOutline.square(16), 1, 1, 0, 0.25, false, false);
        SignMesh.Quad front = quads.get(quads.size() - 1);
        for (int i = 0; i < 4; i++) {
            float x = front.positions[i * 3];
            float y = front.positions[i * 3 + 1];
            // 北から見ると +X が左、+Y が上
            assertEquals(x > 0 ? 0 : 1, front.uvs[i * 2], 0);
            assertEquals(y > 0 ? 0 : 1, front.uvs[i * 2 + 1], 0);
        }
    }
}
