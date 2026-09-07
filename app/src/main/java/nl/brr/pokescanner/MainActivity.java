package nl.brr.pokescanner;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final String CARDMARKET_HOME = "https://www.cardmarket.com/en/Pokemon";

    private ImageView imagePreview;
    private EditText editSearch;
    private TextView txtStatus, txtSlab, txtResult, txtWebStatus;
    private ScrollView mainScroll;
    private LinearLayout webContainer;
    private WebView webView;
    private Uri pendingCameraUri;
    private CardOcrParser.Result lastOcr;
    private String pendingSearch = null;
    private boolean submittedSearch = false;
    private boolean autoOpenedProduct = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) showImageAndOcr(uri);
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && pendingCameraUri != null) showImageAndOcr(pendingCameraUri);
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imagePreview = findViewById(R.id.imagePreview);
        editSearch = findViewById(R.id.editSearch);
        txtStatus = findViewById(R.id.txtStatus);
        txtSlab = findViewById(R.id.txtSlab);
        txtResult = findViewById(R.id.txtResult);
        txtWebStatus = findViewById(R.id.txtWebStatus);
        mainScroll = findViewById(R.id.mainScroll);
        webContainer = findViewById(R.id.webContainer);
        webView = findViewById(R.id.webView);

        setupWebView();

        findViewById(R.id.btnCamera).setOnClickListener(v -> openCamera());
        findViewById(R.id.btnGallery).setOnClickListener(v -> galleryLauncher.launch("image/*"));
        findViewById(R.id.btnCardmarket).setOnClickListener(v -> openCardmarket());
        findViewById(R.id.btnSearch).setOnClickListener(v -> startCardmarketSearch());
        findViewById(R.id.btnReadPage).setOnClickListener(v -> {
            if (!isCardmarketUrl(webView.getUrl())) {
                Toast.makeText(this, "Open eerst Cardmarket.", Toast.LENGTH_SHORT).show();
            } else {
                readCurrentCardmarketPage();
            }
        });
        findViewById(R.id.btnBackToScanner).setOnClickListener(v -> showScanner());
        findViewById(R.id.btnWebBack).setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        findViewById(R.id.btnWebForward).setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
    }

    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "images");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Kan afbeeldingsmap niet maken");
            File file = File.createTempFile("pokemon_", ".jpg", dir);
            pendingCameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            cameraLauncher.launch(pendingCameraUri);
        } catch (Exception e) {
            Toast.makeText(this, "Camera kon niet worden geopend: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showImageAndOcr(Uri uri) {
        imagePreview.setImageURI(uri);
        txtStatus.setText("Tekst op kaart/slab herkennen…");
        try {
            InputImage image = InputImage.fromFilePath(this, uri);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        lastOcr = CardOcrParser.parse(result.getText());
                        editSearch.setText(lastOcr.query);
                        if (!lastOcr.grader.isEmpty()) {
                            String slab = lastOcr.grader + (lastOcr.grade.isEmpty() ? "" : " " + lastOcr.grade);
                            txtSlab.setText("Slab: " + slab);
                        } else {
                            txtSlab.setText("Slab: raw / geen grader herkend");
                        }
                        if (lastOcr.query.isEmpty()) {
                            txtStatus.setText("OCR voltooid, maar kaartnaam/nummer niet zeker. Vul de zoekterm handmatig aan.");
                        } else {
                            txtStatus.setText("Herkenning klaar. Controleer de zoekterm en tik op ‘Zoek op Cardmarket’.");
                        }
                    })
                    .addOnFailureListener(e -> txtStatus.setText("OCR mislukt: " + e.getMessage()))
                    .addOnCompleteListener(task -> recognizer.close());
        } catch (Exception e) {
            txtStatus.setText("Afbeelding kon niet worden gelezen: " + e.getMessage());
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(s.getUserAgentString() + " PokemonCardScanner/0.1");
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new PageBridge(), "PokeScanner");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && ("http".equals(u.getScheme()) || "https".equals(u.getScheme()))) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                txtWebStatus.setText(url == null ? "Cardmarket" : url.replace("https://www.cardmarket.com", "Cardmarket"));
                if (pendingSearch != null && !submittedSearch && isCardmarketUrl(url)) {
                    handler.postDelayed(() -> injectSearch(pendingSearch), 450);
                } else if (submittedSearch && !autoOpenedProduct && isCardmarketUrl(url)) {
                    handler.postDelayed(MainActivity.this::tryOpenBestSearchResult, 700);
                }
                if (looksLikeProductPage(url)) handler.postDelayed(MainActivity.this::readCurrentCardmarketPage, 900);
            }
        });
    }

    private void openCardmarket() {
        showWeb();
        if (!isCardmarketUrl(webView.getUrl())) webView.loadUrl(CARDMARKET_HOME);
    }

    private void startCardmarketSearch() {
        String q = editSearch.getText().toString().trim();
        if (q.isEmpty()) {
            Toast.makeText(this, "Vul eerst een kaartnaam of collector number in.", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingSearch = q;
        submittedSearch = false;
        autoOpenedProduct = false;
        showWeb();
        if (!isCardmarketUrl(webView.getUrl())) webView.loadUrl(CARDMARKET_HOME); else injectSearch(q);
    }

    private void injectSearch(String query) {
        String safe = JSONObject.quote(query);
        String js = "(function(){" +
                "const q=" + safe + ";" +
                "const inputs=[...document.querySelectorAll('input')];" +
                "let i=inputs.find(x=>/search cardmarket/i.test(x.placeholder||''));" +
                "if(!i) i=inputs.find(x=>/search/i.test(x.name||'')||/search/i.test(x.getAttribute('aria-label')||''));" +
                "if(!i){PokeScanner.onMessage('SEARCH_INPUT_NOT_FOUND');return;}" +
                "i.focus(); i.value=q; i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new Event('change',{bubbles:true}));" +
                "const f=i.closest('form'); if(f){f.submit();PokeScanner.onMessage('SEARCH_SUBMITTED');return;}" +
                "i.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));" +
                "PokeScanner.onMessage('SEARCH_SUBMITTED');" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void tryOpenBestSearchResult() {
        String q = pendingSearch == null ? "" : pendingSearch;
        String safe = JSONObject.quote(q.toLowerCase(Locale.ROOT));
        String js = "(function(){" +
                "const q=" + safe + "; const parts=q.split(/\\s+/).filter(x=>x.length>1);" +
                "const links=[...document.querySelectorAll('a[href*=\"/Products/Singles/\"]')];" +
                "let scored=links.map(a=>{const t=(a.innerText||a.textContent||'').toLowerCase();let s=0;parts.forEach(p=>{if(t.includes(p))s+=p.match(/\\d/) ? 4:1;});return {a,s,t};}).sort((x,y)=>y.s-x.s);" +
                "if(scored.length&&scored[0].s>=2){scored[0].a.click();PokeScanner.onMessage('BEST_RESULT_OPENED');}" +
                "else PokeScanner.onMessage('NO_SAFE_AUTO_MATCH');" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void readCurrentCardmarketPage() {
        String js = "(function(){" +
                "const lines=(document.body.innerText||'').split(/\\n+/).map(x=>x.trim()).filter(Boolean);" +
                "const norm=s=>s.toLowerCase().replace(/[^a-z0-9]/g,'');" +
                "function after(label){const n=norm(label);for(let i=0;i<lines.length;i++){const z=norm(lines[i]);if(z===n&&i+1<lines.length)return lines[i+1];if(z.startsWith(n)&&lines[i].length>label.length)return lines[i].slice(label.length).trim();}return ''; }" +
                "const stats={available:after('Available items'),from:after('From'),trend:after('Price Trend'),avg30:after('30-days average'),avg7:after('7-days average'),avg1:after('1-day average')};" +
                "const contexts=[];const rx=/\\b(PSA|BGS|CGC|TAG|ACE|PCA|SGC)\\s*(?:GRADE\\s*)?[-:]?\\s*(10(?:\\.0)?|9\\.5|9|8\\.5|8|7\\.5|7|6\\.5|6|5\\.5|5|4|3|2|1)\\b/i;" +
                "for(let i=0;i<lines.length;i++){if(rx.test(lines[i])){contexts.push(lines.slice(Math.max(0,i-3),Math.min(lines.length,i+5)).join(' | '));if(contexts.length>=40)break;}}" +
                "const o={title:(document.querySelector('h1')||{}).innerText||document.title,url:location.href,stats:stats,slabs:contexts};" +
                "PokeScanner.onPageData(JSON.stringify(o));" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    private void renderPageData(String json) {
        try {
            JSONObject o = new JSONObject(json);
            JSONObject stats = o.optJSONObject("stats");
            StringBuilder b = new StringBuilder();
            b.append(o.optString("title", "Cardmarket-product")).append("\n\n");
            if (stats != null) {
                appendIf(b, "Vanaf", stats.optString("from"));
                appendIf(b, "Price Trend", stats.optString("trend"));
                appendIf(b, "30 dagen", stats.optString("avg30"));
                appendIf(b, "7 dagen", stats.optString("avg7"));
                appendIf(b, "1 dag", stats.optString("avg1"));
                appendIf(b, "Aanbod", stats.optString("available"));
            }

            JSONArray slabs = o.optJSONArray("slabs");
            String wantedGrader = lastOcr == null ? "" : lastOcr.grader;
            String wantedGrade = lastOcr == null ? "" : lastOcr.grade;
            List<String> matching = new ArrayList<>();
            List<Double> prices = new ArrayList<>();
            if (slabs != null) {
                for (int i = 0; i < slabs.length(); i++) {
                    String s = slabs.optString(i);
                    boolean ok = wantedGrader.isEmpty() || s.toUpperCase(Locale.ROOT).contains(wantedGrader);
                    if (ok && !wantedGrade.isEmpty()) ok = containsExactGrade(s, wantedGrader, wantedGrade);
                    if (ok) {
                        matching.add(s);
                        Double p = extractEuroPrice(s);
                        if (p != null) prices.add(p);
                    }
                }
            }

            if (!wantedGrader.isEmpty()) {
                b.append("\n").append(wantedGrader);
                if (!wantedGrade.isEmpty()) b.append(" ").append(wantedGrade);
                b.append(" in opmerkingen: ").append(matching.size()).append(" match(es)\n");
                if (!prices.isEmpty()) {
                    double min = Collections.min(prices);
                    b.append("Laagste gevonden prijs in matching context: ")
                            .append(NumberFormat.getCurrencyInstance(Locale.GERMANY).format(min)).append("\n");
                }
                int n = Math.min(8, matching.size());
                for (int i = 0; i < n; i++) b.append("\n• ").append(matching.get(i));
            } else if (slabs != null && slabs.length() > 0) {
                b.append("\n\nGraded opmerkingen gevonden: ").append(slabs.length());
                for (int i = 0; i < Math.min(5, slabs.length()); i++) b.append("\n• ").append(slabs.optString(i));
            }

            b.append("\n\n").append(o.optString("url"));
            txtResult.setText(b.toString());
            txtStatus.setText("Cardmarket-pagina uitgelezen met jouw huidige WebView-sessie.");
            showScanner();
        } catch (Exception e) {
            txtResult.setText("Kon Cardmarket-data niet verwerken: " + e.getMessage());
        }
    }

    private static boolean containsExactGrade(String text, String grader, String grade) {
        String g = grader == null ? "" : grader.trim();
        String gr = grade == null ? "" : grade.trim();
        if (g.isEmpty() || gr.isEmpty()) return true;
        String upper = text.toUpperCase(Locale.ROOT).replace('-', ' ');
        return upper.matches(".*\\b" + java.util.regex.Pattern.quote(g.toUpperCase(Locale.ROOT)) +
                "\\s*(?:GRADE\\s*)?" + java.util.regex.Pattern.quote(gr) + "\\b.*");
    }

    private static Double extractEuroPrice(String context) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:€|EUR)\\s*([0-9]{1,6}(?:[.,][0-9]{2})?)|([0-9]{1,6}(?:[.,][0-9]{2})?)\\s*€", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(context);
        List<Double> candidates = new ArrayList<>();
        while (m.find()) {
            String raw = m.group(1) != null ? m.group(1) : m.group(2);
            try {
                String cleaned = raw.trim();
                if (cleaned.contains(",") && cleaned.contains(".")) cleaned = cleaned.replace(".", "").replace(',', '.');
                else if (cleaned.contains(",")) cleaned = cleaned.replace(',', '.');
                candidates.add(Double.parseDouble(cleaned));
            } catch (Exception ignored) { }
        }
        return candidates.isEmpty() ? null : Collections.min(candidates);
    }

    private static void appendIf(StringBuilder b, String label, String value) {
        if (value != null && !value.trim().isEmpty()) b.append(label).append(": ").append(value.trim()).append("\n");
    }

    private boolean isCardmarketUrl(String url) {
        return url != null && url.startsWith("https://www.cardmarket.com/");
    }

    private boolean looksLikeProductPage(String url) {
        if (!isCardmarketUrl(url) || !url.contains("/Pokemon/Products/Singles/")) return false;
        String tail = url.substring(url.indexOf("/Pokemon/Products/Singles/") + "/Pokemon/Products/Singles/".length());
        return !tail.isEmpty() && !tail.startsWith("?");
    }

    private void showWeb() {
        mainScroll.setVisibility(View.GONE);
        webContainer.setVisibility(View.VISIBLE);
    }

    private void showScanner() {
        webContainer.setVisibility(View.GONE);
        mainScroll.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (webContainer.getVisibility() == View.VISIBLE) {
            if (webView.canGoBack()) webView.goBack(); else showScanner();
        } else {
            super.onBackPressed();
        }
    }

    private final class PageBridge {
        @JavascriptInterface
        public void onMessage(String message) {
            runOnUiThread(() -> {
                if ("SEARCH_SUBMITTED".equals(message)) submittedSearch = true;
                if ("BEST_RESULT_OPENED".equals(message)) autoOpenedProduct = true;
                if ("SEARCH_INPUT_NOT_FOUND".equals(message)) {
                    txtWebStatus.setText("Zoekveld niet gevonden — zoek handmatig op Cardmarket");
                    Toast.makeText(MainActivity.this, "Cardmarket-layout gewijzigd of zoekveld niet gevonden. Zoek handmatig en gebruik daarna ‘Lees huidige pagina’.", Toast.LENGTH_LONG).show();
                }
                if ("NO_SAFE_AUTO_MATCH".equals(message)) txtWebStatus.setText("Kies de juiste kaart uit de zoekresultaten");
            });
        }

        @JavascriptInterface
        public void onPageData(String json) {
            runOnUiThread(() -> renderPageData(json));
        }
    }
}
