package com.devotedmc.ExilePearl.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import com.devotedmc.ExilePearl.ExilePearlApi;
import com.devotedmc.ExilePearl.Lang;
import com.devotedmc.ExilePearl.PearlPlayer;
import com.devotedmc.ExilePearl.SuicideHandler;
import com.devotedmc.ExilePearl.util.Guard;

/**
 * Lets exiled players kill themselves with a timer
 * @author Gordon
 */
class PlayerSuicideTask implements Runnable, SuicideHandler, Listener {

	private final ExilePearlApi pearlApi;

	private boolean enabled = false;
	private int taskId = 0;

	public static final int TICKS_PER_SECOND = 20;
	
	class SuicideRecord {
		public final Location location;
		public int seconds;
		
		public SuicideRecord(final Location location, final int seconds) {
			this.location = location;
			this.seconds = seconds;
		}
	}
	
	private final HashMap<UUID, SuicideRecord> players = new HashMap<UUID, SuicideRecord>();
	private final HashSet<UUID> toRemove = new HashSet<UUID>();

	/**
	 * Creates a new FactoryWorker instance
	 */
	public PlayerSuicideTask(final ExilePearlApi pearlApi) {
		Guard.ArgumentNotNull(pearlApi, "pearlApi");

		this.pearlApi = pearlApi;
	}


	/**
	 * Starts the worker task
	 */
	public void start() {
		if (enabled) {
			pearlApi.log(Level.WARNING, "Tried to start the player suicide task but it was already started.");
			return;
		}
		
		taskId = pearlApi.getScheduler().scheduleSyncRepeatingTask(pearlApi.getPlugin(), this, TICKS_PER_SECOND, TICKS_PER_SECOND);
		if (taskId == -1) {
			pearlApi.log(Level.SEVERE, "Failed to start the player suicide task");
			return;
		} else {
			players.clear();
			enabled = true;
			pearlApi.log("Started the player suicide task");
		}
	}

	/**
	 * Stops the worker task
	 */
	public void stop() {
		if (enabled) {
			pearlApi.getScheduler().cancelTask(taskId);
			enabled = false;
			taskId = 0;
			pearlApi.log("Stopped the player suicide task");
		}
	}

	/**
	 * Restarts the worker task
	 */
	public void restart() {
		stop();
		start();
	}


	@Override
	public boolean isRunning() {
		return enabled;
	}


	@Override
	public void run() {
		if (!enabled) {
			return;
		}
		
		toRemove.clear();
		
		for (Entry<UUID, SuicideRecord> rec : players.entrySet()) {
			PearlPlayer p = pearlApi.getPearlPlayer(rec.getKey());
			int seconds = rec.getValue().seconds - 1;
			rec.getValue().seconds = seconds;
			
			if (seconds > 0) {
				// Notify every 10 seconds and last 5 seconds
				if (seconds <= 5 || seconds % 10 == 0) {
					p.msg(Lang.suicideInSeconds, seconds);
				}
			} else {
				toRemove.add(rec.getKey());
			}
		}
		
		for(UUID uid : toRemove) {
			players.remove(uid);
			Player p = pearlApi.getPearlPlayer(uid).getPlayer();
			if (p != null) {
				p.setHealth(0);
			}
		}
	}


	@Override
	public void addPlayer(PearlPlayer player) {
		int timeout = pearlApi.getPearlConfig().getSuicideTimeoutSeconds();
		players.put(player.getUniqueId(), new SuicideRecord(player.getPlayer().getLocation(), timeout));
		player.msg(Lang.suicideInSeconds, timeout);
	}


	@Override
	public boolean isAdded(UUID uid) {
		return players.containsKey(uid);
	}
	
	/**
	 * Cancels a pending suicide if the player moves
	 * @param e
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlayerMove(PlayerMoveEvent e) {
		SuicideRecord rec = players.get(e.getPlayer().getUniqueId());
		if (rec != null && e.getTo().distance(rec.location) > 2) {
			players.remove(e.getPlayer().getUniqueId());
			pearlApi.getPearlPlayer(e.getPlayer().getUniqueId()).msg(Lang.suicideCancelled);
		}
	}
}