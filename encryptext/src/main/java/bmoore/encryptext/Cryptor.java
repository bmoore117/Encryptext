package bmoore.encryptext;


import android.util.Log;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.TreeMap;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class Cryptor
{
    private static String TAG = "Cryptor";
    private Files manager;

    private Cipher aes;
    private KeyAgreement ecdh;
    private KeyPairGenerator pairGen;
    private SecureRandom source;
    private MessageDigest sha256;

    private KeyPair pair;

    private TreeMap<String, PublicKey> inNegotiation;

    private KeyStore secretKeyDB, publicKeyDB;

    public static final char[] SECRET_STORE_PASSWORD = "3q498tyakjgDD[;*DF".toCharArray(); //to be replaced
    public static final char[] PUBLIC_STORE_PASSWORD = ":ET:DI34S:DLKRT#[;".toCharArray(); //to be replaced


    public Cryptor(Files obj)
    {
        manager = obj;

        try
        {
            source = SecureRandom.getInstance("SHA1PRNG");
            pairGen = KeyPairGenerator.getInstance("EC");

            aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
            ecdh = KeyAgreement.getInstance("ECDH");
            sha256 = MessageDigest.getInstance("SHA-256");

            secretKeyDB = manager.createOrLoadKeystore(false, SECRET_STORE_PASSWORD);
            publicKeyDB = manager.createOrLoadKeystore(true, PUBLIC_STORE_PASSWORD);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException e)
        {
            Log.e(TAG, e.toString());
            e.printStackTrace();
        }
    }

    void finalize(String address)
    {
        PublicKey k = inNegotiation.get(address);
        inNegotiation.remove(address);

        try
        {
            ecdh.init(pair.getPrivate());
            ecdh.doPhase(k, true);
            byte[] secret = ecdh.generateSecret();

            byte[] newSecret = sha256.digest(secret);
            SecretKey finalKey = new SecretKeySpec(newSecret, "AES");

            storeSecretKey(finalKey, address);
        }
        catch (InvalidKeyException e)
        {
            e.printStackTrace();
        }
    }

    public byte[] getMyKeyBytes()
    {
        return pair.getPublic().getEncoded();
    }

    void initPublicCrypto()
    {
        if(loadMyKeys() == false)
            return;

        ECGenParameterSpec ecParamSpec = new ECGenParameterSpec("secp384r1");
        try
        {
            pairGen.initialize(ecParamSpec);
            pair = pairGen.generateKeyPair();
            Log.i(TAG, pair.getPrivate().getFormat());
            Log.i(TAG, pair.getPublic().getFormat());
            storeMyKeys(pair);
        }
        catch (InvalidAlgorithmParameterException e)
        {
            e.printStackTrace();
        }
    }

    boolean checkAndHold(byte[] key, String address, String name)
    {
        KeyFactory generator;
        PublicKey k = null;
        try
        {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(key);
            generator = KeyFactory.getInstance("EC");
            k = generator.generatePublic(publicKeySpec);
        }
        catch (NoSuchAlgorithmException | InvalidKeySpecException e)
        {
            e.printStackTrace();
        }

        if(loadPublicKey(address) != null) //should not find our public key in the private entry it's stored in
            return false;                   //thus self-texting should be fine and renegotiations won't happen

        storePublicKey(k, address, name);

        if(loadPublicKey(address) == null)
            Log.i(TAG, "Key not stored");

        if(inNegotiation == null)
            inNegotiation = new TreeMap<>();

        inNegotiation.put(address, k);

        return true;
    }

    PrivateKey loadPrivateKey(String address)
    {
        PrivateKey key = null;
        try
        {
            KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry)
                    publicKeyDB.getEntry("Me", null);

            if(pkEntry != null)
                key = pkEntry.getPrivateKey();
        }
        catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException e)
        {
            e.printStackTrace();
        }

        return key;
    }

    PublicKey loadPublicKey(String address)
    {
        PublicKey key = null;
        try
        {
            Log.i(TAG, "LPK " + address + " " + publicKeyDB.containsAlias(address));

            EncrypCert cert = (EncrypCert) publicKeyDB.getCertificate(address);

            if(cert != null)
                key = cert.getPublicKey();
            else
                Log.i(TAG, "LPK " + address + " null cert");
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }

        if(key == null)
            Log.i(TAG, "LPK " + address + " null");

        return key;
    }

    SecretKey loadSecretKey(String address)
    {
        SecretKey key = null;
        try
        {
            Log.i(TAG, "LSK " + address + " " + secretKeyDB.containsAlias(address));

            KeyStore.SecretKeyEntry pkEntry = (KeyStore.SecretKeyEntry)
                    secretKeyDB.getEntry(address, null);

            if(pkEntry != null)
                key = pkEntry.getSecretKey();
            else
                Log.i(TAG, "LSK " + address + " null entry");
        }
        catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException e)
        {
            e.printStackTrace();
        }

        if(key == null)
            Log.i(TAG, "LSK " + address + " null key");

        return key;
    }

    boolean loadMyKeys()
    {
        PrivateKey key;
        PublicKey key1;

        try
        {
            Log.i(TAG, "Me" + " " + publicKeyDB.containsAlias("Me"));
            KeyStore.PrivateKeyEntry pkEntry = (KeyStore.PrivateKeyEntry)
                    publicKeyDB.getEntry("Me", null);


            if(pkEntry != null)
            {
                key = pkEntry.getPrivateKey();
                key1 = pkEntry.getCertificateChain()[0].getPublicKey();

                pair = new KeyPair(key1, key);
            }

            return true;
        }
        catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableEntryException e)
        {
            e.printStackTrace();
        }

        return false;
    }

    void storeMyKeys(KeyPair pair)
    {
        EncrypCert pub = new EncrypCert(pair.getPublic(), "Me", "");
        Certificate[] certs = new Certificate[] {pub};
        KeyStore.PrivateKeyEntry prv = new KeyStore.PrivateKeyEntry(pair.getPrivate(), certs);

        try
        {
            publicKeyDB.setEntry("Me", prv, null);
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }

        manager.saveKeyStore(publicKeyDB, PUBLIC_STORE_PASSWORD);

        try
        {
            Log.i(TAG, "Me" + " " + publicKeyDB.containsAlias("Me"));
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }
    }

    void storeSecretKey(SecretKey key, String address)
    {
        KeyStore.SecretKeyEntry skEntry = new KeyStore.SecretKeyEntry(key);

        try
        {
            secretKeyDB.setEntry(address, skEntry, null);
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }

        manager.saveKeyStore(secretKeyDB, SECRET_STORE_PASSWORD);

        try
        {
            Log.i(TAG, "SSK " + address + " " + secretKeyDB.containsAlias(address));
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }
    }

    void storePublicKey(PublicKey key, String address, String name)
    {
        Log.i(TAG, "Storing key for " + address);

        try
        {
            publicKeyDB.setCertificateEntry(address, new EncrypCert(key, name, address));
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }

        manager.saveKeyStore(publicKeyDB, SECRET_STORE_PASSWORD);

        try
        {
            Log.i(TAG, "SPK " + address + " " + publicKeyDB.containsAlias(address));
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }
    }




    /*public void ensurePublicKeys()
    {
        if (manager.hasRSAKeys())
            return;

        PRNGFixes.apply();


    }*/


}