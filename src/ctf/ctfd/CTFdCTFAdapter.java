package ctf.ctfd;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ctf.CTF;
import ctfdapi.CTFdApi;

public class CTFdCTFAdapter implements CTF {

    // keep accessible for subclasses
    protected CTFdApi ctfd;
    protected String flagRegex;

    public CTFdCTFAdapter(String endpoint, String flagRegex){
        this.ctfd = new CTFdApi(endpoint);
        this.flagRegex = flagRegex;
    }

    @Override
    public boolean login(String username, String password) {
        ctfd.defaultLogin(username, password);
        return this.ctfd.simpleReq(HttpRequest.newBuilder().GET().uri(URI.create(this.ctfd.url))).get().contains("logout");
    }

    @Override
    public List<? extends CTFdChallengeAdapter> getChallenges() {
        return Arrays.stream(this.ctfd.getChallengeList(Map.of()).get()).map(x -> new CTFdChallengeAdapter(this.ctfd, x)).toList();
    }

    @Override
    public Optional<String> getFlagRegex() {
        return Optional.ofNullable(this.flagRegex);
    }
    
}
