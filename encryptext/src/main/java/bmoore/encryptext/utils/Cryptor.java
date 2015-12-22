package bmoore.encryptext.utils;


import android.util.Log;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyAgreement;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Cryptor
{
    public enum KeyTypes {
        SECRET,
        PRIVATE,
        PUBLIC
    }

    private static String TAG = "Cryptor";
    private DBUtils dbUtils;

    private Cipher aes;
    private KeyAgreement ecdh;
    private KeyPairGenerator pairGen;
    private KeyFactory keyFactory;
    private SecureRandom source;
    private MessageDigest sha256;

    private KeyPair pair;

    private HashMap<String, PublicKey> inNegotiation;
    private HashMap<SecretKey, byte[]> lastReceivedCipher;
    private HashMap<SecretKey, byte[]> lastSentCipher;

    public Cryptor(DBUtils utils) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeySpecException
    {
        dbUtils = utils;
        lastReceivedCipher = new HashMap<>();
        lastSentCipher = new HashMap<>();
        inNegotiation = new HashMap<>();

        keyFactory = KeyFactory.getInstance("EC");

        source = SecureRandom.getInstance("SHA1PRNG");
        pairGen = KeyPairGenerator.getInstance("EC");

        aes = Cipher.getInstance("AES/CBC/PKCS5Padding");
        ecdh = KeyAgreement.getInstance("ECDH");
        sha256 = MessageDigest.getInstance("SHA-256");

        HashMap<String, byte[]> keyBlobs = dbUtils.loadKeysInNegotiation();

        for(Map.Entry<String, byte[]> entry : keyBlobs.entrySet())
        {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(entry.getValue());
            inNegotiation.put(entry.getKey(), keyFactory.generatePublic(publicKeySpec));
        }
    }

    public SecretKey createAndStoreSecretKey(String address) throws InvalidKeyTypeException, InvalidKeyException
    {
        PublicKey k = inNegotiation.get(address);
        inNegotiation.remove(address);

        ecdh.init(pair.getPrivate());
        ecdh.doPhase(k, true);
        byte[] secret = ecdh.generateSecret();

        byte[] newSecret = sha256.digest(secret);
        SecretKey finalKey = new SecretKeySpec(newSecret, "AES");

        storeSecretKey(finalKey, address);

        return finalKey;
    }

    public void storeLastEncryptedBlock(SecretKey key, String address) {
        byte[] lastReceived = lastReceivedCipher.get(key);

        if(lastReceived != null) {
            dbUtils.storeEncryptedBlock(address, lastReceived);
        }
    }

    public PublicKey getMyPublicKey()
    {
        return pair.getPublic();
    }

    public void initPublicCrypto() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyTypeException, InvalidKeySpecException
    {
        loadMyKeys();
        if(pair == null)
            return;

        ECGenParameterSpec ecParamSpec = new ECGenParameterSpec("secp384r1");

        pairGen.initialize(ecParamSpec);
        pair = pairGen.generateKeyPair();
        Log.i(TAG, pair.getPrivate().getFormat());
        Log.i(TAG, pair.getPublic().getFormat());
        storeMyKeys(pair);
    }

    public boolean checkAndHold(byte[] key, String address) throws NoSuchAlgorithmException, InvalidKeySpecException, InvalidKeyTypeException
    {
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(key);
        PublicKey k = keyFactory.generatePublic(publicKeySpec);

        if(loadPublicKey(address) != null) //should not find our public key in the private entry it's stored in
            return false;                   //thus self-texting should be fine and renegotiations won't happen

        storePublicKey(k, address);

        if(loadPublicKey(address) == null)
            Log.i(TAG, "Key not stored");

        inNegotiation.put(address, k);

        return true;
    }

    PrivateKey loadPrivateKey(String address) throws InvalidKeyTypeException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        byte[] keyBytes = dbUtils.loadKeyBytes(address, KeyTypes.PRIVATE);

        if(keyBytes == null)
            return null;
        else {
            //for private keys use PKCS8EncodedKeySpec; for public keys use X509EncodedKeySpec
            PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(keyBytes);
            return keyFactory.generatePrivate(ks);
        }
    }

    PublicKey loadPublicKey(String address) throws InvalidKeyTypeException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        byte[] keyBytes = dbUtils.loadKeyBytes(address, KeyTypes.PUBLIC);

        if(keyBytes == null)
            return null;
        else {
            X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(keyBytes);
            return keyFactory.generatePublic(publicKeySpec);
        }
    }

    public SecretKey loadSecretKey(String address) throws InvalidKeyTypeException
    {
        byte[] keyBytes = dbUtils.loadKeyBytes(address, KeyTypes.SECRET);

        if(keyBytes == null)
            return null;
        else
            return new SecretKeySpec(keyBytes, "AES");
    }

    private void loadMyKeys() throws InvalidKeyTypeException, NoSuchAlgorithmException, InvalidKeySpecException
    {
        PrivateKey priv = loadPrivateKey("Me");
        PublicKey pub = loadPublicKey("Me");

        pair = new KeyPair(pub, priv);
    }

    void storeMyKeys(KeyPair pair) throws InvalidKeyTypeException
    {
        dbUtils.storeKeyBytes("Me", pair.getPrivate().getEncoded(), KeyTypes.PRIVATE);
        dbUtils.storeKeyBytes("Me", pair.getPublic().getEncoded(), KeyTypes.PUBLIC);
    }

    void storeSecretKey(SecretKey key, String address) throws InvalidKeyTypeException
    {
        dbUtils.storeKeyBytes(address, key.getEncoded(), KeyTypes.SECRET);
    }

    void storePublicKey(PublicKey key, String address) throws InvalidKeyTypeException
    {
        dbUtils.storeKeyBytes(address, key.getEncoded(), KeyTypes.PUBLIC);
    }

    public byte[] encryptMessage(byte[] message, SecretKey key, int sendIV) throws InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException, InvalidAlgorithmParameterException
    {
        if(sendIV == 0)
        {
            aes.init(Cipher.ENCRYPT_MODE, key, source);
            byte[] encryptedMessage = aes.doFinal(message);

            byte[] lastSent = new byte[16];
            System.arraycopy(encryptedMessage, encryptedMessage.length - 16, lastSent, 0, 16);
            lastSentCipher.put(key, lastSent);

            byte[] IV = aes.getIV();
            byte[] ivAndMessage = new byte[encryptedMessage.length + IV.length];
            System.arraycopy(IV, 0, ivAndMessage, 0, IV.length);
            System.arraycopy(encryptedMessage, 0, ivAndMessage, IV.length, encryptedMessage.length);

            return ivAndMessage;
        }
        else
        {
            AlgorithmParameterSpec ivSpec = new IvParameterSpec(lastSentCipher.get(key));
            aes.init(Cipher.ENCRYPT_MODE, key, ivSpec);

            byte[] encryptedMessage = aes.doFinal(message);

            byte[] lastSent = new byte[16];
            System.arraycopy(encryptedMessage, encryptedMessage.length - 16, lastSent, 0, 16);
            lastSentCipher.put(key, lastSent);

            return encryptedMessage;
        }
    }

    public byte[] decryptMessage(byte[] cipherTextPacket, SecretKey key, int containsIV, String address) throws InvalidKeyException, BadPaddingException,
            IllegalBlockSizeException, InvalidAlgorithmParameterException
    {

        byte[] plaintext;

        if(containsIV == 0)
        {
            byte[] IV = new byte[16];
            System.arraycopy(cipherTextPacket, 0, IV, 0, 16);

            byte[] cipherText = new byte[cipherTextPacket.length - 16];
            System.arraycopy(cipherTextPacket, 16, cipherText, 0, cipherText.length);

            AlgorithmParameterSpec ivSpec = new IvParameterSpec(IV);
            aes.init(Cipher.DECRYPT_MODE, key, ivSpec);

            plaintext = aes.doFinal(cipherText);
        }
        else
        {
            if(lastReceivedCipher.get(key) == null) {
                lastReceivedCipher.put(key, dbUtils.loadEncryptedBlock(address));
            }

            AlgorithmParameterSpec ivSpec = new IvParameterSpec(lastReceivedCipher.get(key));
            aes.init(Cipher.DECRYPT_MODE, key, ivSpec);
            plaintext = aes.doFinal(cipherTextPacket);
        }

        byte[] newIV = new byte[16];
        System.arraycopy(cipherTextPacket, cipherTextPacket.length - 16, newIV, 0, 16);
        lastReceivedCipher.put(key, newIV);

        return plaintext;
    }

    public void removePublicKey(String address) throws InvalidKeyTypeException {
        inNegotiation.remove(address);
        dbUtils.deleteKey(address, KeyTypes.PUBLIC);
    }

    public boolean checkSecretKeyExists(String number) {
        return dbUtils.checkKeyExists(number, KeyTypes.SECRET);
    }
}