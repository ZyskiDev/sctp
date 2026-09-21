package com.snailtools.shoplogger.mapart;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-game view of the mapart scanner: what it's found nearby, whether each
 * piece is queued / uploaded / already known, plus the on/off switch. The
 * scanner itself never prints to chat — this screen is the only place it talks.
 */
public class MapartPreviewScreen extends Screen {

	private static final int TILE_W = 104;
	private static final int TILE_H = 104;
	private static final int TEXTURES_PER_FRAME = 2;

	private final Screen parent;
	private int page = 0;
	private Button toggleButton;
	private Button prevButton;
	private Button nextButton;

	/** piece key -> registered texture, and the thumbnail bytes it was made from (to spot a re-render). */
	private final Map<String, Identifier> textures = new HashMap<>();
	private final Map<String, byte[]> textureSource = new HashMap<>();
	private int nextTextureId = 0;

	public MapartPreviewScreen(Screen parent) {
		super(Component.literal("Mapart scanner"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int bottom = height - 28;
		toggleButton = addRenderableWidget(Button.builder(toggleLabel(), btn -> {
			MapartScanner s = MapartScanner.getInstance();
			s.setEnabled(!s.isEnabled());
			toggleButton.setMessage(toggleLabel());
		}).bounds(centerX - 154, 30, 150, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Re-send nearby mapart"), btn ->
				MapartScanner.getInstance().resendAll())
				.bounds(centerX + 4, 30, 150, 20).build());

		prevButton = addRenderableWidget(Button.builder(Component.literal("< Prev"), btn -> { if (page > 0) page--; })
				.bounds(centerX - 154, bottom, 70, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(centerX - 40, bottom, 80, 20).build());
		nextButton = addRenderableWidget(Button.builder(Component.literal("Next >"), btn -> page++)
				.bounds(centerX + 84, bottom, 70, 20).build());
	}

	private static Component toggleLabel() {
		return Component.literal("Scanning: " + (MapartScanner.getInstance().isEnabled() ? "ON" : "OFF"));
	}

	private int columns() { return Math.max(1, (width - 20) / TILE_W); }

	private int rows() { return Math.max(1, (height - 56 - 40 - 6) / TILE_H); }

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		MapartScanner scanner = MapartScanner.getInstance();
		context.centeredText(font, title, width / 2, 10, 0xFFFFFFFF);

		List<MapartScanner.Piece> pieces = scanner.snapshot();
		int perPage = columns() * rows();
		int pages = Math.max(1, (pieces.size() + perPage - 1) / perPage);
		if (page >= pages) page = pages - 1;
		prevButton.active = page > 0;
		nextButton.active = page < pages - 1;

		String status = scanner.isEnabled()
				? scanner.lastFrameCount() + " framed maps in range · " + scanner.queuedCount() + " uploading · " + pieces.size() + " found"
				: "Scanning is off — nothing is being uploaded.";
		context.centeredText(font, status, width / 2, 56, 0xFF8FA593);

		if (pieces.isEmpty()) {
			context.centeredText(font, scanner.isEnabled()
					? "Walk near item-frame mapart and it shows up here."
					: "Turn scanning on to find mapart around you.", width / 2, height / 2, 0xFF8FA593);
			return;
		}

		int cols = columns();
		int gridW = cols * TILE_W;
		int startX = (width - gridW) / 2;
		int startY = 72;
		int from = page * perPage;
		int created = 0;
		for (int i = from; i < Math.min(pieces.size(), from + perPage); i++) {
			MapartScanner.Piece p = pieces.get(i);
			int idx = i - from;
			int x = startX + (idx % cols) * TILE_W;
			int y = startY + (idx / cols) * TILE_H;

			context.fill(x + 2, y + 2, x + TILE_W - 2, y + TILE_H - 2, 0x33000000);
			byte[] thumb = p.thumbPng;
			if (thumb != null && textureSource.get(p.key) != thumb && created < TEXTURES_PER_FRAME) {
				created += makeTexture(p.key, thumb) ? 1 : 0;
			}
			Identifier tex = textures.get(p.key);
			if (tex != null) {
				context.blit(RenderPipelines.GUI_TEXTURED, tex, x + (TILE_W - MapartScanner.THUMB) / 2, y + 6, 0, 0,
						MapartScanner.THUMB, MapartScanner.THUMB, MapartScanner.THUMB, MapartScanner.THUMB);
			} else {
				context.centeredText(font, "...", x + TILE_W / 2, y + 6 + MapartScanner.THUMB / 2 - 4, 0xFF8FA593);
			}
			context.centeredText(font, trim(p.title, TILE_W - 8), x + TILE_W / 2, y + 74, 0xFFFFFFFF);
			String size = p.width + "x" + p.height + " · " + statusText(p.status);
			context.centeredText(font, size, x + TILE_W / 2, y + 86, statusColor(p.status));
		}
		context.centeredText(font, "Page " + (page + 1) + " / " + pages, width / 2, height - 42, 0xFF8FA593);
	}

	private boolean makeTexture(String key, byte[] png) {
		try {
			NativeImage image = NativeImage.read(png);
			Identifier old = textures.remove(key);
			if (old != null) minecraft.getTextureManager().release(old);
			Identifier id = Identifier.fromNamespaceAndPath("shoplogger", "mapart/" + (nextTextureId++));
			minecraft.getTextureManager().register(id, new DynamicTexture(() -> key, image));
			textures.put(key, id);
			textureSource.put(key, png);
			return true;
		} catch (Exception e) {
			textureSource.put(key, png); // don't retry a broken thumbnail every frame
			return false;
		}
	}

	private String trim(String s, int maxPx) {
		if (font.width(s) <= maxPx) return s;
		String cut = s;
		while (cut.length() > 1 && font.width(cut + "...") > maxPx) cut = cut.substring(0, cut.length() - 1);
		return cut + "...";
	}

	private static String statusText(MapartScanner.Status s) {
		return switch (s) {
			case QUEUED -> "uploading";
			case UPLOADED -> "uploaded";
			case UNCHANGED -> "known";
			case FAILED -> "failed";
		};
	}

	private static int statusColor(MapartScanner.Status s) {
		return switch (s) {
			case QUEUED -> 0xFFE2A33D;
			case UPLOADED -> 0xFFB7E23D;
			case UNCHANGED -> 0xFF8FA593;
			case FAILED -> 0xFFE2643D;
		};
	}

	@Override
	public void onClose() {
		Minecraft client = Minecraft.getInstance();
		for (Identifier id : textures.values()) client.getTextureManager().release(id);
		textures.clear();
		textureSource.clear();
		client.setScreenAndShow(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
