package com.termux.app.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public final class GeminiApi {

    public static final class Result {
        public final String text;
        public final JSONObject functionCall;

        public Result(String text, JSONObject functionCall) {
            this.text = text == null ? "" : text;
            this.functionCall = functionCall;
        }
    }

    public static Result generateContent(String apiKey, String model, String userText, JSONArray tools) throws Exception {
        JSONArray contents = new JSONArray();
        JSONObject c0 = new JSONObject();
        c0.put("role", "user");
        JSONArray parts = new JSONArray();
        JSONObject p0 = new JSONObject();
        p0.put("text", userText == null ? "" : userText);
        parts.put(p0);
        c0.put("parts", parts);
        contents.put(c0);
        return generateContentWithContents(apiKey, model, contents, tools);
    }

    public static Result streamGenerateContentSse(String apiKey, String model, String userText, JSONArray tools, Consumer<String> onDelta) throws Exception {
        JSONArray contents = new JSONArray();
        JSONObject c0 = new JSONObject();
        c0.put("role", "user");
        JSONArray parts = new JSONArray();
        JSONObject p0 = new JSONObject();
        p0.put("text", userText == null ? "" : userText);
        parts.put(p0);
        c0.put("parts", parts);
        contents.put(c0);
        return streamGenerateContentSseWithContents(apiKey, model, contents, tools, onDelta);
    }

    public static Result generateContentWithContents(String apiKey, String model, JSONArray contents, JSONArray tools) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        JSONObject req = buildRequestFromContents(contents, tools);
        JSONObject resp = postJson(endpoint, apiKey, req);

        JSONObject extracted = extractTextAndFunctionCall(resp);
        String text = extracted.optString("text", "");
        JSONObject fc = extracted.optJSONObject("functionCall");
        return new Result(text, fc);
    }

    public static Result streamGenerateContentSseWithContents(String apiKey, String model, JSONArray contents, JSONArray tools, Consumer<String> onDelta) throws Exception {
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse";
        JSONObject req = buildRequestFromContents(contents, tools);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(0);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("x-goog-api-key", apiKey);

        byte[] body = req.toString().getBytes("UTF-8");
        conn.getOutputStream().write(body);
        conn.getOutputStream().flush();
        conn.getOutputStream().close();

        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));

        StringBuilder full = new StringBuilder();
        JSONObject lastFunctionCall = null;

        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith("data:")) continue;

            String payload = line.substring(5).trim();
            if (payload.isEmpty()) continue;

            JSONObject chunk;
            try {
                chunk = new JSONObject(payload);
            } catch (Exception e) {
                continue;
            }

            JSONObject extracted = extractTextAndFunctionCall(chunk);
            String delta = extracted.optString("text", "");
            JSONObject fc = extracted.optJSONObject("functionCall");

            if (fc != null) lastFunctionCall = fc;

            if (!delta.isEmpty()) {
                full.append(delta);
                if (onDelta != null) onDelta.accept(delta);
            }
        }

        br.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new Exception("Gemini HTTP " + code);
        }

        return new Result(full.toString().trim(), lastFunctionCall);
    }

    public static JSONObject buildRequestFromContents(JSONArray contents, JSONArray tools) throws Exception {
        JSONObject req = new JSONObject();
        req.put("contents", contents == null ? new JSONArray() : contents);
        if (tools != null) req.put("tools", tools);
        return req;
    }

    public static JSONObject buildModelFunctionCallPart(JSONObject functionCall) throws Exception {
        JSONObject part = new JSONObject();
        part.put("functionCall", functionCall);
        return part;
    }

    public static JSONObject buildUserFunctionResponsePart(String name, JSONObject response) throws Exception {
        JSONObject fr = new JSONObject();
        fr.put("name", name);
        fr.put("response", response == null ? new JSONObject() : response);
        JSONObject part = new JSONObject();
        part.put("functionResponse", fr);
        return part;
    }

    private static JSONObject postJson(String endpoint, String apiKey, JSONObject req) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("x-goog-api-key", apiKey);

        byte[] body = req.toString().getBytes("UTF-8");
        conn.getOutputStream().write(body);
        conn.getOutputStream().flush();
        conn.getOutputStream().close();

        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        if (code < 200 || code >= 300) {
            throw new Exception("Gemini HTTP " + code + ": " + sb);
        }

        conn.disconnect();
        return new JSONObject(sb.toString());
    }

    private static JSONObject extractTextAndFunctionCall(JSONObject resp) {
        JSONObject out = new JSONObject();
        try {
            JSONArray candidates = resp.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return out;

            JSONObject cand0 = candidates.optJSONObject(0);
            if (cand0 == null) return out;

            JSONObject content = cand0.optJSONObject("content");
            if (content == null) return out;

            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) return out;

            StringBuilder text = new StringBuilder();
            JSONObject functionCall = null;

            for (int i = 0; i < parts.length(); i++) {
                JSONObject part = parts.optJSONObject(i);
                if (part == null) continue;

                String t = part.optString("text", "");
                if (t != null && !t.isEmpty()) text.append(t);

                JSONObject fc = part.optJSONObject("functionCall");
                if (fc == null) fc = part.optJSONObject("function_call");
                if (fc != null) functionCall = fc;
            }

            if (text.length() > 0) out.put("text", text.toString());
            if (functionCall != null) out.put("functionCall", functionCall);
        } catch (Exception ignored) {
        }
        return out;
    }
}
