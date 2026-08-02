package com.snailtools.shoplogger;

import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Sprinkles a faint red particle near chests/barrels ShopAutoScanner has
 * scanned in the last few minutes — a subtle "already checked, no need to
 * open this one again yet" hint while walking a shop route. Deliberately
 * understated: dim color, small scale, sparse spawn rate, not a persistent
 * outline.
 */
public final class ShopMarkerRenderer {

	private static final ShopMarkerRenderer INSTANCE = new ShopMarkerRenderer();
	public static ShopMarkerRenderer getInstance() { return INSTANCE; }

	private static final int COLOR = 0xB33A3A; // muted brick red, not alarm-red
	// Own shops with an uncollected payment sitting in them (see
	// OwnShopSaleTracker) get this instead — same muted styling, green.
	private static final int SALE_COLOR = 0x3AB35A;
	private static final float SCALE = 1.25f;
	/** How often (in ticks) each marked chest gets a fresh particle. 60 ticks = 3s. */
	private static final int SPAWN_INTERVAL_TICKS = 20;
	/** Only bother for chests actually close enough to notice. */
	private static final double MAX_DISTANCE = 24.0;

	private final Random random = new Random();
	private boolean enabled = true;
	private int tickCounter = 0;

	private ShopMarkerRenderer() {}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void tick(MinecraftClient client) {
		if (!enabled || client.world == null || client.player == null) return;

		tickCounter++;
		if (tickCounter % SPAWN_INTERVAL_TICKS != 0) return;

		Vec3d eye = client.player.getEyePos();

		for (BlockPos containerPos : ShopAutoScanner.getInstance().getRecentlyScannedPositions()) {
			// Mark the sign, not the container — that's what the player is
			// actually reading while walking a shop route. Falls back to the
			// container position if this container has no known sign yet
			// (e.g. a manually-opened chest ShopAutoScanner never discovered).
			ShopSign sign = ShopAutoScanner.getInstance().getKnownSign(containerPos);
			BlockPos pos = sign != null ? sign.signPos() : containerPos;

			if (!pos.isWithinDistance(eye, MAX_DISTANCE)) continue;

			int color = OwnShopSaleTracker.hasPendingPayment(containerPos) ? SALE_COLOR : COLOR;
			DustParticleEffect effect = new DustParticleEffect(color, SCALE);

			double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			double y = pos.getY() + 0.85;
			double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6;
			client.world.addParticleClient(effect, x, y, z, 0.0, 0.01, 0.0);
		}
	}
}
