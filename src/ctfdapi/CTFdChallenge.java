package ctfdapi;

import com.google.gson.annotations.SerializedName;

public class CTFdChallenge {
	
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
