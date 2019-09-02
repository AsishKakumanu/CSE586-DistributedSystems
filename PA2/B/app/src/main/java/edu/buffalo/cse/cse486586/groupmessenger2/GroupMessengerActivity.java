package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.lang.String;
import java.nio.Buffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Thread.*;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

    /* Referred from PA-II A */

    static final String TAG = GroupMessengerActivity.class.getName();

    /* ---- PORTS ---- */
    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORT = new String[] { "11108", "11112", "11116", "11120", "11124" };
    String port;
    String failedPort = null;

    /* ---- URI Builder ---- */
    String authority = "edu.buffalo.cse.cse486586.groupmessenger2.provider";
    String scheme = "content";
    uriBuilder uB = new uriBuilder();
    private final Uri providerUri = uB.buildUri(scheme, authority);

    /* ---- Counters ---- */
    static int counter = 0; // Content Provider
    int serverCounter = 0;
    float counterValueFloat = 0;
    int counterValueInt;

    final ContentValues contentValues = new ContentValues();

    /* ---- MSG TYPES ---- */
    String initMessage = "INIT_MSG";
    String proposal = "PROPOSAL";
    String agreement = "AGREE";
    String failureReply;

    /* ---- FAILURE DETECTION ---- */
    HashMap<Integer, Boolean> FailureList = new HashMap<Integer, Boolean>(){
        {
            put(11108,false);
            put(11112,false);
            put(11116,false);
            put(11120,false);
            put(11124,false);
        }
    };
    ArrayList<Integer> successList = new ArrayList<Integer>();
    boolean check = false;
    boolean portCheck = false;

    /* ---- Message Queues ---- */
    /* ---- ORDERING & QUEUES ---- */
    HashMap<String, msg> msgMap = new HashMap<String, msg>();
    PriorityQueue<msg> msgPriorityQueue = new PriorityQueue<msg>(200, new Comparator<msg>() {
        @Override
        public int compare(msg lhs, msg rhs) {
            if (lhs.getSequence() < rhs.getSequence()) {
                return -1;
            }
            else if(lhs.getSequence() == rhs.getSequence()){
                return 0;
            }
            else{
                return 1;
            }

        }
    });


    /*public class checkPort extends TimerTask{
        public void run(){
            try{
                if(!portCheck){
                    for(String remPorts: REMOTE_PORT){
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }),
                                Integer.parseInt(remPorts));

                        PrintWriter pw = new PrintWriter(socket.getOutputStream());
                        String sendCheckMessage = "Check";
                        pw.println(sendCheckMessage);
                        Thread.sleep(10);

                        *//* INPUT STREAM*//*
                        InputStreamReader in = new InputStreamReader(socket.getInputStream());
                        BufferedReader br = new BufferedReader(in);
                        String replyCheckMessage = br.readLine();
                        if(replyCheckMessage != null){
                            if(replyCheckMessage.startsWith("ACK")){
                                check = true;
                            }
                        }
                        if(!check){
                            portCheck = true;
                            Log.e(TAG,"Error Port Found : " + remPorts);
                        }
                    }
                }
            }
            catch(UnknownHostException e){
                e.printStackTrace();
            }
            catch(IOException e){
                e.printStackTrace();
            }
            catch(InterruptedException e){
                e.printStackTrace();
            }

        }
    }*/

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

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send"
         * button. In your implementation you need to get the message from the input box
         * (EditText) and send it to other AVDs.
         */




        final String myPort = getPort();

        if (startServerSock())
            return;

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

        /*Timer timer = new Timer();
        timer.schedule( new checkPort(),10,10);*/
    }

    private boolean startServerSock() {
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Exception Raised");
            return true;
        }
        return false;
    }

    private String getPort() {
        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        Log.i(TAG, "getPort/Telephone Manager : " + tel);
        Log.i(TAG, "getPort/Line1 Number : " + tel.getLine1Number());
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        Log.i(TAG, "getPort/Port Str : " + portStr);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        Log.i(TAG, "getPort/My Port :" + myPort);
        return myPort;
    }

    String portNumber(String port) {
        HashMap<String, String> portMap = new HashMap<String, String>();
        portMap.put("11108", "0");
        portMap.put("11112", "1");
        portMap.put("11116", "2");
        portMap.put("11120", "3");
        portMap.put("11124", "4");

        String returnedPortMapNumber = portMap.get(port);
        return returnedPortMapNumber;

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
                String msgProposal;
                String ID;

                while (true) {

                    // Create Socket
                    Log.i(TAG, "ServerTask/Socket Establishing");
                    Socket serverSock = serverSocket.accept();
                    Log.i(TAG, "ServerTask/Connection Accepted");

                    // Reads bytes from the server socket and decodes them to characters
                    InputStreamReader inputStream = new InputStreamReader(serverSock.getInputStream());

                    // Reading text from the Character Stream (Instance of InputStreamReader).
                    BufferedReader reader = new BufferedReader(inputStream);

                    PrintWriter pw = null;

                    // Message
                    message = String.valueOf(reader.readLine());

                    /*
                     * If the Message is not null, Publish the message to the Main Activity while
                     * the Background Computations are still Running
                     */
                    String newm = " " + message;
                    Log.e(TAG,"Message Recieved :" + newm);

                    if (message != null) {
                        Log.e(TAG,"ServerTask/Failed : " + message);
                        if(message.startsWith("FAILED")){
                            String[] failureNode = message.split("#");
                            String port = failureNode[1];
//                            Log.e(TAG,"ServerTask/Failure Reply : " + successList.size());
                            Log.e(TAG,"ServerTask/Failure Reply PORT : " + port);

                        }


                        /* ---- RECIEVING INIT_MSG ---- */
                        /* ---- SENDING PROPOSAL ----- */
                        if (message.startsWith(initMessage)) {

                            /* ---- Variable Initializing ---- */
                            String[] arrMessage = message.split("#");
                            String type = arrMessage[0];
                            String counterValue = arrMessage[1];
                            String portSeqFrom = arrMessage[2];
                            String messageRecvd = arrMessage[3];




                            /* ---- Counter ---- */
                            counterValueInt = (int) Float.parseFloat(counterValue);
                            //serverCounter = counterValueInt;

                            /* ---- Server Ports Sequence ---- */
                            String portSeqServer;
                            portSeqServer = portNumber(getPort());


                            //serverCounter = Math.max(counterValueInt+1,serverCounter);

                            Log.i(TAG, "ServerTask/Message : " + message);

                            // MESSAGE FORMATTED
                            msgProposal = proposal + "#" + serverCounter + "." + portSeqServer + "#" + portSeqFrom
                                    + "#" + messageRecvd + "#" + false + "#" + getPort();
                            Log.i(TAG, "ServerTask/Recieved Message (Formatted) : " + msgProposal);
                            Log.i(TAG,"SERVER PORT : " + portNumber(getPort()));

                            // CREATING SEQ NUM
                            String seqNum = serverCounter + "." + portSeqServer;
                            Float seqNumFloat = Float.parseFloat(seqNum);
                            // Log.i(TAG,"SeqNum : " + seqNumFloat);

                            // MESSAGE OBJECT
                            msg msgObject = new msg(proposal, seqNumFloat, Integer.parseInt(portSeqFrom), messageRecvd,
                                    false);

                            msgMap.put(messageRecvd, msgObject);
                            msgPriorityQueue.add(msgObject);

                            /*if(failedPort != null){
                                for(String i:msgMap.keySet()){
                                    msg msgObjFail = msgMap.get(i);
                                    if(msgObjFail.getSourcePort() == Integer.parseInt(failedPort) && msgObjFail.isStatus() == false){
                                        msgPriorityQueue.remove(msgObjFail);
                                    }
                                }
                            }*/

                            Log.i(TAG,"Inserting Data into Map :" + messageRecvd + "#" + msgObject);
                            Log.i(TAG,"Hash Map Size : " + msgMap.size());
                            Log.i(TAG,"Queue Size : " + msgPriorityQueue.size());

                            Log.i(TAG,"Hash Map : " + msgMap.values());
                            Log.i(TAG,"Queue : " + msgPriorityQueue.toArray());
                            // msgArrayList.add(msgObject);
                            // msg obj = (msg) msgMap.values().toArray()[0];



                            // SENDING OUT
                            Log.i(TAG, "ServerTask/Sending Out: " + msgObject);
                            pw = new PrintWriter(serverSock.getOutputStream(), true);
                            pw.println(msgProposal);

                            // Log.i(TAG,"Map First Element : " + obj);
                            // publishProgress(msgProposal);
                            serverCounter++;
                        }

                        /* ---- PROPOSAL MAX RECIEVED ---- */
                        if(message.startsWith("AGREE")){
                            String[] arrAgreeMessage = message.split("#");
                            String msgType = arrAgreeMessage[0];
                            float agreedSeq = Float.parseFloat(arrAgreeMessage[1]);
                            String finalMessage = arrAgreeMessage[2];
                            String f = arrAgreeMessage[3];
                            int port;
                            boolean status;

                            int seq = (int) Float.parseFloat(arrAgreeMessage[1]);

                            Log.i(TAG,"Agreement Recieved : " + message);
                            Log.i(TAG, "ServerTask/Printing failed Port : " + failedPort );
                            if(f!=null && !f.trim().equalsIgnoreCase("null")){
                                Log.i(TAG,"ServerTask/Failed Port : " + f);
                            }
                            msg msgObj = msgMap.get(finalMessage);
                            port = msgObj.getSourcePort();


                            msg omsg = msgMap.get(finalMessage);
                            omsg.setStatus(true);
                            omsg.setSequence(agreedSeq);
                            omsg.setMsgType(agreement);

                            if(f != null && !f.trim().equalsIgnoreCase("null")){
                                for(String i:msgMap.keySet()){
                                    msg msgObjFail = msgMap.get(i);
                                    if(msgObjFail.getSourcePort() == Integer.parseInt(f)){
                                        msgPriorityQueue.remove(msgObjFail);
                                    }
                                }
                            }

                            Log.e(TAG,"omsg String" + omsg.getMsgType() + "#" + omsg.getSequence() + "#" + omsg.getSourcePort() + "" + omsg.getMessage() + "" + omsg.isStatus());

                            Log.i(TAG,"Removing Object from Queue");



                            msgPriorityQueue.remove(msgObj);





                            serverCounter = Math.max(seq+1,serverCounter);

                            msgObj.setStatus(true);
                            msgPriorityQueue.add(omsg);

                            Log.e(TAG,"Agreed #"+agreedSeq+"#"+omsg.getMessage());
                            Log.e(TAG,"Status set to : " + msgMap.get(finalMessage).isStatus());
                            Log.e(TAG,"Sequence : " + omsg.getSequence());

                        }




                        while (msgPriorityQueue.size()>0 && msgPriorityQueue.peek().isStatus()) {
                            msg msgObj = msgPriorityQueue.peek();

                            /* ---- GET MESSAGE CONTENT ---- */
                            String Message = msgObj.getMessage();
                            String msgType = msgObj.getMsgType();
                            Boolean status = msgObj.isStatus();
                            float seq = msgObj.getSequence();
                            Log.e(TAG,"Peek Message/ Print : "+ Message + "#" + msgType + "#" + status + "#" +seq + "#" + msgObj.getSourcePort());


                            msg msgObject = msgPriorityQueue.remove();
                            String msgToPublish = msgObject.getMessage();

                            Log.e(TAG,"Publishing Message ");
                            publishProgress(msgToPublish);

                            String log="";
                            for(msg msgQueueObj:msgPriorityQueue){
                                log = log+msgQueueObj.getMessage()+"."+msgQueueObj.isStatus()+"--Priority--"+msgQueueObj.getSequence()+"--Sender--"+msgQueueObj.getSourcePort() +" $$$ ";
                            }
                            Log.e(TAG,"Queue: "+log);
                        }



                        /// publishing Message
                    }

                    // Closing the server socket
                    serverSock.close();

                }
            } catch (IOException exp) {
                Log.i(TAG, "Exception in Server Task");
                exp.printStackTrace();
            }
            return null;
        }

        // Reference :: PA1
        protected void onProgressUpdate(String... strings){

            /*
             * The following code displays what is received in doInBackground().
             */
            String message = strings[0];
            Log.i(TAG, "ProgressUpdate/Message:" + message);
            Log.i(TAG, "ProgressUpdate/Forwarding to Content Resolver");
            final TextView tv = (TextView) findViewById(R.id.textView1);
            tv.setText(message + "\n");

            try {

                contentValues.put("key", Integer.toString(counter));
                contentValues.put("value", message);
                counter++;

                // Passing the Values to insert <key,value> to a file.
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

                float proposalCounter = 0;

                String msgToSend = msgs[0];
                String message;

                PrintWriter pw = null;
                PrintWriter pw_agree = null;

                InputStreamReader in = null;
                BufferedReader br = null;


                String portSeq;
                port = getPort();
                portSeq = portNumber(port);
                /* ---- Sending Message ---- */
                Log.i(TAG, "ClientTask/Message : " + msgToSend);


                /* SENDING INIT MESSAGE */
                msgToSend = initMessage + "#" + 0 + "." + portSeq + "#" + port + "#" + msgToSend + "#"
                        + false + "#" + msgToSend + ":" + port;
                // messageFormat = messageFormat.toString();
                Log.i(TAG, "ClientTask/Formatted Message :" + msgToSend);

                for (String remotePort : REMOTE_PORT) {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }),
                            Integer.parseInt(remotePort));


                    if ((msgToSend != null || !msgToSend.equals("")) && socket.isConnected()) {

                        /*
                         * TODO: Fill in your client code that sends out a message.
                         */

                        /*
                         * Printing an Output Stream of bytes to text-output stream through the
                         * specified socket.
                         */

                        pw = new PrintWriter(socket.getOutputStream(), true);
                        pw.println(msgToSend);

                        /*
                         * Closing PrintWriter Stream and release any system resources associated along
                         * with it. Closing the Socket and discard / stop data waiting to be sent.
                         */

                        //Thread.sleep(10);

                        try {
                            /* RECIEVING PROPOSAL */
                            in = new InputStreamReader(socket.getInputStream());

                            // Reading text from the Character Stream (Instance of InputStreamReader).
                            br = new BufferedReader(in);

                            message = br.readLine();

                            if (!(message == null) && !message.equals("")) {
                                msg rcvdMessage = new msg(message);
                                if (rcvdMessage.getMsgType().equalsIgnoreCase(proposal)) {
                                    if (proposalCounter < (rcvdMessage.getSequence())) {
                                        proposalCounter = rcvdMessage.getSequence();
                                    }

                                    Log.i(TAG, "Dest Port : " + rcvdMessage.getDestPort());

                                    FailureList.put(rcvdMessage.getDestPort(),true);

                                    /*successList.add(rcvdMessage.getSourcePort());
                                    Log.e(TAG,"List Display" + successList.size());
                                    for(Integer m:successList){
                                        Log.e(TAG,"#" + m);
                                    }*/
                                }

                            }

                            /*inputStream.close();
                            reader.close();*/
                        } catch (SocketException se){
                            se.printStackTrace();
                            String p = getPort();
                            Log.e(TAG,"Socket Exception Found : " + se + "#" + socket);
                            Log.e(TAG,"Socket Exception Found : " + se + "#" + socket.getPort());
                            int portArr = socket.getPort();
                            Log.e(TAG,"Socket Exception Found : " + se + "#" + portArr);
                            //Log.e(TAG,"Socket Exception Found at Port : " + se + "#" + port);
                            //Log.e(TAG,"Socket Exception Found at Port New : " + se + "#" + p);
                            failedPort = String.valueOf(portArr);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.e(TAG,"Exception Found at Socket : " + socket);
                        }

                        Log.e(TAG,"Client/Success Size : " + successList.size());
                        Log.e(TAG,"List : " + FailureList.size() + "#" + FailureList.values());

                    }

                    pw.close();
                    socket.close();
                }

                Log.i(TAG, "ClientTask/RecievedMessage ProposalMax : " + proposalCounter);

                for(String remPorts:REMOTE_PORT){
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }),
                            Integer.parseInt(remPorts));

                    String recievedMessage = msgs[0];

                    //msg msg_agree = new msg(recievedMessage);
                    //String proposalMsg = msg_agree.getStr_msg();
                    //String[] arrProposalMsg = recievedMessage.split("#");
                    String agreeMsg = agreement + "#" + proposalCounter + "#" +recievedMessage.trim() + "#" + failedPort + "\n";
                    //msg_agree.setMsgType(agreement);
                    //msg_agree.setSequence(proposalCounter);

                    //Log.i(TAG,"ClientTask/Sequence Agreement : " + msg_agree.getSequence());
                    //Log.i(TAG,"ClientTask/MSGTYPE Agreement : " + msg_agree.getMsgType());
                    pw_agree = new PrintWriter(socket.getOutputStream(), true);
                    pw_agree.println(agreeMsg);
                    //pw_agree.println(msg_agree.getStr_msg());
                    Log.i(TAG,"ClientTask/Agreement Message : " + agreeMsg);

                    pw_agree.close();
                    socket.close();
                }

                /* ---- FAILURE DETECTION ---- */
                for(String remPorts:REMOTE_PORT){

                    Socket socket = new Socket(InetAddress.getByAddress(new byte[] { 10, 0, 2, 2 }),
                            Integer.parseInt(remPorts));


                    if(socket.isConnected()){
                        //String failure = "FAILED#";
                        String failNode = String.valueOf(failedPort);

                        if(failNode!=null && !failNode.equals("") && !failNode.equals("null") && !failNode.equals(remPorts)){
                            pw_agree = new PrintWriter(socket.getOutputStream(), true);
                            pw_agree.println(failNode);
                            //pw_agree.println(msg_agree.getStr_msg());
                            Log.i(TAG,"ClientTask/Failure Sending: " + "FAILED" + "#" + failNode + "Sending to --->" + "#" + socket.getPort() );
                            Log.i(TAG,"Sending from port :" + socket.getPort());
                            //pw_agree.close();

                        }
                    }


                    socket.close();
                }

            } catch (UnknownHostException e) {
                Log.e(TAG, "ClientTask UnknownHostException");
            } catch (IOException e) {
                Log.e(TAG, "ClientTask socket IOException");
            } /*catch (InterruptedException e) {
                Log.e(TAG, "Interrupted Exception raised" + e);
            }*/

            return null;
        }
    }

    /* ----- Map Constructor ---- */

    public class msg {
        String msgType;
        Float sequence;
        int sourcePort;
        String message;
        boolean status;
        String str_msg;
        int destPort;

        /* ---- Constructor ---- */
        public msg(String msgType, float sequence, int sourcePort, String message, boolean status) {
            this.msgType = msgType;
            this.sequence = sequence;
            this.sourcePort = sourcePort;
            this.message = message;
            this.status = status;
            //this.string_msg = msgType + "#" + sequence + "#" + sourcePort + "#" + message + "#" + status + "#" + UID;
        }

        public msg(String msg) {
            String[] arrString = msg.split("#");
            this.msgType = arrString[0];
            this.sequence = Float.parseFloat(arrString[1]);
            this.sourcePort = Integer.parseInt(arrString[2]);
            this.message = arrString[3];
            this.status = Boolean.parseBoolean(arrString[4]);
            this.destPort = Integer.parseInt(arrString[5]);
            this.str_msg = msgType + "#" + sequence + "#" + sourcePort + "#" + message + "#" + status;
        }

        public int getDestPort() {
            return destPort;
        }

        public void setDestPort(int destPort) {
            this.destPort = destPort;
        }

        public String getStr_msg() {
            return str_msg;
        }

        public boolean isStatus() {
            return status;
        }

        public void setStatus(boolean status) {
            this.status = status;
        }

        public String getMsgType() {
            return msgType;
        }

        public void setMsgType(String msgType) {
            this.msgType = msgType;
        }

        public Float getSequence() {
            return sequence;
        }

        public void setSequence(Float sequence) {
            this.sequence = sequence;
        }

        public int getSourcePort() {
            return sourcePort;
        }

        public void setSourcePort(int sourcePort) {
            this.sourcePort = sourcePort;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

    }


}
