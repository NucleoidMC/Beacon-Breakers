package io.github.haykam821.beaconbreakers.game.player.team;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import io.github.haykam821.beaconbreakers.game.phase.BeaconBreakersActivePhase;
import io.github.haykam821.beaconbreakers.game.player.BeaconPlacement;
import io.github.haykam821.beaconbreakers.game.player.PlayerEntry;

public abstract class TeamEntry {
	private final BeaconBreakersActivePhase phase;

	private BeaconPlacement beacon = BeaconPlacement.Unplaced.INSTANCE;

	protected TeamEntry(BeaconBreakersActivePhase phase) {
		this.phase = phase;
	}

	public final BeaconBreakersActivePhase getPhase() {
		return this.phase;
	}

	public final BeaconPlacement getBeacon() {
		return this.beacon;
	}

	public final void setBeacon(BeaconPlacement beacon) {
		this.beacon = beacon;
	}

	public final void tick() {
		Set<PlayerEntry> removed = new HashSet<>();

		for (PlayerEntry entry : this.getPlayers()) {
			if (entry.tick()) {
				this.getPhase().applyEliminationToPlayer(entry);
				removed.add(entry);
			}
		}

		for (PlayerEntry entry : removed) {
			this.removePlayer(entry);
		}
	}

	// Messages

	protected abstract Component getName();

	public final Component getSidebarEntryText() {
		return Component.empty().append(this.beacon.getSidebarEntryIcon()).append(CommonComponents.SPACE).append(this.getName());
	}

	protected String getBeaconBreakTranslationKey() {
		return "text.beaconbreakers.beacon_break";
	}

	public final MutableComponent getBeaconBreakMessage(PlayerEntry breaker, boolean explosion) {
		String translationKey = this.getBeaconBreakTranslationKey();
		if (explosion) translationKey += ".explosion";

		Component name = this.getName();

		if (breaker == null) {
			return Component.translatable(translationKey + ".unattributed", name);
		} else {
			return Component.translatable(translationKey, name, breaker.getName());
		}
	}

	public MutableComponent getCannotBreakOwnBeaconMessage() {
		return Component.translatable("text.beaconbreakers.cannot_break_own_beacon");
	}

	public final MutableComponent getTattleMessageFor(PlayerEntry entry) {
		boolean own = entry != null && this == entry.getTeam();
		return own ? this.getOwnTattleMessage() : this.getTattleMessage();
	}

	protected MutableComponent getTattleMessage() {
		return Component.translatable("text.beaconbreakers.tattle", this.getName());
	}

	protected MutableComponent getOwnTattleMessage() {
		return Component.translatable("text.beaconbreakers.tattle.own");
	}

	public MutableComponent getWinMessage() {
		return Component.translatable("text.beaconbreakers.win", this.getName());
	}

	// Players

	public abstract void addPlayer(PlayerEntry player);

	public abstract void removePlayer(PlayerEntry player);

	public abstract Collection<PlayerEntry> getPlayers();

	/**
	 * {@return the player that receives the team's beacon to place}
	 */
	public abstract PlayerEntry getMainPlayer();

	public final boolean isEliminated() {
		return this.getPlayers().isEmpty();
	}
}
