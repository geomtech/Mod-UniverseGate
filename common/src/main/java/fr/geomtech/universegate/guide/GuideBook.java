package fr.geomtech.universegate.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.geomtech.universegate.UniverseGate;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.ItemStack;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The guide's content, read from the resource packs so it can be translated and edited without
 * touching the code:
 * <ul>
 *   <li>assets/universegate/guide/&lt;language&gt;.json: chapters and their text (falls back to en_us);</li>
 *   <li>assets/universegate/guide/scenes/&lt;id&gt;.json: animated scenes, see {@link GuideScene}.</li>
 * </ul>
 * A chapter is {"id", "title", "icon", "elements": [...]}, each element one of
 * {"title": text}, {"text": text}, {"placeholder": text} (lore still to be written),
 * {"scene": id, "captions": [one per step]}, {"recipes": [recipe file names]},
 * {"items": [item ids]} or {"setting": "live_portal_view"}.
 */
final class GuideBook {
    sealed interface Element permits Title, Text, Scene, Recipes, Items, Setting {
    }

    record Title(String text) implements Element {
    }

    record Text(String text, boolean placeholder) implements Element {
    }

    record Scene(String id, SceneView scene, List<String> captions) implements Element {
    }

    record Recipes(List<GuideRecipes.Recipe> recipes) implements Element {
    }

    record Items(List<ItemStack> items) implements Element {
    }

    record Setting(String key) implements Element {
    }

    record Chapter(String id, String title, ItemStack icon, List<Element> elements) {
    }

    final List<Chapter> chapters;

    private GuideBook(List<Chapter> chapters) {
        this.chapters = chapters;
    }

    static GuideBook load() {
        GuideRecipes.clearCache();
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        JsonObject json = read("guide/" + language + ".json").orElseGet(() -> read("guide/en_us.json").orElse(null));
        List<Chapter> chapters = new ArrayList<>();
        Map<String, SceneView> scenes = new HashMap<>();
        if (json != null && json.has("chapters")) {
            for (JsonElement element : json.getAsJsonArray("chapters")) {
                try {
                    chapters.add(chapter(element.getAsJsonObject(), scenes));
                } catch (RuntimeException e) {
                    UniverseGate.LOGGER.warn("Guide: skipping a broken chapter", e);
                }
            }
        }
        if (chapters.isEmpty()) {
            chapters.add(new Chapter("missing", "UniverseGate", ItemStack.EMPTY,
                    List.of(new Text("The guide files could not be loaded.", false))));
        }
        return new GuideBook(List.copyOf(chapters));
    }

    private static Chapter chapter(JsonObject json, Map<String, SceneView> scenes) {
        List<Element> elements = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray("elements")) {
            JsonObject e = entry.getAsJsonObject();
            if (e.has("title")) elements.add(new Title(e.get("title").getAsString()));
            else if (e.has("text")) elements.add(new Text(e.get("text").getAsString(), false));
            else if (e.has("placeholder")) elements.add(new Text(e.get("placeholder").getAsString(), true));
            else if (e.has("scene")) {
                String id = e.get("scene").getAsString();
                SceneView scene = scenes.computeIfAbsent(id, GuideBook::scene);
                if (scene != null) elements.add(new Scene(id, scene, strings(e.getAsJsonArray("captions"))));
            } else if (e.has("recipes")) {
                List<GuideRecipes.Recipe> recipes = new ArrayList<>();
                for (String id : strings(e.getAsJsonArray("recipes"))) {
                    GuideRecipes.Recipe recipe = GuideRecipes.get(id);
                    if (recipe != null) recipes.add(recipe);
                }
                if (!recipes.isEmpty()) elements.add(new Recipes(List.copyOf(recipes)));
            } else if (e.has("items")) {
                List<ItemStack> items = new ArrayList<>();
                for (String id : strings(e.getAsJsonArray("items"))) {
                    ItemStack stack = GuideRender.stack(id, 1);
                    if (!stack.isEmpty()) items.add(stack);
                }
                if (!items.isEmpty()) elements.add(new Items(List.copyOf(items)));
            } else if (e.has("setting")) elements.add(new Setting(e.get("setting").getAsString()));
        }
        String icon = json.has("icon") ? json.get("icon").getAsString() : "";
        return new Chapter(json.get("id").getAsString(), json.get("title").getAsString(),
                GuideRender.stack(icon, 1), List.copyOf(elements));
    }

    private static SceneView scene(String id) {
        Optional<JsonObject> json = read("guide/scenes/" + id + ".json");
        if (json.isEmpty()) {
            UniverseGate.LOGGER.warn("Guide: scene {} not found", id);
            return null;
        }
        try {
            JsonObject data = json.get();
            if (data.has("world") && data.get("world").getAsBoolean()) {
                GuideScene3D scene = GuideScene3D.parse(data);
                if (scene != null) return scene;
            }
            return GuideScene.parse(data);
        } catch (RuntimeException e) {
            UniverseGate.LOGGER.warn("Guide: could not read scene {}", id, e);
            return null;
        }
    }

    private static List<String> strings(JsonArray array) {
        List<String> list = new ArrayList<>();
        if (array != null) for (JsonElement element : array) list.add(element.getAsString());
        return list;
    }

    private static Optional<JsonObject> read(String path) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(UniverseGate.MOD_ID, path);
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) return Optional.empty();
        try (Reader reader = resource.get().openAsReader()) {
            return Optional.of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            UniverseGate.LOGGER.warn("Guide: could not read {}", location, e);
            return Optional.empty();
        }
    }
}
