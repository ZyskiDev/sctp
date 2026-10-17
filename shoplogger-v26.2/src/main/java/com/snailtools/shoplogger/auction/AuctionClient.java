package com.snailtools.shoplogger.auction;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * Talks to the Worker's in-game auction endpoints (/mod/auctions...). Signs in
 * with the player's Minecraft name: hosts of an auction get access by name,
 * anyone else adds an auction with the code from the auction admin page.
 */
public final class AuctionClient {

	private AuctionClient() {}

	private static final String API_BASE = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev";
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final Gson GSON = new Gson();
	private static volatile String token;
	private static volatile String tokenFor;

	public static final class Auction {
		public String id, title, type, world, auctionAt, status, hostName, dropOff;
		public double cutPercent;
		public boolean isHost, live, canEnter;
		public int itemCount, soldCount;

		public boolean dutch() { return !"regular".equals(type); }
	}

	public static final class Lot {
		public String id, batchId, seller, rareId, itemName, texture, status, createdAt;
		public int quantity = 1;
		public int startDia, minDia;
		public boolean hostPicks;
		public Double estimateDia;
		public Integer soldDia, cutDia, payoutDia;
	}

	public static final class ListResponse { public String error; public List<Auction> auctions; }
	public static final class AddCodeResponse { public String error; public Auction auction; }
	public static final class ItemsResponse { public String error; public Auction auction; public List<Lot> items; }
	public static final class ResultResponse { public String error; public Lot item; public Previous previous; }
	public static final class Previous { public String status; public Integer soldDia; }
	public static final class EnterResponse {
		public String error, message;
		public boolean needsChoice, linkedToAccount;
		public List<Lot> existing, added;
		public List<Map<String, Object>> incoming;
		public List<String> unknown;
		public int skipped, replaced;
	}

	/** Thrown (inside the future) with a message that's fine to show the player. */
	public static final class AuctionException extends RuntimeException {
		public AuctionException(String message) { super(message); }
	}

	public static String messageOf(Throwable t) {
		Throwable c = t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
		return c instanceof AuctionException ? c.getMessage() : "Couldn't reach sctp.nl. Try again in a moment.";
	}

	public static CompletableFuture<ListResponse> auctions() {
		return call(() -> get("/mod/auctions"), ListResponse.class);
	}

	public static CompletableFuture<AddCodeResponse> addCode(String code) {
		return call(() -> post("/mod/auctions/add-code", Map.of("code", code)), AddCodeResponse.class);
	}

	public static CompletableFuture<ItemsResponse> items(String auctionId) {
		return call(() -> get("/mod/auctions/items?auctionId=" + URLEncoder.encode(auctionId, StandardCharsets.UTF_8)), ItemsResponse.class);
	}

	/** status: "sold" (with soldDia), "unsold", or "entered" (undo). */
	public static CompletableFuture<ResultResponse> result(String itemId, String status, Integer soldDia) {
		JsonObject body = new JsonObject();
		body.addProperty("itemId", itemId);
		body.addProperty("status", status);
		if (soldDia != null) body.addProperty("soldDia", soldDia);
		return call(() -> post("/mod/auctions/result", body), ResultResponse.class);
	}

	/** items: [{name, count}]; mode null the first time, then "add" | "override" | "merge". */
	public static CompletableFuture<EnterResponse> enter(String auctionId, String mcUsername, List<Map<String, Object>> items, String mode) {
		JsonObject body = new JsonObject();
		body.addProperty("auctionId", auctionId);
		body.addProperty("mcUsername", mcUsername);
		body.add("items", GSON.toJsonTree(items));
		if (mode != null) body.addProperty("mode", mode);
		return call(() -> post("/mod/auctions/enter", body), EnterResponse.class);
	}

	// ---- plumbing ----

	private static HttpRequest.Builder get(String path) {
		return HttpRequest.newBuilder(URI.create(API_BASE + path)).GET();
	}

	private static HttpRequest.Builder post(String path, Object body) {
		return HttpRequest.newBuilder(URI.create(API_BASE + path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body instanceof JsonObject j ? j.toString() : GSON.toJson(body)));
	}

	/** Runs a call with a session, signing in first if needed (again if the session expired), and parses the answer. */
	private static <T> CompletableFuture<T> call(Supplier<HttpRequest.Builder> request, Class<T> type) {
		String me = Minecraft.getInstance().getUser().getName();
		CompletableFuture<Void> ready = token != null && me.equals(tokenFor) ? CompletableFuture.completedFuture(null) : login(me);
		return ready.thenCompose(v -> send(request.get())).thenCompose(res -> {
			if (res.statusCode() != 401) return CompletableFuture.completedFuture(res);
			token = null;
			return login(me).thenCompose(v -> send(request.get()));
		}).thenApply(res -> parse(res, type));
	}

	private static CompletableFuture<HttpResponse<String>> send(HttpRequest.Builder b) {
		HttpRequest req = b.header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(15)).build();
		return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString())
				.exceptionally(ex -> { throw new AuctionException("Couldn't reach sctp.nl. Check your connection and try again."); });
	}

	private static <T> T parse(HttpResponse<String> res, Class<T> type) {
		T out;
		try {
			out = GSON.fromJson(res.body(), type);
		} catch (Exception e) {
			throw new AuctionException("sctp.nl sent back something unexpected (HTTP " + res.statusCode() + ").");
		}
		if (out == null) throw new AuctionException("sctp.nl didn't answer. Try again in a moment.");
		try {
			Object err = type.getField("error").get(out);
			if (err != null) throw new AuctionException(err.toString());
		} catch (ReflectiveOperationException ignored) {
			// every response type has an error field
		}
		return out;
	}

	private static CompletableFuture<Void> login(String username) {
		return CompletableFuture.runAsync(() -> {
			HttpRequest req = HttpRequest.newBuilder(URI.create(API_BASE + "/mod/auction-login"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(Map.of("username", username))))
					.timeout(Duration.ofSeconds(15))
					.build();
			HttpResponse<String> res;
			try {
				res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
			} catch (Exception e) {
				throw new AuctionException("Couldn't reach sctp.nl. Check your connection and try again.");
			}
			Map<?, ?> out = GSON.fromJson(res.body(), Map.class);
			if (res.statusCode() != 200 || out == null || out.get("token") == null) {
				Object err = out != null ? out.get("error") : null;
				throw new AuctionException(err != null ? err.toString() : "Couldn't sign in to sctp.nl (HTTP " + res.statusCode() + ").");
			}
			token = out.get("token").toString();
			tokenFor = username;
		});
	}
}
