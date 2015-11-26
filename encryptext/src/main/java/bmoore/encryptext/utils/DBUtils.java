package bmoore.encryptext.utils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

import bmoore.encryptext.db.DBManager;
import bmoore.encryptext.db.Schema;
import bmoore.encryptext.model.Contact;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.model.KeyRequest;

/**
 * Created by Benjamin Moore on 11/24/2015.
 */
public class DBUtils {

    private DBManager manager;
    private ContentResolver contentResolver;

    public DBUtils(Context context)
    {
        manager = new DBManager(context);
        contentResolver = context.getContentResolver();
    }

    public List<ConversationEntry> loadConversation(String number, long startFromMessageId)
    {
        SQLiteDatabase db = manager.getReadableDatabase();

        Bitmap me = ContactUtils.getBitmap(contentResolver, null);
        Bitmap other = ContactUtils.getBitmap(contentResolver, number);

        Cursor c = db.rawQuery("select * from conversations c where c.message_id >= ? order by c.message_id asc;", new String[]{String.valueOf(startFromMessageId)});

        ArrayList<ConversationEntry> results = new ArrayList<>();

        if(c.moveToFirst()) {
            do {
                long messageId = c.getLong(c.getColumnIndex(Schema.conversations.message_id));
                String message = c.getString(c.getColumnIndex(Schema.conversations.message));
                String name = c.getString(c.getColumnIndex(Schema.conversations.name));
                String date = c.getString(c.getColumnIndex(Schema.conversations.status_date));
                results.add(new ConversationEntry(messageId, message, name, date, "Me".equals(name) ? me : other));
            } while (c.moveToNext());
        }
        c.close();

        return results;
    }

    public long storeMessage(ConversationEntry item)
    {
        SQLiteDatabase db = manager.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(Schema.conversations.phone_number, item.getNumber());
        values.put(Schema.conversations.name, item.getName());
        values.put(Schema.conversations.status_date, item.getDate());
        values.put(Schema.conversations.message, item.getMessage());

        //should return primary key of new row
        return db.insert(Schema.conversations.class.getSimpleName(), "null", values);
    }

    public void confirmMessageSent(String time, long messageId)
    {
        SQLiteDatabase db = manager.getWritableDatabase();

        SQLiteStatement statement = db.compileStatement("update conversations set status_date = ? where message_id = ?");
        statement.bindString(1, time);
        statement.bindLong(2, messageId);

        statement.executeUpdateDelete();
    }

    public List<ConversationEntry> readPreviews()
    {

        SQLiteDatabase db = manager.getReadableDatabase();

        Cursor conversations = db.rawQuery("select distinct phone_number from conversations", null);

        ArrayList<ConversationEntry> results = new ArrayList<>();

        if(conversations.moveToFirst()) {
            do {

                String number = conversations.getString(conversations.getColumnIndex(Schema.conversations.phone_number));
                Bitmap other = ContactUtils.getBitmap(contentResolver, number);

                Cursor previews = db.rawQuery("select * from conversations where message_id in (select max(message_id) from conversations where phone_number = ?)",
                        new String[]{number});

                if(previews.moveToFirst()) {
                    do {
                        String message = previews.getString(previews.getColumnIndex(Schema.conversations.message));
                        String name = previews.getString(previews.getColumnIndex(Schema.conversations.name));
                        String date = previews.getString(previews.getColumnIndex(Schema.conversations.status_date));
                        results.add(new ConversationEntry(message, number, name, date, other));
                    } while (previews.moveToNext());
                }
                previews.close();
            } while (conversations.moveToNext());
        }
        conversations.close();

        return results;
    }

    public long generateKeyRequestEntry(String address, String name, Contact.KeyStatus status, String date)
    {
        SQLiteDatabase db = manager.getReadableDatabase();

        ContentValues values = new ContentValues();
        values.put(Schema.key_exchange_statuses.phone_number, address);
        values.put(Schema.key_exchange_statuses.name, name);
        values.put(Schema.key_exchange_statuses.status, status.toString());
        values.put(Schema.key_exchange_statuses.status_date, date);

        //should return primary key of new row
        return db.insert(Schema.key_exchange_statuses.class.getSimpleName(), "null", values);
    }

    public List<KeyRequest> loadKeyRequests()
    {
        SQLiteDatabase db = manager.getReadableDatabase();

        Cursor statuses = db.rawQuery("select * from " + Schema.key_exchange_statuses.class.getSimpleName(), null);

        ArrayList<KeyRequest> results = new ArrayList<>();

        if(statuses.moveToFirst()) {
            do {

                String number = statuses.getString(statuses.getColumnIndex(Schema.key_exchange_statuses.phone_number));
                String name = statuses.getString(statuses.getColumnIndex(Schema.key_exchange_statuses.name));
                String status = statuses.getString(statuses.getColumnIndex(Schema.key_exchange_statuses.status));
                String date = statuses.getString(statuses.getColumnIndex(Schema.key_exchange_statuses.status_date));
                Bitmap other = ContactUtils.getBitmap(contentResolver, number);

                results.add(new KeyRequest(name, status, date, other));

            } while (statuses.moveToNext());
        }
        statuses.close();

        return results;
    }

    public byte[] loadKeyBytes(String address, Cryptor.KeyTypes type) throws InvalidKeyTypeException
    {
        SQLiteDatabase db = manager.getReadableDatabase();

        String column;
        if(Cryptor.KeyTypes.PRIVATE.equals(type)) {
            column = Schema.contact_keys.private_key;
        } else if(Cryptor.KeyTypes.PUBLIC.equals(type)) {
            column = Schema.contact_keys.public_key;
        } else if(Cryptor.KeyTypes.SECRET.equals(type)) {
            column = Schema.contact_keys.secret_key;
        } else {
            throw new InvalidKeyTypeException();
        }


        Cursor c = db.rawQuery("select " + column + " from " +
                Schema.contact_keys.class.getSimpleName() + " where " + Schema.contact_keys.phone_number + " = ?", new String[] { address });

        byte[] keyBytes = null;

        if(c.moveToFirst()) {
            keyBytes = c.getBlob(c.getColumnIndex(column));
        }
        c.close();

        return keyBytes;
    }

    public long storeKeyBytes(String address, byte[] keyBytes, Cryptor.KeyTypes type) throws InvalidKeyTypeException
    {
        SQLiteDatabase db = manager.getWritableDatabase();

        Cursor c = db.rawQuery("select count(*) from " + Schema.contact_keys.class.getSimpleName() + " where " + Schema.contact_keys.phone_number + " = ?", new String[]{address});

        int exists = 0;
        if(c.moveToFirst())
            exists = c.getInt(0);
        c.close();

        ContentValues values = new ContentValues();

        if(Cryptor.KeyTypes.SECRET.equals(type))
            values.put(Schema.contact_keys.secret_key, keyBytes);
        else if(Cryptor.KeyTypes.PUBLIC.equals(type))
            values.put(Schema.contact_keys.public_key, keyBytes);
        else if(Cryptor.KeyTypes.PRIVATE.equals(type))
            values.put(Schema.contact_keys.private_key, keyBytes);
        else
            throw new InvalidKeyTypeException();

        if(exists == 0) {
            values.put(Schema.contact_keys.phone_number, address);
            return db.insert(Schema.contact_keys.class.getSimpleName(), "null", values);
        }
        else {
            String whereClause = Schema.contact_keys.phone_number + " = ?";
            String[] whereArg = new String[] { address };
            return db.update(Schema.contact_keys.class.getSimpleName(), values, whereClause, whereArg);
        }
    }

    public void deleteKey(String address, Cryptor.KeyTypes type) throws InvalidKeyTypeException
    {
        SQLiteDatabase db = manager.getWritableDatabase();

        ContentValues values = new ContentValues();
        if(Cryptor.KeyTypes.SECRET.equals(type))
            values.putNull(Schema.contact_keys.secret_key);
        else if(Cryptor.KeyTypes.PUBLIC.equals(type))
            values.putNull(Schema.contact_keys.public_key);
        else if(Cryptor.KeyTypes.PRIVATE.equals(type))
            values.putNull(Schema.contact_keys.private_key);

        String whereClause = Schema.contact_keys.phone_number + " = ?";
        String[] whereArg = new String[] { address };
        db.update(Schema.contact_keys.class.getSimpleName(), values, whereClause, whereArg);
    }

}
