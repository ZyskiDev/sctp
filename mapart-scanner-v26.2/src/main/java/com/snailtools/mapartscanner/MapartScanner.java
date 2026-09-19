package com.snailtools.mapartscanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Scans item frames holding filled maps near the player, merges frames that
 * touch each other on one wall into full rectangles (2x1, 3x2, ...), stitches
 * each rectangle into one picture and uploads it to the SCTP mapart catalog.
 * Off until toggled on with the \ key.
 */
final class MapartScanner {

	private static final String API_URL = "https://snailcraft-trading-post.snailcraft-trading-post.workers.dev/mapart/upload";
	// Same shared key every other mod upload uses (see the real mod's ShopUploader).
	private static final String API_KEY = "JjabYIfRtghvBJNoy6857TFVHbjknlMOi6754E5dcfvhgBHNI6b564";

	private static final int SCAN_INTERVAL_TICKS = 60;
	private static final double SCAN_RADIUS = 48.0;
	private static final int MAX_GRID = 20; // matches the Worker's MAPART_MAX_GRID
	private static final int BATCH_SIZE = 5;

	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "mapart-scanner-upload");
		t.setDaemon(true);
		return t;
	});

	private boolean enabled = false;
	private int tickCounter = 0;
	private long lastWorldNagAt = 0;
	/** world|leadMapId -> signature of what was last uploaded, so unchanged pieces aren't re-sent. */
	private final Map<String, String> uploaded = new java.util.concurrent.ConcurrentHashMap<>();

	private record FrameInfo(int gx, int gy, int mapId, int rotation, String name, byte[] colors) {}

	private record Group(int minX, int minY, int width, int height, List<FrameInfo> frames) {}

	void toggle(Minecraft client) {
		enabled = !enabled;
		tickCounter = SCAN_INTERVAL_TICKS; // scan right away when turned on
		String world = MapartWorld.get();
		if (enabled) {
			say(client, "Mapart scanning ON" + (world != null ? " (world: " + world + ")" : ""), ChatFormatting.GREEN);
			if (world == null) say(client, "Set your world first: /mapartworld firefly  or  /mapartworld honeybee", ChatFormatting.YELLOW);
		} else {
			say(client, "Mapart scanning OFF", ChatFormatting.GRAY);
		}
	}

	void tick(Minecraft client) {
		if (!enabled || client.level == null || client.player == null) return;
		if (++tickCounter < SCAN_INTERVAL_TICKS) return;
		tickCounter = 0;

		String world = MapartWorld.get();
		if (world == null) {
			long now = System.currentTimeMillis();
			if (now - lastWorldNagAt > 30_000) {
				lastWorldNagAt = now;
				say(client, "Mapart scanner needs your world: /mapartworld firefly  or  /mapartworld honeybee", ChatFormatting.YELLOW);
			}
			return;
		}
		scan(client, world);
	}

	private void scan(Minecraft client, String world) {
		AABB box = client.player.getBoundingBox().inflate(SCAN_RADIUS);
		List<ItemFrame> frames = client.level.getEntitiesOfClass(ItemFrame.class, box);

		Map<String, Map<Long, FrameInfo>> planes = new HashMap<>();
		List<Group> groups = new ArrayList<>();

		for (ItemFrame frame : frames) {
			ItemStack stack = frame.getItem();
			if (!stack.is(Items.FILLED_MAP)) continue;
			MapId id = stack.get(DataComponents.MAP_ID);
			if (id == null) continue;
			MapItemSavedData data = client.level.getMapData(id);
			if (data == null) continue; // pixel data not received yet — picked up on a later scan

			Component custom = stack.get(DataComponents.CUSTOM_NAME);
			String name = custom == null ? null : custom.getString();
			int rot = Math.floorMod(frame.getRotation(), 4);
			Direction dir = frame.getDirection();
			BlockPos pos = frame.blockPosition();

			if (dir.getAxis().isHorizontal()) {
				Direction right = dir.getOpposite().getClockWise(); // viewer's right when looking at the frame
				int gx = pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
				int gy = -pos.getY();
				int plane = dir.getAxis() == Direction.Axis.Z ? pos.getZ() : pos.getX();
				String key = dir.getName() + "@" + plane;
				planes.computeIfAbsent(key, k -> new HashMap<>()).put(cellKey(gx, gy), new FrameInfo(gx, gy, id.id(), rot, name, data.colors));
			} else {
				// Floor/ceiling frames have no wall grid to merge on — standalone.
				FrameInfo single = new FrameInfo(0, 0, id.id(), rot, name, data.colors);
				groups.add(new Group(0, 0, 1, 1, List.of(single)));
			}
		}
		for (Map<Long, FrameInfo> cells : planes.values()) groups.addAll(decompose(cells));
		if (groups.isEmpty()) return;

		JsonArray pending = new JsonArray();
		Map<String, String> pendingSignatures = new HashMap<>();
		for (Group g : groups) {
			FrameInfo lead = pickLead(g);
			String sig = signature(g, lead);
			String key = world + "|" + lead.mapId();
			if (sig.equals(uploaded.get(key))) continue;
			try {
				pending.add(toJson(g, lead));
				pendingSignatures.put(key, sig);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (pending.isEmpty()) return;

		List<JsonObject> all = new ArrayList<>();
		for (JsonElement el : pending) all.add(el.getAsJsonObject());
		for (int i = 0; i < all.size(); i += BATCH_SIZE) {
			JsonArray batch = new JsonArray();
			List<String> batchKeys = new ArrayList<>();
			for (JsonObject o : all.subList(i, Math.min(all.size(), i + BATCH_SIZE))) {
				batch.add(o);
				batchKeys.add(world + "|" + o.get("leadMapId").getAsInt());
			}
			upload(client, world, batch, batchKeys, pendingSignatures);
		}
	}

	// ---------------- grouping ----------------

	private static long cellKey(int gx, int gy) {
		return ((long) gx << 32) ^ (gy & 0xFFFFFFFFL);
	}

	/**
	 * Splits one wall's frames into full rectangles: repeatedly takes the
	 * largest solid WxH block still left, so a perfect 3x2 wall merges into one
	 * piece while an L shape becomes a rectangle plus standalone leftovers.
	 */
	private static List<Group> decompose(Map<Long, FrameInfo> cells) {
		List<Group> result = new ArrayList<>();
		Map<Long, FrameInfo> remaining = new HashMap<>(cells);
		while (!remaining.isEmpty()) {
			Group best = bestRectangle(remaining);
			result.add(best);
			for (FrameInfo f : best.frames()) remaining.remove(cellKey(f.gx(), f.gy()));
		}
		return result;
	}

	private static Group bestRectangle(Map<Long, FrameInfo> cells) {
		List<FrameInfo> ordered = new ArrayList<>(cells.values());
		ordered.sort(Comparator.comparingInt(FrameInfo::gy).thenComparingInt(FrameInfo::gx));

		Map<Long, Integer> downRun = new HashMap<>();
		int bestArea = 0, bestX = 0, bestY = 0, bestW = 0, bestH = 0;
		for (FrameInfo top : ordered) {
			int minH = Integer.MAX_VALUE;
			for (int w = 1; w <= MAX_GRID; w++) {
				int x = top.gx() + (w - 1);
				if (!cells.containsKey(cellKey(x, top.gy()))) break;
				minH = Math.min(minH, Math.min(MAX_GRID, run(cells, downRun, x, top.gy())));
				int area = w * minH;
				if (area > bestArea) {
					bestArea = area; bestX = top.gx(); bestY = top.gy(); bestW = w; bestH = minH;
				}
			}
		}
		List<FrameInfo> frames = new ArrayList<>();
		for (int y = bestY; y < bestY + bestH; y++) {
			for (int x = bestX; x < bestX + bestW; x++) frames.add(cells.get(cellKey(x, y)));
		}
		return new Group(bestX, bestY, bestW, bestH, frames);
	}

	/** Number of consecutive present cells going down from (x, y). */
	private static int run(Map<Long, FrameInfo> cells, Map<Long, Integer> memo, int x, int y) {
		long key = cellKey(x, y);
		Integer cached = memo.get(key);
		if (cached != null) return cached;
		int value = cells.containsKey(key) ? 1 + run(cells, memo, x, y + 1) : 0;
		memo.put(key, value);
		return value;
	}

	/** The named map (title + seller live in its custom name); the longest name wins, else the top-left frame. */
	private static FrameInfo pickLead(Group g) {
		FrameInfo lead = null;
		for (FrameInfo f : g.frames()) {
			if (f.name() == null || f.name().isBlank()) continue;
			if (lead == null || f.name().length() > lead.name().length()) lead = f;
		}
		return lead != null ? lead : g.frames().get(0);
	}

	private static String signature(Group g, FrameInfo lead) {
		StringBuilder sb = new StringBuilder();
		sb.append(g.width()).append('x').append(g.height()).append('|').append(lead.mapId()).append('|').append(lead.name());
		for (FrameInfo f : g.frames()) {
			sb.append('|').append(f.mapId()).append(':').append(f.rotation()).append(':').append(Arrays.hashCode(f.colors()));
		}
		return Integer.toHexString(sb.toString().hashCode()) + "-" + sb.length();
	}

	// ---------------- image + upload ----------------

	private static JsonObject toJson(Group g, FrameInfo lead) throws Exception {
		int width = g.width() * 128, height = g.height() * 128;
		int[] argb = new int[width * height];
		JsonArray partIds = new JsonArray();
		for (FrameInfo f : g.frames()) {
			partIds.add(f.mapId());
			int ox = (f.gx() - g.minX()) * 128, oy = (f.gy() - g.minY()) * 128;
			for (int sy = 0; sy < 128; sy++) {
				for (int sx = 0; sx < 128; sx++) {
					int px = colorOf(f.colors()[sx + sy * 128]);
					int dx, dy;
					switch (f.rotation()) { // clockwise quarter turns, as the frame shows the map
						case 1 -> { dx = 127 - sy; dy = sx; }
						case 2 -> { dx = 127 - sx; dy = 127 - sy; }
						case 3 -> { dx = sy; dy = 127 - sx; }
						default -> { dx = sx; dy = sy; }
					}
					argb[(oy + dy) * width + ox + dx] = px;
				}
			}
		}
		JsonObject o = new JsonObject();
		o.addProperty("leadMapId", lead.mapId());
		o.addProperty("rawName", lead.name() == null ? "" : lead.name());
		o.addProperty("width", g.width());
		o.addProperty("height", g.height());
		o.add("partMapIds", partIds);
		o.addProperty("png", Base64.getEncoder().encodeToString(PngWriter.encode(width, height, argb)));
		return o;
	}

	/** Packed map colour byte -> ARGB (base colour id in the high 6 bits, brightness in the low 2). */
	private static int colorOf(byte packed) {
		int id = packed & 0xFF;
		int base = id >> 2;
		if (base == 0) return 0; // unexplored/transparent
		int mult = switch (id & 3) {
			case 0 -> 180;
			case 1 -> 220;
			case 2 -> 255;
			default -> 135;
		};
		int rgb = MapColor.byId(base).col;
		int r = ((rgb >> 16) & 0xFF) * mult / 255;
		int gr = ((rgb >> 8) & 0xFF) * mult / 255;
		int b = (rgb & 0xFF) * mult / 255;
		return 0xFF000000 | (r << 16) | (gr << 8) | b;
	}

	private void upload(Minecraft client, String world, JsonArray batch, List<String> batchKeys, Map<String, String> signatures) {
		JsonObject body = new JsonObject();
		body.addProperty("world", world);
		body.add("maps", batch);
		String payload = body.toString();

		WORKER.submit(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(API_URL))
						.timeout(Duration.ofSeconds(60))
						.header("Content-Type", "application/json")
						.header("Authorization", "Bearer " + API_KEY)
						.POST(HttpRequest.BodyPublishers.ofString(payload))
						.build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) {
					client.execute(() -> say(client, "Mapart upload failed (HTTP " + response.statusCode() + ")", ChatFormatting.RED));
					return;
				}
				JsonArray results = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("results");
				int created = 0, updated = 0, merged = 0, skipped = 0;
				for (JsonElement el : results) {
					JsonObject r = el.getAsJsonObject();
					String status = r.get("status").getAsString();
					switch (status) {
						case "created" -> created++;
						case "updated" -> updated++;
						case "merged" -> merged++;
						default -> skipped++;
					}
					if (!status.equals("error") && r.has("leadMapId") && !r.get("leadMapId").isJsonNull()) {
						String key = world + "|" + r.get("leadMapId").getAsInt();
						String sig = signatures.get(key);
						if (sig != null) uploaded.put(key, sig);
					}
				}
				int c = created, u = updated, m = merged, s = skipped;
				client.execute(() -> say(client, "Mapart: " + c + " new, " + u + " updated, " + m + " merged" + (s > 0 ? ", " + s + " skipped" : ""), ChatFormatting.AQUA));
			} catch (Exception e) {
				e.printStackTrace();
				client.execute(() -> say(client, "Mapart upload error: " + e.getMessage(), ChatFormatting.RED));
			}
		});
	}

	static void say(Minecraft client, String text, ChatFormatting color) {
		if (client.player == null) return;
		client.player.sendSystemMessage(Component.literal("[Mapart] ").withStyle(ChatFormatting.GOLD)
				.append(Component.literal(text).withStyle(color)));
	}
}
