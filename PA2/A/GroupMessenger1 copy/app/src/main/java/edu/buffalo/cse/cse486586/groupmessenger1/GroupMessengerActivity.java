package edu.buffalo.cse.cse486586.groupmessenger1;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    static final String TAG = GroupMessengerActivity.class.getName();

    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORT = { "11108", "11112", "11116", "11120", "11124" };

    static int counter = 0;

    String authority = "edu.buffalo.cse.cse486586.groupmessenger1.provider";
    String scheme = "content";
    private final Uri providerUri = buildUri(scheme, authority);

    final ContentValues contentValues = new ContentValues();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);



        /*
         * TODO: Use the TextView to display your messages. Though there is no grading
         * component on how you display the messages, if you implement it, it'll make
         * your debugging easier.
         */

        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the
         * "PTest" button. OnPTestClickListener demonstrates how to access a
         * ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(new OnPTestClickListener(tv, getContentResolver()));

        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        Log.i(TAG, "Telephone Manager : " + tel);
        Log.i(TAG, "Line1 Number : " + tel.getLine1Number());
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        Log.i(TAG, "Port Str : " + portStr);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.i(TAG, "My Port :" + myPort);


        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Exception Raised");
            return;
        }


        /*
         * TODO: You need to register and implement an OnClickListener for the "Send"
         * button. In your implementation you need to get the message from the input box
         * (EditText) and send it to other AVDs.
         */


        final EditText editText = (EditText) findViewById(R.id.editText1);

        final Button send = (Button) findViewById(R.id.button4);


        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /*
                 * If the key is pressed (i.e., KeyEvent.ACTION_DOWN) and it is an enter key
                 * (i.e., KeyEvent.KEYCODE_ENTER), then we display the string. Then we create an
                 * AsyncTask that sends the string to the remote AVD.
                 */
                String msg = editText.getText().toString() + "\n";
                editText.setText(""); // This is one way to reset the input box.
                TextView tv = (TextView) findViewById(R.id.textView1);
                tv.setMovementMethod(new ScrollingMovementMethod());
                tv.append("\t" + msg + "\n"); // This is one way to display a string.
                /*
                 * Note that the following AsyncTask uses AsyncTask.SERIAL_EXECUTOR, not
                 * AsyncTask.THREAD_POOL_EXECUTOR as the above ServerTask does. To understand
                 * the difference, please take a look at
                 * http://developer.android.com/reference/android/os/AsyncTask.html
                 */
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort);

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            /*
             * TODO: Fill in your server code that receives messages and passes them to
             * onProgressUpdate().
             */

            try {

                String message;

                while (true) {

                    /// Create Socket
                    Log.i(TAG, "Socket Establishing");
                    Socket serverSock = serverSocket.accept();
                    Log.i(TAG, "Connection Accepted");

                    /// Reads bytes from the server socket and decodes them to characters
                    InputStreamReader inputStream = new InputStreamReader(serverSock.getInputStream());

                    /// Reading text from the Character Stream (Instance of InputStreamReader).
                    BufferedReader reader = new BufferedReader(inputStream);

                    /// Message
                    message = reader.readLine();

                    /*
                     * If the Message is not null, Publish the message to the Main Activity while
                     * the Background Computations are still Running
                     */
                    if (message != null) {
                        /// publishing Message
                        Log.i(TAG, "Message : " + message);
                        publishProgress(message);
                    }

                    serverSock.close();

                }
            } catch (IOException exp) {
                Log.i(TAG, "Exception in Server Task");
                exp.printStackTrace();
            }
            return null;
        }

        protected void onProgressUpdate(String... strings) {
            /*
             * The following code displays what is received in doInBackground().
             */

            String message = strings[0].trim();
            final TextView tv = (TextView) findViewById(R.id.textView1);
            tv.setText(message + "\n");

            try {

                contentValues.put("key", Integer.toString(counter));
                contentValues.put("value", message);
                counter++;

                getContentResolver().insert(providerUri, contentValues);

            } catch (Exception e) {
                Log.e(TAG, "OnProgressUpdate :: Exception raised :" + e);
            }

        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {
            try {

                for (String remotePort : REMOTE_PORT) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }),
                            Integer.parseInt(remotePort));
                    String msgToSend = msgs[0];

                    PrintWriter pw = null;

                    if (msgToSend != null || !msgToSend.equals("")) {
                        /*
                         * TODO: Fill in your client code that sends out a message.
                         */

                        /*
                         * Printing an Output Stream of bytes to text-output stream through the
                         * specified socket.
                         */

                        Log.i(TAG, "Message : " + msgToSend);
                        pw = new PrintWriter(socket.getOutputStream(), true);
                        pw.println(msgToSend);

                        /*
                         * Closing PrintWriter Stream and release any system resources associated along
                         * with it. Closing the Socket and discard / stop data waiting to be sent.
                         */

                        Thread.sleep(10);
                    }

                    pw.close();
                    socket.close();
                }

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted Exception raised" + e);
            }

            return null;
        }
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

}
