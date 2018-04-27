package activitystreamer.util;

import activitystreamer.server.Connection;

public class WaitingMessage {
             private Connection con;
             private String command;
             private String key;
             private String[] recieve;
             private int expect;
             private int num;
             
           public WaitingMessage(Connection con, String command,String key,int expect) {
				this.con=con;
				this.command=command;
				this.expect=expect;
				this.key=key;
				this.num=0;
			}
             
            public void setNum() {
            	this.num++;
            }

            public void setRecieve(String recieve){
            	this.recieve[num]=recieve;
            	this.setNum();
            }

            public String getKey() {
            	return this.key;
            }
            
            public Connection getConnection() {
            	return this.con;
            }
            
			public boolean compare() {
            	return this.num==this.expect;
            }

}
