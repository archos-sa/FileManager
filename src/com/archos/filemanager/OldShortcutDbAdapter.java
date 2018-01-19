package com.archos.filemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;


public class OldShortcutDbAdapter {
    private static final String TAG = "ShortcutDbAdapter";

    // To be incremented each time the architecture of the database is changed
    private static final int DATABASE_VERSION = 1;

    public static final String KEY_ROWID = "_id";
    public static final String KEY_PATH = "path";
    
    static final String DATABASE_NAME = "shortcuts_db";
    private static final String DATABASE_TABLE = "shortcuts_table";

    private static final String DATABASE_CREATE =
        "create table shortcuts_table (_id integer primary key autoincrement, " + "path text not null);";

    private String[] SHORTCUT_COLS = new String[] { KEY_ROWID, KEY_PATH };

    private final DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;


    public OldShortcutDbAdapter(Context context) {
        mDbHelper = new DatabaseHelper(context);
    }

    /*
     * Open the shortcut database
     */
    public void open() throws SQLException {
        mDb = mDbHelper.getWritableDatabase();
    }

    /*
     * Close the shortcut database
     */
    public void close() {
        if (mDb != null) {
            mDb.close();
        }
        if (mDbHelper != null) {
            mDbHelper.close();
        }
    }
    
    /*
     * Insert a new shortcut into the database
     */
    public long insertShortcut(String path) {
        ContentValues initialValues = new ContentValues();
        initialValues.put(KEY_PATH, path);
        return mDb.insert(DATABASE_TABLE, null, initialValues);
    }

    /*
     * Delete the shortcut corresponding to the provided row id
     */
    public boolean deleteShortcut(long rowId) {
        // Delete the shortcut stored in the provided row
        return mDb.delete(DATABASE_TABLE, KEY_ROWID + "=" + rowId, null) > 0;
    }

    /*
     * Retrieve all the shortcuts stored in the table
     */
    public Cursor getAllShortcuts() {
        try {
            return mDb.query(DATABASE_TABLE,
                            SHORTCUT_COLS, 
                            null, 
                            null, 
                            null, 
                            null, 
                            null);
        }
        catch (SQLiteException e) {
            // The table corresponding to this type does not exist yet => create it
            mDb.execSQL(DATABASE_CREATE);
            return null;
        }
    }

    /*
     * Retrieve the shortcut corresponding to the provided row id
     */
    public Cursor getShortcut(long rowId) throws SQLException {
        Cursor cursor = mDb.query(true,
                                  DATABASE_TABLE,
                                  SHORTCUT_COLS, 
                                  KEY_ROWID + "=" + rowId, 
                                  null,
                                  null, 
                                  null, 
                                  null, 
                                  null);
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;
    }

    /*
     * Update the path of the shortcut corresponding to the provided row id
     */
    public boolean updateShortcut(long rowId, String path) {
        ContentValues args = new ContentValues();
        args.put(KEY_PATH, path);
        return mDb.update(DATABASE_TABLE, args, KEY_ROWID + "=" + rowId, null) > 0;
    }

    private static class DatabaseHelper extends SQLiteOpenHelper {
        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // This method is only called once when the database is created for the first time
            db.execSQL(DATABASE_CREATE);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d(TAG, "Upgrading database from version " + oldVersion + " to " + newVersion);
        }
    }    
}
