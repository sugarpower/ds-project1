package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static boolean term=false;
	private static Listener listener;
	private static ArrayList<String> usernameList;
	private static ArrayList<String> secretList;
	private static ArrayList<Integer> remotePortList;
	private static ArrayList<String> remoteNameList;
	private static ArrayList<Integer> remoteLoadList;
	private static String serverSecret = Settings.getSecret();
	private static String id = Settings.nextSecret();
	private static String localhost = Settings.getLocalHostname();
	private static int localport = Settings.getLocalPort();
	private static int load = 0 ;
	private JSONParser parser = new JSONParser();
	
	protected static Control control = null;
	
	public static Control getInstance() {
		if(control==null){
			control=new Control();	
		} 
		return control;
	}
	
	public Control() {
		// initialize the connections array
		connections = new ArrayList<Connection>();
		usernameList = new ArrayList<String>();
		secretList = new ArrayList<String>();
		remotePortList = new ArrayList<Integer>();
		remoteNameList = new ArrayList<String>();
		remoteLoadList = new ArrayList<Integer>();
		load = 0;
		// start a listener
		try {
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}	
	}
	
	@SuppressWarnings("unchecked")
	public void initiateConnection(){
		// make a connection to another server if remote hostname is supplied
		if(Settings.getRemoteHostname()!=null){
			try {
				outgoingConnection(new Socket(Settings.getRemoteHostname(),Settings.getRemotePort()));
				
				//zhenyuan
				JSONObject outgoingObj = new JSONObject();
				outgoingObj.put("command", "AUTHENTICATE");
				outgoingObj.put("secret", serverSecret);
				connections.get(0).writeMsg(outgoingObj.toJSONString());
				//zhenyuan
				
			} catch (IOException e) {
				log.error("failed to make connection to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" :"+e);
				System.exit(-1);
			}
		}
	}
	
	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	public synchronized boolean process(Connection con,String msg){
		//zhenyuan
		try {
			JSONObject incomingObj = (JSONObject) this.parser.parse(msg);
			cmdOperator(con,incomingObj);          //xueyang
		} catch (Exception e) {
			log.error("invalid JSON object");
		}
		//zhenyuan
		return false;
	}
	
	//zhenyuan&xueyang
	@SuppressWarnings("unchecked")
	public static void cmdOperator(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = null;
		
		
		if(incomingObj.containsKey("command")) {
			String cmd = (String) incomingObj.get("command");
			switch(cmd){
				case "LOGIN":
					if(ifLogin(incomingObj)) {
						outgoingObj = loginSuccess(incomingObj);
						redirect(con, incomingObj);
					}else {
						outgoingObj = loginFail(incomingObj);
					}
					con.writeMsg(outgoingObj.toJSONString());
					load++;
					break;
				case "REGISTER":
					outgoingObj = register(incomingObj);
					con.writeMsg(outgoingObj.toJSONString());
					break;
				case "LOGOUT" :
					//TODO remove it from loginList
					control.connectionClosed(con);  //remove connection
					term=true;         //disconnect //see if it works
					load--;
					break;
				case "AUTHENTICATE":
					if(authenticateFail(incomingObj)) {
						outgoingObj = authenticateReply(incomingObj);
						con.writeMsg(outgoingObj.toJSONString());
						control.connectionClosed(con);  //remove connection
					}else {
						con.setAuthenticatedServer(true);
					}
					break;
				case "AUTHENTICATE_FAIL":
					control.connectionClosed(con);  //remove connection
					term = true;
					break;
				case "SERVER_ANNOUNCE":
					remoteLoadList.add((Integer) incomingObj.get("load"));
					remoteNameList.add(incomingObj.get("hostname").toString());
					remotePortList.add((Integer) incomingObj.get("port"));
					for(int i=0;i< connections.size();i++) {
						if (i != connections.indexOf(con) && connections.get(i).getAuthenticatedServer()) {
						connections.get(i).writeMsg(incomingObj.toJSONString());
						}
					}
					break;
					
				case "ACTIVITY_MESSAGE":
					outgoingObj = activityMessage(incomingObj);
					for(int i=0;i< connections.size();i++) {
						if (i != connections.indexOf(con)) {
						connections.get(i).writeMsg(outgoingObj.toJSONString());
						}
					}
					break;
				case "ACTIVITY_BROADCAST":
					outgoingObj = incomingObj;
					for(int i=0;i< connections.size();i++) {
						if (i != connections.indexOf(con)) {
						connections.get(i).writeMsg(outgoingObj.toJSONString());
						}
					}
				case "LOCK_REQUEST":
					outgoingObj = lockReply(incomingObj);
					con.writeMsg(outgoingObj.toJSONString());
					break;
				default:
					outgoingObj = new JSONObject();
					outgoingObj.put( "command", "INVALID_MESSAGE");
					outgoingObj.put( "info", "JSON parse error while parsing message");
					con.writeMsg(outgoingObj.toJSONString());
		}
		} else {
			outgoingObj = new JSONObject();
			outgoingObj.put( "command", "INVALID_MESSAGE");
			outgoingObj.put( "info","the received message did not contain a command");
			con.writeMsg(outgoingObj.toJSONString());
			
		}
		
		}
		
	
	//zhenyuan&xueyang
	
	public static boolean ifLogin(JSONObject incomingObj) {
		boolean successLogin = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		if(username.equals("anonymous")) {
			successLogin = true;
		}else if(usernameList.contains(username) && secretList.contains(secret)) {
			if(usernameList.indexOf(username) == secretList.indexOf(secret)) {
				successLogin = true;
			}
		}
		return successLogin;
	}
	
	
	
	//zhenyuan
	@SuppressWarnings("unchecked")
	public static JSONObject loginSuccess(JSONObject incomingObj) {
		
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		
		log.info("LOGIN SUCCESS!");
		outgoingObj.put("command", "LOGIN_SUCCESS");
		outgoingObj.put("info", "logged in as user "+username);
		
		return outgoingObj;
	}
	//zhenyuan
	
	//zhenyuan
	@SuppressWarnings("unchecked")
	public static JSONObject loginFail(JSONObject incomingObj) {
		
		JSONObject outgoingObj = new JSONObject();

		outgoingObj.put("command", "LOGIN_FAILED");
		outgoingObj.put("info", "attempt to login with wrong secret");	
		
		return outgoingObj;
	}
	//zhenyuan
	
	//zhenyuan
	@SuppressWarnings("unchecked")
	public static void redirect(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		for(int i=0; i<remoteLoadList.size(); i++) {
			if(remoteLoadList.get(i) <= load - 2) {
				outgoingObj.put("command", "REDIRECT");
				outgoingObj.put("hostname", remoteNameList.get(i));
				outgoingObj.put("port", remotePortList.get(i));
				con.writeMsg(outgoingObj.toJSONString());
				con.closeCon();
				break;
			}		
		}
	}
	//zhenyuan
	
	//zhenyuan
	@SuppressWarnings("unchecked")
	public static JSONObject register(JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		boolean successRegister = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		//TODO verify if successRegister
		//assume successfully Register
		successRegister = true;
		
		if(successRegister) {
			log.info("REGISTER SUCCESS!");
			usernameList.add(username);
			secretList.add(secret);
			outgoingObj.put("command", "REGISTER_SUCCESS");
			outgoingObj.put("info", "register success for "+username);
		}else {
			outgoingObj.put("command", "REGISTER_FAILED");
			outgoingObj.put("info", username+" is already registered with the system");
		}
		
		return outgoingObj;
	}
	//zhenyuan
	
	@SuppressWarnings("unchecked")
	public static JSONObject lockRequest(JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		outgoingObj.put( "command" , "LOCK_REQUEST");
		outgoingObj.put("username", username);
		outgoingObj.put("secret", secret);
		return outgoingObj;
	}
	

	public static boolean authenticateFail(JSONObject incomingObj) {
		String secret = (String) incomingObj.get("secret");
		return !secret.equals(serverSecret);
	}
	
	@SuppressWarnings("unchecked")
	public static JSONObject authenticateReply(JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String secret = (String) incomingObj.get("secret");
		outgoingObj.put( "command" , "AUTHENTICATION_FAIL");
		outgoingObj.put(  "info" , "the supplied secret is incorrect: "+secret);
		return outgoingObj;
	}
	
	@SuppressWarnings("unchecked")
	public static JSONObject activityMessage(JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		boolean successLogin = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		String activity = (String) incomingObj.get("activity");
		
		if(username.equals("anonymous")) {
			successLogin = true;
		}else if(usernameList.contains(username) && secretList.contains(secret)) {
			if(usernameList.indexOf(username) == secretList.indexOf(secret)) {
				successLogin = true;
			}
		}
		
		if(successLogin) {
			outgoingObj.put("command", "ACTIVITY_BROADCAST");
			outgoingObj.put("activity", activity);
		}
		return outgoingObj;
	}

	@SuppressWarnings("unchecked")
	public static JSONObject lockReply(JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		if(usernameList.contains(username)){
			outgoingObj.put("command", "LOCK_DENIED");
		}else {
			outgoingObj.put("command", "ALLOWED");
			usernameList.add(username);
			secretList.add(secret);
		}
		outgoingObj.put("username", username);
		outgoingObj.put("secret", secret);
		return outgoingObj;
	}
	
	
	/*
	 * The connection has been closed by the other party.
	 */
	public synchronized void connectionClosed(Connection con){
		if(!term) connections.remove(con);
	}
	
	/*
	 * A new incoming connection has been established, and a reference is returned to it
	 */
	public synchronized Connection incomingConnection(Socket s) throws IOException{
		log.debug("incomming connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
		
	}
	
	/*
	 * A new outgoing connection has been established, and a reference is returned to it
	 */
	public synchronized Connection outgoingConnection(Socket s) throws IOException{
		log.debug("outgoing connection: "+Settings.socketAddress(s));
		Connection c = new Connection(s);
		connections.add(c);
		return c;
		
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void run(){
		log.info("using activity interval of "+Settings.getActivityInterval()+" milliseconds");
		while(!term){
			// do something with 5 second intervals in between
			
			//zhenyuan
			JSONObject outgoingObj = new JSONObject();
			outgoingObj.put("command", "SERVER_ANNOUNCE");
			outgoingObj.put("id",id);
			outgoingObj.put("load",load);
			outgoingObj.put("hostname",localhost);
			outgoingObj.put("port",localport);
			for(int i=0;i< connections.size();i++) {
				if(connections.get(i).getAuthenticatedServer()) {
					connections.get(i).writeMsg(outgoingObj.toJSONString());
				}		
			}
			//zhenyuan
			
			try {
				Thread.sleep(Settings.getActivityInterval());
			} catch (InterruptedException e) {
				log.info("received an interrupt, system is shutting down");
				break;
			}
			if(!term){
				log.debug("doing activity");
				term=doActivity();
			}
			
		}
		log.info("closing "+connections.size()+" connections");
		// clean up
		for(Connection connection : connections){
			connection.closeCon();
		}
		listener.setTerm(true);
	}
	
	public boolean doActivity(){
		return false;
	}
	
	public final void setTerm(boolean t){
		term=t;
	}
	
	public final ArrayList<Connection> getConnections() {
		return connections;
	}
}
