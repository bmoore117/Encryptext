package bmoore.encryptext.model;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class ConversationEntry implements Parcelable {
    public static final Creator<ConversationEntry> CREATOR = new Creator<ConversationEntry>() {
        public ConversationEntry createFromParcel(Parcel paramAnonymousParcel) {
            return new ConversationEntry(paramAnonymousParcel);
        }

        public ConversationEntry[] newArray(int paramAnonymousInt) {
            return new ConversationEntry[paramAnonymousInt];
        }
    };

    private long messageId;
    private String message;
    private String number;
    private String name;
    private String date;
    private Integer imageResourceId;
    private Bitmap photo;


    public ConversationEntry() {
        this(-1, null, null, null, null, null);
    }

    public ConversationEntry(Parcel p) {
        this.messageId = p.readLong();
        this.message = p.readString();
        this.number = p.readString();
        this.name = p.readString();
        this.date = p.readString();
        this.imageResourceId = p.readInt();

        if (imageResourceId == Integer.MIN_VALUE) {
            imageResourceId = null;
        }

        this.photo = p.readParcelable(Bitmap.class.getClassLoader());
    }

    /**
     * For reading from file
     *
     * @param message
     * @param name
     * @param date
     * @param pic
     */
    public ConversationEntry(long messageId, String message, String name, String date, Bitmap pic) {
        this(messageId, message, null, name, date, pic);
    }

    public ConversationEntry(String message, String number, String name, String date, Bitmap pic) {
        this(-1, message, number, name, date, pic);
    }

    /**
     * When created otherwise
     *
     * @param message
     * @param number
     * @param name
     * @param date
     * @param pic
     */
    public ConversationEntry(long messageId, String message, String number, String name, String date, Bitmap pic) {
        this.message = message;
        this.name = name;
        this.number = number;
        this.photo = pic;
        this.date = date;
    }

    public int describeContents() {
        return 0;
    }

    public String getNumber() {
        return this.number;
    }

    public String getMessage() {
        return this.message;
    }

    public String getName() {
        return this.name;
    }

    public Bitmap getPhoto() {
        return this.photo;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public void setAddress(String paramString) {
        this.number = paramString;
    }

    public void setName(String paramString) {
        this.name = paramString;
    }

    public void setPhoto(Bitmap paramBitmap) {
        this.photo = paramBitmap;
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    public void setImageResourceId(Integer imageResourceId) {
        this.imageResourceId = imageResourceId;
    }

    public Integer getImageResourceId() {
        return imageResourceId;
    }

    public void writeToParcel(Parcel p, int paramInt) {
        p.writeLong(messageId);
        p.writeString(message);
        p.writeString(number);
        p.writeString(name);
        p.writeString(date);
        p.writeInt(imageResourceId == null ? Integer.MIN_VALUE : imageResourceId);
        p.writeParcelable(photo, 0);
    }
}