package bmoore.encryptext.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DBManager extends SQLiteOpenHelper {

    public DBManager(Context context) {
        super(context, Schema.DATABASE_NAME, null, Schema.DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(Schema.key_exchange_statuses.DDL);
        db.execSQL(Schema.contact_keys.DDL);
        db.execSQL(Schema.conversations.DDL);
        db.execSQL(Schema.last_encrypted_blocks.DDL);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
