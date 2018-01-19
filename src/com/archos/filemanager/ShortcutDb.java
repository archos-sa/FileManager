package com.archos.filemanager;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.provider.BaseColumns;
import android.support.v4.content.CursorLoader;
import android.util.Log;

public enum ShortcutDb {

    STATIC();

    private static final String TAG = "ShortcutDb";
    protected final static boolean DBG = true;

    private static final String DATABASE_NAME = "shortcuts2_db";
    private static final String TABLE_NAME = "shortcuts";

    // To be incremented each time the architecture of the database is changed
    private static final int DATABASE_VERSION = 1;

    public static final String KEY_URI = "uri";
    public static final String KEY_SHORTCUT_NAME = "shortcut_name";
    private static final String[] SHORTCUT_COLS = { BaseColumns._ID, KEY_URI, KEY_SHORTCUT_NAME };

    private DatabaseHelper mDbHelper;

    private SQLiteDatabase mDb;

    /**
     * get Cursor with all columns of all shortcuts
     * @param context
     * @return
     */
    private Cursor getAllShortcuts(Context context) {
        //mContext = context;
        mDbHelper = new DatabaseHelper(context);
        mDb = mDbHelper.getWritableDatabase();

        try {
            return mDb.query(TABLE_NAME,
                    SHORTCUT_COLS,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        catch (SQLiteException e) {
            // The table corresponding to this type does not exist yet
            Log.w(TAG, e);
            return null;
        }
    }

    /**
     * get a CursorLoader to get all columns of all shortcuts
     * @param context
     * @return
     */
    public CursorLoader getAllShortcutsCursorLoader(Context context) {
        return new CursorLoader(context) {
            @Override
            public Cursor loadInBackground() {
                return getAllShortcuts(getContext());
            }
        };
    }

    /**
     * Get the shortcut name (can be different than Uri if it has been renamed) from it's id
     * @param id
     * @return
     */
    public String getShortcutName(long id) {
        Cursor c;
        try {
            c = mDb.query(TABLE_NAME,
                    SHORTCUT_COLS,
                    "_id="+id,
                    null, null, null, null);
        }
        catch (SQLiteException e) {
            // The table corresponding to this type does not exist yet
            Log.w(TAG, e);
            return "";
        }
        c.moveToFirst();
        String name = c.getString(c.getColumnIndexOrThrow(KEY_SHORTCUT_NAME));
        c.close();
        return name;
    }

    /**
     * Insert a new shortcut into the database
     * @param uri: string obtained with android.net.Uri.toString()
     * @param name
     * @return true if the insert succeeded
     */
    public boolean insertShortcut(Uri uri, String name) {
        Log.d(TAG, "insertShortcut " + uri + " " + name);
        ContentValues initialValues = new ContentValues(2);
        initialValues.put(KEY_URI, uri.toString());
        initialValues.put(KEY_SHORTCUT_NAME, name);

        return (mDb.insert(TABLE_NAME, null, initialValues)!=-1);
    }

    /**
     * Remove the shortcut(s) corresponding to the provided Uri
     * 
     * @param uri 
     * @return the number of shortcuts removed
     */
    public int removeShortcut(Uri uri) {
        final String selection = KEY_URI+"=?";
        final String[] selectionArgs = new String[] {uri.toString()};

        int result = mDb.delete(TABLE_NAME, selection, selectionArgs);
        Log.d(TAG, "removeShortcut " + uri.toString() + " mDb.delete returns " + result);

        return result;
    }

    /**
     * Remove the shortcut(s) corresponding to the provided DB id
     * @return true if it erased something
     */
    public boolean removeShortcut(long id) {
        final String selection = BaseColumns._ID+"=?";
        final String[] selectionArgs = new String[] {Long.toString(id)};

        int result = mDb.delete(TABLE_NAME, selection, selectionArgs);
        Log.d(TAG, "removeShortcut "+id+" mDb.delete returns "+result);

        return result>0;
    }

    /**
     * Rename a shortcut. The Uri is not changed (of course)
     * @param id
     * @param newName
     * @return
     */
    public boolean renameShortcut(long id, String newName) {
        ContentValues cv = new ContentValues();
        cv.put(KEY_SHORTCUT_NAME, newName);
        int nb = mDb.update(TABLE_NAME, cv, "_id="+id,null);
        return nb==1;
    }

    /**
     * @param uri
     * @return true if there is at least one shortcut for this Uri
     */
    public boolean containShortcut(Uri uri) {

        final String selection = KEY_URI+"=?";
        final String[] selectionArgs = new String[] {uri.toString()};

        Cursor c = mDb. query(
                TABLE_NAME,     // The table to query
                SHORTCUT_COLS,  // The columns to return from the query
                selection,      // selection
                selectionArgs,  // selectionArgs
                null,           // groupBy
                null,           // having
                null            // sort
                );
        boolean result = c.getCount()>0;
        c.close();
        return result;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // This method is only called once when the database is created for the first time
            db.execSQL("create table " + TABLE_NAME + "( "
                    + BaseColumns._ID + " integer primary key autoincrement, "
                    + KEY_URI + " text not null, "
                    + KEY_SHORTCUT_NAME + " text not null );");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {            
        }
    }
}
