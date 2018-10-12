package activitystreamer.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.json.simple.JSONObject;

public class ActivityBroad {
	private JSONObject obj;
	private Map<String,Long> user;
	private long time;
	
       public ActivityBroad(JSONObject obj, Map<String,Long> user) {
    	   this.obj=obj;
    	   this.user=user;
    	   this.time=Long.parseLong((String) obj.get("timestamp"));
       }
       
       public Long getTimestamp() {
    	   return time;
       }
       
       @SuppressWarnings("unchecked")
	public ArrayList<JSONObject> message(){
    	   ArrayList<JSONObject> msgs=new ArrayList<JSONObject>();
  
    	   JSONObject Temp;
    	   for(String key:user.keySet())
    	   {
    		   if(user.get(key)<=time)
    		   {
    			 Temp=(JSONObject) obj.clone();
    			  Temp.put("reciever", key);
    			  msgs.add(Temp);
    		   }
    	   }
    	   
    	   Temp=(JSONObject) obj.clone();
			  Temp.put("reciever", "anonymous");
			  msgs.add(Temp);
			  
    	   for(int j=0;j<msgs.size();j++)
    		   System.out.println(msgs.get(j).toJSONString());
    	   
    	   return msgs;
       }
}
