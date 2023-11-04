package ctfdapi;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
		this.httpClient = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
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

	public void setApiToken(String token) {
		this.headers.put("Authorization", token.startsWith("Token ") ? token : "Token " + token);
	}

	public void setUserAgent(String userAgent) {
		this.headers.put("User-Agent", userAgent);
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
		// add headers
		for (Map.Entry<String, String> header : this.headers.entrySet()) {
			partialRequest = partialRequest.header(header.getKey(), header.getValue());
		}

		// try to send request
		try {
			String response = this.httpClient
					.send(partialRequest.build(), BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
			return parseResponse(response, clazz);
		} catch (IOException | InterruptedException e) {
			return new CTFdApiResponse<T>(e);
		}
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
	
	// Challenges
	
	public CTFdApiResponse<CTFdChallenge[]> getChallengeList(Map<String, String> parameters) {
		return GET("/api/v1/challenges", parameters, CTFdChallenge[].class);
	}
	
	public CTFdApiResponse<CTFdChallenge[]> getChallengeList(String name, Integer maxAttempts, Integer value, String category, String type, String state, String q){
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

	public CTFdApiResponse<CTFdChallenge[]> getChallengeListByCategory(String category){
		return getChallengeList(Map.of("category", category));
	}
	
	public CTFdApiResponse<CTFdChallenge[]> getChallengeListByState(String status){
		return getChallengeList(Map.of("state", status));
	}
		
	

}