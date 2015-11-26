package bmoore.encryptext.model;

import android.graphics.Bitmap;

public class Contact
{
    private String name;
    private String number;
    private Bitmap thumb;
    private int alpha;

    public enum KeyStatus
    {
        REQUEST_SENT,
        NEEDS_REVIEW,
        KEYS_EXCAHNGED
    }

    public Contact()
    {
        name = "";
        number = "";
    }

    public Contact(String name, String number, Bitmap thumb, int alpha)
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

    public String getNumber()
    {
        return this.number;
    }

    public void setName(String paramString)
    {
        this.name = paramString;
    }

    public void setNumber(String paramString)
    {
        this.number = paramString;
    }
}