package bmoore.encryptext;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

public class ConversationEntry
implements Parcelable
{
	public static final Creator<ConversationEntry> CREATOR = new Creator<ConversationEntry>()
	{
		public ConversationEntry createFromParcel(Parcel paramAnonymousParcel)
		{
			return new ConversationEntry(paramAnonymousParcel);
		}

		public ConversationEntry[] newArray(int paramAnonymousInt)
		{
			return new ConversationEntry[paramAnonymousInt];
		}
	};
	private String number;
	private String message;
	private String name;
	private Bitmap photo;
	private String date;

	public ConversationEntry()
	{
		this.message = "";
		this.name = "";
		this.number = null;
		this.photo = null;
	}

	public ConversationEntry(Parcel p)
	{
		this.message = p.readString();
		this.number = p.readString();
		this.name = p.readString();
		this.date = p.readString();
		this.photo = p.readParcelable(Bitmap.class.getClassLoader());
	}

	/**
	 * For reading from file
	 * @param message
	 * @param number
	 * @param name
	 * @param pic
	 */
	public ConversationEntry(String message, String number, String name, Bitmap pic)
	{
		this.message = message;
		this.name = name;
		this.number = number;
		this.photo = pic;
	}
	
	/**
	 * When created otherwise
	 * @param message
	 * @param number
	 * @param name
	 * @param date
	 * @param pic
	 */
	public ConversationEntry(String message, String number, String name, String date, Bitmap pic)
	{
		this.message = message;
		this.name = name;
		this.number = number;
		this.photo = pic;
		this.date = date;
	}

	public int describeContents()
	{
		return 0;
	}

	public String getNumber()
	{
		return this.number;
	}

	public String getMessage()
	{
		return this.message;
	}

	public String getName()
	{
		return this.name;
	}

	public Bitmap getPhoto()
	{
		return this.photo;
	}
	
	public String getDate()
	{
		return date;
	}
	
	public void setDate(String date)
	{
		this.date = date;
	}

	public void setAddress(String paramString)
	{
		this.number = paramString;
	}

	public void setName(String paramString)
	{
		this.name = paramString;
	}

	public void setPhoto(Bitmap paramBitmap)
	{
		this.photo = paramBitmap;
	}

	public void writeToParcel(Parcel p, int paramInt)
	{
		p.writeString(this.message);
		p.writeString(this.number);
		p.writeString(this.name);
		p.writeString(this.date);
		p.writeParcelable(this.photo, 0);
	}
}

/* Location:           C:\Users\Benjamin Moore\Dropbox\App\Code Recovery\classes_dex2jar.jar
 * Qualified Name:     com.encryptext.ConversationEntry
 * JD-Core Version:    0.6.2
 */