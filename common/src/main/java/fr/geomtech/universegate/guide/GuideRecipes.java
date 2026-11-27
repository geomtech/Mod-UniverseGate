package fr.geomtech.universegate.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.geomtech.universegate.UniverseGate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the mod's own recipe files from its jar. Since 1.21.2 the client no longer receives the
 * recipes from the server, so the guide reads the JSON shipped with the mod instead (the 1.21.11 jar
 * holds the 1.21.11 copies, with their string ingredients).
 */
final class GuideRecipes {
    enum Kind { CRAFTING, SMELTING }

    /** {@code grid} has 9 entries (row by row) for crafting, 1 for smelting. Empty stacks are empty slots. */
    record Recipe(String id, Kind kind, ItemStack[] grid, ItemStack result) {
    }

    private static final Map<String, Recipe> CACHE = new HashMap<>();

    private GuideRecipes() {
    }

    /** {@code id} is a recipe file name in data/universegate/recipe, without ".json"; may be null if missing. */
    static Recipe get(String id) {
        return CACHE.computeIfAbsent(id, GuideRecipes::load);
    }

    static void clearCache() {
        CACHE.clear();
    }

    private static Recipe load(String id) {
        String path = "/data/" + UniverseGate.MOD_ID + "/recipe/" + id + ".json";
        try (InputStream in = GuideRecipes.class.getResourceAsStream(path)) {
            if (in == null) {
                UniverseGate.LOGGER.warn("Guide: recipe {} not found", path);
                return null;
            }
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(id, json);
        } catch (Exception e) {
            UniverseGate.LOGGER.warn("Guide: could not read recipe {}", path, e);
            return null;
        }
    }

    private static Recipe parse(String id, JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        ItemStack result = result(json.get("result"));
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                ItemStack[] grid = emptyGrid();
                JsonArray pattern = json.getAsJsonArray("pattern");
                JsonObject key = json.getAsJsonObject("key");
                for (int row = 0; row < Math.min(3, pattern.size()); row++) {
                    String line = pattern.get(row).getAsString();
                    for (int col = 0; col < Math.min(3, line.length()); col++) {
                        String symbol = String.valueOf(line.charAt(col));
                        if (!symbol.isBlank() && key.has(symbol)) grid[row * 3 + col] = ingredient(key.get(symbol));
                    }
                }
                return new Recipe(id, Kind.CRAFTING, grid, result);
            }
            case "minecraft:crafting_shapeless" -> {
                ItemStack[] grid = emptyGrid();
                JsonArray ingredients = json.getAsJsonArray("ingredients");
                for (int i = 0; i < Math.min(9, ingredients.size()); i++) grid[i] = ingredient(ingredients.get(i));
                return new Recipe(id, Kind.CRAFTING, grid, result);
            }
            case "minecraft:smelting", "minecraft:smoking", "minecraft:blasting", "minecraft:campfire_cooking" ->
                    { return new Recipe(id, Kind.SMELTING, new ItemStack[] { ingredient(json.get("ingredient")) }, result); }
            default -> {
                UniverseGate.LOGGER.warn("Guide: recipe type {} of {} is not shown", type, id);
                return null;
            }
        }
    }

    private static ItemStack[] emptyGrid() {
        ItemStack[] grid = new ItemStack[9];
        for (int i = 0; i < 9; i++) grid[i] = ItemStack.EMPTY;
        return grid;
    }

    /** 1.21.1: {"item": id} or [ ... ]; 1.21.11: "id", "#tag" or [ ... ]. Shows the first alternative. */
    private static ItemStack ingredient(JsonElement element) {
        if (element == null || element.isJsonNull()) return ItemStack.EMPTY;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            return array.size() == 0 ? ItemStack.EMPTY : ingredient(array.get(0));
        }
        if (element.isJsonPrimitive()) return orBarrier(GuideRender.stack(element.getAsString(), 1));
        JsonObject object = element.getAsJsonObject();
        if (object.has("item")) return orBarrier(GuideRender.stack(object.get("item").getAsString(), 1));
        return new ItemStack(Items.BARRIER);
    }

    private static ItemStack result(JsonElement element) {
        if (element == null) return ItemStack.EMPTY;
        if (element.isJsonPrimitive()) return GuideRender.stack(element.getAsString(), 1);
        JsonObject object = element.getAsJsonObject();
        String id = object.has("id") ? object.get("id").getAsString() : object.has("item") ? object.get("item").getAsString() : "";
        int count = object.has("count") ? object.get("count").getAsInt() : 1;
        return GuideRender.stack(id, count);
    }

    private static ItemStack orBarrier(ItemStack stack) {
        return stack.isEmpty() ? new ItemStack(Items.BARRIER) : stack;
    }
}
