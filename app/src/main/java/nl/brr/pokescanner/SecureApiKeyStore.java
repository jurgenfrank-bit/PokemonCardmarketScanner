package nl.brr.pokescanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureApiKeyStore {
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "pokemon_scanner_openai_key_v1";
    private static final String PREFS = "pokemon_scanner_secure";
    private static final String PREF_CIPHER = "openai_cipher";
    private static final String PREF_IV = "openai_iv";

    private SecureApiKeyStore() { }

    public static void save(Context context, String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            clear(context);
            return;
        }
        SecretKey key = getOrCreateSecretKey();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    public static String load(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String enc = prefs.getString(PREF_CIPHER, "");
            String iv = prefs.getString(PREF_IV, "");
            if (enc.isEmpty() || iv.isEmpty()) return "";

            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            if (!ks.containsAlias(KEY_ALIAS)) return "";
            SecretKey key = (SecretKey) ks.getKey(KEY_ALIAS, null);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(enc, Base64.NO_WRAP));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        try {
            KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) ks.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) { }
    }

    private static SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEY_STORE);
        ks.load(null);
        if (ks.containsAlias(KEY_ALIAS)) {
            return (SecretKey) ks.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        generator.init(spec);
        return generator.generateKey();
    }
}
