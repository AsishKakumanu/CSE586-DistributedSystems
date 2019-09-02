package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.Buffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

public class SimpleDhtProvider extends ContentProvider {

    /* TAG for Logging */
    static final String TAG = SimpleDhtProvider.class.getName();

    /* PORTS */
    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORTS = new String[] {
            "11108",
            "11112",
            "11116",
            "11120",
            "11124"
    };

    /* URI Builder */
    String authority = "edu.buffalo.cse.cse486586.simpledht.provider";
    String scheme = "content";
    uriBuilder uB  = new uriBuilder();
    private final Uri providerUri = uB.buildUri(scheme,authority);

    /* Nodes */
    ArrayList<Node> nodeArrayList = new ArrayList<Node>();
    ArrayList<String> nodeList = new ArrayList<String>();
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(100000);
    HashMap<String, String>  hMap = new HashMap<String, String>();
    public String masterNode = "5554";
    public String successor = "";
    public String predecessor = "";

    /* Files */
    String nullResult = null;
    ArrayList<String> files = new ArrayList<String>();

    /* Messages */
    String insert = "INSERT";
    String delete = "DELETE";
    String query = "QUERY";
    String delimiter = "#";
    String message_type_JOIN = "NEW_NODE";
    String message_type_update = "UPDATE";

    /* Delete */
    boolean GDUMP = false;
    boolean LDUMP = false;
    boolean individual = false;
    HashMap<String, String> keysToDelete = new HashMap<String, String>();

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    public boolean checkCondition(String key){
        boolean check = false;
        try {
            if(loneNodeCondition() ||
                    (genHash(getAvdNumber(getPort())).compareTo(genHash(predecessor))<0 && (genHash(key).compareTo(genHash(predecessor))>0 || genHash(key).compareTo(genHash(getAvdNumber(getPort())))<0))){
                Log.i(TAG, "Inserting / key :" + key  );
                Log.i(TAG,"Before setting check to true #" + check);
                check = true;
                Log.i(TAG,"After setting check to true #" + check);
            }

            else if(loneNodeCondition() || (genHash(getAvdNumber(getPort())).compareTo(genHash(predecessor))>0 && (genHash(key).compareTo(genHash(getAvdNumber(getPort())))<0 && genHash(key).compareTo(genHash(predecessor))>0))){
                Log.i(TAG, "Inserting / key :" + key );
                Log.i(TAG,"2 Before setting check to true #" + check);
                check = true;
                Log.i(TAG,"2 After setting check to true #" + check);

            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return check;
    }


    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub


        String key = values.get("key").toString();
        String value = values.get("value").toString();

        Log.i(TAG,"Inside INSERT PREDECESSOR:" + predecessor);
        Log.i(TAG,"Inside INSERT SUCCESSOR:" + successor);
        Log.i(TAG,"Booleans in insert : " + GDUMP + "#" + LDUMP + "#" + individual);

        try {
            String hashKey = genHash(key);
            checkCondition(key);
            Log.i(TAG,"Check Condition " + checkCondition(key));
            if(checkCondition(key) == true){
                Log.i(TAG,"Insert Method inside if");
                PrintWriter printWriter = new PrintWriter(getContext().openFileOutput(key,Context.MODE_PRIVATE));
                printWriter.println(value);
                printWriter.close();
                queue.put(key);
                hMap.put(key, value);
                Log.i(TAG,"Queue list : " + queue.toString());
                Log.i(TAG,"HMAP List :" + "PORT :" +  getPort().toString() + "#" + hMap.toString());
            }
            else {
                String insertMessage = insert + "#" +key + "#" +value + "#" + successor;
                ClientTask clientTask = new ClientTask();
                clientTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,insertMessage,String.valueOf(Integer.parseInt(successor)*2));
                Log.i(TAG,"Insert Method inside else, sending message to successor null " + successor.toString() + "#" + insertMessage);
                if(successor !=null){
                    Log.i(TAG,"Insert Method inside else not null, sending message to successor " + successor.toString() + "#" + insertMessage);

                }
            }


        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG,"insert / No Such Algorithm Exception " + e);
        } catch (FileNotFoundException e){
            Log.e(TAG,"insert / File Not Found Exception " + e);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return null;
    }

    private boolean loneNodeCondition() {
        return successor.equals("") || successor.equals(null);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if(selection.contains("*")){
            GDUMP = true;
            Log.i(TAG,"GDUMP boolean : " + GDUMP);

        }
        else if(selection.contains("@")){
            LDUMP = true;
            Log.i(TAG,"LDUMP boolean : " + LDUMP);

        }
        else {
            individual = true;
            Log.i(TAG,"Individual boolean : " + individual);
            keysToDelete.put(selection,selection);
            Log.i(TAG,"keys to delete are " + keysToDelete.toString());
        }



        return 0;
    }


    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        // TODO Auto-generated method stub


        String message = "";

        // A mutable cursor created to add rows.
        String[] keyValuePair = {"key", "value"};

        // Building a row of values using KeyValue Pair
        MatrixCursor myCursor = new MatrixCursor(keyValuePair);

        // Retrieving the dialogs context.
        Context context = getContext();

        String[] listOfFiles = context.fileList();

        try {

            if (selection.contains("@") || selection.contains("*")) {
                Log.i(TAG, "enetered *  with slection " + selection);
                // Opening a private file with context associated with the applications context and reading the bytes into characters.
                if(!successor.equalsIgnoreCase("")  && selection.contains("*")){
                    Socket successorSocket = getSocket(String.valueOf(Integer.parseInt(successor) * 2));
                    PrintWriter printWriter = new PrintWriter(successorSocket.getOutputStream(),true);
                    String message1 = "GDUMP#" + Integer.parseInt(getPort())/2;
                    printWriter.println( message1);
                    printWriter.flush();
//                    printWriter.close();

                    InputStreamReader inputStreamReader = new InputStreamReader(successorSocket.getInputStream());
                    BufferedReader bufferedReader =new BufferedReader(inputStreamReader);
                    String AckMessageGDUMP= null;
                    while (true){
                        AckMessageGDUMP= bufferedReader.readLine();
                        if (AckMessageGDUMP != null){
                            break;
                        }
                    }
                    Log.i(TAG, " AckMessageGDUMP in query " + AckMessageGDUMP);

                    try {

                        Iterator<String> iter = hMap.keySet().iterator();
                        while (iter.hasNext()){
                            String key = iter.next();
                            String value = hMap.get(key);
                            if(!keysToDelete.containsKey(key)){
                                myCursor.addRow(new String[]{key, value});
                            }

                        }
                        JSONObject json = new JSONObject(AckMessageGDUMP);

                        Iterator<String> iter1 = json.keys();
                        while (iter1.hasNext()){
                            String key = iter1.next();
                            String value = json.getString(key);
                            if(!keysToDelete.containsKey(key)){
                                myCursor.addRow(new String[]{key, value});
                            }


                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }


                }
                else{
                    for(String file: listOfFiles){

                        FileInputStream fileInputStream = context.openFileInput(file);
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));

                        message = bufferedReader.readLine();

                        Log.i(TAG, "Query/Message : " + message);

                        if (message != null){
                            //Log.i(TAG,"Adding a row to the cursor");
                            String[] record  = {file,message};

                            Log.i(TAG,"Query/ Printing Row : " + record.toString());

                            // Adding the value pair to the end of the matrix.
                            if(!keysToDelete.containsKey(file)){
                                myCursor.addRow(record);
                            }
                        }

                        // Closing Buffered Reader
                        bufferedReader.close();
                        fileInputStream.close();
                    }
                }





            }
            else {
                Log.i(TAG,"Inside Query Else Block");
                try {
                    String searchKeyHash = genHash(selection);
                    if(checkCondition(selection)){
                        String name;
                        FileInputStream fileInputStream = context.openFileInput(selection);
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
                        message = bufferedReader.readLine();
                        Log.i(TAG, "Query/ Else Block / Checking Condition / Message : " + message);

                        if(message != null){
                            String[] record = {selection,message};
                            myCursor.addRow(record);
                        }
                    }
                    else {
                        String queryMessage = query + "#" + successor+ "#KEY_WANTED#" + selection + "#" + getPort().toString();
//                        ClientTask clientTask = new ClientTask();
//                        clientTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,queryMessage,String.valueOf(Integer.parseInt(getAvdNumber(getPort()))*2));


                        String[] queryArgs = message.split("#");
                        String successorNode = String.valueOf(Integer.parseInt(successor)*2);
                        Socket successorSock = getSocket(successorNode);
                        Log.i(TAG,"Query message sent" + queryMessage);
                        PrintWriter printWriter = new PrintWriter(successorSock.getOutputStream(),true);
                        printWriter.println(queryMessage);
                        printWriter.flush();

                        InputStreamReader inputStreamReader = new InputStreamReader(successorSock.getInputStream());
                        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                        String AckMessage = bufferedReader.readLine();
                        Log.i(TAG,"AckMessage inside Query "+ AckMessage);

                        String AckMessageArgs[] = AckMessage.split("#");
                        String key = AckMessageArgs[1];
                        String value = AckMessageArgs[2];
                        Log.i(TAG,"Key and Value in Query Ack :" + key +"#" +value);
                        myCursor.addRow(new String[]{key,value});


                        Log.i(TAG,"Query Method inside else, sending message to successor :" + successor.toString() + "#" + queryMessage);
                    }
                } catch (NoSuchAlgorithmException e) {
                    Log.e(TAG,"Insert: Else Block / No Such Algorithm Exception " + e);
                }

            }
            return myCursor;

        } catch (FileNotFoundException e){
            Log.e(TAG,"Query / FileNotFoundException : " + e);
        } catch (IOException e){
            Log.e(TAG,"Query / IO Exception : " + e);
            e.printStackTrace();
        }


        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        String currentNode = getPort(); // returns 111XX
        String nodeAvdNumber = getAvdNumber(currentNode); // returns 555X
        try {
            if(nodeAvdNumber.equals("5554")){
                Node node = new Node(nodeAvdNumber,genHash(nodeAvdNumber),null,null);
                nodeArrayList.add(node);
                nodeList.add(node.getPortNumber());
                Log.i(TAG,"onCreate / Adding Node to array : " + node.getPortNumber() + "# Array Size : " + nodeArrayList.size());
            }
            else {
                Thread.sleep(1000);
            }


            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            ServerTask serverTask = new ServerTask();
            serverTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,serverSocket);

            if(!nodeAvdNumber.equals("5554")){
                ClientTask clientTask = new ClientTask();
                if(nodeAvdNumber.equals("5556")){
                    onCreateNodeJoinClientTask(nodeAvdNumber, clientTask);
                }
                else if(nodeAvdNumber.equals("5558")){
                    onCreateNodeJoinClientTask(nodeAvdNumber,clientTask);
                }
                else if(nodeAvdNumber.equals("5560")){
                    onCreateNodeJoinClientTask(nodeAvdNumber,clientTask);
                }
                else if(nodeAvdNumber.equals("5562")){
                    onCreateNodeJoinClientTask(nodeAvdNumber,clientTask);
                }
            }

        } catch (Exception e) {
            Log.e(TAG,"onCreate / Exception " + e);
        }



        return false;
    }

    private void onCreateNodeJoinClientTask(String nodeAvdNumber, ClientTask clientTask) {
        String message = message_type_JOIN + "#" + nodeAvdNumber;
        clientTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,message,String.valueOf(Integer.parseInt(masterNode)*2));
        Log.i(TAG,"onCreateNodeJoinClientTask / Message : " + message);
    }

    // Generate Hash for a key.
    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    // To Insert <Key, Value>
    // where Key is the Filename and Value is the Content of the File.
    /* Method used to Insert */
    public void keyIn(String key,String value){
        try{
            FileOutputStream fileOutputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            fileOutputStream.write(value.getBytes());
            fileOutputStream.close();
        }
        catch(FileNotFoundException e){
            Log.e(TAG,"File Not Found Exception Raised : " + e);
        }
        catch(Exception e){
            Log.e(TAG, "Unknown Exception Caught : "+ e);
        }
    }

    /* Method used for Query */
    public String keyOut(String key){
        String message = null;
        try{
            Context context = getContext();
            FileInputStream fileInputStream = context.openFileInput(key);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            message = bufferedReader.readLine();
            bufferedReader.close();
        }
        catch(FileNotFoundException e){
            Log.e(TAG,"File not found exception : " + e);
        }
        catch(IOException e){
            Log.e(TAG,"IOException :" + e);
        }
        catch(Exception e){
            Log.e(TAG,"Exception :"+ e);
        }
        return message;
    }

    // Returns avd number :: 5554
    public String getAvdNumber(String node){
        String port = String.valueOf(Integer.parseInt(node)/2);
        return port;
    }

    // Returns port number :: 11108
    private String getPort() {
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        //Log.i(TAG, "getPort/Telephone Manager : " + tel);
        //Log.i(TAG, "getPort/Line1 Number : " + tel.getLine1Number());
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        //Log.i(TAG, "getPort/Port Str : " + portStr);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        //Log.i(TAG, "getPort/My Port :" + myPort);
        return myPort;
    }

    // Create socket using the passed port.
    private Socket getSocket(String port) throws IOException {
        return new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt(port));
    }


    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @SuppressLint("WrongThread")
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            while(true){
                try {
                    Socket serverSock = serverSocket.accept();
                    Log.i(TAG,"ServerSocket : "+serverSock.isClosed() + "#isConn"
                            + serverSock.isConnected() + "#" + serverSock.toString());
                    InputStreamReader inputStreamReader = new InputStreamReader(serverSock.getInputStream());
                    Log.i(TAG,"Post InputStreamReader");
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String message = bufferedReader.readLine();
                    Log.i(TAG, serverSock.toString());
                    Log.i(TAG, "received message which is " + message);
                    if(message != null){

                        if(message.trim().startsWith(insert)){
                            Log.i(TAG,"Recieving insert Message in Server:" + message);
                            String[] keyValuePairs = message.split("#");
                            Log.i(TAG,"Key:" + keyValuePairs[1]);
                            Log.i(TAG,"Value:" + keyValuePairs[2]);
                            ContentValues cv = new ContentValues();
                            String key = keyValuePairs[1];
                            String value = keyValuePairs[2];
                            cv.put("key",key);
                            cv.put("value",value);
                            insert(providerUri,cv);
                        }

                        if(message.trim().startsWith(query)){
                            Log.i(TAG,"Recieved Query Message :" + message);
                            String[] queryMessageinServer = message.split("#");
                            String key = queryMessageinServer[3];
                            String sourcePort = queryMessageinServer[4];
                            Log.i(TAG,"Recieved Query Key selection :" + key);
                            Log.i(TAG,"Recieved Query Key selection from " + sourcePort + " Port");
                            if(queue.contains(key)){
                                Log.i(TAG,"Key " + key +" found in this AVD:" + "CurrentPort" + getPort());
                                String value = hMap.get(key);
                                String Message = "FOUND#" + key + "#" + value + "#" + sourcePort;
                                Log.i(TAG,"Key " + key +" Value " + value);

                                PrintWriter printWriter = new PrintWriter(serverSock.getOutputStream(),true);
                                printWriter.println(Message);
                                printWriter.flush();
//                                                               ClientTask clientTask = new ClientTask();
//                                clientTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,Message,String.valueOf(Integer.parseInt(sourcePort)));

                            }
                            else{
                                Log.i(TAG,"Server Query / Else Block");
                                Socket succSocket = getSocket(String.valueOf(Integer.parseInt(successor)*2));
                                PrintWriter printWriter = new PrintWriter(succSocket.getOutputStream(),true);
                                printWriter.println(message);
                                Log.i(TAG,"Else - sending to successor:" + message + "to" + successor);
                                printWriter.flush();

                                InputStreamReader inputStreamReader1 = new InputStreamReader(succSocket.getInputStream());
                                BufferedReader bufferedReader1 = new BufferedReader(inputStreamReader1);
                                String AckMessage = bufferedReader1.readLine();

                                PrintWriter printWriter1 = new PrintWriter(serverSock.getOutputStream(),true);
                                printWriter1.println(AckMessage);
                                printWriter1.flush();

                            }
                        }

                        if(message.trim().startsWith("Delete")){
                            if(message.equalsIgnoreCase("DeleteALL")){
                                Log.i(TAG,"DeleteAll Message in server :" + message);
                                hMap.clear();
                            }

                        }


                        if(message.trim().startsWith("GDUMP")){

                            Log.i(TAG, "received GDUMP in server for message " + message);
                            String[] args = message.split("#");
                            String sender = args[1];
                            if(sender.equalsIgnoreCase(successor)){
                                Log.i(TAG, "sender.equalsIgnoreCase(successor) " + sender.equalsIgnoreCase(successor));
                                PrintWriter printWriter = new PrintWriter(serverSock.getOutputStream(),true);
                                JSONObject json = new JSONObject();
                                Iterator<String> iter = hMap.keySet().iterator();
                                while (iter.hasNext()){
                                    String key = iter.next();
                                    String value = hMap.get(key);
                                    try {
                                        json.put(key, value);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                }
                                printWriter.println(json.toString());
                                printWriter.flush();
                            } else {
                                Log.i(TAG, "Not sender.equalsIgnoreCase(successor) " + sender.equalsIgnoreCase(successor) + " successor is " + successor);
                                Socket successorSocket = getSocket(String.valueOf(Integer.parseInt(successor) * 2));
                                PrintWriter printWriter0 = new PrintWriter(successorSocket.getOutputStream(),true);
                                printWriter0.println(message);
                                printWriter0.flush();
                                InputStreamReader inputStreamReader1 = new InputStreamReader(successorSocket.getInputStream());
                                BufferedReader bufferedReader1 =new BufferedReader(inputStreamReader1);
                                String AckMessageGDUMP= bufferedReader1.readLine();
                                Log.i(TAG, " AckMessageGDUMP in server " + AckMessageGDUMP);

                                JSONObject json = new JSONObject(AckMessageGDUMP);

                                PrintWriter printWriter = new PrintWriter(serverSock.getOutputStream(),true);
                                Iterator<String> iter = hMap.keySet().iterator();
                                while (iter.hasNext()){
                                    String key = iter.next();
                                    String value = hMap.get(key);
                                    try {
                                        json.put(key, value);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }

                                }
                                printWriter.println(json.toString());
                                Log.i(TAG, " json.toString() in server " + json.toString());
                                printWriter.flush();
                            }
                        }

                        if(message.startsWith(message_type_JOIN)){
                            Log.i(TAG,"ServerTask / Inside MSG TYPE - JOIN : " + message + "#Port#" + getPort());
                            String[] messageArgs = message.split("#");
                            if(nodeArrayList.size() >= 1){
                                Log.i(TAG, "ServerTask / Node Array List Size : " + nodeArrayList.size() );
                                Node loneNode = nodeArrayList.get(0);
                                Node newNode  = new Node(messageArgs[1],genHash(messageArgs[1]),loneNode,loneNode);
                                loneNode.setPredecessor(newNode);
                                loneNode.setSuccessor(newNode);
                                newNode.setSuccessor(loneNode);
                                newNode.setPredecessor(loneNode);
                                nodeArrayList.add(newNode);
                                nodeList.add(newNode.getPortNumber());
                                Collections.sort(nodeList, new Comparator<String>() {
                                    @Override
                                    public int compare(String lhs, String rhs) {

                                        try {
                                            String genhah_lhs = genHash(lhs);
                                            String genhah_rhs = genHash(rhs);

                                            return genhah_lhs.compareTo(genhah_rhs);
                                        } catch (NoSuchAlgorithmException e) {
                                            e.printStackTrace();
                                        }
                                        return 0;
                                    }
                                });

                                Log.i(TAG,"NodeList is " + nodeList.toString());

                                String predecessorPort = null;
                                String sucessorPort = null;
                                for(int i=0; i< nodeList.size();i++){
                                    if(nodeList.get(i).equalsIgnoreCase(newNode.getPortNumber())){
                                        if(i != 0){
                                            predecessorPort = nodeList.get(i - 1);
                                        } else {
                                            predecessorPort = nodeList.get(nodeList.size() - 1);
                                        }

                                        if(i + 1 == nodeList.size()){
                                            sucessorPort = nodeList.get(0);
                                        } else {
                                            sucessorPort = nodeList.get(i + 1);
                                        }
                                    }
                                }
                                Log.i(TAG,"predecessorPort is " + predecessorPort);
                                Log.i(TAG,"sucessorPort is " + sucessorPort);

                                PrintWriter printWriter = new PrintWriter(serverSock.getOutputStream());

                                printWriter.println("ack" + "#Predecessor#" + predecessorPort
                                        + "#Sucessor#" + sucessorPort);
                                printWriter.flush();
                                printWriter.close();
                                serverSock.close();

                            }

                            Log.i(TAG, "ServerTask / New Array size : " + nodeArrayList.size() );
                        }

                        if(message.startsWith(message_type_update)){
                            Log.i(TAG,"Message in server: " + message);
                            String[] updateMessageArgs = message.split("#");
                            if(updateMessageArgs[2].equalsIgnoreCase("UPDATE_YOUR_SUCCESSOR")){
                                successor = updateMessageArgs[3];
                                Log.i(TAG,"Successor set to :" + successor);
                            }
                            if(updateMessageArgs[2].equalsIgnoreCase("UPDATE_YOUR_PREDECESSOR")){
                                predecessor = updateMessageArgs[3];
                                Log.i(TAG,"Predecessor set to :" + predecessor);
                            }
                            PrintWriter printWriter = new PrintWriter(serverSock.getOutputStream());
                            printWriter.println("OK");
                            printWriter.flush();
                            printWriter.close();
                            Log.i(TAG," POST predecessorPort is " + predecessor);
                            Log.i(TAG," POST sucessorPort is " + successor);
                            serverSock.close();
                        }

                    }

                    Log.i(TAG," POST if predecessorPort is " + predecessor);
                    Log.i(TAG," POST if sucessorPort is " + successor);
                    //serverSock.close();
//                    serverSock.close();

                } catch (IOException e) {
                    Log.e(TAG,"ServerTask / IO Exception  : " + e);
                    e.printStackTrace();
                } catch (NoSuchAlgorithmException e){
                    Log.e(TAG,"ServerTask / No such Algorithm : " + e);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    private class ClientTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... msgs) {

            try {
                Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), Integer.parseInt("5554")*2);
                String message = msgs[0];
                Log.i(TAG,"ClientTask / General Message : " + message + "#Port#" + getPort());
                if(message.startsWith(message_type_JOIN)){
                    Log.i(TAG,"ClientTask / Message : " + message + "Port" + getPort());
                    PrintWriter printWriter = new PrintWriter(socket.getOutputStream(), true);
                    printWriter.println(message);
                    printWriter.flush();


                    Log.i(TAG,"Socket Status / ClientTask " + socket.isConnected());
                    Log.i(TAG,"Socket Status / ClientTask " + socket.isClosed());

                    InputStreamReader inputStreamReader = new InputStreamReader(socket.getInputStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String ackmessage = bufferedReader.readLine();
                    Log.i(TAG,"ClientTask/ ACK Message : " + ackmessage);
                    printWriter.close();
                    if(message.startsWith("ack")){
                        socket.close();
                    }

                    //socket.close();

                    if(ackmessage != null){
                        String[] updateMessage = ackmessage.split("#");
                        successor = updateMessage[4];
                        predecessor = updateMessage[2];

                        String remotePredecessorNode = String.valueOf(Integer.parseInt(predecessor)*2);
                        String remoteSucessorNode = String.valueOf(Integer.parseInt(successor)*2);

                        Socket predecessorSocket = getSocket(remotePredecessorNode);
                        Log.i(TAG,"Predecessor :" + predecessor + "#Sucessor :" + successor );
                        Log.i(TAG,"Predecessor Node:" + remotePredecessorNode + "#Sucessor Node:" + remoteSucessorNode );
                        Log.i(TAG,predecessorSocket.toString());
                        PrintWriter printWriter1 = new PrintWriter(predecessorSocket.getOutputStream(),true);
                        String messageToPredecessor = message_type_update + "#" + predecessor + "#UPDATE_YOUR_SUCCESSOR#" + getAvdNumber(getPort());
                        Log.i(TAG,"Message to predecessor : " + messageToPredecessor);
                        Log.i(TAG,"Current AVD :" + getAvdNumber(getPort()));
                        printWriter1.println(messageToPredecessor);
                        printWriter1.flush();

                        InputStreamReader inputStreamReader1 = new InputStreamReader(predecessorSocket.getInputStream());
                        BufferedReader bufferedReader1 = new BufferedReader(inputStreamReader1);
                        String remotePredecessorMessageReply = bufferedReader1.readLine();
                        Log.i(TAG,"ACK Recieved RemotePredecessor before if :" + remotePredecessorMessageReply);
                        if(remotePredecessorMessageReply.equalsIgnoreCase("OK")){
                            Log.i(TAG,"ACK Recieved RemotePredecessor :" + remotePredecessorMessageReply);
                            printWriter1.close();
                            inputStreamReader1.close();
                            bufferedReader1.close();
                            predecessorSocket.close();

                        }


                        Socket successorSocket = getSocket(remoteSucessorNode);
                        Log.i(TAG,"Predecessor :" + predecessor + "#Sucessor :" + successor );
                        Log.i(TAG,"Predecessor Node:" + remotePredecessorNode + "#Sucessor Node:" + remoteSucessorNode );
                        PrintWriter printWriter2 = new PrintWriter(successorSocket.getOutputStream(),true);
                        String messageToSuccessor = message_type_update + "#" + successor + "#UPDATE_YOUR_PREDECESSOR#" + getAvdNumber(getPort());
                        Log.i(TAG,"Message to successor : " + messageToSuccessor);
                        Log.i(TAG,"Current AVD :" + getAvdNumber(getPort()));
                        printWriter2.println(messageToSuccessor);
                        printWriter2.flush();
                        Log.i(TAG,"Printing To Predecessor");
                        InputStreamReader inputStreamReader2 = new InputStreamReader(successorSocket.getInputStream());
                        BufferedReader bufferedReader2 = new BufferedReader(inputStreamReader2);
                        String remoteSuccessorMessageReply = bufferedReader2.readLine();
                        Log.i(TAG,"remoteSuccessorMessageReply is " + remoteSuccessorMessageReply);
                        if(remoteSuccessorMessageReply.equalsIgnoreCase("OK")){
                            Log.i(TAG,"sd");
                            inputStreamReader2.close();
                            bufferedReader2.close();
                            successorSocket.close();

                        }

                    }



                    Log.i(TAG,"post if");
                }

                if(message.startsWith(insert)){

                    String successorNode = String.valueOf(Integer.parseInt(successor)*2);
                    Socket successorSock = getSocket(successorNode);
                    PrintWriter printWriter1 = new PrintWriter(successorSock.getOutputStream(),true);
                    printWriter1.println(message);
                    printWriter1.flush();
                    socket.close();
                    Log.i(TAG,"Insert Message recieved in ClientTask : " + message);
                }

                if(message.startsWith(query)){
                    String[] queryArgs = message.split("#");
                    String successorNode = String.valueOf(Integer.parseInt(successor)*2);
                    Socket successorSock = getSocket(successorNode);
                    Log.i(TAG,"Query message received in clientTask" + message);
                    PrintWriter printWriter = new PrintWriter(successorSock.getOutputStream(),true);
                    printWriter.println(message);
                    printWriter.flush();
                }

                /*if(message.startsWith("FOUND")){
                    Log.i(TAG,"Found Key in client"+ message);
                    String[] foundMessageArgs = message.split("#");
                    String source = foundMessageArgs[3];
                    String key = foundMessageArgs[1];
                    String value = foundMessageArgs[2];
                    String foundNode = String.valueOf(Integer.parseInt(source));
                    Socket foundSock = getSocket(foundNode);
                    PrintWriter printWriter1 = new PrintWriter(foundSock.getOutputStream(),true);
                    printWriter1.println(message);
                    printWriter1.flush();
                    //socket.close();
                }*/

                Log.i(TAG,"My Predecessor" + predecessor);
                Log.i(TAG,"My Successor" + successor);

            } catch (IOException e) {
                Log.e(TAG,"ClientTask / IO Exception : "+ e);
                e.printStackTrace();
            }

            return null;
        }
    }

    public class Node {
        public String PortNumber;
        public String PortNumberHash;
        public Node predecessor;
        public Node successor;

        public Node(String currentPortNumber, String currentPortNumberHash, Node predecessorPort, Node successorNode){
            this.PortNumber = currentPortNumber;
            this.PortNumberHash = currentPortNumberHash;
            this.predecessor = predecessor;
            this.successor = successor;
        }

        public String getPortNumber() {
            return PortNumber;
        }

        public Node setPortNumber(String PortNumber) {
            this.PortNumber = PortNumber;
            return this;
        }

        public String getPortNumberHash() {
            return PortNumberHash;
        }

        public Node setPortNumberHash(String PortNumberHash) {
            this.PortNumberHash = PortNumberHash;
            return this;
        }

        public Node getPredecessor() {
            return predecessor;
        }

        public Node setPredecessor(Node predecessor) {
            this.predecessor = predecessor;
            return this;
        }

        public Node getSuccessor() {
            return successor;
        }

        public Node setSuccessor(Node successor) {
            this.successor = successor;
            return this;
        }
    }

}