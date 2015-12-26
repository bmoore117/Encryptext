package bmoore.encryptext.utils;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import bmoore.encryptext.R;
import bmoore.encryptext.db.DBManager;
import bmoore.encryptext.db.Schema;
import bmoore.encryptext.db.Schema.contact_keys;
import bmoore.encryptext.db.Schema.conversations;
import bmoore.encryptext.db.Schema.key_exchange_statuses;
import bmoore.encryptext.db.Schema.last_encrypted_blocks;
import bmoore.encryptext.model.Contact;
import bmoore.encryptext.model.ConversationEntry;
import bmoore.encryptext.model.KeyRequest;

/**
 * Created by Benjamin Moore on 11/24/2015.
 */
public class DBUtils {

    private DBManager manager;
    private ContentResolver contentResolver;
    private Context context;

    public DBUtils(Context context) {
        this.context = context;
        manager = new DBManager(context);
        contentResolver = context.getContentResolver();
    }

    public List<ConversationEntry> loadConversation(String number, long startFromMessageId) {
        SQLiteDatabase db = manager.getReadableDatabase();

        boolean useDrawableMe = false, useDrawableOther = false;

        Bitmap me = null, other = null;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            me = ContactUtils.getBitmap(contentResolver, null);
            other = ContactUtils.getBitmap(contentResolver, number);
        }

        if (me == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            useDrawableMe = true;
        } else if (me == null) {
            me = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_account_box_gray_48dp);
        }

        if (other == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            useDrawableOther = true;
        } else if (other == null) {
            if (me != null) {
                other = me;
            } else {
                other = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_account_box_gray_48dp);
            }
        }

        Cursor c = db.rawQuery("select * from conversations c where c.message_id > ? order by c.message_id asc;", new String[]{String.valueOf(startFromMessageId)});

        ArrayList<ConversationEntry> results = new ArrayList<>();

        if (c.moveToFirst()) {
            do {
                long messageId = c.getLong(c.getColumnIndex(conversations.message_id));
                String message = c.getString(c.getColumnIndex(conversations.message));
                String name = c.getString(c.getColumnIndex(conversations.name));
                String date = c.getString(c.getColumnIndex(conversations.status_date));
                ConversationEntry item = new ConversationEntry(messageId, message, name, date, null);

                if ("Me".equals(name)) {
                    if (useDrawableMe) {
                        item.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
                    } else {
                        item.setPhoto(me);
                    }
                } else {
                    if (useDrawableOther) {
                        item.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
                    } else {
                        item.setPhoto(other);
                    }
                }

                results.add(item);
            } while (c.moveToNext());
        }
        c.close();

        return results;
    }

    public void storeEncryptedBlock(String address, byte[] block) {
        SQLiteDatabase db = manager.getWritableDatabase();

        Cursor c = db.rawQuery("select count(*) from " + last_encrypted_blocks.class.getSimpleName() + " where " + last_encrypted_blocks.phone_number + " = ?", new String[]{address});

        int exists = 0;
        if (c.moveToFirst())
            exists = c.getInt(0);
        c.close();

        ContentValues values = new ContentValues();
        values.put(last_encrypted_blocks.encrypted_block, block);
        values.put(last_encrypted_blocks.phone_number, address);

        //db.insertWithOnConflict(last_encrypted_blocks.class.getSimpleName(), null, values, SQLiteDatabase.CONFLICT_REPLACE);

        if (exists == 0) {
            values.put(last_encrypted_blocks.phone_number, address);
            db.insert(last_encrypted_blocks.class.getSimpleName(), "null", values);
        } else {
            String whereClause = last_encrypted_blocks.phone_number + " = ?";
            String[] whereArg = new String[]{address};
            db.update(last_encrypted_blocks.class.getSimpleName(), values, whereClause, whereArg);
        }
    }

    public byte[] loadEncryptedBlock(String address) {
        SQLiteDatabase db = manager.getReadableDatabase();

        Cursor c = db.rawQuery("select " + last_encrypted_blocks.encrypted_block + " from "
                + last_encrypted_blocks.class.getSimpleName() + " where " + last_encrypted_blocks.phone_number + " = ?", new String[]{address});

        byte[] key = null;
        if (c.moveToFirst()) {
            key = c.getBlob(c.getColumnIndex(last_encrypted_blocks.encrypted_block));
        }
        c.close();

        return key;
    }

    public long storeMessage(ConversationEntry item) {
        SQLiteDatabase db = manager.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(conversations.phone_number, item.getNumber());
        values.put(conversations.name, item.getName());
        values.put(conversations.status_date, item.getDate());
        values.put(conversations.message, item.getMessage());

        //should return primary key of new row
        return db.insert(conversations.class.getSimpleName(), "null", values);
    }

    public void confirmMessageSent(String time, long messageId) {
        SQLiteDatabase db = manager.getWritableDatabase();

        SQLiteStatement statement = db.compileStatement("update conversations set status_date = ? where message_id = ?");
        statement.bindString(1, time);
        statement.bindLong(2, messageId);

        statement.executeUpdateDelete();
    }

    public List<ConversationEntry> readPreviews() {

        SQLiteDatabase db = manager.getReadableDatabase();
        boolean useDrawable = false;

        Cursor conversations = db.rawQuery("select distinct phone_number from conversations", null);

        ArrayList<ConversationEntry> results = new ArrayList<>();

        if (conversations.moveToFirst()) {
            do {

                String number = conversations.getString(conversations.getColumnIndex(Schema.conversations.phone_number));

                Bitmap other = null;
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    other = ContactUtils.getBitmap(contentResolver, number);
                }

                if (other == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    useDrawable = true;
                } else if (other == null) {
                    other = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_account_box_gray_48dp);
                }

                Cursor previews = db.rawQuery("select * from conversations where message_id in (select max(message_id) from conversations where phone_number = ?)",
                        new String[]{number});

                if (previews.moveToFirst()) {
                    do {
                        String message = previews.getString(previews.getColumnIndex(Schema.conversations.message));
                        String name = previews.getString(previews.getColumnIndex(Schema.conversations.name));
                        String date = previews.getString(previews.getColumnIndex(Schema.conversations.status_date));

                        ConversationEntry item = new ConversationEntry(message, number, name, date, other);
                        if (useDrawable) {
                            item.setImageResourceId(R.drawable.ic_account_box_gray_48dp);
                        }

                        results.add(item);
                    } while (previews.moveToNext());
                }
                previews.close();
            } while (conversations.moveToNext());
        }
        conversations.close();

        return results;
    }

    public long generateKeyRequestEntry(String address, String name, Contact.KeyStatus status, String date) {
        SQLiteDatabase db = manager.getReadableDatabase();

        ContentValues values = new ContentValues();
        values.put(key_exchange_statuses.phone_number, address);
        values.put(key_exchange_statuses.name, name);
        values.put(key_exchange_statuses.status, status.toString());
        values.put(key_exchange_statuses.status_date, date);

        //should return primary key of new row
        return db.insert(key_exchange_statuses.class.getSimpleName(), "null", values);
    }

    public void deleteKeyRequestEntry(String address) {
        SQLiteDatabase db = manager.getWritableDatabase();

        String where = key_exchange_statuses.phone_number + " = ?";
        String[] whereArgs = new String[]{address};

        db.delete(key_exchange_statuses.class.getSimpleName(), where, whereArgs);
    }

    public List<KeyRequest> loadKeyRequests() {
        SQLiteDatabase db = manager.getReadableDatabase();

        Cursor statuses = db.rawQuery("select * from " + key_exchange_statuses.class.getSimpleName(), null);

        ArrayList<KeyRequest> results = new ArrayList<>();

        if (statuses.moveToFirst()) {
            do {

                String number = statuses.getString(statuses.getColumnIndex(key_exchange_statuses.phone_number));
                String name = statuses.getString(statuses.getColumnIndex(key_exchange_statuses.name));
                String status = statuses.getString(statuses.getColumnIndex(key_exchange_statuses.status));
                String date = statuses.getString(statuses.getColumnIndex(key_exchange_statuses.status_date));

                Bitmap other;
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    other = ContactUtils.getBitmap(contentResolver, number);
                } else {
                    other = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_account_box_gray_48dp);
                }

                results.add(new KeyRequest(name, number, status, date, other));

            } while (statuses.moveToNext());
        }
        statuses.close();

        return results;
    }

    public long getKeyRequestsCount() {
        SQLiteDatabase db = manager.getReadableDatabase();

        SQLiteStatement statement = db.compileStatement("select count(*) from " + key_exchange_statuses.class.getSimpleName());

        return statement.simpleQueryForLong();
    }

    public HashMap<String, byte[]> loadKeysInNegotiation() {
        SQLiteDatabase db = manager.getReadableDatabase();

        Cursor keys = db.rawQuery("select ck.phone_number, ck.public_key from contact_keys ck " +
                "inner join key_exchange_statuses kes on ck.phone_number = " +
                "kes.phone_number where kes.status = ?", new String[]{Contact.KeyStatus.NEEDS_REVIEW.toString()});

        HashMap<String, byte[]> results = new HashMap<>();

        if (keys.moveToFirst()) {
            do {
                String phoneNumber = keys.getString(keys.getColumnIndex(contact_keys.phone_number));
                byte[] key = keys.getBlob(keys.getColumnIndex(contact_keys.public_key));

                results.put(phoneNumber, key);
            } while (keys.moveToNext());
        }
        keys.close();

        return results;
    }


    public byte[] loadKeyBytes(String address, Cryptor.KeyTypes type) throws InvalidKeyTypeException {
        SQLiteDatabase db = manager.getReadableDatabase();

        String column;
        if (Cryptor.KeyTypes.PRIVATE.equals(type)) {
            column = contact_keys.private_key;
        } else if (Cryptor.KeyTypes.PUBLIC.equals(type)) {
            column = contact_keys.public_key;
        } else if (Cryptor.KeyTypes.SECRET.equals(type)) {
            column = contact_keys.secret_key;
        } else {
            throw new InvalidKeyTypeException();
        }


        Cursor c = db.rawQuery("select " + column + " from " +
                contact_keys.class.getSimpleName() + " where " + contact_keys.phone_number + " = ?", new String[]{address});

        byte[] keyBytes = null;

        if (c.moveToFirst()) {
            keyBytes = c.getBlob(c.getColumnIndex(column));
        }
        c.close();

        return keyBytes;
    }

    public long storeKeyBytes(String address, byte[] keyBytes, Cryptor.KeyTypes type) throws InvalidKeyTypeException {
        SQLiteDatabase db = manager.getWritableDatabase();

        Cursor c = db.rawQuery("select count(*) from " + contact_keys.class.getSimpleName() + " where " + contact_keys.phone_number + " = ?", new String[]{address});

        int exists = 0;
        if (c.moveToFirst())
            exists = c.getInt(0);
        c.close();

        ContentValues values = new ContentValues();

        if (Cryptor.KeyTypes.SECRET.equals(type))
            values.put(contact_keys.secret_key, keyBytes);
        else if (Cryptor.KeyTypes.PUBLIC.equals(type))
            values.put(contact_keys.public_key, keyBytes);
        else if (Cryptor.KeyTypes.PRIVATE.equals(type))
            values.put(contact_keys.private_key, keyBytes);
        else
            throw new InvalidKeyTypeException();

        if (exists == 0) {
            values.put(contact_keys.phone_number, address);
            return db.insert(contact_keys.class.getSimpleName(), "null", values);
        } else {
            String whereClause = contact_keys.phone_number + " = ?";
            String[] whereArg = new String[]{address};
            return db.update(contact_keys.class.getSimpleName(), values, whereClause, whereArg);
        }
    }

    public void deleteKey(String address, Cryptor.KeyTypes type) throws InvalidKeyTypeException {
        SQLiteDatabase db = manager.getWritableDatabase();

        ContentValues values = new ContentValues();
        if (Cryptor.KeyTypes.SECRET.equals(type))
            values.putNull(contact_keys.secret_key);
        else if (Cryptor.KeyTypes.PUBLIC.equals(type))
            values.putNull(contact_keys.public_key);
        else if (Cryptor.KeyTypes.PRIVATE.equals(type))
            values.putNull(contact_keys.private_key);

        String whereClause = contact_keys.phone_number + " = ?";
        String[] whereArg = new String[]{address};
        db.update(contact_keys.class.getSimpleName(), values, whereClause, whereArg);
    }

    public boolean checkKeyExists(String number, Cryptor.KeyTypes type) {
        SQLiteDatabase db = manager.getReadableDatabase();

        String typeString;
        if (Cryptor.KeyTypes.SECRET.equals(type))
            typeString = contact_keys.secret_key;
        else if (Cryptor.KeyTypes.PUBLIC.equals(type))
            typeString = contact_keys.public_key;
        else //(Cryptor.KeyTypes.PRIVATE.equals(type))
            typeString = contact_keys.private_key;


        Cursor c = db.rawQuery("select 1 from " + contact_keys.class.getSimpleName()
                + " where " + contact_keys.phone_number + " = ? and " + typeString + " is not null", new String[]{number});

        if (c.moveToFirst()) {
            c.close();
            return true;
        } else {
            c.close();
            return false;
        }
    }

}
