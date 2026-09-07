from pathlib import Path
import re

main_path = Path('app/src/main/java/nl/brr/pokescanner/MainActivity.java')
text = main_path.read_text(encoding='utf-8')

old_no_key = '''        } else {
            txtStatus.setText("AI vision is nog niet ingesteld; lokale OCR wordt als fallback gebruikt.");
            runLocalOcrFallback(uri, "Tik bovenaan op AI voor veel betere beeldherkenning. ");
        }
'''
new_no_key = '''        } else {
            editSearch.setText("");
            editGrader.setText("");
            editGrade.setText("");
            txtSlab.setText("Slab: nog niet geanalyseerd");
            txtAiDetails.setText("AI vision niet beschikbaar: geen API-key ingesteld");
            txtStatus.setText("Geen AI-analyse uitgevoerd. Er wordt bewust geen automatische OCR-fallback gebruikt omdat die verkeerde kaartnamen kan geven. Tik op AI om toegang in te stellen, of vul de kaartgegevens handmatig in.");
        }
'''
if old_no_key not in text:
    raise SystemExit('Expected no-key fallback block not found')
text = text.replace(old_no_key, new_no_key, 1)

old_error = '''            @Override
            public void onError(String message) {
                txtAiDetails.setText("AI vision mislukt: " + message + " — lokale OCR wordt gebruikt");
                runLocalOcrFallback(uri, "AI vision mislukt. ");
            }
'''
new_error = '''            @Override
            public void onError(String message) {
                editSearch.setText("");
                editGrader.setText("");
                editGrade.setText("");
                txtSlab.setText("Slab: niet geanalyseerd");
                txtAiDetails.setText("AI vision niet beschikbaar: " + message);
                txtStatus.setText("Geen kaartresultaat getoond: de AI-analyse is niet uitgevoerd of mislukt. De app gebruikt bewust geen automatische OCR-fallback. Je kunt de gegevens handmatig invullen of later opnieuw met AI proberen.");
            }
'''
if old_error not in text:
    raise SystemExit('Expected AI error fallback block not found')
text = text.replace(old_error, new_error, 1)

# Keep the manual OCR helper in source for development/debugging, but it is no longer
# called automatically from either no-key or AI-error paths.
if 'runLocalOcrFallback(uri' in text:
    # The only occurrence should now be the method declaration, not a call.
    call_count = len(re.findall(r'runLocalOcrFallback\(uri\s*,', text))
    if call_count:
        raise SystemExit(f'Unexpected automatic OCR fallback call remains: {call_count}')

main_path.write_text(text, encoding='utf-8')

build_path = Path('app/build.gradle')
build = build_path.read_text(encoding='utf-8')
build = re.sub(r"versionCode\s+\d+", "versionCode 9", build, count=1)
build = re.sub(r"versionName\s+'[^']+'", "versionName '0.9.0'", build, count=1)
build_path.write_text(build, encoding='utf-8')

print('Applied v0.9: AI failures no longer fall back to misleading OCR')
