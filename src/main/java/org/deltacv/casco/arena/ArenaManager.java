package org.deltacv.casco.arena;

import com.google.gson.Gson;
import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary;
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException;
import net.megavex.scoreboardlibrary.api.noop.NoopScoreboardLibrary;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.bukkit.Bukkit.getServer;

public class ArenaManager {

    public static final String ARENA_WORLD_PREFIX = "__casco__";

    ArenaWorldData worldData = new ArenaWorldData();

    Player configPlayer;
    Location newLobbyLocation;
    Location newGameLocation;

    public HashMap<Arena, MultiverseWorld> activeArenas = new HashMap<>();

    MultiverseCore mvCore;
    Economy econ;
    ScoreboardLibrary scoreboardLibrary;

    JavaPlugin plugin;
    Logger log;
    File worldDataFile;

    Gson gson = new Gson();

    public ArenaManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();

        mvCore = (MultiverseCore) plugin.getServer().getPluginManager().getPlugin("Multiverse-Core");

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            log.severe("Vault not found, disabling plugin.");
            getServer().getPluginManager().disablePlugin(plugin);
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            log.severe("Economy not found, disabling plugin.");
            getServer().getPluginManager().disablePlugin(plugin);
        }

        econ = rsp.getProvider();

        try {
            scoreboardLibrary = ScoreboardLibrary.loadScoreboardLibrary(plugin);
        } catch (NoPacketAdapterAvailableException e) {
            // If no packet adapter was found, you can fallback to the no-op implementation:
            scoreboardLibrary = new NoopScoreboardLibrary();
            plugin.getLogger().warning("No scoreboard packet adapter available!");
        }

        worldDataFile = new File(plugin.getDataFolder(), "worldData.json");
    }

    public void close() {
        scoreboardLibrary.close();
    }

    public void purgeUnusedArenas() {
        // remove all arenas and worlds
        for(MultiverseWorld world : mvCore.getMVWorldManager().getMVWorlds()) {
            if(world.getName().startsWith(ARENA_WORLD_PREFIX) && !activeArenas.containsValue(world)) {
                mvCore.getMVWorldManager().deleteWorld(world.getName());
                log.info("Deleted unused arena world: " + world.getName());
            }
        }
    }

    public void loadWorldData() {
        // load world data from json config, in plugin.getDataFolder() -> "worldData.json"
        if(worldDataFile.exists()) {
            try {
                log.info("Loading world data file " + worldDataFile.getAbsolutePath());

                worldData = gson.fromJson(Files.readString(worldDataFile.toPath()), ArenaWorldData.class);
                log.info("Loaded world data from file: " + worldData);
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to load world data from file.", e);
            }
        }
    }

    public void saveWorldData() {
        // save world data to json config
        try {
            Files.writeString(worldDataFile.toPath(), gson.toJson(worldData));
            log.info("Saved world data to file.");
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to save world data to file.", e);
        }
    }

    public void config(Player player, String[] args) {
        if(configPlayer == player && args.length == 0) {
            configPlayer = null;

            if(newLobbyLocation == null || newGameLocation == null) {
                player.sendMessage(ChatColor.RED + "[Casco] No se han definido las ubicaciones. No se ha guardado la configuración.");
            } else {
                if(newLobbyLocation.getWorld() != newGameLocation.getWorld()) {
                    player.sendMessage(ChatColor.RED + "[Casco] Las ubicaciones deben estar en el mismo mundo. No se ha guardado la configuración.");
                    return;
                }

                // save new locations to worldData
                // rounds up to be at the center of the block
                worldData.lobbyX = newLobbyLocation.getX();
                worldData.lobbyY = newLobbyLocation.getY();
                worldData.lobbyZ = newLobbyLocation.getZ();

                worldData.gameX = newGameLocation.getX();
                worldData.gameY = newGameLocation.getY();
                worldData.gameZ = newGameLocation.getZ();

                worldData.templateWorldName = newLobbyLocation.getWorld().getName();

                saveWorldData();

                player.sendMessage(ChatColor.GREEN + "[Casco] Configuración guardada OK.");
            }
        } else if(args.length == 1 && args[0].equalsIgnoreCase("lobby")) {
            newLobbyLocation = player.getLocation();
            player.sendMessage(ChatColor.GREEN + "[Casco] Ubicación del lobby guardada.");

            if(newGameLocation == null) {
                player.sendMessage("Ahora define la ubicación del juego con /casco config game");
            } else {
                player.sendMessage("Para salir de la configuración y guardar, usa /casco config");
            }
        } else if(args.length == 1 && args[0].equalsIgnoreCase("game")) {
            newGameLocation = player.getLocation();
            player.sendMessage(ChatColor.GREEN + "[Casco] Ubicación del juego guardada.");

            if(newLobbyLocation == null) {
                player.sendMessage("Ahora define la ubicación del lobby con /casco config lobby");
            } else {
                player.sendMessage("Para salir de la configuración y guardar, usa /casco config");
            }
        } else {
            configPlayer = player;

            for(int i = 0; i < 20; i++) {
                player.sendMessage("");
            }

            player.sendMessage(ChatColor.YELLOW + "[Casco] INICIANDO CONFIGURACION DE ARENA PLANTILLA ---");
            player.sendMessage("Define las siguientes ubicaciones, poniendo el jugador en el lugar:");
            player.sendMessage("Ubicación del lobby con /casco config lobby");
            player.sendMessage("Ubicación del juego con /casco config game");
            player.sendMessage("");
            player.sendMessage("Para salir de la configuración y guardar, usa /casco config");
        }
    }

    public Arena createArena() {
        // fetch world data from worldData
        MultiverseWorld templateWorld = mvCore.getMVWorldManager().getMVWorld(worldData.templateWorldName);

        if(templateWorld == null) {
            plugin.getServer().broadcastMessage(ChatColor.RED +"[Casco] Error: No se pudo encontrar el mundo de plantilla. No se puede crear una arena.");
            return null;
        }

        // copy the template world to a new world
        // truncate currentTimeMilliseconds to the last 5 digits
        String newWorldName = ARENA_WORLD_PREFIX + (System.currentTimeMillis() + "").substring(5);
        mvCore.getMVWorldManager().cloneWorld(worldData.templateWorldName, newWorldName);

        MultiverseWorld newWorld = mvCore.getMVWorldManager().getMVWorld(newWorldName);

        if(newWorld == null) {
            plugin.getServer().broadcastMessage(ChatColor.RED +"[Casco] Error: No se pudo clonar el mundo de plantilla. No se puede crear una arena.");
            return null;
        }

        newWorld.getCBWorld().setTime(6000);
        newWorld.getCBWorld().setStorm(false);

        newWorld.getCBWorld().setGameRule(GameRule.DO_MOB_SPAWNING, false);
        newWorld.getCBWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        newWorld.getCBWorld().setGameRule(GameRule.DO_INSOMNIA, false);

        log.info("Created arena " + newWorldName);

        Location lobbyLocation = new Location(
                newWorld.getCBWorld(),
                worldData.lobbyX,
                worldData.lobbyY,
                worldData.lobbyZ
        );

        Location gameLocation = new Location(
                newWorld.getCBWorld(),
                worldData.gameX,
                worldData.gameY,
                worldData.gameZ
        );

        Arena arena = new Arena(this, lobbyLocation, gameLocation);
        arena.runTaskTimer(plugin, 0, 0);

        activeArenas.put(arena, newWorld);

        return arena;
    }

    public void joinPlayer(Player player) {
        for(Arena arena : activeArenas.keySet()) {
            if(arena.players.contains(player)) {
                player.sendMessage(ChatColor.RED + "Ya estás en una partida.");
                return;
            }
        }

        for(Arena arena : activeArenas.keySet()) {
            if(arena.status == Arena.ArenaStatus.LOBBY && arena.players.size() < getMaxPlayersPerArena()) {
                arena.addPlayer(player);
                return;
            }
        }

        // if all arenas are full or unavailable, create a new one
        Arena arena = createArena();
        if(arena != null) {
            arena.addPlayer(player);
        }
    }

    public void ceaseArena(Arena arena) {
        MultiverseWorld world = activeArenas.get(arena);
        mvCore.getMVWorldManager().deleteWorld(world.getName());

        arena.cancel();
        activeArenas.remove(arena);
    }

    public int getMinPlayersPerArena() {
        return plugin.getConfig().getInt("minPlayersToStart");
    }
    public int getMaxPlayersPerArena() {
        return plugin.getConfig().getInt("maxPlayersPerArena");
    }

    public int getLobbyDelaySeconds() {
        return plugin.getConfig().getInt("lobbyDelaySeconds");
    }
    public int getGameDurationSeconds() {
        return plugin.getConfig().getInt("gameDurationSeconds");
    }

    public int getPointsToWin() {
        return plugin.getConfig().getInt("pointsToWin");
    }


    // taggedPlayersPerTenPlayers
    public int getTaggedPlayersPerTenPlayers() {
        return plugin.getConfig().getInt("taggedPlayersPerTenPlayers");
    }
}
