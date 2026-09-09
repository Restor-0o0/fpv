package org.restor.fpv.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

// Solid untextured geometry for race markers: banded square hoops and tube route lines,
// drawn as full-bright quads so a gate reads like a real racing hoop rather than a wireframe.
public final class RaceFrames {
    public static final float[] WHITE = { 0.95f, 0.96f, 1.00f };
    public static final float[] DARK = { 0.06f, 0.07f, 0.09f };
    public static final float[] NEXT = { 0.15f, 1.00f, 0.40f };

    private static final Vector3f LIGHT = new Vector3f(0.35f, 1.0f, 0.25f).normalize();

    private RaceFrames() {}

    public static RenderType type() {
        return RenderType.debugQuads();
    }

    public static Vector3f[] basis(Vector3f normal) {
        Vector3f n = new Vector3f(normal).normalize();
        Vector3f ref = Math.abs(n.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(ref).cross(n).normalize();
        Vector3f up = new Vector3f(n).cross(right).normalize();
        return new Vector3f[] { right, up, n };
    }

    public static float[] shade(int argb, float factor) {
        return new float[] {
            Math.min(1f, ((argb >> 16) & 0xFF) / 255f * factor),
            Math.min(1f, ((argb >> 8) & 0xFF) / 255f * factor),
            Math.min(1f, (argb & 0xFF) / 255f * factor)
        };
    }

    public static void hoop(VertexConsumer vc, Matrix4f m, Vector3f center, Vector3f normal, float half,
                           float thickness, float[] main, float[] alt, float alpha) {
        Vector3f[] axes = basis(normal);
        Vector3f right = axes[0], up = axes[1], n = axes[2];
        Vector3f ex = new Vector3f(right).mul(thickness);
        Vector3f ey = new Vector3f(up).mul(thickness);
        Vector3f ez = new Vector3f(n).mul(thickness);
        float outer = half + thickness;
        float inner = Math.max(0.01f, half - thickness);
        float band = Math.max(0.55f, half * 0.3f);

        // The horizontals run the full width and the uprights butt into them, so no two
        // boxes share a face and nothing z-fights along the corners.
        bar(vc, m, at(center, right, up, -outer, -half), at(center, right, up, outer, -half), ey, ez, band, main, alt, alpha);
        bar(vc, m, at(center, right, up, -outer, half), at(center, right, up, outer, half), ey, ez, band, main, alt, alpha);
        bar(vc, m, at(center, right, up, -half, -inner), at(center, right, up, -half, inner), ex, ez, band, alt, main, alpha);
        bar(vc, m, at(center, right, up, half, -inner), at(center, right, up, half, inner), ex, ez, band, alt, main, alpha);
    }

    public static void ribbon(VertexConsumer vc, Matrix4f m, List<Vector3f> points, float radius,
                              float[] color, float alpha, boolean dashed) {
        for (int i = 0; i + 1 < points.size(); i++) {
            if (dashed && i % 3 == 2) continue;
            Vector3f a = points.get(i);
            Vector3f b = points.get(i + 1);
            Vector3f d = new Vector3f(b).sub(a);
            if (d.lengthSquared() < 1e-8f) continue;
            Vector3f dir = new Vector3f(d).normalize();
            Vector3f ref = Math.abs(dir.y) > 0.99f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
            Vector3f ax = new Vector3f(ref).cross(dir).normalize().mul(radius);
            Vector3f ay = new Vector3f(dir).cross(ax).normalize().mul(radius);
            box(vc, m, new Vector3f(a).add(b).mul(0.5f), d.mul(0.5f), ax, ay, color[0], color[1], color[2], alpha);
        }
    }

    private static Vector3f at(Vector3f c, Vector3f right, Vector3f up, float u, float v) {
        return new Vector3f(c.x + right.x * u + up.x * v, c.y + right.y * u + up.y * v, c.z + right.z * u + up.z * v);
    }

    private static void bar(VertexConsumer vc, Matrix4f m, Vector3f a, Vector3f b, Vector3f ex1, Vector3f ex2,
                            float bandLength, float[] main, float[] alt, float alpha) {
        Vector3f d = new Vector3f(b).sub(a);
        float len = d.length();
        if (len < 1e-4f) return;
        int bands = Math.max(1, Math.round(len / bandLength));
        Vector3f step = new Vector3f(d).mul(1f / bands);
        Vector3f halfStep = new Vector3f(step).mul(0.5f);
        for (int i = 0; i < bands; i++) {
            Vector3f c = new Vector3f(a).add(new Vector3f(step).mul(i)).add(halfStep);
            float[] col = (i & 1) == 0 ? main : alt;
            box(vc, m, c, halfStep, ex1, ex2, col[0], col[1], col[2], alpha);
        }
    }

    private static void box(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f ex, Vector3f ey, Vector3f ez,
                            float r, float g, float b, float a) {
        face(vc, m, c, ez, ex, ey, r, g, b, a);
        face(vc, m, c, new Vector3f(ez).negate(), ex, ey, r, g, b, a);
        face(vc, m, c, ex, ey, ez, r, g, b, a);
        face(vc, m, c, new Vector3f(ex).negate(), ey, ez, r, g, b, a);
        face(vc, m, c, ey, ez, ex, r, g, b, a);
        face(vc, m, c, new Vector3f(ey).negate(), ez, ex, r, g, b, a);
    }

    private static void face(VertexConsumer vc, Matrix4f m, Vector3f c, Vector3f out, Vector3f u, Vector3f v,
                             float r, float g, float b, float a) {
        Vector3f o = new Vector3f(c).add(out);
        float dot = out.lengthSquared() < 1e-8f ? 0f : new Vector3f(out).normalize().dot(LIGHT);
        float s = 0.55f + 0.45f * (0.5f + 0.5f * dot);
        float cr = r * s, cg = g * s, cb = b * s;
        vertex(vc, m, o, u, v, -1, -1, cr, cg, cb, a);
        vertex(vc, m, o, u, v, 1, -1, cr, cg, cb, a);
        vertex(vc, m, o, u, v, 1, 1, cr, cg, cb, a);
        vertex(vc, m, o, u, v, -1, 1, cr, cg, cb, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Vector3f o, Vector3f u, Vector3f v,
                               float su, float sv, float r, float g, float b, float a) {
        vc.vertex(m, o.x + u.x * su + v.x * sv, o.y + u.y * su + v.y * sv, o.z + u.z * su + v.z * sv)
            .color(r, g, b, a).endVertex();
    }
}
