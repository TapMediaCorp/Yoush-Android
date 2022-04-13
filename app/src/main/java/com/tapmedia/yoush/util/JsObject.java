package com.tapmedia.yoush.util;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import com.tapmedia.yoush.ApplicationContext;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsObject {

    private JsonObject obj;

    public JsObject(JsonObject jsonObject) {
        if (null != jsonObject) {
            obj = jsonObject;
        } else {
            obj = new JsonObject();
        }
    }

    public static JsObject create() {
        JsObject o = new JsObject(new JsonObject());
        return o;
    }

    public static JsObject create(String stringJson) {
        JsObject o = new JsObject(parse(stringJson, JsonObject.class));
        return o;
    }

    public JsObject put(String key, String value) {
        obj.addProperty(key, value);
        return this;
    }

    public JsObject put(String key, Number value) {
        obj.addProperty(key, value);
        return this;
    }

    public JsObject put(String key, Boolean value) {
        obj.addProperty(key, value);
        return this;
    }

    public JsObject put(String key, JsObject value) {
        obj.add(key, value.build());
        return this;
    }

    public JsObject put(String key, JsArray value) {
        obj.add(key, value.build());
        return this;
    }

    public JsonObject build() {
        return obj;
    }

    public static Gson convertFactory = new Gson();

    public static <T> T parse(String stringJson, Class<T> cls) {
        if (TextUtils.isEmpty(stringJson)) {
            return null;
        }
        try {
            return convertFactory.fromJson(stringJson, cls);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static <T> JsObject toJsonObject(T any) {
        try {
            JsonElement element = convertFactory.toJsonTree(any, new TypeToken<T>() {
            }.getType());
            JsonObject obj = element.getAsJsonObject();
            return new JsObject(obj);
        } catch (Exception ignore) {
            return null;
        }
    }

    public static JsObject readAssets(String fileName) {
        try {
            StringBuilder sb = new StringBuilder();
            InputStream is = ApplicationContext.getInstance().getAssets().open(fileName);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String str;
            while ((str = br.readLine()) != null) {
                sb.append(str);
            }
            br.close();
            return JsObject.create(sb.toString());
        } catch (Exception ignore) {
            return null;
        }
    }


    @Override
    public String toString() {
        return obj.toString();
    }

    public String str(String key) {
        if (!obj.has(key)) return "";
        if (obj.get(key).isJsonNull()) return "";
        try {
            return obj.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    public int getInt(String key) {
        if (!obj.has(key)) return -1;
        if (obj.get(key).isJsonNull()) return -1;
        try {
            return obj.get(key).getAsInt();
        } catch (Exception e) {
            return -1;
        }
    }

    public long getLong(String key) {
        if (!obj.has(key)) return -1L;
        if (obj.get(key).isJsonNull()) return -1L;
        try {
            return obj.get(key).getAsLong();
        } catch (Exception e) {
            return -1L;
        }
    }

    public double getDouble(String key) {
        if (!obj.has(key)) return -1.0;
        if (obj.get(key).isJsonNull()) return -1.0;
        try {
            return obj.get(key).getAsDouble();
        } catch (Exception e) {
            return -1.0;
        }
    }

    public float getFloat(String key) {
        if (!obj.has(key)) return -1f;
        if (obj.get(key).isJsonNull()) return -1f;
        try {
            return obj.get(key).getAsFloat();
        } catch (Exception e) {
            return -1f;
        }
    }

    public JsObject obj(String key) {
        if (!obj.has(key)) return null;
        if (obj.get(key).isJsonNull()) return null;
        try {
            JsonObject o = obj.get(key).getAsJsonObject();
            return new JsObject(o);
        } catch (Exception e) {
            return null;
        }
    }

    public List<JsObject> list(String key) {
        ArrayList<JsObject> list = new ArrayList<>();
        if (!obj.has(key)) return list;
        if (obj.get(key).isJsonNull()) return list;
        try {
            JsonElement eArray = obj.get(key);
            if (eArray.isJsonNull() || !eArray.isJsonArray()) {
                return list;
            }
            JsonArray arr = eArray.getAsJsonArray();
            for (JsonElement e : arr) {
                if (e.isJsonNull()) continue;
                if (e.isJsonObject()) {
                    list.add(new JsObject(e.getAsJsonObject()));
                }
            }
            return list;
        } catch (Exception e) {
            return list;
        }
    }

}