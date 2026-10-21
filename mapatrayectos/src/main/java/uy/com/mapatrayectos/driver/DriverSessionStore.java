package uy.com.mapatrayectos.driver;

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

/** A long-lived driver session is stored encrypted with Android Keystore.
 * Neither the original PIN nor a plain session credential is persisted.
 * The session is valid only if the backend has approved this device.
 */
public final class DriverSessionStore {
    private static final String KEY_NAME="quicktrip.mapa.driver_session.v1";
    private static final String PREFS="driver_session";
    private static final String FIELD="session_encrypted_v1";
    private DriverSessionStore(){}
    private static SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry entry=store.getEntry(KEY_NAME,null);
        if(entry instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry)entry).getSecretKey();
        KeyGenerator k=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        k.init(new KeyGenParameterSpec.Builder(KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(false).build());
        return k.generateKey();
    }
    public static synchronized void save(Context context,String token)throws Exception{
        if(token==null||!token.matches("mqs1\\.[0-9a-f]{64}"))
            throw new IllegalArgumentException("Credencial de dispositivo inválida");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,key());
        byte[] ciphertext=c.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String encoded=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+"."+
            Base64.encodeToString(ciphertext,Base64.NO_WRAP);
        SharedPreferences p=context.getApplicationContext()
            .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        if(!p.edit().putString(FIELD,encoded).remove("pin").commit())
            throw new IllegalStateException("No se pudo guardar autorización de Mapa");
    }
    public static synchronized String token(Context context){
        if(context==null)return "";
        String encoded=context.getApplicationContext()
            .getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(FIELD,"");
        if(encoded==null||encoded.isEmpty())return "";
        try{
            String[] parts=encoded.split("\\.",2);
            if(parts.length!=2)return "";
            byte[] iv=Base64.decode(parts[0],Base64.NO_WRAP);
            byte[] cipherBytes=Base64.decode(parts[1],Base64.NO_WRAP);
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            String value=new String(c.doFinal(cipherBytes),StandardCharsets.UTF_8);
            return value.matches("mqs1\\.[0-9a-f]{64}")?value:"";
        }catch(Exception e){return "";}
    }
    public static boolean linked(Context context){return !token(context).isEmpty();}
    public static void clear(Context context){
        context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().remove(FIELD).remove("pin").apply();
    }
}
