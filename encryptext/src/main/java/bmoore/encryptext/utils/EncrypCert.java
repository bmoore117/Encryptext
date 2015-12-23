package bmoore.encryptext.utils;

import java.security.PublicKey;
import java.security.cert.Certificate;


/**
 * Basically, a fancy wrapper class to a public key. Created because Java is a butt about how it
 * handles public/private keys in a keystore
 */
public class EncrypCert extends Certificate {
    private PublicKey pub;
    private String name;
    private String address;

    public EncrypCert() {
        super("EncrypCert");
    }

    public EncrypCert(PublicKey pub, String name, String address) {
        super("EncrypCert");

        this.pub = pub;
        this.name = name;
        this.address = address;
    }

    @Override
    public byte[] getEncoded() {
        return pub.getEncoded();
    }

    @Override
    public String toString() {
        return name + ": (" + address + "), key of: " + new String(pub.getEncoded());
    }

    @Override
    public void verify(PublicKey pub, String sigProvider) {

    }

    @Override
    public void verify(PublicKey pub) {

    }

    @Override
    public PublicKey getPublicKey() {
        return pub;
    }


}
