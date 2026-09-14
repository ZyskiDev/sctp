package com.snailtools.shoplogger.gui;

import com.snailtools.shoplogger.ScanChatLogger;
import com.snailtools.shoplogger.SearchPreferences;
import com.snailtools.shoplogger.ShopAutoScanner;
import com.snailtools.shoplogger.ShopVisitAlert;
import com.snailtools.shoplogger.TeleportHighlight;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Less-commonly-touched settings, split out of SettingsScreen (reachable via
 * its "Advanced settings..." button) to keep that screen's two columns from
 * growing a third.
 */
public class AdvancedSettingsScreen extends Screen {

	private final Screen parent;
	private EditBox cooldownField;

	public AdvancedSettingsScreen(Screen parent) {
		super(Component.literal("Advanced Settings"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int colW = 220;
		int x = width / 2 - colW / 2;
		int gap = 24;
		int y = 50;

		addRenderableWidget(CycleButton.builder((Boolean v) -> Component.literal(v ? "Single line" : "Multiple lines"), ScanChatLogger.isSingleLine())
				.withValues(Boolean.FALSE, Boolean.TRUE)
				.create(x, y, colW, 20, Component.literal("Chat log format"),
						(btn, value) -> ScanChatLogger.setSingleLine(value)));
		// Extra room here (vs. the plain "gap" used elsewhere) because the
		// cooldown field's label is drawn 10px above it — a plain gap would
		// leave that label overlapping this row's button.
		y += gap + 12;

		cooldownField = new EditBox(font, x, y, colW, 20, Component.literal("Cooldown (minutes)"));
		cooldownField.setValue(Integer.toString((int) (ShopAutoScanner.getPerShopCooldownMs() / 60000L)));
		cooldownField.setResponder(s -> {
			try {
				int minutes = Integer.parseInt(s);
				if (minutes > 0) ShopAutoScanner.setPerShopCooldownMinutes(minutes);
			} catch (NumberFormatException ignored) {
				// not a full number yet — wait for more input
			}
		});
		addRenderableWidget(cooldownField);
		y += gap + 12;

		addRenderableWidget(CycleButton.builder((TeleportHighlight.BeamStyle v) -> Component.literal(v.label), TeleportHighlight.getStyle())
				.withValues(TeleportHighlight.BeamStyle.values())
				.create(x, y, colW, 20, Component.literal("Teleport beam style"),
						(btn, value) -> TeleportHighlight.setStyle(value)));
		y += gap;

		addRenderableWidget(CycleButton.builder((Boolean v) -> Component.literal(v ? "GUI" : "Chat"), SearchPreferences.isGuiSearch())
				.withValues(Boolean.TRUE, Boolean.FALSE)
				.create(x, y, colW, 20, Component.literal("/search opens"),
						(btn, value) -> SearchPreferences.setGuiSearch(value)));
		y += gap;

		addRenderableWidget(CycleButton.onOffBuilder(ShopVisitAlert.isShopInfoOnVisitEnabled())
				.create(x, y, colW, 20, Component.literal("Shop info on visit"),
						(btn, value) -> ShopVisitAlert.setShopInfoOnVisitEnabled(value)));
		y += gap + 12;

		addRenderableWidget(Button.builder(Component.literal("Back"), btn -> onClose())
				.bounds(x, y, colW, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
		super.extractRenderState(context, mouseX, mouseY, delta);
		context.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);
		// EditBox has no built-in visible label (its Component constructor arg is
		// narration-only), unlike the toggle buttons above which show "Label: value"
		// on their own — so this one needs an explicit label drawn above it.
		context.text(font, "Recently-scanned cooldown, in minutes:", cooldownField.getX(), cooldownField.getY() - 10, 0xFF8FA593);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreenAndShow(parent);
	}
}
