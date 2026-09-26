package com.otectus.immersiveenchanting.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.otectus.immersiveenchanting.ImmersiveEnchanting;
import com.otectus.immersiveenchanting.ritual.EventType;
import com.otectus.immersiveenchanting.ritual.MotifLibrary;
import com.otectus.immersiveenchanting.ritual.PatternSetDef;
import com.otectus.immersiveenchanting.ritual.RitualParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Loads pattern sets ({@code data/<ns>/immersive_enchanting/pattern_sets/*.json}). The built-in default is always
 * available, even if every datapack file is removed or broken.
 */
public final class PatternSetManager extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "immersive_enchanting/pattern_sets";
    private static final Gson GSON = new GsonBuilder().create();
    public static final ResourceLocation DEFAULT_ID = new ResourceLocation(PatternSetDef.DEFAULT_ID);

    private static volatile Map<ResourceLocation, PatternSetDef> sets = Map.of(DEFAULT_ID, PatternSetDef.DEFAULT);

    public PatternSetManager() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        try {
            Map<ResourceLocation, PatternSetDef> loaded = new HashMap<>();
            loaded.put(DEFAULT_ID, PatternSetDef.DEFAULT);
            for (Map.Entry<ResourceLocation, JsonElement> entry : files.entrySet()) {
                ResourceLocation id = entry.getKey();
                try {
                    loaded.put(id, parse(id, entry.getValue(), m -> ImmersiveEnchanting.LOGGER.warn("Pattern set {}: {}", id, m)));
                } catch (RuntimeException e) {
                    ImmersiveEnchanting.LOGGER.error("Skipping invalid Immersive Enchanting pattern set {}: {}", id, e.getMessage());
                }
            }
            sets = Map.copyOf(loaded);
        } catch (RuntimeException e) {
            ImmersiveEnchanting.LOGGER.error("Immersive Enchanting could not rebuild pattern sets; keeping the previous set", e);
        }
    }

    public static PatternSetDef get(ResourceLocation id) {
        PatternSetDef set = id == null ? null : sets.get(id);
        if (set == null) {
            if (id != null && !id.equals(DEFAULT_ID)) ImmersiveEnchanting.debug("unknown pattern set {}; using the default", id);
            return sets.getOrDefault(DEFAULT_ID, PatternSetDef.DEFAULT);
        }
        return set;
    }

    public static boolean has(ResourceLocation id) {
        return sets.containsKey(id);
    }

    public static int count() {
        return sets.size();
    }

    static PatternSetDef parse(ResourceLocation id, JsonElement json, Consumer<String> warn) {
        JsonObject root = JsonHelper.object(json, "pattern set");
        List<RitualParams.MotifWeight> motifs = root.has("motifs") ? motifs(root.get("motifs"), warn) : PatternSetDef.defaultMotifs();
        PatternSetDef.Limits limits = limits(JsonHelper.optionalObject(root, "limits"), warn);
        int countIn = JsonHelper.getInt(root, "count_in_beats", 3, 2, 4, warn);
        List<PatternSetDef.TierDef> tiers = new ArrayList<>();
        boolean flat = !root.has("tiers");
        if (flat) {
            tiers.add(tier(root, PatternSetDef.DEFAULT.tier(3), warn, true));
        } else {
            JsonElement array = root.get("tiers");
            if (!array.isJsonArray() || array.getAsJsonArray().size() != 6) throw new JsonParseException("\"tiers\" must list exactly 6 tiers (0-5)");
            JsonArray list = array.getAsJsonArray();
            for (int i = 0; i < 6; i++) tiers.add(tier(JsonHelper.object(list.get(i), "tier " + i), PatternSetDef.DEFAULT.tier(i), warn, false));
        }
        return new PatternSetDef(id.toString(), flat, tiers, motifs, limits, countIn);
    }

    private static PatternSetDef.TierDef tier(JsonObject o, PatternSetDef.TierDef fallback, Consumer<String> warn, boolean flat) {
        int anchors = JsonHelper.getInt(o, "anchors", flat ? 0 : fallback.anchors(), 0, 4, warn);
        if (anchors != 0 && anchors < 3) {
            warn.accept("anchors must be 3 or 4; using 3");
            anchors = 3;
        }
        int minEvents = fallback.minEvents(), maxEvents = fallback.maxEvents();
        JsonObject events = JsonHelper.optionalObject(o, "event_count");
        if (events != null) {
            minEvents = JsonHelper.getInt(events, "min", minEvents, 1, RitualParams.HARD_MAX_EVENTS, warn);
            maxEvents = JsonHelper.getInt(events, "max", maxEvents, 1, RitualParams.HARD_MAX_EVENTS, warn);
        }
        double minBpm = fallback.minBpm(), maxBpm = fallback.maxBpm();
        JsonObject bpm = JsonHelper.optionalObject(o, "bpm");
        if (bpm != null) {
            minBpm = JsonHelper.getDouble(bpm, "min", minBpm, 40, 240, warn);
            maxBpm = JsonHelper.getDouble(bpm, "max", maxBpm, 40, 240, warn);
        }
        if (maxEvents < minEvents || maxBpm < minBpm) throw new JsonParseException("ranges must have min <= max");
        int perfect = fallback.perfectMs(), good = fallback.goodMs(), graze = fallback.grazeMs();
        JsonObject windows = JsonHelper.optionalObject(o, "timing_windows_ms");
        if (windows != null) {
            perfect = JsonHelper.getInt(windows, "perfect", perfect, 15, 250, warn);
            good = JsonHelper.getInt(windows, "good", good, 20, 350, warn);
            graze = JsonHelper.getInt(windows, "graze", graze, 25, 450, warn);
        }
        if (!(perfect < good && good < graze)) throw new JsonParseException("timing windows must grow: perfect < good < graze");
        boolean holds = fallback.allowHolds(), chords = fallback.allowChords();
        if (o.has("allowed_event_types")) {
            holds = false;
            chords = false;
            for (String type : JsonHelper.stringList(o, "allowed_event_types")) {
                EventType t = EventType.byId(type);
                if (t == null) throw new JsonParseException("unknown event type \"" + type + "\" (tap, hold, chord)");
                holds |= t == EventType.HOLD;
                chords |= t == EventType.CHORD;
            }
        }
        List<RitualParams.MotifWeight> motifs = !flat && o.has("motifs") ? motifs(o.get("motifs"), warn) : null;
        return new PatternSetDef.TierDef(anchors, minEvents, maxEvents, minBpm, maxBpm, perfect, good, graze, true, holds, chords, motifs);
    }

    private static List<RitualParams.MotifWeight> motifs(JsonElement element, Consumer<String> warn) {
        if (!element.isJsonArray()) throw new JsonParseException("\"motifs\" must be a list");
        List<RitualParams.MotifWeight> out = new ArrayList<>();
        for (JsonElement e : element.getAsJsonArray()) {
            JsonObject m = JsonHelper.object(e, "motif entry");
            ResourceLocation motif = JsonHelper.optionalId(m, "id");
            if (motif == null) throw new JsonParseException("motif entry without \"id\"");
            if (MotifLibrary.get(motif.toString()) == null) {
                warn.accept("unknown motif " + motif + " ignored");
                continue;
            }
            double weight = JsonHelper.getDouble(m, "weight", 1.0, 0.0, 100.0, warn);
            int minTier = JsonHelper.getInt(m, "min_tier", -1, -1, 5, warn);
            out.add(new RitualParams.MotifWeight(motif.toString(), weight, minTier));
        }
        return out;
    }

    private static PatternSetDef.Limits limits(JsonObject o, Consumer<String> warn) {
        if (o == null) return PatternSetDef.Limits.DEFAULT;
        int chord = JsonHelper.getInt(o, "max_chord_size", 2, 1, 4, warn);
        if (chord != 2) warn.accept("max_chord_size " + chord + " is not supported by generator version 1; chords use 2 lanes");
        int holds = JsonHelper.getInt(o, "max_simultaneous_holds", 1, 0, 4, warn);
        if (holds != 1) warn.accept("max_simultaneous_holds " + holds + " is not supported by generator version 1; using 1");
        double duration = JsonHelper.getDouble(o, "max_duration_seconds", 25.0, 5.0, 60.0, warn);
        return new PatternSetDef.Limits(2, 1, duration);
    }
}
