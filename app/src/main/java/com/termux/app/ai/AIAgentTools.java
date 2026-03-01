package com.termux.app.ai;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class AIAgentTools {

    public static final class ToolResult {
        public final String name;
        public final JSONObject response;

        public ToolResult(String name, JSONObject response) {
            this.name = name;
            this.response = response == null ? new JSONObject() : response;
        }
    }

    public static JSONArray buildToolSchemas() throws Exception {
        JSONArray tools = new JSONArray();

        JSONObject fn1 = new JSONObject();
        fn1.put("name", "list_dir");
        fn1.put("description", "List files in a directory. Supports Termux-home paths and SAF tree URIs.");
        JSONObject p1 = new JSONObject();
        p1.put("type", "object");
        JSONObject props1 = new JSONObject();
        props1.put("path", new JSONObject().put("type", "string").put("description", "Path like /data/data/com.termux/files/home/... or a tree URI string"));
        props1.put("maxItems", new JSONObject().put("type", "integer").put("description", "Maximum entries").put("default", 200));
        p1.put("properties", props1);
        p1.put("required", new JSONArray().put("path"));
        fn1.put("parameters", p1);

        JSONObject fn2 = new JSONObject();
        fn2.put("name", "read_file");
        fn2.put("description", "Read a text file. Supports Termux-home paths and SAF document URIs.");
        JSONObject p2 = new JSONObject();
        p2.put("type", "object");
        JSONObject props2 = new JSONObject();
        props2.put("path", new JSONObject().put("type", "string").put("description", "File path or document URI"));
        props2.put("maxBytes", new JSONObject().put("type", "integer").put("description", "Max bytes to read").put("default", 200000));
        p2.put("properties", props2);
        p2.put("required", new JSONArray().put("path"));
        fn2.put("parameters", p2);

        JSONObject fn3 = new JSONObject();
        fn3.put("name", "write_file");
        fn3.put("description", "Write a text file. Termux-home paths only unless a SAF document URI is provided.");
        JSONObject p3 = new JSONObject();
        p3.put("type", "object");
        JSONObject props3 = new JSONObject();
        props3.put("path", new JSONObject().put("type", "string").put("description", "File path or document URI"));
        props3.put("content", new JSONObject().put("type", "string").put("description", "New file contents"));
        p3.put("properties", props3);
        p3.put("required", new JSONArray().put("path").put("content"));
        fn3.put("parameters", p3);

        JSONObject fn4 = new JSONObject();
        fn4.put("name", "set_saf_root");
        fn4.put("description", "Set the SAF root tree URI that tools may access for listing/reading/writing.");
        JSONObject p4 = new JSONObject();
        p4.put("type", "object");
        JSONObject props4 = new JSONObject();
        props4.put("treeUri", new JSONObject().put("type", "string").put("description", "Tree URI from ACTION_OPEN_DOCUMENT_TREE"));
        p4.put("properties", props4);
        p4.put("required", new JSONArray().put("treeUri"));
        fn4.put("parameters", p4);

        JSONArray functions = new JSONArray();
        functions.put(fn1);
        functions.put(fn2);
        functions.put(fn3);
        functions.put(fn4);

        JSONObject tool0 = new JSONObject();
        tool0.put("functionDeclarations", functions);

        tools.put(tool0);
        return tools;
    }

    public static ToolResult dispatch(Context ctx, String prefName, JSONObject functionCall) throws Exception {
        String name = functionCall.optString("name", "");
        JSONObject args = functionCall.optJSONObject("args");
        if (args == null) args = functionCall.optJSONObject("arguments");
        if (args == null) args = new JSONObject();

        if ("set_saf_root".equals(name)) {
            String treeUri = args.optString("treeUri", "");
            JSONObject out = new JSONObject();
            if (treeUri == null || treeUri.trim().isEmpty()) {
                out.put("ok", false);
                out.put("error", "treeUri is required");
                return new ToolResult(name, out);
            }
            ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().putString("saf_root_tree_uri", treeUri).apply();
            out.put("ok", true);
            out.put("treeUri", treeUri);
            return new ToolResult(name, out);
        }

        if ("list_dir".equals(name)) {
            String path = args.optString("path", "");
            int maxItems = args.optInt("maxItems", 200);
            JSONObject out = listDir(ctx, prefName, path, maxItems);
            return new ToolResult(name, out);
        }

        if ("read_file".equals(name)) {
            String path = args.optString("path", "");
            int maxBytes = args.optInt("maxBytes", 200000);
            JSONObject out = readFile(ctx, prefName, path, maxBytes);
            return new ToolResult(name, out);
        }

        if ("write_file".equals(name)) {
            String path = args.optString("path", "");
            String content = args.optString("content", "");
            JSONObject out = writeFile(ctx, prefName, path, content);
            return new ToolResult(name, out);
        }

        JSONObject out = new JSONObject();
        out.put("ok", false);
        out.put("error", "Unknown tool: " + name);
        return new ToolResult(name, out);
    }

    private static boolean isTermuxHomePath(String p) {
        if (p == null) return false;
        return p.startsWith("/data/data/com.termux/files/home") || p.startsWith("/data/data/com.termux/files/usr");
    }

    private static JSONObject listDir(Context ctx, String prefName, String pathOrUri, int maxItems) throws Exception {
        JSONObject out = new JSONObject();
        if (pathOrUri == null || pathOrUri.trim().isEmpty()) {
            out.put("ok", false);
            out.put("error", "path required");
            return out;
        }

        if (isTermuxHomePath(pathOrUri)) {
            File dir = new File(pathOrUri);
            if (!dir.exists() || !dir.isDirectory()) {
                out.put("ok", false);
                out.put("error", "Not a directory: " + pathOrUri);
                return out;
            }
            File[] files = dir.listFiles();
            JSONArray arr = new JSONArray();
            if (files != null) {
                int c = 0;
                for (File f : files) {
                    if (c >= maxItems) break;
                    JSONObject e = new JSONObject();
                    e.put("name", f.getName());
                    e.put("path", f.getAbsolutePath());
                    e.put("dir", f.isDirectory());
                    e.put("sizeBytes", f.isFile() ? f.length() : 0);
                    e.put("modifiedMs", f.lastModified());
                    arr.put(e);
                    c++;
                }
            }
            out.put("ok", true);
            out.put("entries", arr);
            return out;
        }

        Uri uri = Uri.parse(pathOrUri);
        DocumentFile df = DocumentFile.fromTreeUri(ctx, uri);
        if (df == null || !df.isDirectory()) {
            String rootTree = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE).getString("saf_root_tree_uri", "");
            if (rootTree != null && !rootTree.isEmpty()) {
                DocumentFile root = DocumentFile.fromTreeUri(ctx, Uri.parse(rootTree));
                if (root != null && root.isDirectory()) {
                    df = findChildDirByPath(root, pathOrUri);
                }
            }
        }

        if (df == null || !df.isDirectory()) {
            out.put("ok", false);
            out.put("error", "SAF directory not accessible. Provide a tree URI via set_saf_root or pass a tree URI directly.");
            return out;
        }

        JSONArray arr = new JSONArray();
        int c = 0;
        for (DocumentFile child : df.listFiles()) {
            if (c >= maxItems) break;
            JSONObject e = new JSONObject();
            e.put("name", child.getName());
            e.put("uri", child.getUri().toString());
            e.put("dir", child.isDirectory());
            e.put("sizeBytes", child.isFile() ? child.length() : 0);
            e.put("modifiedMs", child.lastModified());
            arr.put(e);
            c++;
        }
        out.put("ok", true);
        out.put("entries", arr);
        return out;
    }

    private static JSONObject readFile(Context ctx, String prefName, String pathOrUri, int maxBytes) throws Exception {
        JSONObject out = new JSONObject();
        if (pathOrUri == null || pathOrUri.trim().isEmpty()) {
            out.put("ok", false);
            out.put("error", "path required");
            return out;
        }

        if (isTermuxHomePath(pathOrUri)) {
            File f = new File(pathOrUri);
            if (!f.exists() || !f.isFile()) {
                out.put("ok", false);
                out.put("error", "Not a file: " + pathOrUri);
                return out;
            }
            byte[] data = readBytesLimited(new FileInputStream(f), maxBytes);
            out.put("ok", true);
            out.put("text", new String(data, StandardCharsets.UTF_8));
            return out;
        }

        Uri uri = Uri.parse(pathOrUri);
        ContentResolver cr = ctx.getContentResolver();
        InputStream in = cr.openInputStream(uri);
        if (in == null) {
            out.put("ok", false);
            out.put("error", "Could not open URI: " + pathOrUri);
            return out;
        }
        byte[] data = readBytesLimited(in, maxBytes);
        out.put("ok", true);
        out.put("text", new String(data, StandardCharsets.UTF_8));
        JSONObject meta = new JSONObject();
        meta.put("uri", uri.toString());
        fillMeta(cr, uri, meta);
        out.put("meta", meta);
        return out;
    }

    private static JSONObject writeFile(Context ctx, String prefName, String pathOrUri, String content) throws Exception {
        JSONObject out = new JSONObject();
        if (pathOrUri == null || pathOrUri.trim().isEmpty()) {
            out.put("ok", false);
            out.put("error", "path required");
            return out;
        }

        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);

        if (isTermuxHomePath(pathOrUri)) {
            File f = new File(pathOrUri);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            FileOutputStream fos = new FileOutputStream(f, false);
            fos.write(bytes);
            fos.flush();
            fos.close();
            out.put("ok", true);
            out.put("path", f.getAbsolutePath());
            out.put("bytes", bytes.length);
            return out;
        }

        Uri uri = Uri.parse(pathOrUri);
        ContentResolver cr = ctx.getContentResolver();
        try {
            java.io.OutputStream os = cr.openOutputStream(uri, "wt");
            if (os == null) {
                out.put("ok", false);
                out.put("error", "Could not open URI for writing: " + pathOrUri);
                return out;
            }
            os.write(bytes);
            os.flush();
            os.close();
            out.put("ok", true);
            out.put("uri", uri.toString());
            out.put("bytes", bytes.length);
            return out;
        } catch (Exception e) {
            out.put("ok", false);
            out.put("error", "Write failed: " + e.getMessage());
            return out;
        }
    }

    private static byte[] readBytesLimited(InputStream in, int maxBytes) throws Exception {
        BufferedInputStream bin = new BufferedInputStream(in);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int r;
        while ((r = bin.read(buf)) != -1) {
            int take = r;
            if (total + take > maxBytes) take = maxBytes - total;
            if (take > 0) bout.write(buf, 0, take);
            total += take;
            if (total >= maxBytes) break;
        }
        bin.close();
        return bout.toByteArray();
    }

    private static void fillMeta(ContentResolver cr, Uri uri, JSONObject meta) {
        try {
            Cursor c = cr.query(uri, null, null, null, null);
            if (c != null) {
                int nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIdx = c.getColumnIndex(OpenableColumns.SIZE);
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) meta.put("displayName", c.getString(nameIdx));
                    if (sizeIdx >= 0) meta.put("sizeBytes", c.getLong(sizeIdx));
                }
                c.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static DocumentFile findChildDirByPath(DocumentFile root, String path) {
        if (root == null || !root.isDirectory()) return null;
        String p = path;
        if (p.startsWith("content://")) return null;
        if (p.startsWith("/")) p = p.substring(1);
        if (p.isEmpty()) return root;
        String[] parts = p.split("/");
        DocumentFile cur = root;
        for (String part : parts) {
            if (part == null || part.isEmpty()) continue;
            DocumentFile next = null;
            for (DocumentFile c : cur.listFiles()) {
                if (c.isDirectory() && part.equals(c.getName())) {
                    next = c;
                    break;
                }
            }
            if (next == null) return null;
            cur = next;
        }
        return cur;
    }
}
