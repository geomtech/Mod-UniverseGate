package fr.geomtech.universegate.seamless;

import fr.geomtech.universegate.seamless.math.ObliqueFrustumMath;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObliqueFrustumMathTest {

    @Test
    void obliquePlaneMapsPortalPlanePointsToNearNdcAndPreservesFarPlane() {
        float near = 0.05F;
        float far = 256.0F;
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0D), 16.0F / 9.0F, near, far);

        // Destination portal plane 8 blocks in front of camera (z = -8), tilted 30 degrees around Y
        float angle = (float) Math.toRadians(30.0D);
        float nx = (float) Math.sin(angle);
        float nz = (float) -Math.cos(angle);
        // Plane passes through (0, 0, -8): nx*0 + nz*(-8) + d = 0 => d = 8*nz
        float d = 8.0F * nz;
        Vector4f clipPlane = new Vector4f(nx, 0.0F, nz, d);

        Matrix4f oblique = ObliqueFrustumMath.applyObliqueClipPlane(proj, clipPlane);

        // Verify multiple points lying on the portal plane map to z_ndc = -1.0
        for (float x : new float[]{-2.0F, 0.0F, 1.5F}) {
            float zOnPlane = (-nx * x - d) / nz;
            for (float y : new float[]{-1.5F, 0.0F, 2.0F}) {
                Vector4f p = new Vector4f(x, y, zOnPlane, 1.0F);
                oblique.transform(p);
                float zNdc = p.z / p.w;
                assertEquals(-1.0F, zNdc, 1.0E-3F,
                        "Point on clip plane must map to near plane z_ndc=-1");
            }
        }

        // Verify point strictly on the kept side (further away, z = -20) has z_ndc > -1 and <= 1
        Vector4f kept = new Vector4f(0.0F, 0.0F, -20.0F, 1.0F);
        oblique.transform(kept);
        float keptNdc = kept.z / kept.w;
        assertTrue(keptNdc > -1.0F && keptNdc < 1.0F, "Kept point should lie inside [-1, 1], got " + keptNdc);

        // Verify point strictly behind the destination portal (closer to camera, z = -2) has z_ndc < -1 (clipped!)
        Vector4f clipped = new Vector4f(0.0F, 0.0F, -2.0F, 1.0F);
        oblique.transform(clipped);
        float clippedNdc = clipped.z / clipped.w;
        assertTrue(clippedNdc < -1.0F, "Behind-portal point should be clipped (z_ndc < -1), got " + clippedNdc);
    }

    @Test
    void degenerateOrBehindCameraPlanesReturnOriginalProjectionSafely() {
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(70.0D), 1.5F, 0.05F, 128.0F);

        Matrix4f zeroRes = ObliqueFrustumMath.applyObliqueClipPlane(proj, new Vector4f(0.0F, 0.0F, 0.0F, 0.0F));
        assertEquals(proj, zeroRes);

        // Plane facing toward camera (cz > 0)
        Matrix4f facingAway = ObliqueFrustumMath.applyObliqueClipPlane(proj, new Vector4f(0.0F, 0.0F, 1.0F, -5.0F));
        assertEquals(proj, facingAway);
    }
}
