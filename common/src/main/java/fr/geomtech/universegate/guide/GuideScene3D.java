package fr.geomtech.universegate.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.geomtech.universegate.ModBlocks;
import fr.geomtech.universegate.PortalCoreBlock;
import fr.geomtech.universegate.PortalFrameBlock;
import fr.geomtech.universegate.PortalGateGeometry;
import fr.geomtech.universegate.PortalRingSegments;
import fr.geomtech.universegate.UniverseGate;
import fr.geomtech.universegate.compat.ImmediateBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
//? if <1.21.11 {
import com.mojang.blaze3d.platform.Lighting;
//?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A guide scene built in a {@link GuideWorld} and drawn with the game's own block models and block
 * entity renderers, so the portal ring, wires and clips look exactly as in game.
 *
 * <p>Scene file (assets/universegate/guide/scenes/&lt;id&gt;.json) with {"world": true}:
 * <pre>{"world": true, "steps": [ {"duration": 60,
 *     "place": [ {"block": "universegate:electric_wire", "from": [0,0,0], "to": [3,0,0], "props": {"facing": "down"}} ],
 *     "ring": {"core": [0,0,0], "right": "east", "segments": [1, 2]},
 *     "form": {"core": [0,0,0], "right": "east"},
 *     "set": [ same as place, but the block changes in place instead of dropping in ],
 *     "remove": [ [1,0,0] ] } ]}</pre>
 * "ring" places portal ring segments (1 to 6) or single pieces (1 to 17) around a core, "form" assembles the gate
 * (the 3D ring then replaces the blocks).
 */
final class GuideScene3D implements SceneView {
    private static final int STAGGER = 3;
    private static final int DROP = 6;
    private static final int END_HOLD = 60;
    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;

    /** One operation in a step, applied in order. */
    private sealed interface Op permits Place, Remove, Form {
    }

    private record Place(BlockPos pos, BlockState state) implements Op {
    }

    private record Remove(BlockPos pos) implements Op {
    }

    private record Form(BlockPos core, Direction right) implements Op {
    }

    private record Step(int duration, List<Op> ops, List<BlockPos> dropped) {
    }

    private final List<Step> steps;
    private final BlockState floor;
    private final List<BlockPos> floorCells;
    private final float centerX, centerY, centerZ, radius;
    private GuideWorld world;
    private int builtStep = -1;

    private GuideScene3D(List<Step> steps, BlockState floor, List<BlockPos> floorCells,
                         float cx, float cy, float cz, float radius) {
        this.steps = steps;
        this.floor = floor;
        this.floorCells = floorCells;
        this.centerX = cx;
        this.centerY = cy;
        this.centerZ = cz;
        this.radius = radius;
    }

    // ------------------------------------------------------------------ parsing

    static GuideScene3D parse(JsonObject json) {
        List<Step> steps = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (JsonElement element : json.getAsJsonArray("steps")) {
            JsonObject step = element.getAsJsonObject();
            List<Op> ops = new ArrayList<>();
            List<BlockPos> dropped = new ArrayList<>();
            if (step.has("remove")) {
                for (JsonElement pos : step.getAsJsonArray("remove")) ops.add(new Remove(pos(pos.getAsJsonArray())));
            }
            for (String key : new String[] { "place", "set" }) {
                if (!step.has(key)) continue;
                boolean drop = key.equals("place");
                for (JsonElement entry : step.getAsJsonArray(key)) {
                    JsonObject place = entry.getAsJsonObject();
                    BlockState state = state(place.get("block").getAsString(), place.getAsJsonObject("props"));
                    if (state == null) continue;
                    BlockPos from = pos(place.getAsJsonArray(place.has("at") ? "at" : "from"));
                    BlockPos to = place.has("to") ? pos(place.getAsJsonArray("to")) : from;
                    List<BlockState> column = column(state, place.getAsJsonObject("props"));
                    for (BlockPos p : BlockPos.betweenClosed(from, to)) {
                        for (int i = 0; i < column.size(); i++) {
                            BlockPos pos = p.above(i).immutable();
                            ops.add(new Place(pos, column.get(i)));
                            if (drop) dropped.add(pos);
                        }
                    }
                }
            }
            if (step.has("ring")) {
                JsonObject ring = step.getAsJsonObject("ring");
                BlockPos core = pos(ring.getAsJsonArray("core"));
                Direction right = horizontal(ring);
                List<Integer> pieces = new ArrayList<>();
                if (ring.has("segments")) {
                    for (JsonElement segment : ring.getAsJsonArray("segments")) {
                        for (int piece : PortalGateGeometry.segmentPieces(segment.getAsInt() - 1)) pieces.add(piece);
                    }
                }
                if (ring.has("pieces")) {
                    for (JsonElement piece : ring.getAsJsonArray("pieces")) pieces.add(piece.getAsInt());
                }
                for (int index : pieces) {
                    BlockPos cell = PortalRingSegments.cell(core, right, index);
                    ops.add(new Place(cell, PortalRingSegments.anchorState(index, right)));
                    dropped.add(cell);
                }
            }
            if (step.has("form")) {
                JsonObject form = step.getAsJsonObject("form");
                Direction right = horizontal(form);
                ops.add(new Form(pos(form.getAsJsonArray("core")), right));
            }
            for (Op op : ops) {
                BlockPos p = op instanceof Place place ? place.pos() : null;
                if (p == null) continue;
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            }
            int auto = dropped.size() * STAGGER + DROP + 30;
            int duration = step.has("duration") ? Math.max(step.get("duration").getAsInt(), dropped.size() * STAGGER + DROP) : auto;
            steps.add(new Step(Math.max(20, duration), List.copyOf(ops), List.copyOf(dropped)));
        }
        if (steps.isEmpty() || minX > maxX) return null;
        // A floor under the scene, as in game: wires, clips and machines need something to stand on.
        List<BlockPos> floorCells = new ArrayList<>();
        BlockState floor = null;
        JsonElement floorJson = json.get("floor");
        boolean noFloor = floorJson != null && floorJson.isJsonPrimitive() && floorJson.getAsJsonPrimitive().isBoolean()
                && !floorJson.getAsBoolean();
        if (!noFloor) {
            String id = floorJson != null && floorJson.isJsonPrimitive() && floorJson.getAsJsonPrimitive().isString()
                    ? floorJson.getAsString() : "minecraft:smooth_stone";
            floor = state(id, null);
        }
        if (floor != null) {
            for (int x = minX - 1; x <= maxX + 1; x++) {
                for (int z = minZ - 1; z <= maxZ + 1; z++) floorCells.add(new BlockPos(x, minY - 1, z));
            }
            minY -= 1;
        }
        float cx = (minX + maxX + 1) / 2.0F, cy = (minY + maxY + 1) / 2.0F, cz = (minZ + maxZ + 1) / 2.0F;
        float rx = (maxX - minX + 1) / 2.0F, ry = (maxY - minY + 1) / 2.0F, rz = (maxZ - minZ + 1) / 2.0F;
        float radius = Mth.sqrt(rx * rx + ry * ry + rz * rz);
        return new GuideScene3D(steps, floor, List.copyOf(floorCells), cx, cy, cz, Math.max(1.0F, radius));
    }

    /** The "right" direction of a ring (east or south; anything else means east). */
    private static Direction horizontal(JsonObject json) {
        String name = json.has("right") ? json.get("right").getAsString() : "east";
        for (Direction direction : Direction.values()) {
            if (direction.getSerializedName().equals(name) && direction.getAxis().isHorizontal()) return direction;
        }
        return Direction.EAST;
    }

    private static BlockPos pos(JsonArray array) {
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }

    /** "namespace:block" plus optional properties, or null if the block does not exist. */
    private static BlockState state(String id, JsonObject props) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) return null;
        //? if >=1.21.11 {
        /*Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(location);
        *///?} else {
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(location);
        //?}
        if (block.isEmpty()) {
            UniverseGate.LOGGER.warn("Guide: unknown block {}", id);
            return null;
        }
        BlockState state = block.get().defaultBlockState();
        if (props != null) {
            for (Map.Entry<String, JsonElement> entry : props.entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
                if (property == null) continue;
                state = with(state, property, entry.getValue().getAsString());
            }
        }
        return state;
    }

    /**
     * Tall machines (solar panel, parabola, lightning antenna) are a column of "part" blocks in game.
     * Unless the scene names a part, the whole column is placed, bottom first.
     */
    private static List<BlockState> column(BlockState state, JsonObject props) {
        Property<?> part = state.getBlock().getStateDefinition().getProperty("part");
        if (part == null || (props != null && props.has("part"))) return List.of(state);
        List<BlockState> column = new ArrayList<>();
        for (String name : new String[] { "base", "lower", "upper", "top" }) {
            BlockState piece = with(state, part, name);
            if (column.isEmpty() ? !name.equals(partName(piece, part)) : piece == column.get(column.size() - 1)) {
                return List.of(state);
            }
            column.add(piece);
        }
        return column;
    }

    private static <T extends Comparable<T>> String partName(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.isPresent() ? state.setValue(property, parsed.get()) : state;
    }

    // ------------------------------------------------------------------ timeline

    @Override
    public int stepCount() {
        return steps.size();
    }

    @Override
    public int length() {
        int total = END_HOLD;
        for (Step step : steps) total += step.duration;
        return total;
    }

    @Override
    public int stepAt(float time) {
        float t = time;
        for (int i = 0; i < steps.size(); i++) {
            if (t < steps.get(i).duration) return i;
            t -= steps.get(i).duration;
        }
        return steps.size() - 1;
    }

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

    /** Rebuilds the world as it stands at the end of {@code step}. */
    private boolean build(int step) {
        if (world == null) world = GuideWorld.create();
        if (world == null) return false;
        if (builtStep == step) return true;
        world.clear();
        for (BlockPos pos : floorCells) world.put(pos, floor);
        for (int i = 0; i <= step; i++) {
            for (Op op : steps.get(i).ops) {
                if (op instanceof Place place) world.put(place.pos(), place.state());
                else if (op instanceof Remove remove) world.put(remove.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                else if (op instanceof Form form) form(form);
            }
        }
        world.settle();
        builtStep = step;
        return true;
    }

    /** Marks the core and its ring segments as an assembled gate, as the core does in game. */
    private void form(Form form) {
        BlockState core = world.getBlockState(form.core());
        if (core.is(ModBlocks.PORTAL_CORE)) {
            world.put(form.core(), core.setValue(PortalCoreBlock.FORMED, true).setValue(PortalCoreBlock.AXIS, form.right().getAxis()));
        }
        for (int piece = 1; piece <= PortalGateGeometry.PIECE_COUNT; piece++) {
            BlockPos cell = PortalRingSegments.cell(form.core(), form.right(), piece);
            BlockState frame = world.getBlockState(cell);
            if (frame.is(ModBlocks.PORTAL_FRAME)) world.put(cell, frame.setValue(PortalFrameBlock.FORMED, true));
        }
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int x, int y, int w, int h, float time, float yaw,
                       int clipX0, int clipY0, int clipX1, int clipY1) {
        int step = stepAt(time);
        if (!build(step)) {
            Component text = Component.translatable("guide.universegate.scene.in_world");
            var font = Minecraft.getInstance().font;
            g.drawString(font, text, x + (w - font.width(text)) / 2, y + h / 2 - 4, 0xFF8C8C8C, false);
            return;
        }
        world.setTime((long) time);
        float scale = Math.min(w, h) * 0.5F / (radius + 0.5F);
        Frame frame = new Frame(time - startOf(step), step, time, yaw, 30.0F, scale);
        //? if >=1.21.11 {
        /*GuideScenePip.submit(g, this, frame, x, y, x + w, y + h, clipX0, clipY0, clipX1, clipY1);
        *///?} else {
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(x + w / 2.0F, y + h / 2.0F, 200.0F);
        pose.scale(scale, -scale, scale);
        g.enableScissor(x, y, x + w, y + h);
        Lighting.setupFor3DItems();
        drawWorld(pose, g.bufferSource(), frame);
        g.flush();
        g.disableScissor();
        pose.popPose();
        //?}
    }

    /** Everything a frame needs, so 1.21.11 can draw it later into its own texture. */
    record Frame(float stepTime, int step, float time, float yaw, float pitch, float scale) {
    }

    /**
     * Draws the world around the origin of {@code pose}, one unit per block, Y up, turned by the
     * frame's yaw and pitch. Blocks of the current step drop into place one after the other.
     */
    void drawWorld(PoseStack pose, MultiBufferSource buffers, Frame frame) {
        if (world == null || builtStep != frame.step()) return;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(frame.pitch()));
        pose.mulPose(Axis.YP.rotationDegrees(225.0F + frame.yaw()));
        pose.translate(-centerX, -centerY, -centerZ);

        Map<BlockPos, Float> lift = new HashMap<>();
        List<BlockPos> dropped = steps.get(frame.step()).dropped;
        for (int i = 0; i < dropped.size(); i++) {
            float t = (frame.stepTime() - i * STAGGER) / DROP;
            lift.put(dropped.get(i), t < 0 ? -1.0F : (1.0F - easeOut(Mth.clamp(t, 0.0F, 1.0F))) * 1.5F);
        }

        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        float partial = frame.time() - (float) Math.floor(frame.time());
        for (Map.Entry<BlockPos, BlockState> entry : new LinkedHashMap<>(world.blocks()).entrySet()) {
            BlockPos pos = entry.getKey();
            Float up = lift.get(pos);
            if (up != null && up < 0) continue; // not dropped yet
            pose.pushPose();
            pose.translate(pos.getX(), pos.getY() + (up == null ? 0.0F : up), pos.getZ());
            try {
                blocks.renderSingleBlock(entry.getValue(), pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                BlockEntity be = world.getBlockEntity(pos);
                if (be != null) renderBlockEntity(be, partial, pose, buffers);
            } catch (RuntimeException e) {
                UniverseGate.LOGGER.debug("Guide: could not draw {}", entry.getValue(), e);
            }
            pose.popPose();
        }
        pose.popPose();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void renderBlockEntity(BlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers) {
        var dispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
        //? if >=1.21.11 {
        /*Object renderer = dispatcher.getRenderer(be);
        if (renderer instanceof ImmediateBlockEntityRenderer immediate) {
            immediate.render(be, partial, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
        *///?} else {
        var renderer = dispatcher.getRenderer(be);
        if (renderer != null) renderer.render(be, partial, pose, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        //?}
    }

    private static float easeOut(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }
}
