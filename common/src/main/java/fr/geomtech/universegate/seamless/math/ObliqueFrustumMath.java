package fr.geomtech.universegate.seamless.math;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Lengyel's oblique near-plane clipping frustum modification for OpenGL perspective matrices
 * ({@code z_ndc in [-1, +1]}).
 *
 * <p>Replaces the near clipping plane of a perspective projection matrix with the destination
 * portal plane in camera space so geometry behind the destination portal is clipped cleanly at
 * zero extra shader cost.</p>
 */
public final class ObliqueFrustumMath {

    private static final float MIN_DENOMINATOR = 1.0E-5F;

    private ObliqueFrustumMath() {}

    /**
     * Transforms a world-space (camera-relative) plane {@code (nx, ny, nz, d)} into view/camera space
     * using the inverse-transpose of the rotational/rigid view matrix {@code viewMatrix}.
     */
    public static Vector4f worldPlaneToCameraSpace(Matrix4f viewMatrix, Vector4f cameraRelativePlane) {
        Matrix4f invTranspose = new Matrix4f(viewMatrix).invert().transpose();
        Vector4f result = new Vector4f(cameraRelativePlane);
        invTranspose.transform(result);
        return result;
    }

    /**
     * Returns a copy of {@code projection} whose near clipping plane ({@code z_ndc = -1}) coincides
     * with {@code clipPlaneCameraSpace}, where points on the kept (visible) side satisfy
     * {@code clipPlane . P_cam >= 0}.
     *
     * <p>If the clip plane is degenerate, behind the camera, or nearly parallel to the view ray
     * in a way that would invert depth, returns an unmodified copy of {@code projection}.</p>
     */
    public static Matrix4f applyObliqueClipPlane(Matrix4f projection, Vector4f clipPlaneCameraSpace) {
        Matrix4f out = new Matrix4f(projection);

        float cx = clipPlaneCameraSpace.x;
        float cy = clipPlaneCameraSpace.y;
        float cz = clipPlaneCameraSpace.z;
        float cw = clipPlaneCameraSpace.w;

        float normalLen = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (normalLen < MIN_DENOMINATOR) {
            return out;
        }
        cx /= normalLen;
        cy /= normalLen;
        cz /= normalLen;
        cw /= normalLen;

        // If camera itself (0, 0, 0, 1) is already strictly on the kept side (cw > 0), or plane faces away
        // (cz >= 0 when looking down -Z), oblique clipping would clip everything or invert depth.
        if (cw >= -1.0E-4F || cz >= -1.0E-4F) {
            return out;
        }

        // Far-plane corner Q in camera space opposite the clip plane normal:
        // Q = P^-1 * (sgn(cx), sgn(cy), 1, 1)
        float m00 = out.m00();
        float m11 = out.m11();
        float m22 = out.m22();
        float m32 = out.m32(); // JOML column-major: m32 = row 2, col 3 (translation Z term)
        float m20 = out.m20(); // row 0, col 2
        float m21 = out.m21(); // row 1, col 2
        if (Math.abs(m00) < MIN_DENOMINATOR || Math.abs(m11) < MIN_DENOMINATOR || Math.abs(m32) < MIN_DENOMINATOR) {
            return out;
        }

        float qx = (signum(cx) + m20) / m00;
        float qy = (signum(cy) + m21) / m11;
        float qz = -1.0F;
        float qw = (1.0F + m22) / m32;

        float dotCQ = cx * qx + cy * qy + cz * qz + cw * qw;
        if (dotCQ <= MIN_DENOMINATOR) {
            return out;
        }

        // Row 3 of standard perspective P is M4 = (0, 0, -1, 0), so M4 . Q = -qz = +1.0F
        float m4DotQ = -(out.m03() * qx + out.m13() * qy + out.m23() * qz + out.m33() * qw);
        // Note: in JOML column-major, row 3 is (m03, m13, m23, m33) = (0, 0, -1, 0),
        // so row3 . Q = m23 * qz = (-1)*(-1) = +1.0F.
        float row3DotQ = out.m03() * qx + out.m13() * qy + out.m23() * qz + out.m33() * qw;
        float scale = (2.0F * row3DotQ) / dotCQ;
        if (!Float.isFinite(scale) || scale <= MIN_DENOMINATOR) {
            return out;
        }

        // Replace row 2 of P (m02, m12, m22, m32) with scale * C - row3(P)
        out.m02(cx * scale - out.m03());
        out.m12(cy * scale - out.m13());
        out.m22(cz * scale - out.m23());
        out.m32(cw * scale - out.m33());
        return out;
    }

    private static float signum(float v) {
        if (v > 0.0F) return 1.0F;
        if (v < 0.0F) return -1.0F;
        return 0.0F;
    }
}
