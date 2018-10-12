package activitystreamer.util;

import org.json.simple.JSONObject;
import activitystreamer.server.Connection;

public class Message {
       private JSONObject msg;
       private Connection con;
       private String command; 
       
       public Message(){
    	   
       }
       
       public Message(JSONObject msg,Connection con,String command){
    	   this.msg=msg;
    	   this.con=con;
    	   this.command=command;
       }

	public JSONObject getMsg() {
		return msg;
	}

	public void setCon(Connection con) {
		this.con=con;
	}
	public Connection getCon() {
		return con;
	}

	public String getCommand() {
		return command;
	}
     
       
}
