package ctf.rctf;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;

import ctf.CTFApi;
import ctf.CTFChallenge;
import utils.SerializableTypes.StringMap;

import static utils.SerializableTypes.StringMap;

public class RCTFApi extends CTFApi {
    
    public String endpoint;
    public StringMap cookies = new StringMap();
    public StringMap headers = new StringMap();

    // non-serializable stuff
    private transient Gson g = new Gson();
    private transient HttpClient cl;

    public RCTFApi(String endpoint){
        this.endpoint = endpoint;
    }

    public void setHeader(String name, String value){
        headers.put(name, value);
    }

    private HttpClient getClient(){
        if(cl == null){
            cl = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).followRedirects(HttpClient.Redirect.ALWAYS).build();
        }
        return cl;
    }

    private Gson getGson(){
        if(g == null){
            g = new Gson();
        }
        return g;
    }

    private static String buildUri(String base, String path, Map<String, String> parameters) {
        // remove leading slash in path (our string formatting will add it back in)
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return parameters.keySet().stream()
                .map(param -> String.format("%s=%s", param,
                        URLEncoder.encode(parameters.get(param), StandardCharsets.UTF_8)))
                .collect(Collectors.joining("&", String.format("%s/%s%s", base, path, parameters.isEmpty() ? "" : "?"),
                        ""));
    }

    public RCTFApiResponse<byte[]> simpleRawReq(HttpRequest.Builder builder) {
        // add headers
        for (Map.Entry<String, String> header : this.headers.entrySet()) {
            builder = builder.header(header.getKey(), header.getValue());
        }

        // add cookies
        if(!this.cookies.isEmpty()){
            StringJoiner sjCookies = new StringJoiner("; ");
            for(Map.Entry<String,String> cookie : this.cookies.entrySet()){
                sjCookies.add(String.format("%s=%s", cookie.getKey(), cookie.getValue()));
            }
            builder = builder.header("Cookie", sjCookies.toString());
        }

        try {

            HttpResponse<byte[]> resp = this.getClient().send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

            // parse Set-Cookie headers
            Map<String, List<String>> headerMap = resp.headers().map();
            for (String cookieString : headerMap.getOrDefault("Set-Cookie", List.of())) {
                String name = cookieString.split("=")[0];
                String value = cookieString.split("=")[1].split(";")[0];
                this.cookies.put(name, value);
                System.out.println(String.format("new Cookie: %s = %s", name, value));
            }

            return new RCTFApiResponse<byte[]>(resp.body());
        } catch (Exception e) {
            return new RCTFApiResponse<byte[]>(e);
        }
    }

   
    public RCTFApiResponse<JsonElement> doApiRequest(HttpRequest.Builder partialRequest) {
        return this.simpleRawReq(partialRequest).map(x -> new RCTFApiResponse<>(JsonParser.parseString(new String(x, StandardCharsets.UTF_8))));
    }

    public RCTFApiResponse<JsonElement> GET(String endpoint, Map<String, String> parameters) {
        // build url from base url, endpoint, and paramters
        String fullUrl = RCTFApi.buildUri(this.endpoint, endpoint, parameters);

        // build partial GET request and fire it
        return this.doApiRequest(HttpRequest.newBuilder().GET().uri(URI.create(fullUrl)));
    }

    public RCTFApiResponse<JsonElement> POST(String endpoint, Object body) {
 
        // build url from base url and endpoint
        String fullUrl = RCTFApi.buildUri(this.endpoint, endpoint, Map.of());

        // build partial POST request and fire it
        return this.doApiRequest(
                HttpRequest.newBuilder().uri(URI.create(fullUrl)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(getGson().toJson(body), StandardCharsets.UTF_8)));
    }

    @Override
    public List<String> getChallengeIds() {
        return getChallenges().stream().map(x -> x.getId()).toList();
    }

    @Override
    public CTFChallenge getChallenge(String id) {
        return getChallenges().stream().filter(x -> x.getId().equals(id)).findAny().get();
    }

    @Override
    public List<CTFChallenge> getChallenges() {
        JsonObject json = GET("api/v1/challs", Map.of()).get().getAsJsonObject();

        if(!json.get("kind").getAsString().equals("goodChallenges")){
            throw new RuntimeException("Failed to retrieve challenges");
        }

        List<RCTFChallenge> l = json.get("data").getAsJsonArray().asList().stream().map(x -> new RCTFChallenge(this, x.getAsJsonObject().get("id").getAsString(), x.getAsJsonObject())).toList();

        try{
            // make /me request to mark solved challenges as solved
            JsonObject me = GET("api/v1/users/me", Map.of()).get().getAsJsonObject();
            me.get("data").getAsJsonObject().get("solves").getAsJsonArray().forEach(x -> {
                l.stream().filter(a -> a.getId().equals(x.getAsJsonObject().get("id").getAsString())).findAny().ifPresent(b -> {
                    b.solved = true;
                });
            });
        } catch(Exception e){

        }

        return l.stream().map(x -> (CTFChallenge)x).toList();
    }
    
}
