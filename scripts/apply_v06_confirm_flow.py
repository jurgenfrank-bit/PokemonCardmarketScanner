from pathlib import Path
import re

main_path = Path('app/src/main/java/nl/brr/pokescanner/MainActivity.java')
text = main_path.read_text(encoding='utf-8')

old_auto = '''                if (looksLikeProductPage(url)) {
                    handler.postDelayed(MainActivity.this::readCurrentCardmarketPage, 1000);
                }
'''
new_auto = '''                if (looksLikeProductPage(url)) {
                    txtWebStatus.setText("Controleer kaart / log in → tik daarna op ✓ Juiste kaart");
                    handler.postDelayed(MainActivity.this::injectConfirmButton, 300);
                }
'''
if old_auto not in text:
    raise SystemExit('Expected auto-read block not found')
text = text.replace(old_auto, new_auto, 1)

marker = '''    private void readCurrentCardmarketPage() {
'''
confirm_method = '''    private void injectConfirmButton() {
        if (!looksLikeProductPage(webView.getUrl())) return;
        String js = "(function(){" +
                "const old=document.getElementById('poke-confirm-card');if(old)old.remove();" +
                "const b=document.createElement('button');" +
                "b.id='poke-confirm-card';b.textContent='✓ JUISTE KAART';" +
                "b.style.position='fixed';b.style.left='12px';b.style.right='12px';b.style.bottom='12px';" +
                "b.style.zIndex='2147483647';b.style.padding='16px';b.style.border='0';b.style.borderRadius='10px';" +
                "b.style.background='#d9232e';b.style.color='white';b.style.fontSize='18px';b.style.fontWeight='700';" +
                "b.style.boxShadow='0 3px 12px rgba(0,0,0,.35)';" +
                "b.onclick=function(){b.disabled=true;b.textContent='Kaart bevestigen…';PokeScanner.onMessage('CONFIRM_PRODUCT');};" +
                "document.body.appendChild(b);" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

'''
if marker not in text:
    raise SystemExit('readCurrentCardmarketPage marker not found')
text = text.replace(marker, confirm_method + marker, 1)

old_msg = '''                if ("BEST_RESULT_OPENED".equals(message)) autoOpenedProduct = true;
                if ("NO_SAFE_AUTO_MATCH".equals(message)) {
                    txtWebStatus.setText("Kies de juiste kaart uit de zoekresultaten");
                    Toast.makeText(MainActivity.this, "Meerdere mogelijke kaarten gevonden. Tik één keer op de juiste kaart; daarna wordt de pagina automatisch uitgelezen.", Toast.LENGTH_LONG).show();
                }
'''
new_msg = '''                if ("BEST_RESULT_OPENED".equals(message)) autoOpenedProduct = true;
                if ("CONFIRM_PRODUCT".equals(message)) {
                    if (looksLikeProductPage(webView.getUrl())) {
                        txtWebStatus.setText("Kaart bevestigd — gegevens uitlezen…");
                        readCurrentCardmarketPage();
                    } else {
                        Toast.makeText(MainActivity.this, "Open eerst de juiste Cardmarket-productpagina.", Toast.LENGTH_LONG).show();
                    }
                }
                if ("NO_SAFE_AUTO_MATCH".equals(message)) {
                    txtWebStatus.setText("Kies de juiste kaart; log eventueel in; bevestig daarna");
                    Toast.makeText(MainActivity.this, "Kies de juiste kaart uit de zoekresultaten. Log zo nodig in en tik daarna onderaan op ‘✓ Juiste kaart’.", Toast.LENGTH_LONG).show();
                }
'''
if old_msg not in text:
    raise SystemExit('Expected PageBridge message block not found')
text = text.replace(old_msg, new_msg, 1)

main_path.write_text(text, encoding='utf-8')

build_path = Path('app/build.gradle')
build = build_path.read_text(encoding='utf-8')
build = re.sub(r"versionCode\s+\d+", "versionCode 6", build, count=1)
build = re.sub(r"versionName\s+'[^']+'", "versionName '0.6.0'", build, count=1)
build_path.write_text(build, encoding='utf-8')

print('Applied v0.6 Cardmarket confirmation flow patch')
