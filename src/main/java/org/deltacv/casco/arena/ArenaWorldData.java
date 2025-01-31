package org.deltacv.casco.arena;

public class ArenaWorldData {
    String templateWorldName;

    double lobbyX;
    double lobbyY;
    double lobbyZ;

    double gameX;
    double gameY;
    double gameZ;

    @Override
    public String toString() {
        return "ArenaWorldData{" +
                "templateWorldName='" + templateWorldName + '\'' +
                ", lobbyX=" + lobbyX +
                ", lobbyY=" + lobbyY +
                ", lobbyZ=" + lobbyZ +
                ", gameX=" + gameX +
                ", gameY=" + gameY +
                ", gameZ=" + gameZ +
                '}';
    }
}
