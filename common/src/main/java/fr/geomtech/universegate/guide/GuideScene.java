package fr.geomtech.universegate.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An animated build scene: blocks drop into place step by step, drawn in the same isometric view
 * as block icons in inventories (so every block looks exactly like its item, in pixel art).
 *
 * <p>Scene files live in assets/universegate/guide/scenes/&lt;id&gt;.json:
 * <pre>{"steps": [ {"duration": 60, "place": [ {"item": "universegate:portal_core", "at": [2, 0, 0]},
 *                                            {"item": "universegate:portal_frame", "from": [0, 0, 0], "to": [4, 0, 0]} ],
 *                 "remove": [ [2, 0, 0] ]} ]}</pre>
 * Coordinates are [x, y, z] with y up; the camera looks at the +x and +z faces.
 * Captions come from the chapter file, so they can be translated.
 */
final class GuideScene implements SceneView {
    /** Pixels per block edge at scale 1 (block icons are 16 * 0.625 wide). */
    private static final float EDGE = 10.0F;
    private static final float ISO_X = EDGE * 0.70710677F;
    private static final float ISO_Y = EDGE * 0.35355338F;
    private static final float ISO_H = EDGE * 0.8660254F;
    private static final float DEPTH_XZ = EDGE * 0.61237244F;
    private static final float DEPTH_Y = EDGE * 0.5F;
    /** Ticks between two blocks of the same step, and the drop time of one block. */
    private static final int STAGGER = 3;
    private static final int DROP = 6;
    private static final int END_HOLD = 60;

    record Block(int x, int y, int z, ItemStack stack) {
        long key() {
            return GuideScene.key(x, y, z);
        }
    }

    /** {@code placed} are the blocks added by this step, {@code visible} everything shown once it ends. */
    record Step(int duration, List<Block> placed, List<Block> visible) {
    }

    final List<Step> steps;
    private final int minX, maxX, minY, maxY, minZ, maxZ;

    private GuideScene(List<Step> steps) {
        this.steps = steps;
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE,
                z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Step step : steps) {
            for (Block b : step.placed) {
                x0 = Math.min(x0, b.x); x1 = Math.max(x1, b.x);
                y0 = Math.min(y0, b.y); y1 = Math.max(y1, b.y);
                z0 = Math.min(z0, b.z); z1 = Math.max(z1, b.z);
            }
        }
        if (x0 > x1) { x0 = x1 = y0 = y1 = z0 = z1 = 0; }
        minX = x0; maxX = x1; minY = y0; maxY = y1; minZ = z0; maxZ = z1;
    }

    static GuideScene parse(JsonObject json) {
        Map<Long, Block> world = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            if (step.has("remove")) {
                for (JsonElement pos : step.getAsJsonArray("remove")) {
                    int[] p = pos(pos.getAsJsonArray());
                    world.remove(key(p[0], p[1], p[2]));
                }
            }
            List<Block> placed = new ArrayList<>();
            if (step.has("place")) {
                for (JsonElement entry : step.getAsJsonArray("place")) {
                    JsonObject place = entry.getAsJsonObject();
                    ItemStack stack = GuideRender.stack(place.get("item").getAsString(), 1);
                    if (stack.isEmpty()) continue;
                    int[] from = pos(place.getAsJsonArray(place.has("at") ? "at" : "from"));
                    int[] to = place.has("to") ? pos(place.getAsJsonArray("to")) : from;
                    for (int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
                        for (int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
                            for (int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
                                Block block = new Block(x, y, z, stack);
                                world.put(block.key(), block);
                                placed.add(block);
                            }
                        }
                    }
                }
            }
            int minDuration = placed.size() * STAGGER + DROP + 30;
            int duration = step.has("duration") ? Math.max(step.get("duration").getAsInt(), placed.size() * STAGGER + DROP) : minDuration;
            steps.add(new Step(Math.max(duration, 20), List.copyOf(placed), List.copyOf(world.values())));
        }
        return new GuideScene(steps);
    }

    private static int[] pos(JsonArray array) {
        return new int[] { array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt() };
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    @Override
    public int stepCount() {
        return steps.size();
    }

    /** Total loop length in ticks, the last step being held a little longer. */
    @Override
    public int length() {
        int total = END_HOLD;
        for (Step step : steps) total += step.duration;
        return total;
    }

    /** The step playing at {@code time} ticks into the loop. */
    @Override
    public int stepAt(float time) {
        float t = time;
        for (int i = 0; i < steps.size(); i++) {
            if (t < steps.get(i).duration) return i;
            t -= steps.get(i).duration;
        }
        return steps.size() - 1;
    }

    /** The loop time at which step {@code index} starts. */
    @Override
    public int startOf(int index) {
        int t = 0;
        for (int i = 0; i < Math.min(index, steps.size()); i++) t += steps.get(i).duration;
        return t;
    }

    @Override
    public int stepDuration(int index) {
        return steps.get(index).duration;
    }

    @Override
    public void render(GuiGraphics g, int x, int y, int w, int h, float time, float yaw,
                       int clipX0, int clipY0, int clipX1, int clipY1) {
        render(g, x, y, w, h, time, Math.floorMod(Math.round(yaw / 90.0F), 4));
    }

    /**
     * Draws the scene centred in the box, at loop time {@code time} (ticks), turned by
     * {@code rotation} quarter turns.
     */
    void render(GuiGraphics g, int boxX, int boxY, int boxW, int boxH, float time, int rotation) {
        if (steps.isEmpty()) return;
        int stepIndex = stepAt(time);
        float stepTime = time - startOf(stepIndex);
        Step step = steps.get(stepIndex);

        // Shown: what this step keeps from before, plus this step's blocks once their drop started.
        Map<Long, Float> drops = new LinkedHashMap<>();
        java.util.Set<Long> placedKeys = new java.util.HashSet<>();
        for (int i = 0; i < step.placed.size(); i++) {
            long key = step.placed.get(i).key();
            placedKeys.add(key);
            float start = i * STAGGER;
            if (stepTime >= start) drops.put(key, Mth.clamp((stepTime - start) / DROP, 0.0F, 1.0F));
        }
        List<Block> shown = new ArrayList<>();
        for (Block block : step.visible) {
            if (!placedKeys.contains(block.key()) || drops.containsKey(block.key())) shown.add(block);
        }

        float scale = fitScale(boxW, boxH);
        float cx = (minX + maxX) / 2.0F, cy = (minY + maxY) / 2.0F, cz = (minZ + maxZ) / 2.0F;
        float originX = boxX + boxW / 2.0F;
        float originY = boxY + boxH / 2.0F;

        List<float[]> placed = new ArrayList<>(shown.size());
        float minDepth = Float.MAX_VALUE;
        for (Block block : shown) {
            float[] p = rotate(block.x - cx, block.z - cz, rotation);
            float y = block.y - cy;
            Float drop = drops.get(block.key());
            float lift = drop == null ? 0.0F : (1.0F - easeOut(drop)) * 1.5F;
            float depth = depth(p[0], y + lift, p[1]);
            minDepth = Math.min(minDepth, depth);
            placed.add(new float[] { p[0], y, p[1], lift, depth });
        }
        // Painter's order: far blocks first. 1.21.1 also depth-tests GUI items, so each block is
        // given its real depth as well.
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < placed.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble(i -> placed.get(i)[0] + placed.get(i)[1] + placed.get(i)[2]));

        g.enableScissor(boxX, boxY, boxX + boxW, boxY + boxH);
        for (int i : order) {
            float[] p = placed.get(i);
            float sx = originX + (p[0] - p[2]) * ISO_X * scale;
            float sy = originY + ((p[0] + p[2]) * ISO_Y - (p[1] + p[3]) * ISO_H) * scale;
            float z = 60.0F + (p[4] - minDepth) * scale - 150.0F * scale;
            GuideRender.push(g);
            GuideRender.translate(g, sx - 8.0F * scale, sy - 8.0F * scale, z);
            GuideRender.scale(g, scale);
            g.renderItem(shown.get(i).stack, 0, 0);
            GuideRender.pop(g);
        }
        g.disableScissor();
    }

    private static float easeOut(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static float depth(float x, float y, float z) {
        return (x + z) * DEPTH_XZ + y * DEPTH_Y;
    }

    private static float[] rotate(float x, float z, int rotation) {
        return switch (rotation & 3) {
            case 1 -> new float[] { -z, x };
            case 2 -> new float[] { -x, -z };
            case 3 -> new float[] { z, -x };
            default -> new float[] { x, z };
        };
    }

    /** Largest scale (capped) at which the whole scene fits the box in every rotation. */
    private float fitScale(int boxW, int boxH) {
        float hx = (maxX - minX) / 2.0F, hz = (maxZ - minZ) / 2.0F, hy = (maxY - minY) / 2.0F;
        float halfW = (hx + hz) * ISO_X + ISO_X;
        float halfH = (hx + hz) * ISO_Y + hy * ISO_H + ISO_Y + ISO_H / 2.0F;
        // falling blocks start 1.5 blocks higher
        halfH += ISO_H * 0.75F;
        float scale = Math.min((boxW - 8) / (2.0F * halfW), (boxH - 8) / (2.0F * halfH));
        return Mth.clamp(scale, 0.5F, 2.5F);
    }
}
