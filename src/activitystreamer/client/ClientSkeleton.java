package activitystreamer.client;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import activitystreamer.server.Connection;
import activitystreamer.server.Control;
import activitystreamer.util.Settings;

public class ClientSkeleton extends Thread {
	private static final Logger log = LogManager.getLogger();
	private static ClientSkeleton clientSolution;
	private TextFrame textFrame;
	private Socket socket;
	private DataInputStream in;
	private DataOutputStream out;
	private BufferedReader inreader;
	private PrintWriter outwriter;
	private boolean term = false;
	private JSONParser parser = new JSONParser();

	
	public static ClientSkeleton getInstance(){
		if(clientSolution==null){
			clientSolution = new ClientSkeleton();
		}
		return clientSolution;
	}
	
	public ClientSkeleton(){
		
		//Zhenyuan
			try { 
				if (!term) {             //xueyang
				socket = new Socket(Settings.getRemoteHostname(),Settings.getRemotePort());
				in = new DataInputStream(socket.getInputStream());
			    out = new DataOutputStream(socket.getOutputStream());
			    inreader = new BufferedReader(new InputStreamReader(in));
			    outwriter = new PrintWriter(out, true);
			}
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		//Zhenyuan
		
		textFrame = new TextFrame();
		start();
	}
	
	
	
	
	
	
	@SuppressWarnings("unchecked")
	public void sendActivityObject(JSONObject activityObj){
		//Zhenyuan
		outwriter.println(activityObj.toString());
		outwriter.flush(); 
		if (activityObj.containsValue("LOGOUT")) {
			ClientSkeleton.getInstance().disconnect();		//Xueyang
		}
		
		//Zhenyuan
	}
	
	//Xueyang
	public void disconnect(){
		if(!term){
			log.info("closing connection "+Settings.socketAddress(socket));
			try {
				term=true;
				outwriter.close();
				inreader.close();
				socket.close();
			} catch (IOException e) {
				log.error("received exception closing the connection "+Settings.socketAddress(socket)+": "+e);
			}
		}
	}
	//Xueyang
	
	public void run(){
		while(!term) {
		JSONObject incomingObj;
		try {
			incomingObj = (JSONObject) parser.parse(inreader.readLine());
			this.textFrame.setOutputText(incomingObj);
			String command = (String) incomingObj.get("command");
			
			if(command.equals("INVALID_MESSAGE")) {
				this.disconnect();
			}
			
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		}
	}
	
}
