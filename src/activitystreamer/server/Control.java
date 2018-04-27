package activitystreamer.server;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.util.Settings;
import activitystreamer.util.WaitingMessage;

public class Control extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ArrayList<Connection> connections;
	private static boolean term=false;
	private static Listener listener;
	private static Map<String,String> userList;
	private static Map<String,Integer> remoteList;
	private static ArrayList<Connection> serversList;
	private static String serverSecret = Settings.getSecret();
	private static String id = Settings.nextSecret();
	private static String localhost = Settings.getLocalHostname();
	private static int localport = Settings.getLocalPort();
	private static int load = 0;
	private static ArrayList<WaitingMessage> waitings;
	private static ArrayList<String> loginList;
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
		userList = new HashMap<String, String>();
		remoteList = new HashMap<String, Integer>();
		serversList = new ArrayList<Connection>();
		waitings= new  ArrayList<WaitingMessage>();
		loginList = new ArrayList<String>(); 
		
		// start a listener
		try {
			listener = new Listener();
		} catch (IOException e1) {
			log.fatal("failed to startup a listening thread: "+e1);
			System.exit(-1);
		}
		start();
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
				serversList.add(connections.get(0));
				//zhenyuan
				
			} catch (IOException e) {
				log.error("failed to make connection to "+Settings.getRemoteHostname()+":"+Settings.getRemotePort()+" :"+e);
				System.exit(-1);
			}
		}else {
			//serverSecret=Settings.nextSecret();
			log.info("server secret is: "+serverSecret);
		}
	}
	
	/*
	 * Processing incoming messages from the connection.
	 * Return true if the connection should close.
	 */
	@SuppressWarnings("unchecked")
	public synchronized boolean process(Connection con,String msg){
		//zhenyuan
		try {
			JSONObject incomingObj = (JSONObject) this.parser.parse(msg);
			cmdOperator(con,incomingObj);          //xueyang
		} catch (Exception e) {
			log.error("invalid JSON object");
			JSONObject outgoingObj = new JSONObject();
			outgoingObj.put("command", "INVALID_MESSAGE");
			outgoingObj.put("command", "JSON parse error while parsing message");
			con.writeMsg(outgoingObj.toJSONString());
			control.connectionClosed(con);
			con.closeCon();
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
			log.info(cmd);
			if(incomingObj.containsKey("info")) {
				String info = (String) incomingObj.get("info");
				log.info(info);
			}
			switch(cmd){
				case "LOGIN":
					if(ifLogin(incomingObj)) {
						load++;
						outgoingObj = loginSuccess(incomingObj);
						redirect(con, incomingObj);
					}else {
						outgoingObj = loginFail(incomingObj);
					}
					con.writeMsg(outgoingObj.toJSONString());
					break;
				case "REGISTER":
					register(con, incomingObj);
					break;
				case "LOGOUT" :
					if(!incomingObj.get("username").toString().equals("anonymous")) {
						loginList.remove(incomingObj.get("username").toString());
					}
					control.connectionClosed(con);  //remove connection
					con.closeCon();
					term=true;         //disconnect
					load--;
					break;
				case "AUTHENTICATE":
					serversList.add(con);
					if(authenticateFail(incomingObj)) {
						outgoingObj = authenticateReply(incomingObj);
						con.writeMsg(outgoingObj.toJSONString());
						serversList.remove(con);
						control.connectionClosed(con);  //remove connection
						con.closeCon();
					}
					break;
				case "AUTHTENTICATION_FAIL":
					serversList.remove(con);
					control.connectionClosed(con);  //remove connection
					con.closeCon();
					break;
				case "SERVER_ANNOUNCE":
					log.info("incoming server announce");
					
					remoteList.put(incomingObj.get("hostname").toString()+":"+incomingObj.get("port").toString(), Integer.valueOf(incomingObj.get("load").toString()));
					
					log.info("load is "+load);
					for(int i=0;i< connections.size();i++) {
						if (i != connections.indexOf(con) && serversList.contains(connections.get(i))) {
						connections.get(i).writeMsg(incomingObj.toJSONString());
						}
					}
					break;
					
				case "ACTIVITY_MESSAGE":
					activityMessage(con,incomingObj);
					break;
				case "ACTIVITY_BROADCAST":
					outgoingObj = incomingObj;
					for(int i=0;i< connections.size();i++) {
						if (i != connections.indexOf(con)) {
						connections.get(i).writeMsg(outgoingObj.toJSONString());
						}
					}
				case "LOCK_REQUEST":
					lockReply(con,incomingObj);
					break;
				case "LOCK_ALLOWED":
					lockAllowed(con,incomingObj);
					break;
				case "LOCK_DENIED":
					lockDenied(con,incomingObj);
					break;
				default:
					outgoingObj = new JSONObject();
					outgoingObj.put( "command", "INVALID_MESSAGE");
					outgoingObj.put( "info", "No command "+cmd);
					con.writeMsg(outgoingObj.toJSONString());
					if(serversList.contains(con)) {
						serversList.remove(con);}
					control.connectionClosed(con);  //remove connection
					con.closeCon();
					break;
		}
		} else {
			outgoingObj = new JSONObject();
			outgoingObj.put( "command", "INVALID_MESSAGE");
			outgoingObj.put( "info","the received message did not contain a command");
			con.writeMsg(outgoingObj.toJSONString());
			
		}
		
		}
	@SuppressWarnings("unchecked")
	public synchronized static void lockDenied(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj=new JSONObject();
		String username=(String)incomingObj.get("username");
		String secret=(String)incomingObj.get("secret");
		
		if(userList.containsKey(username)&&userList.get(username).equals(secret))
			userList.remove(username);
		
		for(WaitingMessage temp : waitings)
			if(temp.getKey().equals(username+secret)) {
				if(serversList.contains(temp.getConnection())) {
					outgoingObj.put("command", "LOCK_DENIED");
				    outgoingObj.put("username", username);
				    outgoingObj.put("secret", secret);
				}
				else {
				    outgoingObj.put("command", "REGISTER_FAILED");
					outgoingObj.put("info", "register success for"+username);
				}
				temp.getConnection().writeMsg(outgoingObj.toJSONString());
	
		      for(Connection tempCon: serversList) {
			     if(!tempCon.equals(con))
				     tempCon.writeMsg(outgoingObj.toJSONString());
		      }
				waitings.remove(temp);
				break;
			}		
	}

	//
	@SuppressWarnings("unchecked")
	public synchronized static void lockAllowed(Connection con, JSONObject incomingObj) {
		String username=(String)incomingObj.get("username");
		String secret=(String)incomingObj.get("secret");
		log.info("6");
		for(WaitingMessage temp : waitings) {
			log.info("5");
			if(temp.getKey().equals(username+secret)) {
				temp.setNum();
				log.info("4");
				if(temp.compare()) {
					log.info("3");
					JSONObject outgoingObj=new JSONObject();
					if(serversList.contains(temp.getConnection())) {
						outgoingObj.put("command", "LOCK_ALLOWED");
					    outgoingObj.put("username", username);
					    outgoingObj.put("secret", secret);
					    userList.put(username,secret);
					}
					else {
						log.info("REGISTER SUCCESS!");
					    outgoingObj.put("command", "REGISTER_SUCCESS");
						outgoingObj.put("info", "register success for "+username);
						temp.getConnection().writeMsg(outgoingObj.toJSONString());
						
						load++;
						JSONObject loginObj = new JSONObject();
						log.info("LOGIN SUCCESS!");
						loginObj.put("command", "LOGIN_SUCCESS");
						loginObj.put("info", "logged in as user "+username);
						temp.getConnection().writeMsg(loginObj.toJSONString());
						userList.put(username, secret);
						log.info("falg master");
						redirect(temp.getConnection(), incomingObj);
					}
					waitings.remove(temp);
					break;
				}
			}
	}
	}

	//zhenyuan&xueyang
	
	public static boolean ifLogin(JSONObject incomingObj) {
		boolean successLogin = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		if(username.equals("anonymous")) {
			successLogin = true;
		}else if(userList.get(username).equals(secret)) {
				successLogin = true;
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
		loginList.add(username);
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
		for(String key: remoteList.keySet()) {
			log.info("remote load is "+remoteList.get(key));
			log.info("load is "+load);
			if(remoteList.get(key).intValue() < load - 1) {
				log.info("REDIRECT!");
				if(!incomingObj.get("username").toString().equals("anonymous")) {
					loginList.remove(incomingObj.get("username").toString());
				}
				outgoingObj.put("command", "REDIRECT");
				outgoingObj.put("hostname", key.substring(0, key.indexOf(":")));
				outgoingObj.put("port", Integer.valueOf(key.substring(key.indexOf(":")+1)));
				con.writeMsg(outgoingObj.toJSONString());
				con.closeCon();
				load--;
				break;
			}
		}
		
	}
	//zhenyuan
	
	//zhenyuan
	@SuppressWarnings("unchecked")
	public static void register(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		if(username.equals("anonymous"))
			redirect(con,incomingObj);
          
		//TODO verify if successRegister
		else if(!userList.containsKey(username)) {
			if (serversList.size()==0) {
				log.info("REGISTER SUCCESS!");
			    outgoingObj.put("command", "REGISTER_SUCCESS");
				outgoingObj.put("info", "register success for"+username);
				con.writeMsg(outgoingObj.toJSONString());
				loginList.add(username);
				load++;
				JSONObject loginObj = new JSONObject();
				log.info("LOGIN SUCCESS!");
				loginObj.put("command", "LOGIN_SUCCESS");
				loginObj.put("info", "logged in as user "+username);
				con.writeMsg(loginObj.toJSONString());
					
				
			}
			else {
			userList.put(username,secret);
			outgoingObj.put("command", "LOCK_REQUEST");
			outgoingObj.put("username", username);
			outgoingObj.put("secret", secret);
			waitings.add(new WaitingMessage(con,"LOCK_REQUEST",username+secret,serversList.size()));
			for(int i=0;i<serversList.size();i++) {
				serversList.get(i).writeMsg(outgoingObj.toJSONString());	
			}
			}
		}
		else {
			outgoingObj.put("command", "REGISTER_FAILED");
			outgoingObj.put("info", username+" is already registered with the system");
			con.writeMsg(outgoingObj.toJSONString());
		}
	}
	//zhenyuan

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
	public static void activityMessage(Connection con,JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		boolean successLogin = false;
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		String activity = (String) incomingObj.get("activity");
		
		if(username.equals("anonymous")) {
			successLogin = true;
		}else if(loginList.contains(username) && userList.get(username).equals(secret)) {
				successLogin = true;
		}
		
		if(successLogin) {
			outgoingObj.put("command", "ACTIVITY_BROADCAST");
			outgoingObj.put("activity", activity);
			for(int i=0;i< connections.size();i++) {
				if (i != connections.indexOf(con)) {
				connections.get(i).writeMsg(outgoingObj.toJSONString());
				}
			}
		}
		else {
			   outgoingObj.put("command", "AUTHENTICATION_FAIL");
			   outgoingObj.put("info", "anonymous or the user is not login");
			   con.writeMsg(outgoingObj.toJSONString());
			   loginList.remove(username);
			   con.closeCon();
			  }
	}

	@SuppressWarnings("unchecked")
	public static void lockReply(Connection con, JSONObject incomingObj) {
		JSONObject outgoingObj = new JSONObject();
		String username = (String) incomingObj.get("username");
		String secret = (String) incomingObj.get("secret");
		
		if(userList.containsKey(username)){
			outgoingObj.put("command", "LOCK_DENIED");
			outgoingObj.put("username", username);
			outgoingObj.put("secret", secret);
			con.writeMsg(outgoingObj.toJSONString());
		}else {
			if(serversList.size()==1) {
				log.info("2");
				outgoingObj.put("command", "LOCK_ALLOWED");
				outgoingObj.put("username", username);
				outgoingObj.put("secret", secret);
				userList.put(username, secret);
				con.writeMsg(outgoingObj.toJSONString());
				log.info("7");
			}
			else {
			waitings.add(new WaitingMessage(con,"LOCK_REQUEST",username+secret,serversList.size()-1));
			for(int i=0;i<serversList.size();i++)
				if(serversList.get(i)!=con)
			    serversList.get(i).writeMsg(incomingObj.toJSONString());	
			}
		}
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
				if (serversList.contains(connections.get(i))) {
					connections.get(i).writeMsg(outgoingObj.toJSONString());
					log.info("outgoing server announce");
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
