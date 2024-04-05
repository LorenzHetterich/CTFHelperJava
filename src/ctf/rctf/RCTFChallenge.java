package ctf.rctf;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import utils.SerializableTypes;

import ctf.CTFApi;
import ctf.CTFChallenge;

public class RCTFChallenge extends CTFChallenge {

    private String id, name, category, description;
    protected Boolean solved;
    private SerializableTypes.StringList files;

    private transient RCTFApi api;

    public RCTFChallenge(RCTFApi api, String id, JsonObject json){
        this.id = id;
        this.updateFrom(json);
    }

    public void updateFrom(JsonObject json){
        this.name = json.get("name").getAsString();
        this.category = json.get("category").getAsString();
        this.description = json.get("description").getAsString();
        this.files = json.get("files").getAsJsonArray().asList().stream().map(x -> x.getAsJsonObject().get("url").getAsString()).collect(
            SerializableTypes.StringList::new, 
            List::add, 
            List::addAll
        );
    }

    @Override
    public void setApi(CTFApi api) {
        this.api = (RCTFApi)api;
    }

     @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        if(this.name == null){
            this.update();
        }
        return this.name;
    }

    @Override
    public String getCategory() {
        if(this.category == null) {
            this.update();
        }
        return this.category;
    }

    @Override
    public String getDescription() {
        if(this.description == null) {
            this.update();
        }
        return this.description;
    }

    @Override
    public List<String> getFiles() {
        if(this.files == null){
            this.update();
        }
        return this.files;
    }

    @Override
    public boolean submitFlag(String flag) {
        try{
            return api.POST(String.format("api/v1/challs/%s/submit", this.id), Map.of("flag", flag)).get().getAsJsonObject().get("kind").getAsString().equals("goodFlag");
        } catch(Exception e){
            return false;
        }
    }

    @Override
    public boolean isSolved() {
        if(this.solved == null){
            this.update();
        }
        if(this.solved == null){
            return false;
        }
        return this.solved;
    }

    @Override
    public void update() {
        JsonObject json = api.GET("api/v1/challs", Map.of()).get().getAsJsonObject();

        if(!json.get("kind").getAsString().equals("goodChallenges")){
            throw new RuntimeException("Failed to retrieve challenges");
        }

        this.updateFrom(json.get("data").getAsJsonArray().asList().stream().filter(x -> x.getAsJsonObject().get("id").getAsString().equals(this.id)).findFirst().get().getAsJsonObject());
        try{
            // make /me request to mark solved challenges as solved
            JsonObject me = api.GET("api/v1/users/me", Map.of()).get().getAsJsonObject();
            this.solved = me.get("data").getAsJsonObject().get("solves").getAsJsonArray().asList().stream().map(x -> x.getAsJsonObject().get("id").getAsString()).anyMatch(a -> a.equals(this.id));
        } catch(Exception e){

        }
    }

    @Override
    public String getUrl(){
        return String.format("%s/challs", api.endpoint);
    }
    
}
