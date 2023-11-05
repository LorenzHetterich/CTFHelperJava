package ctfdapi;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class CTFdApi {

    private final String url;
    private final Gson gson;
    private final HttpClient httpClient;
    private final Map<String, String> headers;

    public CTFdApi(String url) {
        this.url = url;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_2)
                .followRedirects(HttpClient.Redirect.NEVER).cookieHandler(new CookieManager()).build();
        this.headers = new HashMap<>();

    }

    public HttpClient getClient() {
        return httpClient;
    }

    /*
     * Utility-methods to make authentication, etc. work
     */

    public void addCookie(String name, String value) {
        HttpCookie cookie = new HttpCookie(name, value);
        cookie.setPath("/");
        cookie.setVersion(0);
        ((CookieManager) httpClient.cookieHandler().get()).getCookieStore().add(URI.create(this.url), cookie);
    }

    public void addHeader(String name, String value) {
        this.headers.put(name, value);
    }

    public void setApiToken(String token) {
        this.headers.put("Authorization", token.startsWith("Token ") ? token : "Token " + token);
    }

    public void setUserAgent(String userAgent) {
        this.headers.put("User-Agent", userAgent);
    }

    private CTFdApiResponse<String> simpleReq(HttpRequest.Builder builder) {

        // add headers
        for (Map.Entry<String, String> header : this.headers.entrySet()) {
            builder = builder.header(header.getKey(), header.getValue());
        }

        try {
            HttpResponse<String> resp = this.httpClient.send(builder.build(),
                    BodyHandlers.ofString(StandardCharsets.UTF_8));

            // set cookies
            Map<String, List<String>> headerMap = resp.headers().map();
            for (String cookieString : headerMap.getOrDefault("Set-Cookie", List.of())) {
                String name = cookieString.split("=")[0];
                String value = cookieString.split("=")[1].split(";")[0];
                String path = cookieString.split("Path=")[1].split(";")[0];
                HttpCookie cookie = new HttpCookie(name, value);
                cookie.setPath(path);
                cookie.setVersion(0);
                ((CookieManager) httpClient.cookieHandler().get()).getCookieStore().add(URI.create(this.url), cookie);

                System.out.println("new cookie: " + cookie);
            }

            String html = resp.body();
            return new CTFdApiResponse<String>(html);
        } catch (IOException | InterruptedException e) {
            return new CTFdApiResponse<String>(e);
        }
    }

    public CTFdApiResponse<Void> defaultLogin(String username, String password) {

        CTFdApiResponse<String> loginHtml = simpleReq(
                HttpRequest.newBuilder().uri(URI.create(String.format("%s/login", this.url))).GET());

        return loginHtml.map(x -> {
            try {
                String submit = x.split("name=\"_submit\" type=\"submit\" value=\"")[1].split("\"")[0];
                String nonce = x.split("name=\"nonce\" type=\"hidden\" value=\"")[1].split("\"")[0];
                String body = String.format("name=%s&password=%s&_submit=%s&nonce=%s",
                        URLEncoder.encode(username, StandardCharsets.UTF_8),
                        URLEncoder.encode(password, StandardCharsets.UTF_8),
                        URLEncoder.encode(submit, StandardCharsets.UTF_8),
                        URLEncoder.encode(nonce, StandardCharsets.UTF_8));
                return simpleReq(HttpRequest.newBuilder()
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .uri(URI.create(String.format("%s/login", this.url)))
                        .POST(BodyPublishers.ofString(body, StandardCharsets.UTF_8))).map(y -> {
                            return new CTFdApiResponse<Void>((Void) null);
                        });
            } catch (IndexOutOfBoundsException e) {
                return new CTFdApiResponse<Void>(new Throwable("html was different than expected", e));
            }
        });
    }

    public CTFdApiResponse<String> getAdminCSRFToken() {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(String.format("%s/admin/challenges", this.url))).GET();
        CTFdApiResponse<String> html = simpleReq(builder);
        return html.map(x -> {
            try {
                return new CTFdApiResponse<String>(x.split("'csrfNonce': \"")[1].split("\"")[0]);
            } catch (IndexOutOfBoundsException e) {
                return new CTFdApiResponse<String>(new Throwable("html was not as expected", e));
            }
        });
    }

    /*
     * Low level http api
     */

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

    private <T> CTFdApiResponse<T> parseResponse(String json, Class<T> clazz) {
        JsonObject j = JsonParser.parseString(json).getAsJsonObject();
        if (!j.has("success")) {
            if (j.has("message")) {
                return new CTFdApiResponse<T>(new Throwable(j.get("message").getAsString()));
            }
            return new CTFdApiResponse<T>(new Throwable("invalid json response structure: " + json));
        }
        if (j.get("success").getAsBoolean()) {
            if (j.has("data")) {
                return new CTFdApiResponse<T>(gson.fromJson(j.get("data"), clazz));
            }
            // success but no data
            return new CTFdApiResponse<T>((T) null);
        } else {
            return new CTFdApiResponse<T>(new Throwable("Server returned success: false"));
        }
    }

    private <T> CTFdApiResponse<T> doApiRequest(HttpRequest.Builder partialRequest, Class<T> clazz) {

        return this.simpleReq(partialRequest).map(x -> parseResponse(x, clazz));
    }

    public <T> CTFdApiResponse<T> GET(String endpoint, Map<String, String> parameters, Class<T> clazz) {
        // build url from base url, endpoint, and paramters
        String fullUrl = CTFdApi.buildUri(this.url, endpoint, parameters);

        // build partial GET request and fire it
        return this.doApiRequest(HttpRequest.newBuilder().uri(URI.create(fullUrl)).GET(), clazz);
    }

    public <T> CTFdApiResponse<T> POST(String endpoint, Object body, Class<T> clazz) {
        // build url from base url and endpoint
        String fullUrl = CTFdApi.buildUri(this.url, endpoint, Map.of());

        // build partial POST request and fire it
        return this.doApiRequest(
                HttpRequest.newBuilder().uri(URI.create(fullUrl)).header("Content-Type", "application/json")
                        .POST(BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8)),
                clazz);
    }

    public <T> CTFdApiResponse<T> PATCH(String endpoint, Object body, Class<T> clazz) {
        // build url from base url and endpoint
        String fullUrl = CTFdApi.buildUri(this.url, endpoint, Map.of());

        // build partial PATCH request and fire i
        return this
                .doApiRequest(
                        HttpRequest.newBuilder().uri(URI.create(fullUrl)).header("Content-Type", "application/json")
                                .method("PATCH", BodyPublishers.ofString(gson.toJson(body), StandardCharsets.UTF_8)),
                        clazz);
    }

    public <T> CTFdApiResponse<T> DELETE(String endpoint, Class<T> clazz) {
        // build url from base url and endpoint
        String fullUrl = CTFdApi.buildUri(this.url, endpoint, Map.of());

        // build partial DELETE request and fire i
        return this.doApiRequest(HttpRequest.newBuilder().uri(URI.create(fullUrl)).DELETE(), clazz);
    }

    /*
     * High-level http Api
     */

    public CTFdApiResponse<CTFdChallenge[]> getChallengeList(Map<String, String> parameters) {
        return GET("/api/v1/challenges", parameters, CTFdChallenge[].class);
    }

    public CTFdApiResponse<CTFdChallenge[]> getChallengeList(String name, Integer maxAttempts, Integer value,
            String category, String type, String state, String q) {
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("name", name);
        parameters.put("max_attempts", maxAttempts == null ? null : Integer.toString(maxAttempts));
        parameters.put("value", maxAttempts == null ? null : Integer.toString(value));
        parameters.put("category", category);
        parameters.put("type", type);
        parameters.put("state", state);
        parameters.put("q", q);
        parameters.entrySet().removeIf(x -> x.getValue() == null);
        return getChallengeList(parameters);
    }

    public CTFdApiResponse<CTFdChallenge[]> getChallengeListByCategory(String category) {
        return getChallengeList(Map.of("category", category));
    }

    public CTFdApiResponse<CTFdChallenge[]> getChallengeListByState(String status) {
        return getChallengeList(Map.of("state", status));
    }

    public CTFdApiResponse<CTFdChallenge[]> getChallengeList() {
        return getChallengeList(Map.of());
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge> postChallengeList(Map<String, ? extends Object> challenge) {
        return POST("/api/v1/challenges", challenge, CTFdChallenge.class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge> postChallengeList(CTFdChallenge challenge) {
        switch (challenge.type) {

        case "standard":
            return POST("/api/v1/challenges",
                    Map.ofEntries(Map.entry("category", challenge.category),
                            Map.entry("description", challenge.description), Map.entry("name", challenge.name),
                            Map.entry("state", challenge.state), Map.entry("type", challenge.type),
                            Map.entry("value", Integer.toString(challenge.value))),
                    CTFdChallenge.class);

        default:
            return new CTFdApiResponse<CTFdChallenge>(
                    new Throwable(String.format("challenge type %s is not supported", challenge.type)));
        }
    }

    private static String optString(String s) {
        return s == null ? "" : s;
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge> patchChallenge(CTFdChallenge challenge) {
        return PATCH(String.format("/api/v1/challenges/%d", challenge.id),
                Map.ofEntries(Map.entry("category", optString(challenge.category)),
                        Map.entry("description", optString(challenge.description)),
                        Map.entry("max_attempts", Integer.toString(challenge.maxAttempts)),
                        Map.entry("name", optString(challenge.name)), Map.entry("state", optString(challenge.state)),
                        Map.entry("value", Integer.toString(challenge.value))),
                CTFdChallenge.class);
    }

    public CTFdApiResponse<CTFdAttemptResponseData> postChallengeAttempt(int challengeId, String submission) {
        return POST("/api/v1/challenges/attempt",
                Map.of("challenge_id", Integer.toString(challengeId), "submission", submission),
                CTFdAttemptResponseData.class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallengeTypesResponseData> getChallengeTypes() {
        return GET("/api/v1/challenges/types", Map.of(), CTFdChallengeTypesResponseData.class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge> getChallenge(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d", challengeId), Map.of(), CTFdChallenge.class);
    }

    @CTFdAdmin
    public CTFdApiResponse<Void> deleteChallenge(int challengeId) {
        return DELETE(String.format("/api/v1/challenges/%d", challengeId), Void.class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdFile[]> getChallengeFiles(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/files", challengeId), Map.of(), CTFdFile[].class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge.Flag[]> getChallengeFlags(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/flags", challengeId), Map.of(), CTFdChallenge.Flag[].class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge.Hint[]> getChallengeHints(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/hints", challengeId), Map.of(), CTFdChallenge.Hint[].class);
    }

    @CTFdAdmin
    public CTFdApiResponse<CTFdChallenge.Requirements> getChallengeRequirements(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/requirements", challengeId), Map.of(),
                CTFdChallenge.Requirements.class);
    }

    public CTFdApiResponse<CTFdChallenge.Solve[]> getChallengeSolves(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/solves", challengeId), Map.of(), CTFdChallenge.Solve[].class);
    }

    // TODO return type
    public CTFdApiResponse<Object[]> getChallengeTags(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/tags", challengeId), Map.of(), Object[].class);
    }

    // TODO return type
    @CTFdAdmin
    public CTFdApiResponse<Object[]> getChallengeTopics(int challengeId) {
        return GET(String.format("/api/v1/challenges/%d/topics", challengeId), Map.of(), Object[].class);
    }
}