package edu.buffalo.cse.cse486586.simpledynamo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

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

public class SimpleDynamoProvider extends ContentProvider {

    /* TAG for Logging */
    static final String TAG = SimpleDynamoProvider.class.getName();

    /* PORTS */
    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORTS = new String[] { "11108", "11112", "11116", "11120", "11124" };

    /* URI Builder */
    String authority = "edu.buffalo.cse.cse486586.simpledynamo.provider";
    String scheme = "content";
    uriBuilder uB = new uriBuilder();
    private final Uri providerUri = uB.buildUri(scheme, authority);

    /* Nodes */
    ArrayList<Node> nodeArrayList = new ArrayList<Node>();
    ArrayList<String> nodeList = new ArrayList<String>();
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<String>(100000);
    ConcurrentHashMap<String, String> hMap = new ConcurrentHashMap<String, String>();
    ConcurrentHashMap<String,String> replicatedHMap = new ConcurrentHashMap<String, String>();
    public String masterNode = "5554";

    public String successor = "";
    public String predecessor = "";

    boolean check = false;

    /* Files */
    String nullResult = null;
    ArrayList<String> files = new ArrayList<String>();

    /* Messages */
    String insert = "insert";
    String delete = "DELETE";
    String query = "query";
    String delimiter = "#";
    String message_type_JOIN = "NEW_NODE";
    String message_type_update = "UPDATE";
    String GIVEMEYOURDATA = "GIVEMEYOURDATA";
    String GIVEMEMYDATA = "GIVEMEMYDATA";

    /* Delete */
    boolean GDUMP = false;
    boolean LDUMP = false;
    boolean individual = false;
    HashMap<String, String> keysToDelete = new HashMap<String, String>();

    /* NODE HASHES */
    private String PORT_5554_HASH;
    private String PORT_5556_HASH;
    private String PORT_5558_HASH;
    private String PORT_5560_HASH;
    private String PORT_5562_HASH;

    {
        try {
            PORT_5554_HASH = genHash(Declarations.PORT_5554);
            PORT_5556_HASH = genHash(Declarations.PORT_5556);
            PORT_5558_HASH = genHash(Declarations.PORT_5558);
            PORT_5560_HASH = genHash(Declarations.PORT_5560);
            PORT_5562_HASH = genHash(Declarations.PORT_5562);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }



    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }



    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub

        String key = values.get("key").toString();
        String value = values.get("value").toString();
        Log.i(TAG, "Received insert message " + key + " " + value);

        try {
            String ports = getOwners(key);
            List<String> listOfResponses = new ArrayList<String>();
            for (String port : ports.split("#")){
                Log.i(TAG,"inside insert :" + port);
                if(port.equalsIgnoreCase(getAvdNumber(getPort()))){
                    listOfResponses.add(hMap.get(key));
                } else {
                    String response = sendMessageToServer(port, "query#" + key);
                    if (response != null){
                        String responseValue = response.split(":")[1];
                        if (!responseValue.equalsIgnoreCase("null")){
                            listOfResponses.add(responseValue);
                        }
                    }
                }
            }
            Log.i(TAG, "listOfResponses size is " + listOfResponses.size());

            String versionValue = getRecentVersionNumberAndValue(listOfResponses);
            String[] versionValueArr = versionValue.split("#");
            int version = Integer.parseInt(versionValueArr[0]);
            String responseValue = versionValueArr[1];
            for (String port : ports.split("#")){
                Log.i(TAG,"inside insert sending " + port);
                if(port.equalsIgnoreCase(getAvdNumber(getPort()))){
                    hMap.put(key, (version + 1) + "#" + value);
                    sendMessageToServer(port, "insert#" + key + "#" + (version + 1) + "#" + value);
                } else {
                    sendMessageToServer(port, "insert#" + key + "#" + (version + 1) + "#" + value);
                }
            }

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String getRecentVersionNumberAndValue(List<String> listOfResponses) {
        int version = 0;
        String value = null;
        for (String response : listOfResponses){
            if (response != null && !response.equalsIgnoreCase("null")){
                int responseVersion = Integer.parseInt(response.split("#")[0]);
                String responseValue = response.split("#")[1];

                if(responseVersion > version){
                    version  = responseVersion;
                    value = responseValue;
                }
            }
        }
        return version + "#" + value;
    }


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if (selection.contains("*")) {
            for ( String port : Declarations.REMOTE_PORTS){
                port = getAvdNumber(port);
                if (!port.equalsIgnoreCase(getAvdNumber(getPort()))){
                    sendMessageToServer(port, "Delete#" + selection);
                } else {
                    hMap.clear();
                }
            }

        } else if (selection.contains("@")) {
            hMap.clear();

        } else {
            try {
                String[] owners = getOwners(selection).split("#");
                for(String owner : owners){
                    if (!owner.equalsIgnoreCase(getAvdNumber(getPort()))){
                        sendMessageToServer(owner, "Delete#" + selection);
                    } else {
//                        hMap.remove(selection);
                        hMap.clear();
                    }
                }
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        return 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        // TODO Auto-generated method stub
        String key = selection;
        MatrixCursor myCursor = new MatrixCursor(new String[]{"key", "value"});
        if (selection.contains("@")){
            Iterator<Map.Entry<String, String>> hMapIterator = hMap.entrySet().iterator();
            while (hMapIterator.hasNext()){
                Map.Entry<String, String> next = hMapIterator.next();
                myCursor.addRow(new String[]{next.getKey(), next.getValue().split("#")[1]});
            }
        } else if (selection.contains("*")){
            Iterator<Map.Entry<String, String>> hMapIterator = hMap.entrySet().iterator();
            while (hMapIterator.hasNext()){
                Map.Entry<String, String> next = hMapIterator.next();
                myCursor.addRow(new String[]{next.getKey(), next.getValue().split("#")[1]});
            }

            for ( String port : Declarations.REMOTE_PORTS){
                port = getAvdNumber(port);
                if (!port.equalsIgnoreCase(getAvdNumber(getPort()))){
                    String response = sendMessageToServer(port, "query#" + selection);
                    try {
                        if (response != null){
                            JSONObject jsonObject = new JSONObject(response);
                            Iterator jsonObjectIter = jsonObject.keys();
                            while (jsonObjectIter.hasNext()){
                                String jsonObjectkey = jsonObjectIter.next().toString();
                                Log.i(TAG,"adding to cursor " + key + " " + jsonObject.getString(jsonObjectkey));
                                myCursor.addRow(new String[]{jsonObjectkey, jsonObject.getString(jsonObjectkey).split("#")[1]});
                            }
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

        } else {
            try {
                String ports = getOwners(key);
                List<String> listOfResponses = new ArrayList<String>();
                for (String port : ports.split("#")){
                    if(port.equalsIgnoreCase(getAvdNumber(getPort()))){
                        listOfResponses.add(hMap.get(key));
                    } else {
                        String response = sendMessageToServer(port, "query#" + key);
                        if (response != null){
                            String responseValue = response.split(":")[1];
                            if (!responseValue.equalsIgnoreCase("null")){
                                listOfResponses.add(responseValue);
                            }
                        }
                    }
                }
                String versionValue = getRecentVersionNumberAndValue(listOfResponses);
                String[] versionValueArr = versionValue.split("#");
                myCursor.addRow(new String[]{key, versionValueArr[1]});
                String sendBackPortString = getOwners(key);
                String[] sendBackPorts = sendBackPortString.split("#");
                for(String port:sendBackPorts){
                    if(!port.equals(getAvdNumber(getPort()))){
                        String ack = sendMessageToServer(port, "insert#" + key + "#" + (versionValueArr[0]) + "#" + versionValueArr[1]);
                        if(ack!=null){
                            Log.i(TAG,"Ack in query after inserting updated" + ack);

                        }
                    }

                }


            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }


        return myCursor;
    }



    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean onCreate() {
        boolean check = false;

        String mySuccNPred = "";

        // TODO Auto-generated method stub
        String currentNode = getPort(); // returns 111XX
        String nodeAvdNumber = getAvdNumber(currentNode); // returns 555X
        ServerSocket serverSocket = null;

        try {

            new NodeJoins().invoke();
            setSuccNPred();
            serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);



        } catch (Exception e) {
            Log.e(TAG, "onCreate / Exception " + e);
        }


        for(Node node:nodeArrayList){
            if(node.getPortNumber().equals(getAvdNumber(getPort()))){
                String mySucc = node.getSuccessor().getPortNumber() + "#" + node.getSuccessor().getSuccessor().getPortNumber();
                String myPred = node.getPredecessor().getPortNumber() + "#" + node.getPredecessor().getPredecessor().getPortNumber();
                Log.i(TAG,"My Succ : " + mySucc);
                Log.i(TAG,"My Pred : " + myPred);

                mySuccNPred = myPred + "#" + mySucc;
            }
        }

        String pred1 = mySuccNPred.split("#")[0];
        String pred2 = mySuccNPred.split("#")[1];
        String succ1 = mySuccNPred.split("#")[2];
        String succ2 = mySuccNPred.split("#")[3];

        String filename = "Check";

        try {
            InputStreamReader inputStreamReader = new InputStreamReader(getContext().openFileInput(filename));
            check = true;
        } catch (FileNotFoundException e) {
            check = false;
            e.printStackTrace();
        }

        if (check){
            try {
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"GIVEMEYOURDATA",pred1+"#"+pred2).get();
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,"GIVEMEMYDATA",succ1+"#"+succ2).get();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        } else {

            String value = "dummyvalue";
            PrintWriter printWriter = null;
            try {
                printWriter = new PrintWriter(getContext().openFileOutput(filename, Context.MODE_PRIVATE));
                printWriter.println(value);
                printWriter.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }




        return false;
    }




    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        @SuppressLint("WrongThread")
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            while (true) {
                try {
                    Socket serverSock = serverSocket.accept();
                    Log.i(TAG, "ServerSocket : " + serverSock.isClosed() + "#isConn" + serverSock.isConnected() + "#"
                            + serverSock.toString());
                    InputStreamReader inputStreamReader = new InputStreamReader(serverSock.getInputStream());
                    Log.i(TAG, "Post InputStreamReader");
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String message = bufferedReader.readLine();
                    Log.i(TAG, serverSock.toString());
                    Log.i(TAG, "received message which is " + message);
                    if (message != null) {

                        if (message.trim().startsWith(insert)) {

                            Log.i(TAG, "Recieving insert Message in Server:" + message);
                            String[] keyValuePairs = message.split("#");

                            String key = keyValuePairs[1];
                            int version = Integer.parseInt(keyValuePairs[2]);
                            String value = keyValuePairs[3];

                            if (hMap.get(key) == null){
                                hMap.put(key, version +"#" + value );
                            } else {
                                String hMapVersionValaue = hMap.get(key);
                                hMapVersionValaue.split("#");
                                int hMapVersion = Integer.parseInt(hMapVersionValaue.split("#")[0]);
                                if (hMapVersion < version){
                                    hMap.put(key, version + "#" + value);
                                }
                            }

                            sendMessageToClient(serverSock, " inserted " + key + " "
                                    + version + " " + value);

                        }

                        if (message.trim().startsWith(query)) {
                            String key = message.trim().split("#")[1];
                            Log.i(TAG, "query message is " + message);
                            if (message.contains("*") || message.contains("@")){
                                Log.i(TAG, " inside * and @ of query ");
                                JSONObject jsonObject = new JSONObject();
                                Iterator<Map.Entry<String, String>> hMapIterator = hMap.entrySet().iterator();
                                while (hMapIterator.hasNext()){
                                    Map.Entry<String, String> next = hMapIterator.next();
                                    jsonObject.put(next.getKey(), next.getValue());
                                }
                                sendMessageToClient(serverSock, jsonObject.toString());

                            } else {
                                sendMessageToClient(serverSock, "ACK:" + hMap.get(key));
                            }
                        }

                        if (message.trim().startsWith("Delete")) {
                            hMap.clear();
                            String[] messageArray = message.trim().split("#");
                            if (messageArray[1].equalsIgnoreCase("*") || messageArray[1].equalsIgnoreCase("@")){
                                hMap.clear();
                            } else {
                                hMap.remove(messageArray[0]);
                            }
                            sendMessageToClient(serverSock, "ACK: Deleted key "
                                    + messageArray[1] + " in port " + getAvdNumber(getPort()));

                        }


                        if(message.startsWith(GIVEMEMYDATA) || message.startsWith(GIVEMEYOURDATA)){
                            JSONObject jsonObject = new JSONObject();
                            Iterator<Map.Entry<String, String>> hMapIter = hMap.entrySet().iterator();
                            while (hMapIter.hasNext()){
                                Map.Entry<String, String> next = hMapIter.next();
                                jsonObject.put(next.getKey(), next.getValue());
                            }

                            sendMessageToClient(serverSock, jsonObject.toString());
                        }




                    }

                    Log.i(TAG, " POST if predecessorPort is " + predecessor);
                    Log.i(TAG, " POST if sucessorPort is " + successor);
                    // serverSock.close();
                    // serverSock.close();

                } catch (IOException e) {
                    Log.e(TAG, "ServerTask / IO Exception  : " + e);
                    e.printStackTrace();
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
                /*Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }),
                        Integer.parseInt("5554") * 2);*/
                String message = msgs[0];
                Log.i(TAG, "ClientTask / General Message : " + message + "#Port#" + getPort());

                if(message.startsWith(GIVEMEMYDATA)){

                    String response = sendMessageToServer(msgs[1].split("#")[0],message + "#" +getAvdNumber(getPort()));
                    String response1 = sendMessageToServer(msgs[1].split("#")[1],message + "#" +getAvdNumber(getPort()));

                    if (response != null){
                        JSONObject jsonObject = new JSONObject(response);
                        Iterator jsonObjectkeys = jsonObject.keys();
                        while (jsonObjectkeys.hasNext()){
                            String key = jsonObjectkeys.next().toString();
                            String value = jsonObject.getString(key);

                            String port = getOwners(key).split("#")[0];
                            if(getAvdNumber(getPort()).equals(port)){
                                hMap.put(key,value);

                            }
                        }
                    }
                    if (response1 != null){
                        JSONObject jsonObject = new JSONObject(response);
                        Iterator jsonObjectkeys = jsonObject.keys();
                        while (jsonObjectkeys.hasNext()){
                            String key = jsonObjectkeys.next().toString();
                            String value = jsonObject.getString(key);

                            String port = getOwners(key).split("#")[0];
                                if(getAvdNumber(getPort()).equals(port)){
                                    if(hMap.get(key)==null){
                                        hMap.put(key,value);
                                    }
                                    else{
                                        String ValueString = hMap.get(key);
                                        int hMapversion = Integer.parseInt(ValueString.split("#")[0]);
                                        int newVersion = Integer.parseInt(value.split("#")[0]);
                                        if (newVersion > hMapversion){
                                            hMap.put(key,value);
                                        }

                                    }
                                }
                        }
                    }

                }

                if(message.startsWith(GIVEMEYOURDATA)){

                    String pred1 = msgs[1].split("#")[0];
                    String pred2 = msgs[1].split("#")[1];

                    String response = sendMessageToServer(pred1,message + "#" +getAvdNumber(getPort()));
                    String response1 = sendMessageToServer(pred2,message + "#" +getAvdNumber(getPort()));

                    if (response != null){
                        JSONObject jsonObject = new JSONObject(response);
                        Iterator jsonObjectkeys = jsonObject.keys();
                        while (jsonObjectkeys.hasNext()){
                            String key = jsonObjectkeys.next().toString();
                            String value = jsonObject.getString(key);

                            String port = getOwners(key).split("#")[0];
                            if(pred1.equals(port)){
                                hMap.put(key,value);

                            }
                        }
                    }
                    if (response1 != null){
                        JSONObject jsonObject = new JSONObject(response);
                        Iterator jsonObjectkeys = jsonObject.keys();
                        while (jsonObjectkeys.hasNext()){
                            String key = jsonObjectkeys.next().toString();
                            String value = jsonObject.getString(key);

                            String port = getOwners(key).split("#")[0];
                            if(pred2.equals(port)){
                                if(hMap.get(key)==null){
                                    hMap.put(key,value);
                                }
                                else{
                                    String ValueString = hMap.get(key);
                                    int hMapversion = Integer.parseInt(ValueString.split("#")[0]);
                                    int newVersion = Integer.parseInt(value.split("#")[0]);
                                    if (newVersion > hMapversion){
                                        hMap.put(key,value);
                                    }

                                }
                            }
                        }
                    }

                }

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (JSONException e) {
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

        public Node(String currentPortNumber, String currentPortNumberHash, Node predecessorPort, Node successorNode) {
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

    private class NodeJoins {
        public void invoke() {
            // PA3
            Node node_5562 = new Node(Declarations.PORT_5562,PORT_5562_HASH,null,null);
            Node node_5556 = new Node(Declarations.PORT_5556,PORT_5556_HASH,null,null);
            Node node_5554 = new Node(Declarations.PORT_5554,PORT_5554_HASH,null,null);
            Node node_5558 = new Node(Declarations.PORT_5558,PORT_5558_HASH,null,null);
            Node node_5560 = new Node(Declarations.PORT_5560,PORT_5560_HASH,null,null);

            /* PA3 Reference */
            /* ORDER -> 5562,5556,5554,5558,5560 */
            /* 4,1,0,2,3 */
            setNeighbours(node_5562, node_5556, node_5554, node_5558, node_5560);

            addNodesToList(node_5562, node_5556, node_5554, node_5558, node_5560);
        }
    }

    private void setNeighbours(Node node_5562, Node node_5556, Node node_5554, Node node_5558, Node node_5560) {
        node_5554.setPredecessor(node_5556);
        node_5554.setSuccessor(node_5558);

        node_5556.setPredecessor(node_5562);
        node_5556.setSuccessor(node_5554);

        node_5558.setPredecessor(node_5554);
        node_5558.setSuccessor(node_5560);

        node_5560.setPredecessor(node_5558);
        node_5560.setSuccessor(node_5562);

        node_5562.setPredecessor(node_5560);
        node_5562.setSuccessor(node_5556);
    }

    private void addNodesToList(Node node_5562, Node node_5556, Node node_5554, Node node_5558, Node node_5560) {
        nodeArrayList.add(node_5554);
        nodeArrayList.add(node_5556);
        nodeArrayList.add(node_5558);
        nodeArrayList.add(node_5560);
        nodeArrayList.add(node_5562);
    }

    private void setSuccNPred() {
        if(getAvdNumber(getPort()).equals("5554")){
            successor = "5558";
            predecessor = "5556";
        }
        else if(getAvdNumber(getPort()).equals("5556")){
            predecessor = "5562";
            successor = "5554";
        }
        else if(getAvdNumber(getPort()).equals("5558")){
            predecessor = "5554";
            successor = "5560";
        }
        else if(getAvdNumber(getPort()).equals("5560")){
            predecessor = "5558";
            successor = "5562";
        }
        else if(getAvdNumber(getPort()).equals("5562")){
            predecessor = "5560";
            successor = "5556";
        }
    }


    // Generate Hash for a key.
    public String genHash(String input) throws NoSuchAlgorithmException {
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
    public void keyIn(String key, String value) {
        try {
            FileOutputStream fileOutputStream = getContext().openFileOutput(key, Context.MODE_PRIVATE);
            fileOutputStream.write(value.getBytes());
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File Not Found Exception Raised : " + e);
        } catch (Exception e) {
            Log.e(TAG, "Unknown Exception Caught : " + e);
        }
    }

    /* Method used for Query */
    public String keyOut(String key) {
        String message = null;
        try {
            Context context = getContext();
            FileInputStream fileInputStream = context.openFileInput(key);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream));
            message = bufferedReader.readLine();
            bufferedReader.close();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found exception : " + e);
        } catch (IOException e) {
            Log.e(TAG, "IOException :" + e);
        } catch (Exception e) {
            Log.e(TAG, "Exception :" + e);
        }
        return message;
    }

    // Returns avd number :: 5554
    public String getAvdNumber(String node) {
        String port = String.valueOf(Integer.parseInt(node) / 2);
        return port;
    }

    // Returns port number :: 11108
    private String getPort() {
        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        // Log.i(TAG, "getPort/Telephone Manager : " + tel);
        // Log.i(TAG, "getPort/Line1 Number : " + tel.getLine1Number());
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        // Log.i(TAG, "getPort/Port Str : " + portStr);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        // Log.i(TAG, "getPort/My Port :" + myPort);
        return myPort;
    }

    // Create socket using the passed port.
    private Socket getSocket(String port) throws IOException {
        return new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }), Integer.parseInt(port));
    }

    private String getValue(String value){
        String[] value_string = value.split("#");
        value = value_string[1];
        return value;
    }

    private String getTimeStamp(String value){
        String[] value_string = value.split("#");
        String timeStamp = value_string[0];
        return timeStamp;
    }

    public String getOwners(String key) throws NoSuchAlgorithmException {
        String hashKey = genHash(key);
        String owners =null;
        for(Node n:nodeArrayList){

            if((hashKey.compareTo(genHash(n.getPredecessor().getPortNumber())) >0 && hashKey.compareTo(genHash(n.getPortNumber())) < 0)||
                    (genHash(n.getPredecessor().getPortNumber()).compareTo(genHash(n.getPortNumber()))>0 &&
                            (hashKey.compareTo(genHash(n.getPredecessor().getPortNumber()))>0 || hashKey.compareTo(genHash(n.getPortNumber()))<0))){
                owners =n.getPortNumber()+"#"+n.getSuccessor().getPortNumber()+"#"+n.getSuccessor().getSuccessor().getPortNumber();
                return owners;
            }
        }
        return owners;
    }

    public boolean checkCondition(String key) {
        boolean check = false;
        try {
            Log.i(TAG,"Pred" + predecessor + "# Succe : "  + successor );
            if (loneNodeCondition() || (genHash(getAvdNumber(getPort())).compareTo(genHash(predecessor)) < 0
                    && (genHash(key).compareTo(genHash(predecessor)) > 0
                    || genHash(key).compareTo(genHash(getAvdNumber(getPort()))) < 0))) {
                Log.i(TAG, "key :" + key);
                Log.i(TAG, "Before setting check to true #" + check);
                check = true;
                Log.i(TAG, "After setting check to true #" + check);
            }

            else if (loneNodeCondition() || (genHash(getAvdNumber(getPort())).compareTo(genHash(predecessor)) > 0
                    && (genHash(key).compareTo(genHash(getAvdNumber(getPort()))) < 0
                    && genHash(key).compareTo(genHash(predecessor)) > 0))) {
                Log.i(TAG, "key :" + key);
                Log.i(TAG, "2 Before setting check to true #" + check);
                check = true;
                Log.i(TAG, "2 After setting check to true #" + check);

            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return check;
    }

    private String sendMessageToServer(String node, String message)  {

        Socket serverSocket = null;
        try {
            serverSocket = getSocket(String.valueOf(Integer.parseInt(node) * 2));
            serverSocket.setSoTimeout(500);
            Log.i(TAG,"Message sending to server :" + message + "#" + String.valueOf(Integer.parseInt(node)));
            PrintWriter printWriter = new PrintWriter(serverSocket.getOutputStream(), true);
            printWriter.println(message);
            printWriter.flush();
            InputStreamReader inStr = new InputStreamReader(serverSocket.getInputStream());
            BufferedReader br = new BufferedReader(inStr);
            String recivedMessage = br.readLine();
            Log.i(TAG, "recivedMessage from client is " + recivedMessage);
//            Log.i(TAG, recivedMessage);
            return recivedMessage;
        } catch (SocketTimeoutException e){
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NullPointerException e){
            e.printStackTrace();
        } catch (Exception e){
            e.printStackTrace();
        }

        return null;
    }

    private void sendMessageToClient(Socket socket , String response) throws IOException {
        Log.i(TAG,"Inside sendMessageToClient " + response + " " + socket);
        try {
            PrintWriter pw = new PrintWriter(socket.getOutputStream(), true);
            pw.println(response);
            pw.flush();
            pw.close();
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    private boolean loneNodeCondition() {
        return successor.equals("") || successor.equals(null);
    }

    private boolean inAny(String[] ports1) {
        return ports1[0].equals(getAvdNumber(getPort())) || ports1[1].equals(getAvdNumber(getPort())) || ports1[2].equals(getAvdNumber(getPort()));
    }

}