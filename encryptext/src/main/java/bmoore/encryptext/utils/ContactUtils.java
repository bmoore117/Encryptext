package bmoore.encryptext.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

/**
 * Created by Benjamin Moore on 11/21/2015.
 */
public class ContactUtils {

    public static String TAG = "ContactUtils";

    /**
     * Method to load a contact picture given a phone number. If passed a null String, queries the content
     * resolver for the user profile ID, using the ContactsContract.Profile constants, and then uses the
     * Contacts' class method to open a photo stream. If passed a non-null String, uses the PhoneLookup
     * class together with the number passed to construct a Uri to search for and retrieve a contact ID,
     * performing the same steps afterward.
     *
     * @param number - the phone number to retrieve a picture for
     */
    public static Bitmap getBitmap(ContentResolver contentResolver, String number)
    {
        Uri path;
        long ID = -1L;
        Cursor c;
        if (number == null)
        {
            path = ContactsContract.Profile.CONTENT_URI;

            if(path == null) //do nothing if we couldn't access system services
            {
                Log.v(TAG, "Could not read Profile.CONTENT_URI");
                return null;
            }

            c = contentResolver.query(path, new String[] { ContactsContract.Profile._ID }, null, null, null);

            if(c == null)
            {
                Log.v(TAG, "Content resolver returned no results for personal contact card query");
                return null;
            }

            if (c.moveToFirst())
            {
                do
                    ID = c.getLong(0);
                while (c.moveToNext());
            }
            c.close();


            if (ID > ContactsContract.Profile.MIN_ID)
            {
                Uri temp = ContactsContract.Contacts.CONTENT_URI;

                if(temp == null) //do nothing if we couldn't access system services
                {
                    Log.v(TAG, "Could not read Contacts.CONTENT_URI");
                    return null;
                }

                path = Uri.withAppendedPath(temp, "" + ID);

                if(path == null) //if construction failed, do nothing
                {
                    Log.v(TAG, "Could not construct path with supplied ID & URI");
                    return null;
                }

                return BitmapFactory.decodeStream(ContactsContract.Contacts.openContactPhotoInputStream(
                        contentResolver, path));
            }
        }
        else
        {
            Uri temp = ContactsContract.PhoneLookup.CONTENT_FILTER_URI;

            if(temp == null)
            {
                Log.v(TAG, "Could not read PhoneLookup.CONTENT_FILTER_URI");
                return null;
            }

            path = Uri.withAppendedPath(temp, Uri.encode(number));

            if(path == null)
            {
                Log.v(TAG, "Could not construct Uri from PhoneLookup.CONTENT_FILTER_URI and " +
                        "phone number provided");
                return null;
            }

            c = contentResolver.query(path, new String[] { ContactsContract.Contacts._ID }, null, null, null);

            if(c == null)
            {
                Log.v(TAG, "Content resolver returned no results for target contact card query");
                return null;
            }

            c.moveToFirst();
            ID = c.getLong(0);

            c.close();

            temp = ContactsContract.Contacts.CONTENT_URI;

            if(temp == null) //do nothing if we couldn't access system services
            {
                Log.v(TAG, "Could not read Contacts.CONTENT_URI");
                return null;
            }

            path = Uri.withAppendedPath(temp, "" + ID);

            if(path == null)
            {
                Log.v(TAG, "Could not construct Uri from Contacts.CONTENT_URI and ID provided");
                return null;
            }

            return BitmapFactory.decodeStream(ContactsContract.Contacts.openContactPhotoInputStream(
                    contentResolver, path));
        }

        return null;
    }

    public static String getContactName(ContentResolver contentResolver, String address)
    {
        Uri temp = ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI;

        if(temp == null)
        {
            Log.v(TAG, "Could not read ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI");
            return null;
        }

        Uri path = Uri.withAppendedPath(temp, address);

        if(path == null)
        {
            Log.v(TAG, "Construction of path to contact failed with given base and address");
            return null;
        }


        Log.i(TAG, "Obtaining name");
        String[] projection = {ContactsContract.Contacts.DISPLAY_NAME };
        Cursor cr = contentResolver.query(path, projection, null, null, null);

        if (cr == null || !cr.moveToFirst())
            return "";

        String name = cr.getString(0); //can use zero, bc only queried for one column
        cr.close();
        return name;
    }
}
