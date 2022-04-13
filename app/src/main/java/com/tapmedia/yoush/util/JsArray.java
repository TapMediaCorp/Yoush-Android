package com.tapmedia.yoush.util;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

import com.tapmedia.yoush.ApplicationContext;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsArray {

    private JsonArray array;

    private JsArray(JsonArray jsonArray) {
        if (null != jsonArray) {
            array = jsonArray;
        } else {
            array = new JsonArray();
        }
    }

    public static JsArray create() {
        return new JsArray(new JsonArray());
    }

    public static JsArray create(JsonArray jsonArray) {
        return new JsArray(jsonArray);
    }


    public JsArray put(String value) {
        array.add(value);
        return this;
    }

    public JsArray put(Number value) {
        array.add(value);
        return this;
    }

    public JsArray put(Boolean value) {
        array.add(value);
        return this;
    }

    public JsArray put(JsObject value) {
        array.add(value.build());
        return this;
    }

    public JsArray put(JsArray value) {
        array.add(value.build());
        return this;
    }

    public static Gson convertFactory = new Gson();

    public static <T> ArrayList<T> readAssets(String fileName) {
        try {

            InputStream is = ApplicationContext.getInstance().getAssets().open(fileName);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb= new StringBuilder();
            String s;
            while ((s = br.readLine()) != null) {
                sb.append(s);
            }
            br.close();
            return convertFactory.fromJson(sb.toString(), new TypeToken<ArrayList<String>>() {}.getType());
        } catch (Exception ignore) {
            return null;
        }
    }

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

    public JsonArray build() {
        return array;
    }

}