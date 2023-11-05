package ctf.ctfd;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.List;

import ctf.CTFChallenge;
import ctf.CTFFile;
import ctfdapi.CTFdApi;
import ctfdapi.CTFdChallenge;

public class CTFdChallengeAdapter implements CTFChallenge {
    
    public class FileAdapter implements CTFFile{

        public final String uri;

        public FileAdapter(String uri){
            this.uri = uri;
        }

        @Override
        public String getName() {
            String[] s = URI.create(uri).getPath().split("/");
            return s[s.length - 1];
        }

        @Override
        public byte[] getContent() {
            return CTFdChallengeAdapter.this.ctfd.simpleRawReq(HttpRequest.newBuilder().uri(URI.create(this.uri)).GET()).get();
        }

    }

    protected CTFdApi ctfd;
    
    protected final int id;
    protected String name;
    protected String description;
    protected String category;
    protected int points;
    protected int attempts;
    protected int solves;
    protected boolean isSolved;
    protected List<FileAdapter> files;

    public CTFdChallengeAdapter(CTFdApi ctfd, CTFdChallenge challenge){
        this.ctfd = ctfd;
        this.id = challenge.id;
        this.files = new ArrayList<>();
        this.update(challenge);
    }

    private static String s(String abc){
        if(abc == null){
            return "";
        }
        return abc;
    }

    private void update(CTFdChallenge challenge){
        this.name = s(challenge.name);
        this.description = challenge.description;
        this.category = s(challenge.category);
        this.points = challenge.value;
        this.attempts = challenge.maxAttempts == 0 ? -1 : challenge.maxAttempts;
        this.solves = challenge.solves;
        this.isSolved = challenge.solvedByMe;
        this.files.clear();
        if(challenge.files != null){
            for(String url : challenge.files){
                this.files.add(new FileAdapter(String.format("%s/%s", ctfd.url, url)));
            }   
        }
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getDescription() {
        if(this.description == null){
            this.refresh();
        }
        return this.description;
    }

    @Override
    public String getCategory() {
        return this.category;
    }

    // TODO
    @Override
    public int getDifficulty() {
        return 1;
    }

    @Override
    public int getAttempts() {
        if(this.description == null){
            this.refresh();
        }
        return attempts;
    }

    @Override
    public int getPoints() {
        return points;
    }

    @Override
    public int getSolves() {
        return solves;
    }

    @Override
    public boolean isSolved() {
        return isSolved;
    }

    @Override
    public boolean refresh() {
        try{
            this.update(this.ctfd.getChallenge(this.id).get());
            this.description = s(this.description);
            return true;
        } catch(Throwable t){
            this.description = s(this.description);
            return false;
        }
    }

    @Override
    public boolean submitFlag(String flag) {
        if(this.attempts != -1){
            this.attempts --;
        }
        return this.ctfd.postChallengeAttempt(this.id, flag).get().status.equals("correct");
    }

    @Override
    public String toString() {
        return "CTFdChallengeAdapter [id=" + id + ", name=" + name + ", description=" + description + ", category="
                + category + ", points=" + points + ", attempts=" + attempts + ", solves=" + solves + ", isSolved="
                + isSolved + "]";
    }

    @Override
    public List<? extends FileAdapter> getFiles() {
        if(this.description == null){
            this.refresh();
        }
        return this.files;
    }
    
}
