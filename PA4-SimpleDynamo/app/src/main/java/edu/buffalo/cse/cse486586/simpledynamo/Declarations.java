package edu.buffalo.cse.cse486586.simpledynamo;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import android.util.Log;

public class Declarations {

    /* TAG for Logging */
    static final String TAG = SimpleDynamoProvider.class.getName();

    /* PORTS */
    static final int SERVER_PORT = 10000;
    static final String[] REMOTE_PORTS = new String[] {
            "11108",
            "11112",
            "11116",
            "11120",
            "11124"
    };

    /* REMOTE PORTS -- FOR REPLICA LIST */
    /* ORDER -> 5562,5556,5554,5558,5560 */
    /* 4,1,0,2,3 */
    public static final String PORT_5554 = "5554";
    public static final String PORT_5558 = "5558";
    public static final String PORT_5560 = "5560";
    public static final String PORT_5562 = "5562";
    public static final String PORT_5556 = "5556";


    /* NODES */
    public static String SUCCESSOR = "";
    public static String PREDECESSOR = "";

    /* REPLICATION LIST */
    /* REPLICATION with TWO SUCCESSOR NODES */
    /* ARRAY LIST */
    public String[] NODE_5554_REPLICAS = {PORT_5554,PORT_5558,PORT_5560};
    public String[] NODE_5558_REPLICAS = {PORT_5558,PORT_5560,PORT_5562};
    public String[] NODE_5560_REPLICAS = {PORT_5560,PORT_5562,PORT_5556};
    public String[] NODE_5562_REPLICAS = {PORT_5562,PORT_5556,PORT_5554};
    public String[] NODE_5556_REPLICAS = {PORT_5556,PORT_5554,PORT_5558};


    HashMap<String, String[]> NODE_ALL_REPLICAS = new HashMap<String, String[]>(){
        {
            put("5554",NODE_5554_REPLICAS);
            put("5556",NODE_5556_REPLICAS);
            put("5558",NODE_5558_REPLICAS);
            put("5560",NODE_5560_REPLICAS);
            put("5562",NODE_5562_REPLICAS);
        }
    };

    // Returns 5554, 55XX ....
    public String getReplicaNodes(String avdNumber){
        String replica_nodes = "";
        String[] node_replicas = NODE_ALL_REPLICAS.get(avdNumber);

        for(String node:node_replicas){
            if(!node.equalsIgnoreCase(avdNumber)){
                replica_nodes = replica_nodes + node + "#";
            }
        }
        return replica_nodes;
    }

}
