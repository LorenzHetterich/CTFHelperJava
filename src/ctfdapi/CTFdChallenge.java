package ctfdapi;

import com.google.gson.annotations.SerializedName;

public class CTFdChallenge {
	
	public static class Hint {
		
		@SerializedName("id")
		public int id;
	
		@SerializedName("cost")
		public int cost;
	
		@SerializedName("challenge_id")
		public int challengeId;
	
		@SerializedName("content")
		public String content;
	
		@SerializedName("type")
		public String type;
	
		@SerializedName("challenge")
		public int challenge;
	
		// TODO
		@SerializedName("requirements")
		public Object requirements;

	}

	public static class Requirements {
		
		@SerializedName("prerequisites")
		public int[] prerequisites;

	}

	public static class Solve {

   		@SerializedName("account_id")
    	public int accountId;

    	@SerializedName("name")
    	public String name;

    	@SerializedName("date")
    	public String date;

    	@SerializedName("account_url")
    	public String accountUrl;

	}

	public static class Type {

		@SerializedName("id")
		public String id;
	
	
		@SerializedName("name")
		public String name;
		
		// TODO
		@SerializedName("templates")
		public Object templates;
	
		// TODO
		@SerializedName("scripts")
		public Object scripts;
	
		@SerializedName("create")
		public String create;
		
	}

	public static class Flag {

		@SerializedName("data")
    	public String data;

    	@SerializedName("id")
    	public int id;

    	@SerializedName("challenge_id")
    	public int challengeId;

    	@SerializedName("content")
    	public String content;

    	@SerializedName("type")
    	public String type;
    
    	@SerializedName("challenge")
    	public int challenge;

	}

	@SerializedName("id")
	public int id;
	
	@SerializedName("name")
	public String name;
	
	@SerializedName("description")
	public String description;
	
	@SerializedName("connection_info")
	public String connectionInfo;
	
	@SerializedName("next_id")
	public int nextId;
	
	@SerializedName("max_attempts")
	public int maxAttempts;
	
	@SerializedName("value")
	public int value;
	
	@SerializedName("category")
	public String category;
	
	@SerializedName("type")
	public String type;
	
	@SerializedName("state")
	public String state;
	
	// TODO: get actual type correct!
	@SerializedName("requirements")
	public Object requirements;
	
	@SerializedName("solves")
	public int solves;
	
	@SerializedName("solved_by_me")
	public boolean solvedByMe;
	
}
