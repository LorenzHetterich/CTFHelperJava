package ctf.ctfd;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import ctf.CTFApi;
import ctf.CTFChallenge;
import utils.SerializableTypes.StringList;

public class CTFdChallenge extends CTFChallenge{
    
    public int id;
    private String name, category, description;
    private Boolean solved;
    private StringList files;

    private transient CTFdApi api;
    

    public CTFdChallenge(CTFdApi api, int id, JsonObject json){
        this.api = api;
        this.id = id;
        this.updateFrom(json);
    }

    private void updateFrom(JsonObject json){
        if(json.has("id")){
            if(json.get("id").getAsInt() != this.id){
                throw new IllegalStateException("Challenge Id Changed! This is fatal!");
            }
        }
        if(json.has("name")){
            this.name = json.get("name").getAsString();
        }
        if(json.has("description")){
            this.description = json.get("description").getAsString();
        }
        if(json.has("category")){
            this.category = json.get("category").getAsString();
        }
        if(json.has("solved_by_me")){
            this.solved = json.get("solved_by_me").getAsBoolean();
        }
        if(json.has("files")){
            this.files = json.get("files").getAsJsonArray().asList().stream().map(x -> String.format("%s/%s", api.endpoint, x))
            .collect(
                StringList::new, 
                List::add, 
                List::addAll
            );
        }
    }

    @Override
    public void setApi(CTFApi api) {
        this.api = (CTFdApi) api;
    }

    @Override
    public int getId() {
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
        return this.api.POST("/api/v1/challenges/attempt",
                Map.of("challenge_id", id, "submission", flag)).get().getAsJsonObject().get("status").getAsString().equals("correct");
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
        this.updateFrom(api.GET(String.format("/api/v1/challenges/%d", id), Map.of()).get().getAsJsonObject());
    }

    @Override
    public String getUrl(){
        return  String.format("%s/challenges#%s-%d", api.endpoint, URLEncoder.encode(this.name, StandardCharsets.UTF_8), this.id);
    }
}
