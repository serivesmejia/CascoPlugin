package org.deltacv.casco.arena;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;


public class Arena extends BukkitRunnable implements Listener {

    ArenaManager manager;

    Location lobbyLocation;
    public Location gameLocation;

    ArrayList<Player> playersToRemove = new ArrayList<>();

    public ArrayList<Player> players = new ArrayList<>();
    public ArenaStatus status = ArenaStatus.LOBBY;

    long creationTimestamp = System.currentTimeMillis();
    long startingCountdownTimestamp = -1;
    long gameHaltTimestamp = -1;
    long gameStartTimestamp = -1;

    int previousRemainingTimeSecs = -1;
    long lastZoneAwardTimestamp = -1;
    long resultsTimestamp = -1;
    long resultsLastFireworkTimestamp = System.currentTimeMillis();

    Player winningPlayer = null;

    HashMap<Player, Integer> playerScores = new HashMap<>();
    HashMap<Player, Boolean> playerIsTagged = new HashMap<>();

    int taggedPlayersCount = -1;

    int playersCountOnBegin = -1;

    PotionEffect blindnessEffect = new PotionEffect(PotionEffectType.BLINDNESS, 20 * 10, 10000, false, false, false);
    PotionEffect slownessEffect = new PotionEffect(PotionEffectType.SLOWNESS, 20 * 10, 10000, false, false, false);
    PotionEffect invisibilityEffect = new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 10, 1, false, false, false);

    PotionEffect speedEffect = new PotionEffect(PotionEffectType.SPEED, 20 * 10, 2, false, false, false);

    ItemStack exitItem = new ItemStack(Material.RED_BED);

    Sidebar sidebar;

    boolean hasSentStartWithMinPlayersMessage = false;

    int lastStartingCountdownSeconds = -1;

    public Arena(ArenaManager manager, Location lobbyLocation, Location gameLocation) {
        this.manager = manager;

        ItemMeta im = Objects.requireNonNull(exitItem.getItemMeta());
        im.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Salir al Lobby");
        exitItem.setItemMeta(im);

        sidebar = manager.scoreboardLibrary.createSidebar();

        sidebar.title(Component.text(ChatColor.YELLOW + "" + ChatColor.BOLD + "Rey del Objeto"));
        sidebar.line(0, Component.empty());
        sidebar.line(1, Component.text(ChatColor.GRAY + "Esperando..."));
        sidebar.line(2, Component.empty());

        this.lobbyLocation = lobbyLocation;
        this.gameLocation = gameLocation;
    }

    @Override
    public void run() {
        playersToRemove.clear();

        for (Player player : players) {
            player.setHealth(20);
            player.setSaturation(20);

            if(!player.isOnline() || player.getWorld() != lobbyLocation.getWorld()) {
                if(!playersToRemove.contains(player)) {
                    playersToRemove.add(player);
                }
            }
        }

        for (Player player : playersToRemove) {
            players.remove(player);
            sidebar.removePlayer(player);
            player.getInventory().clear();

            manager.econ.withdrawPlayer(player, manager.econ.getBalance(player));

            for (Player p : players) {
                p.sendMessage(ChatColor.YELLOW + player.getName() + " ha abandonado la partida. (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")");
            }
        }

        switch (status) {
            case LOBBY -> {
                int lobbyDelayHalf = manager.getLobbyDelaySeconds() / 2;

                sidebar.line(3, Component.text(ChatColor.GRAY + "" + players.size() + "/" + manager.getMaxPlayersPerArena() + " jugadores"));

                if (players.size() >= manager.getMinPlayersPerArena() && elapsedSecondsSinceCreation() >= lobbyDelayHalf && !hasSentStartWithMinPlayersMessage) {
                    hasSentStartWithMinPlayersMessage = true;

                    for (Player player : players) {
                        player.sendMessage(ChatColor.GRAY + "La partida empezará en " + lobbyDelayHalf + " segundos si hay suficientes jugadores.");
                    }
                } else if (players.size() >= manager.getMinPlayersPerArena() && elapsedSecondsSinceCreation() >= manager.getLobbyDelaySeconds()) {
                    status = ArenaStatus.STARTING;
                    break;
                } else if (players.size() == manager.getMaxPlayersPerArena()) {
                    // if arena is full, start countdown
                    status = ArenaStatus.STARTING;
                    break;
                } else if (players.size() < manager.getMinPlayersPerArena()) {
                    hasSentStartWithMinPlayersMessage = false;
                    creationTimestamp = System.currentTimeMillis();
                }

                for (Player player : players) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.YELLOW + "" + ChatColor.BOLD + "Esperando jugadores... (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")"));
                    player.setGameMode(GameMode.ADVENTURE);
                    player.getInventory().setItem(8, exitItem);
                }
            }

            case STARTING -> {
                if(players.size() < manager.getMinPlayersPerArena()) {
                    startingCountdownTimestamp = -1;
                    status = ArenaStatus.LOBBY;

                    for(Player player : players) {
                        player.sendMessage(ChatColor.RED + "No hay suficientes jugadores para empezar la partida.");
                    }

                    break;
                }

                // count from five to zero and tp to game
                sidebar.line(1, Component.text(ChatColor.GREEN + "Iniciando..."));

                if (startingCountdownTimestamp == -1) {
                    startingCountdownTimestamp = System.currentTimeMillis();
                }

                int seconds = 5 - (int) elapsedSecondsSinceStartingCountdown();

                if (seconds == 0) {
                    for(Player player : players) {
                        for(int i = 0; i < 20; i++) {
                            player.sendMessage("");
                        }
                    }
                    status = ArenaStatus.BEGIN;
                } else if (seconds != lastStartingCountdownSeconds) {
                    // broadcast countdown message
                    for (Player player : players) {
                        player.sendMessage(ChatColor.YELLOW + "Empezando en " + seconds + "...");
                        player.playSound(player.getLocation(), "block.note_block.pling", 1, 1);

                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.RED + "" + ChatColor.BOLD + "Todos listos !"));
                    }
                }

                lastStartingCountdownSeconds = seconds;
            }

            case BEGIN -> {
                playersCountOnBegin = players.size();

                manager.log.info("Begin game in arena " + gameLocation.getWorld().getName());

                for (Player player : players) {
                    player.getInventory().clear();
                    player.sendMessage(ChatColor.GREEN + "La partida se acabará con el primer jugador que obtenga " + manager.getPointsToWin() + " puntos !");
                    player.teleport(gameLocation);
                }

                double playersRate = players.size() / 10d;
                taggedPlayersCount = Math.max((int) (playersRate * manager.getTaggedPlayersPerTenPlayers()), 1);

                // select random tagged players
                Collections.shuffle(players);

                ItemStack casco = new ItemStack(Material.LEATHER_HELMET);
                // tint white and blue
                LeatherArmorMeta meta = (LeatherArmorMeta) casco.getItemMeta();
                meta.setColor(Color.BLUE);
                casco.setItemMeta(meta);

                for (int i = 0; i < taggedPlayersCount; i++) {
                    players.get(i).getInventory().setHelmet(casco);

                    players.get(i).sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Tienes el casco! ¡Corre en cuanto puedas!");
                    players.get(i).playSound(players.get(i).getLocation(), "entity.experience_orb.pickup", 100000, 1);

                    playerIsTagged.put(players.get(i), true);
                }

                gameHaltTimestamp = System.currentTimeMillis();
                status = ArenaStatus.PLAY_HALT;
            }

            case PLAY_HALT -> {
                if (System.currentTimeMillis() - gameHaltTimestamp >= 3000) {
                    gameStartTimestamp = System.currentTimeMillis();
                    status = ArenaStatus.PLAYING;

                    for (Player player : players) {
                        if(!playerIsTagged.getOrDefault(player, false)) {
                            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "A pelear !");
                        }

                        player.playSound(player.getLocation(), "entity.generic.explode", 100000, 1);

                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        player.removePotionEffect(PotionEffectType.SLOWNESS);
                        player.removePotionEffect(PotionEffectType.INVISIBILITY);

                        double randomX = (Math.random() * 2 - 1) * 4;
                        double randomZ = (Math.random() * 2 - 1) * 4;

                        player.setVelocity(new Vector(
                                (float) randomX,
                                1f,
                                (float) randomZ
                        ));
                    }
                } else {
                    for (Player player : players) {
                        player.addPotionEffect(blindnessEffect);
                        player.addPotionEffect(slownessEffect);
                        player.addPotionEffect(invisibilityEffect);

                        player.teleport(gameLocation);
                    }
                }
            }

            case PLAYING -> {
                long remainingTimeSecs = manager.getGameDurationSeconds() - (System.currentTimeMillis() - gameStartTimestamp) / 1000;
                if (previousRemainingTimeSecs == -1) {
                    previousRemainingTimeSecs = (int) remainingTimeSecs;
                }

                if (previousRemainingTimeSecs != (int) remainingTimeSecs) {
                    if (remainingTimeSecs == 30 || remainingTimeSecs == 15) {
                        for (Player player : players) {
                            player.sendMessage(ChatColor.YELLOW + "Quedan " + remainingTimeSecs + " segundos !");
                            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1, 10);
                        }
                    } else if (remainingTimeSecs <= 10 && remainingTimeSecs > 0) {
                        for (Player player : players) {
                            player.sendMessage(ChatColor.RED + "El juego se acaba en " + remainingTimeSecs + " segundos.");
                            player.playSound(player.getLocation(), "entity.experience_orb.pickup", 1, 1);
                        }
                    }
                }

                previousRemainingTimeSecs = (int) remainingTimeSecs;

                int remainingMinutes = (int) (remainingTimeSecs) / 60;
                int remainingSeconds = (int) (remainingTimeSecs) % 60;

                sidebar.line(1, Component.text(ChatColor.GRAY + "Tiempo restante: " + ChatColor.YELLOW + remainingMinutes + "m " + remainingSeconds + "s"));

                sidebar.line(3, Component.text(ChatColor.YELLOW + "Puntos para ganar: " + ChatColor.GREEN + manager.getPointsToWin()));
                sidebar.line(4, Component.empty());
                sidebar.line(5, Component.text(ChatColor.GRAY + "Top puntajes:"));

                List<Map.Entry<Player, Integer>> topScores = playerScores.entrySet().stream()
                        .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                        .limit(5)
                        .toList();

                // grab at most the top 3
                for (int i = 0; i < Math.min(3, topScores.size()); i++) {
                    Map.Entry<Player, Integer> entry = topScores.get(i);
                    // sidebar with #n player name and score
                    sidebar.line(6 + i, Component.text(ChatColor.GRAY + "#" + (i + 1) + " " + ChatColor.YELLOW + entry.getKey().getName() + ChatColor.GRAY + " - " + ChatColor.YELLOW + entry.getValue() + " puntos"));
                }

                if (remainingTimeSecs <= 0 || (players.size() <= 1 && playersCountOnBegin > 1)) {
                    status = ArenaStatus.RESULTS;
                    break;
                }

                for (Player player : players) {
                    boolean isTagged = playerIsTagged.getOrDefault(player, false);

                    if(isTagged) {
                        player.removePotionEffect(PotionEffectType.SPEED);
                        player.setGlowing(true);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.GREEN + "" + ChatColor.BOLD + "¡Tienes el casco!" + ChatColor.GRAY + " - " + playerScores.getOrDefault(player, 0) + " puntos"));
                    } else {
                        speedEffect.apply(player);
                        player.setGlowing(false);
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.RED + "" + ChatColor.BOLD + "¡No tienes el casco!" + ChatColor.GRAY + " - " + playerScores.getOrDefault(player, 0) + " puntos"));
                    }

                    if (System.currentTimeMillis() - lastZoneAwardTimestamp > 1000) {
                        if (isTagged) {
                            int score = playerScores.getOrDefault(player, 0) + 1;
                            manager.econ.depositPlayer(player, 1);

                            playerScores.put(player, score);
                            // noteblock tick
                            player.playSound(player.getLocation(), "block.note_block.hat", 1, 1);
                        }
                    }

                    if (playerScores.getOrDefault(player, 0) >= manager.getPointsToWin()) {
                        status = ArenaStatus.RESULTS;
                        break;
                    }
                }

                if (System.currentTimeMillis() - lastZoneAwardTimestamp > 1000) {
                    lastZoneAwardTimestamp = System.currentTimeMillis();
                }
            }

            case RESULTS -> {
                if(players.isEmpty()) {
                    status = ArenaStatus.END;
                    break;
                }

                if (resultsTimestamp == -1) {
                    resultsTimestamp = System.currentTimeMillis();
                    // get winning player
                    winningPlayer = playerScores.entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse(null);

                    if(winningPlayer == null) {
                        status = ArenaStatus.END;
                        break;
                    }

                    for (Player player : players) {
                        player.getInventory().clear();
                        player.playSound(player.getLocation(), "entity.player.levelup", 1, 1);
                        player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "¡" + winningPlayer.getName() + " ha ganado la partida!");
                    }
                } else {
                    for (Player player : players) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(ChatColor.YELLOW + "" + ChatColor.BOLD + "¡" + winningPlayer.getName() + " ha ganado la partida!"));
                    }

                    sidebar.line(1, Component.text(ChatColor.GREEN + "¡Victoria para " + winningPlayer.getName() + "!"));

                    // fireworks
                    if (System.currentTimeMillis() - resultsLastFireworkTimestamp >= 1000) {
                        resultsLastFireworkTimestamp = System.currentTimeMillis();

                        winningPlayer.getWorld().spawn(winningPlayer.getLocation(), Firework.class, firework -> {
                            FireworkMeta fm = firework.getFireworkMeta();
                            fm.addEffect(FireworkEffect.builder()
                                    .withColor(Color.WHITE)
                                    .withColor(Color.BLUE)
                                    .with(FireworkEffect.Type.BURST)
                                    .withFlicker()
                                    .withTrail()
                                    .build()
                            );
                            fm.setPower(0);
                            firework.setFireworkMeta(fm);
                        });
                    }

                    if (System.currentTimeMillis() - resultsTimestamp >= 12000) {
                        status = ArenaStatus.END;
                    }
                }
            }

            case END -> {
                for(Player player : players) {
                    player.setGlowing(false);
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(""));


                    manager.econ.withdrawPlayer(player, manager.econ.getBalance(player));
                }

                sidebar.removePlayers(players);
                manager.ceaseArena(this);
            }
        }
    }

    public void addPlayer(Player player) {
        players.add(player);

        player.getInventory().clear();
        player.teleport(lobbyLocation);

        sidebar.addPlayer(player);

        for (Player p : players) {
            p.sendMessage(ChatColor.YELLOW + player.getName() + " se ha unido a la partida. (" + players.size() + "/" + manager.getMaxPlayersPerArena() + ")");
        }
    }

    public void notifyTag(Player damager, Player damaged) {
        if(playerIsTagged.getOrDefault(damaged, false) && !playerIsTagged.getOrDefault(damager, false)) {
            playerIsTagged.put(damager, true);
            playerIsTagged.put(damaged, false);

            ItemStack casco = new ItemStack(Material.LEATHER_HELMET);
            // tint white and blue
            LeatherArmorMeta meta = (LeatherArmorMeta) casco.getItemMeta();
            meta.setColor(Color.BLUE);
            casco.setItemMeta(meta);

            damager.getInventory().setHelmet(casco);
            damaged.getInventory().setHelmet(null);

            damaged.sendMessage(ChatColor.RED + "¡" + damager.getName() + " te ha quitado el casco!");
            damager.sendMessage(ChatColor.GREEN + "¡Le has quitado el casco a" + damaged.getName() + "!");

            damager.playSound(damager.getLocation(), "entity.experience_orb.pickup", 1, 1);
            damaged.playSound(damaged.getLocation(), "entity.experience_orb.pickup", 1, 1);
        }
    }

    public void notifyItemInteract(Player player, ItemStack item) {
        if(item == null) {
            return;
        }

        if(item.getItemMeta().getDisplayName().equals(exitItem.getItemMeta().getDisplayName()) && status == ArenaStatus.LOBBY) {
            World spawnWorld = Bukkit.getWorld("world");
            Location spawnLocation = spawnWorld.getSpawnLocation();

            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacy(""));
            player.teleport(spawnLocation);

            if(!playersToRemove.contains(player)) {
                playersToRemove.add(player);
            }

            for(Player p : players) {
                p.sendMessage(ChatColor.RED + player.getName() + " ha salido de la partida. (" + (players.size()) + "/" + manager.getMaxPlayersPerArena() + ")");
            }
        }
    }

    public double elapsedSecondsSinceCreation() {
        return (System.currentTimeMillis() - creationTimestamp) / 1000.0;
    }

    public double elapsedSecondsSinceStartingCountdown() {
        return (System.currentTimeMillis() - startingCountdownTimestamp) / 1000.0;
    }

    public enum ArenaStatus {
        LOBBY, STARTING,
        BEGIN,
        PLAY_HALT, PLAYING,
        RESULTS, END
    }

}
