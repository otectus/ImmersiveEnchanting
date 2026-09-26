package com.otectus.immersiveenchanting.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Small, strict JSON readers: wrong types fail the file; out-of-range numbers are clamped with a warning. */
final class JsonHelper {

    static JsonObject object(JsonElement element, String what) {
        if (element == null || !element.isJsonObject()) throw new JsonParseException(what + " must be an object");
        return element.getAsJsonObject();
    }

    static JsonObject optionalObject(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        if (e == null || e.isJsonNull()) return null;
        return object(e, "\"" + key + "\"");
    }

    static Double optionalDouble(JsonObject o, String key, double min, double max, Consumer<String> warn) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) throw new JsonParseException("\"" + key + "\" must be a number");
        double v = e.getAsDouble();
        if (!Double.isFinite(v)) throw new JsonParseException("\"" + key + "\" must be finite");
        if (v < min || v > max) {
            warn.accept("\"" + key + "\" = " + v + " clamped to [" + min + ", " + max + "]");
            v = Math.max(min, Math.min(max, v));
        }
        return v;
    }

    static double getDouble(JsonObject o, String key, double fallback, double min, double max, Consumer<String> warn) {
        Double v = optionalDouble(o, key, min, max, warn);
        return v == null ? fallback : v;
    }

    static Integer optionalInt(JsonObject o, String key, int min, int max, Consumer<String> warn) {
        Double v = optionalDouble(o, key, min, max, warn);
        if (v == null) return null;
        if (v != Math.rint(v)) throw new JsonParseException("\"" + key + "\" must be a whole number");
        return (int) Math.round(v);
    }

    static int getInt(JsonObject o, String key, int fallback, int min, int max, Consumer<String> warn) {
        Integer v = optionalInt(o, key, min, max, warn);
        return v == null ? fallback : v;
    }

    static Boolean optionalBoolean(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isBoolean()) throw new JsonParseException("\"" + key + "\" must be true or false");
        return e.getAsBoolean();
    }

    static String optionalString(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) return null;
        if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) throw new JsonParseException("\"" + key + "\" must be a string");
        return e.getAsString();
    }

    static ResourceLocation optionalId(JsonObject o, String key) {
        String s = optionalString(o, key);
        if (s == null) return null;
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null) throw new JsonParseException("\"" + key + "\" is not a valid id: " + s);
        return id;
    }

    static List<String> stringList(JsonObject o, String key) {
        JsonElement e = o.get(key);
        List<String> out = new ArrayList<>();
        if (e == null || e.isJsonNull()) return out;
        if (!e.isJsonArray()) throw new JsonParseException("\"" + key + "\" must be a list");
        for (JsonElement item : e.getAsJsonArray()) {
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) throw new JsonParseException("\"" + key + "\" must contain strings");
            out.add(item.getAsString());
        }
        return out;
    }

    private JsonHelper() {}
}
