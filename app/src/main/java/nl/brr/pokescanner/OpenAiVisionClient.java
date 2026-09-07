package nl.brr.pokescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class OpenAiVisionClient {
    private static final String ENDPOINT = "https://api.openai.com/v1/responses";
    private static final String MODEL = "gpt-5.6-sol";
    private static final int CONNECT_TIMEOUT_MS = 20_000;
    private static final int READ_TIMEOUT_MS = 90_000;
    private static final int MAX_ORIGINAL_BYTES = 14 * 1024 * 1024;

    private OpenAiVisionClient() { }

    public interface Callback {
        void onSuccess(AiCardResult result);
        void onError(String message);
    }

    public static void analyze(Context context, Uri imageUri, String apiKey, Callback callback) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                ImagePayload image = readImage(app, imageUri);
                JSONObject request = buildRequest(image);
                String response = post(request, apiKey);
                String structuredJson = extractOutputText(response);
                AiCardResult result = AiCardResult.fromJson(structuredJson);
                main(() -> callback.onSuccess(result));
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = e.getClass().getSimpleName();
                String finalMessage = message;
                main(() -> callback.onError(finalMessage));
            }
        }, "PokemonVision").start();
    }

    private static JSONObject buildRequest(ImagePayload image) throws Exception {
        String prompt = """
                Identify the exact Pokemon Trading Card Game card in this photo. Use the whole visual layout, not only OCR text.
                The photo can show a RAW card or a graded slab. Return the underlying card identity and, when graded, the slab company and final grade.

                Important rules:
                - The Pokemon/card name is the actual printed card name, not BASIC, an Ability, an attack, set name, rarity text, MINT, GEM MINT, or grading metadata.
                - Read collector number and set code carefully. On promo cards, a bottom line such as 'MEP EN 086' means set code MEP, language English, collector number 086; the Cardmarket search query should be like 'Slowpoke MEP 086'.
                - For Beckett slabs, BGS/Beckett are the same grader. Read the final large grade; subgrades are supporting evidence only.
                - For PSA/BGS/CGC/TAG/ACE/PCA/SGC slabs, separate grader and numerical grade from the underlying card.
                - Preserve leading zeroes in collector numbers when printed, e.g. 086.
                - If a set code is confidently visible, include it in search_query. Otherwise use card name plus collector number.
                - Do not invent a set, code, grade, or language when uncertain. Use an empty string for unknown text fields.
                - search_query must be concise and optimized for Cardmarket, with no grading terms.
                - confidence is your confidence in the exact underlying card identity, from 0 to 1.
                """;

        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject props = new JSONObject();
        props.put("card_name", stringSchema());
        props.put("set_name", stringSchema());
        props.put("set_code", stringSchema());
        props.put("collector_number", stringSchema());
        props.put("language", stringSchema());
        props.put("is_graded", new JSONObject().put("type", "boolean"));
        props.put("grader", stringSchema());
        props.put("grade", stringSchema());
        props.put("grade_label", stringSchema());
        props.put("year", stringSchema());
        props.put("variant", stringSchema());
        props.put("confidence", new JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1));
        props.put("search_query", stringSchema());
        props.put("notes", stringSchema());
        schema.put("properties", props);
        schema.put("required", new JSONArray()
                .put("card_name").put("set_name").put("set_code").put("collector_number")
                .put("language").put("is_graded").put("grader").put("grade")
                .put("grade_label").put("year").put("variant").put("confidence")
                .put("search_query").put("notes"));
        schema.put("additionalProperties", false);

        JSONObject format = new JSONObject()
                .put("type", "json_schema")
                .put("name", "pokemon_card_identity")
                .put("strict", true)
                .put("schema", schema);

        JSONObject text = new JSONObject().put("format", format).put("verbosity", "low");

        JSONArray content = new JSONArray()
                .put(new JSONObject().put("type", "input_text").put("text", prompt))
                .put(new JSONObject()
                        .put("type", "input_image")
                        .put("image_url", "data:" + image.mimeType + ";base64," + image.base64)
                        .put("detail", "high"));

        JSONArray input = new JSONArray().put(new JSONObject()
                .put("role", "user")
                .put("content", content));

        return new JSONObject()
                .put("model", MODEL)
                .put("store", false)
                .put("input", input)
                .put("text", text)
                .put("max_output_tokens", 700);
    }

    private static JSONObject stringSchema() throws Exception {
        return new JSONObject().put("type", "string");
    }

    private static String post(JSONObject request, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) throw new Exception("OpenAI API-key ontbreekt");
        HttpURLConnection connection = (HttpURLConnection) new URL(ENDPOINT).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        byte[] body = request.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        connection.getOutputStream().write(body);

        int code = connection.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? connection.getInputStream() : connection.getErrorStream();
        String response = readUtf8(stream);
        connection.disconnect();

        if (code < 200 || code >= 300) {
            String detail = extractApiError(response);
            if (code == 401) throw new Exception("OpenAI API-key ongeldig of niet geautoriseerd");
            if (code == 429) throw new Exception("OpenAI limiet/budget bereikt. " + detail);
            throw new Exception("OpenAI fout " + code + (detail.isEmpty() ? "" : ": " + detail));
        }
        return response;
    }

    private static String extractOutputText(String responseJson) throws Exception {
        JSONObject response = new JSONObject(responseJson);
        JSONArray output = response.optJSONArray("output");
        if (output != null) {
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) continue;
                JSONArray content = item.optJSONArray("content");
                if (content == null) continue;
                for (int j = 0; j < content.length(); j++) {
                    JSONObject c = content.optJSONObject(j);
                    if (c == null) continue;
                    if ("output_text".equals(c.optString("type")) && !c.optString("text").isEmpty()) {
                        return c.optString("text");
                    }
                }
            }
        }
        JSONObject err = response.optJSONObject("error");
        if (err != null) throw new Exception(err.optString("message", "OpenAI gaf geen resultaat"));
        throw new Exception("OpenAI gaf geen kaartresultaat terug");
    }

    private static String extractApiError(String response) {
        try {
            JSONObject root = new JSONObject(response);
            JSONObject err = root.optJSONObject("error");
            if (err != null) return err.optString("message", "");
        } catch (Exception ignored) { }
        return "";
    }

    private static ImagePayload readImage(Context context, Uri uri) throws Exception {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || !mime.startsWith("image/")) mime = "image/jpeg";

        byte[] original;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("Foto kon niet worden geopend");
            original = readAllBytes(in);
        }

        if (original.length <= MAX_ORIGINAL_BYTES) {
            return new ImagePayload(mime, Base64.encodeToString(original, Base64.NO_WRAP));
        }

        Bitmap bitmap = BitmapFactory.decodeByteArray(original, 0, original.length);
        if (bitmap == null) throw new Exception("Foto is te groot en kon niet worden verkleind");
        int max = 2200;
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Bitmap scaled = bitmap;
        if (Math.max(w, h) > max) {
            float ratio = max / (float) Math.max(w, h);
            scaled = Bitmap.createScaledBitmap(bitmap, Math.round(w * ratio), Math.round(h * ratio), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out);
        if (scaled != bitmap) scaled.recycle();
        bitmap.recycle();
        return new ImagePayload("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP));
    }

    private static byte[] readAllBytes(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }

    private static String readUtf8(InputStream in) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void main(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }

    private static final class ImagePayload {
        final String mimeType;
        final String base64;
        ImagePayload(String mimeType, String base64) {
            this.mimeType = mimeType;
            this.base64 = base64;
        }
    }
}
