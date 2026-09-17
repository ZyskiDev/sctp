package com.snailtools.shoplogger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * Locates the shop sign for a chest/barrel. Per the server's convention the
 * sign always sits on the block's front face (the visible latch/handle side),
 * so we read the FACING property directly instead of searching a radius —
 * that's what makes this reliable when two shop chests sit right next to
 * each other.
 */
public final class SignFinder {

	private SignFinder() {}

	/** Returns the parsed sign for this container, checking both halves if it's a double chest. */
	public static ShopSign find(Level world, BlockPos containerPos, BlockState state) {
		ShopSign own = findOnPos(world, containerPos, state);
		if (own != null) return own;

		BlockPos otherHalf = findDoubleChestPartner(world, containerPos, state);
		if (otherHalf != null) {
			BlockState otherState = world.getBlockState(otherHalf);
			return findOnPos(world, otherHalf, otherState);
		}
		return null;
	}

	private static ShopSign findOnPos(Level world, BlockPos pos, BlockState state) {
		Direction front = frontFacing(state);
		if (front == null) return null;

		BlockPos signPos = pos.relative(front);
		if (!(world.getBlockEntity(signPos) instanceof SignBlockEntity sign)) {
			return null;
		}
		return tryParse(sign);
	}

	private static Direction frontFacing(BlockState state) {
		if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
			return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		}
		if (state.hasProperty(BlockStateProperties.FACING)) {
			return state.getValue(BlockStateProperties.FACING); // barrels
		}
		return null;
	}

	/** For a double chest, finds the adjacent half so we can check its front face too. */
	private static BlockPos findDoubleChestPartner(Level world, BlockPos pos, BlockState state) {
		if (!state.hasProperty(BlockStateProperties.CHEST_TYPE)) return null;
		ChestType type = state.getValue(BlockStateProperties.CHEST_TYPE);
		if (type == ChestType.SINGLE) return null;

		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		// Vanilla convention: the partner half is to the "left" or "right" of
		// the facing direction depending on ChestType.
		Direction toPartner = (type == ChestType.LEFT) ? facing.getClockWise() : facing.getCounterClockWise();
		BlockPos candidate = pos.relative(toPartner);

		BlockState candidateState = world.getBlockState(candidate);
		if (candidateState.getBlock() == state.getBlock() && candidateState.hasProperty(BlockStateProperties.CHEST_TYPE)) {
			return candidate;
		}
		return null;
	}

	private static ShopSign tryParse(SignBlockEntity sign) {
		ShopSign front = parseSide(sign, true);
		if (front != null) return front;

		return parseSide(sign, false);
	}

	private static ShopSign parseSide(SignBlockEntity sign, boolean front) {
		String line1 = line(sign, front, 0);
		String line2 = line(sign, front, 1);
		String line3 = line(sign, front, 2);
		String line4 = line(sign, front, 3);

		ShopSign priced = ShopSign.parse(sign.getBlockPos(), line1, line2, line3, line4);
		if (priced != null) return priced;

		return ShopSign.parseDisplay(sign.getBlockPos(), line1, line2, line3, line4);
	}

	private static String line(SignBlockEntity sign, boolean front, int index) {
		Component[] messages = sign.getText(front).getMessages(false);
		return index < messages.length ? messages[index].getString() : "";
	}
}
