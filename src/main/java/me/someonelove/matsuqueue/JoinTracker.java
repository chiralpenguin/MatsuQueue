package me.someonelove.matsuqueue;

import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.io.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class JoinTracker {
    private final Set<UUID> joinedPlayers;

    public JoinTracker(Set<UUID> joinedPlayers) {
        this.joinedPlayers = joinedPlayers;
    }

    public JoinTracker(Logger logger) {
        Set<UUID> joinedPlayers;
        try {
            joinedPlayers = readJoinedPlayers("plugins/MatsuQueue/joined_players.txt");
        } catch (IOException exception) {
            logger.error("Could not read joined players file!");
            joinedPlayers = new HashSet<>();
        }

        this.joinedPlayers = joinedPlayers;
    }

    private Set<UUID> readJoinedPlayers(String filepath) throws IOException {
        Set<UUID> playerIDs = new HashSet<>();

        try (BufferedReader buffer = new BufferedReader(new FileReader(filepath))) {
            String line = buffer.readLine();

            while (line != null) {
                playerIDs.add(UUID.fromString(line));
                line = buffer.readLine();
            }
        }

        return playerIDs;
    }

    public void saveJoinedPlayers(String filepath) throws IOException {
        try (BufferedWriter buffer = new BufferedWriter(new FileWriter(filepath))) {
            for (UUID id : joinedPlayers) {
                buffer.write(id.toString());
                buffer.newLine();
            }
        }
    }

    public Boolean playerJoinedBefore(UUID playerID) {
        return joinedPlayers.contains(playerID);
    }

    public Boolean playerJoinedBefore(Player player) {
        return playerJoinedBefore(player.getUniqueId());
    }

    public void playerJoin(UUID playerID) {
        joinedPlayers.add(playerID);
    }

    public void playerJoin(Player player) {
        playerJoin(player.getUniqueId());
    }
}
