package edu.buffalo.cse.cse486586.groupmessenger2;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

/**
 * GroupMessengerProvider is a key-value table. Once again, please note that we do not implement
 * full support for SQL as a usual ContentProvider does. We re-purpose ContentProvider's interface
 * to use it as a key-value table.
 * 
 * Please read:
 * 
 * http://developer.android.com/guide/topics/providers/content-providers.html
 * http://developer.android.com/reference/android/content/ContentProvider.html
 * 
 * before you start to get yourself familiarized with ContentProvider.
 * 
 * There are two methods you need to implement---insert() and query(). Others are optional and
 * will not be tested.
 * 
 * @author stevko
 *
 */
public class GroupMessengerProvider extends ContentProvider {

    private static final String TAG = GroupMessengerProvider.class.getName();


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // You do not need to implement this.
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        /*
         * TODO: You need to implement this method. Note that values will have two columns (a key
         * column and a value column) and one row that contains the actual (key, value) pair to be
         * inserted.
         * 
         * For actual storage, you can use any option. If you know how to use SQL, then you can use
         * SQLite. But this is not a requirement. You can use other storage options, such as the
         * internal storage option that we used in PA1. If you want to use that option, please
         * take a look at the code for PA1.
         */

        String key = values.get("key").toString();
        String val = values.get("value").toString();


        Context context = getContext();

        try {

            // Creating a File which can only be accessed by the calling application.
            FileOutputStream fileOutputStream = context.openFileOutput(key,Context.MODE_PRIVATE);
            Log.i(TAG, "Post FileOutputStream");

            Log.i(TAG, "Value : " + val.getBytes());
            // Writing byte from a byte array to output stream.
            fileOutputStream.write(val.getBytes());

            // Closing the file output stream.
            fileOutputStream.close();
            Log.i(TAG, "FileOutputStream Closed");

        }
        catch (Exception e){
            e.printStackTrace();
        }

        Log.v("insert", values.toString());
        return uri;
    }

    @Override
    public boolean onCreate() {
        // If you need to perform any one-time initialization task, please do it here.
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // You do not need to implement this.
        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        /*
         * TODO: You need to implement this method. Note that you need to return a Cursor object
         * with the right format. If the formatting is not correct, then it is not going to work.
         *
         * If you use SQLite, whatever is returned from SQLite is a Cursor object. However, you
         * still need to be careful because the formatting might still be incorrect.
         *
         * If you use a file storage option, then it is your job to build a Cursor * object. I
         * recommend building a MatrixCursor described at:
         * http://developer.android.com/reference/android/database/MatrixCursor.html
         */

        String message = "";

        // A mutable cursor created to add rows.
        String[] keyValuePair = {"key", "value"};

        // Building a row of values using KeyValue Pair
        MatrixCursor myCursor = new MatrixCursor(keyValuePair);

        // Retrieving the dialogs context.
        Context context = getContext();

        try {

            // Opening a private file with context associated with the applications context and reading the bytes into characters.
            FileInputStream fileInputStream = context.openFileInput(selection);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            Log.i(TAG, "Opened a connection to the file and reading it.");

            message = bufferedReader.readLine();

            Log.i(TAG, "Provider/Message : " + message);

            if (message != null){
                //Log.i(TAG,"Adding a row to the cursor");
                String[] record  = {selection,message};

                Log.i(TAG,"Row : " + record.toString());

                // Adding the value pair to the end of the matrix.
                myCursor.addRow(record);
            }

            // Closing Buffered Reader
            bufferedReader.close();
            fileInputStream.close();
            return myCursor;

        }
        catch(Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Exception Raised : " + e.getMessage());
        }

        Log.v("query", selection);
        return null;
    }
}
