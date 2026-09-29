package io.github.haykam821.beaconbreakers.game.player;

import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public sealed interface BeaconPlacement {
	public default boolean isAt(BlockPos pos) {
		return false;
	}

	public Component getSidebarEntryIcon();

	public record Unplaced() implements BeaconPlacement {
		public static final Unplaced INSTANCE = new Unplaced();

		private static final Component ICON = BeaconPlacement.createIcon("⌛", ChatFormatting.YELLOW);

		@Override
		public Component getSidebarEntryIcon() {
			return ICON;
		}

		@Override
		public String toString() {
			return "Unplaced";
		}
	}

	public record Placed(BlockPos pos) implements BeaconPlacement {
		private static final Component ICON = BeaconPlacement.createIcon("✔", ChatFormatting.GREEN);

		public Placed(BlockPos pos) {
			this.pos = Objects.requireNonNull(pos);
		}

		@Override
		public boolean isAt(BlockPos pos) {
			return this.pos.equals(pos);
		}

		@Override
		public Component getSidebarEntryIcon() {
			return ICON;
		}

		@Override
		public String toString() {
			return "Placed at " + this.pos;
		}
	}

	public record Broken() implements BeaconPlacement {
		public static final Broken INSTANCE = new Broken();

		private static final Component ICON = BeaconPlacement.createIcon("❌", ChatFormatting.RED);

		@Override
		public Component getSidebarEntryIcon() {
			return ICON;
		}

		@Override
		public String toString() {
			return "Broken";
		}
	}

	private static Component createIcon(String icon, ChatFormatting color) {
		return Component.literal(icon).withStyle(style -> {
			return style.withColor(color).withBold(true);
		});
	}
}
