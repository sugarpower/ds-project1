package activitystreamer.util;

import org.json.simple.JSONObject;

public class WaitingReply {
	   private String command;
	   private String reciever;
	   private JSONObject activity;
	   private String timestamp;
	   private String key;
	   private String user;
	   
          public WaitingReply(JSONObject obj) {
        	this.command = (String) obj.get("command");
        	this.reciever = (String) obj.get("reciever");
      		this.timestamp=(String) obj.get("timestamp");
      		this.activity=(JSONObject) obj.get("activity");
      		this.user=(String)activity.get("authenticated_user");
      		setKey();
          }
          
         public void setKey() {
        	 this.key=user+reciever+timestamp;
         }
         
         public String getKey() {
        	 return this.key;
         }
         
         public String getUser() {
        	 return user;
         }
         
         @SuppressWarnings({ "unchecked", "null" })
		public String getMsg() {
        	 JSONObject msg = null;
        	 msg.put("command", "ACTIVITY_BROADCAST");
        	 msg.put("reciever", this.reciever);
        	 msg.put("activity", this.activity);
        	 msg.put("timestamp",this.timestamp);
        	 return msg.toJSONString();
         }
}
