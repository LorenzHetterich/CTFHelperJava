package ctf.ctfd;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ctf.CTFApi;
import ctf.CTFChallenge;

import java.util.StringJoiner;
import java.util.Map;
import java.nio.charset.StandardCharsets;

import java.net.URI;
import java.util.stream.Collectors;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.net.URLEncoder;

import static utils.SerializableTypes.StringMap;

public class CTFdApi extends CTFApi{

    public String endpoint;
    public StringMap cookies = new StringMap();
    public StringMap headers = new StringMap();

    // non-serializable stuff
    private transient Gson g = new Gson();
    private transient HttpClient cl;

    public CTFdApi(String endpoint){
        this.endpoint = endpoint;
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

    public void updateCSRFToken(){
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/challenges", this.endpoint))).GET();
        String x = new String(this.simpleRawReq(builder).get(), StandardCharsets.UTF_8);
        
        if(x.contains("'csrfNonce': \"")){
            String nonce = x.split("'csrfNonce': \"")[1].split("\"")[0];
            this.headers.put("Csrf-Token", nonce);
        } else {
            System.out.println(x);
            System.err.printf("Warning: no csrf token found!\n");
        }
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

    public CTFdApiResponse<byte[]> simpleRawReq(HttpRequest.Builder builder) {
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

            return new CTFdApiResponse<byte[]>(resp.body());
        } catch (Exception e) {
            return new CTFdApiResponse<byte[]>(e);
        }
    }

    private CTFdApiResponse<JsonElement> parseResponse(byte[] json) {
        JsonObject j = JsonParser.parseString(new String(json, StandardCharsets.UTF_8)).getAsJsonObject();
        if (!j.has("success")) {
            if (j.has("message")) {
                return new CTFdApiResponse<JsonElement>(new Throwable(j.get("message").getAsString()));
            }
            return new CTFdApiResponse<JsonElement>(new Throwable("invalid json response structure: " + json));
        }
        if (j.get("success").getAsBoolean()) {
            if (j.has("data")) {
                return new CTFdApiResponse<JsonElement>(j.get("data"));
            }
            // success but no data
            return new CTFdApiResponse<JsonElement>(JsonNull.INSTANCE);
        } else {
            return new CTFdApiResponse<JsonElement>(new Throwable("Server returned success: false"));
        }
    }

    public CTFdApiResponse<JsonElement> doApiRequest(HttpRequest.Builder partialRequest) {
        return this.simpleRawReq(partialRequest).map(this::parseResponse);
    }

    public CTFdApiResponse<JsonElement> GET(String endpoint, Map<String, String> parameters) {
        // build url from base url, endpoint, and paramters
        String fullUrl = CTFdApi.buildUri(this.endpoint, endpoint, parameters);

        // build partial GET request and fire it
        return this.doApiRequest(HttpRequest.newBuilder().GET().uri(URI.create(fullUrl)));
    }

    public CTFdApiResponse<JsonElement> POST(String endpoint, Object body) {
        // try to update CSRF token
        updateCSRFToken();

        // build url from base url and endpoint
        String fullUrl = CTFdApi.buildUri(this.endpoint, endpoint, Map.of());

        // build partial POST request and fire it
        return this.doApiRequest(
                HttpRequest.newBuilder().uri(URI.create(fullUrl)).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(getGson().toJson(body), StandardCharsets.UTF_8)));
    }

    public void setCookie(String name, String value){
        this.cookies.put(name, value);
    }

    @Override
    public List<String> getChallengeIds() {
        return GET("/api/v1/challenges", Map.of()).get().getAsJsonArray().asList().stream().map(x -> x.getAsJsonObject().get("id").getAsInt() + "").toList();
    }

    @Override
    public CTFChallenge getChallenge(String id) {
        return new CTFdChallenge(this, id, GET(String.format("/api/v1/challenges/%s", id), Map.of()).get().getAsJsonObject());
    }
    
    // overwrite if there is a more efficient way to do this with less requests!
    @Override
    public List<CTFChallenge> getChallenges() {
        return GET("/api/v1/challenges", Map.of()).get().getAsJsonArray().asList().stream().map(x -> {
            String id = x.getAsJsonObject().get("id").getAsInt() + "";
            return (CTFChallenge) new CTFdChallenge(this, id, x.getAsJsonObject());
        }).toList();
    }
}
