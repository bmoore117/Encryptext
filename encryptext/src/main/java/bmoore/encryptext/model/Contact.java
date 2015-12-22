package bmoore.encryptext.model;

import android.graphics.Bitmap;

import com.google.i18n.phonenumbers.Phonenumber;

public class Contact
{
    private String name;
    private Phonenumber.PhoneNumber number;
    private Bitmap thumb;
    private int alpha;

    public enum KeyStatus
    {
        REQUEST_SENT,
        NEEDS_REVIEW,
    }

    public Contact()
    {
        name = "";
        number = null;
    }

    public Contact(String name, Phonenumber.PhoneNumber number, Bitmap thumb, int alpha)
    {
        this.name = name;
        this.number = number;
        this.thumb = thumb;
        this.alpha = alpha;
    }

    public int getAlpha()
    {
        return alpha;
    }

    public Bitmap getThumb()
    {
        return thumb;
    }

    public String getName()
    {
        return this.name;
    }

    public Phonenumber.PhoneNumber getNumber()
    {
        return this.number;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setNumber(Phonenumber.PhoneNumber phoneNumber)
    {
        this.number = phoneNumber;
    }
}