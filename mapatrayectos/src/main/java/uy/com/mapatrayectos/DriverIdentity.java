package uy.com.mapatrayectos;

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

/**
 * Single driver binding for the ONE native Mapa package.
 * The currently PIN-based server API is unchanged: successful one-time PIN
 * validation occurs before bind(), then the secret is protected by Android
 * Keystore at rest. Device installation alone is never driver authorization.
 *
 * Legacy Mapa preferences are migrated in place without deleting trip/GPS data.
 * A new installation or a different phone must link once. A future backend
 * driver_id/device credential can replace this singleton implementation.
 */
public final class DriverIdentity {
    private static final String PREF="driver_identity_secure_v1";
    private static final String LEGACY="driver_session";
    private static final String FIELD="credential";
    private static final String ALIAS="uy.com.mapatrayectos.driver.identity.v1";
    private DriverIdentity(){}

    public static synchronized String pin(Context context){
        Context app=context.getApplicationContext();
        SharedPreferences prefs=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        String stored=prefs.getString(FIELD,"");
        if(stored!=null&&!stored.isEmpty()){
            try{return decrypt(stored);}
            catch(Exception error){
                // Never infer identity from an old/stale/corrupt encrypted blob.
                // On a new handset or after restore, link explicitly again.
                return "";
            }
        }
        SharedPreferences old=app.getSharedPreferences(LEGACY,Context.MODE_PRIVATE);
        String legacy=old.getString("pin","");
        if(legacy==null||!legacy.matches("[0-9]{4,10}"))return "";
        try{
            bind(app,legacy);
            return legacy;
        }catch(Exception error){
            // Fail closed if the secure store is unavailable. The legacy PIN
            // remains untouched until a successful migration; never use it
            // as a long-term plaintext fallback.
            return "";
        }
    }

    /** Call only AFTER server-side PIN validation has succeeded. */
    public static synchronized void bind(Context context,String validatedPin) throws Exception{
        if(validatedPin==null||!validatedPin.matches("[0-9]{4,10}"))
            throw new IllegalArgumentException("Credencial inválida");
        Context app=context.getApplicationContext();
        String protectedValue=encrypt(validatedPin);
        boolean saved=app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit()
            .putString(FIELD,protectedValue).commit();
        if(!saved)throw new IllegalStateException("No se pudo vincular este dispositivo");
        app.getSharedPreferences(LEGACY,Context.MODE_PRIVATE).edit()
            .remove("pin").apply();
        // Never carry a previous driver's ephemeral reservation cards into a binding.
        TransferAlerts.invalidateLocalSession(app);
    }

    public static synchronized void clear(Context context){
        Context app=context.getApplicationContext();
        app.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(FIELD).commit();
        app.getSharedPreferences(LEGACY,Context.MODE_PRIVATE).edit().remove("pin").apply();
        // Signing out hides cached passenger details without touching trip history.
        TransferAlerts.invalidateLocalSession(app);
    }

    private static SecretKey key() throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry=store.getEntry(ALIAS,null);
        if(entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }

    private static String encrypt(String input) throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] iv=cipher.getIV();
        byte[] ciphertext=cipher.doFinal(input.getBytes(StandardCharsets.UTF_8));
        byte[] data=new byte[1+iv.length+ciphertext.length];
        data[0]=(byte)iv.length;
        System.arraycopy(iv,0,data,1,iv.length);
        System.arraycopy(ciphertext,0,data,1+iv.length,ciphertext.length);
        return Base64.encodeToString(data,Base64.NO_WRAP);
    }

    private static String decrypt(String input) throws Exception{
        byte[] data=Base64.decode(input,Base64.NO_WRAP);
        if(data.length<30)throw new IllegalStateException("Credencial incompleta");
        int n=data[0]&0xff;
        if(n!=12||data.length<=1+n)throw new IllegalStateException("IV inválido");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,data,1,n));
        String result=new String(cipher.doFinal(data,1+n,data.length-1-n),StandardCharsets.UTF_8);
        if(!result.matches("[0-9]{4,10}"))throw new IllegalStateException("Credencial inválida");
        return result;
    }
}
