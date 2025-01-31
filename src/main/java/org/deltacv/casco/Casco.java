package org.deltacv.casco;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.deltacv.casco.arena.Arena;
import org.deltacv.casco.arena.ArenaManager;

import java.util.Objects;

public final class Casco extends JavaPlugin implements Listener {
    ArenaManager arenaManager;

    @Override
    public void onEnable() {
        arenaManager = new ArenaManager(this);
        arenaManager.loadWorldData();
        arenaManager.purgeUnusedArenas();

        Objects.requireNonNull(
                this.getCommand("casco")
        ).setExecutor(new CascoCommand(arenaManager));

        this.saveDefaultConfig();
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        arenaManager.saveWorldData();
        arenaManager.close();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();

        for(Arena arena : arenaManager.activeArenas.keySet()) {
            if(arena.players.contains(p)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();

        for(Arena arena : arenaManager.activeArenas.keySet()) {
            if(arena.players.contains(p)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPlayerDamageByPlayer(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player && e.getEntity() instanceof Player) {
            Player damager = (Player) e.getDamager();
            Player damaged = (Player) e.getEntity();

            for(Arena arena : arenaManager.activeArenas.keySet()) {
                if(arena.players.contains(damager) && arena.players.contains(damaged)) {
                    arena.notifyTag(damager, damaged);
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerItemInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        for(Arena arena : arenaManager.activeArenas.keySet()) {
            if(arena.players.contains(p)) {
                e.setCancelled(true);
                arena.notifyItemInteract(p, e.getItem());
                return;
            }
        }
    }
}
