package me.someonelove.matsuqueue;

import me.someonelove.matsuqueue.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.queue.impl.MatsuSlotCluster;
import net.luckperms.api.LuckPermsProvider;

import java.net.InetAddress;
import java.util.*;

public class ConnectionManager {
    private HashMap<String, InetAddress> connections;
    private HashMap<IMatsuSlotCluster, LinkedList<String>> excessAccounts;

    public ConnectionManager() {
        connections = new HashMap<>();
        excessAccounts = new HashMap<>();
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
            if (!excessAccounts.containsKey(slot)) {
                excessAccounts.put(slot, new LinkedList<>());
            }
        });
    }

    public void addConnection(String player, InetAddress address) {
        connections.put(player, address);
    }

    public void removeConnection(String player) {
        connections.remove(player);
    }

    public List<String> getConnectedAccounts(InetAddress address) {
        List<String> players = new ArrayList<>();
        connections.forEach((player, playerAddress) -> {
            if (playerAddress.equals(address)) {
                players.add(player);
            }
        });

        return players;
    }

    public int getNumberConnected(InetAddress address) {
        return getConnectedAccounts(address).size();
    }

    public void addExcessAccount(IMatsuSlotCluster slot, String player, InetAddress address) {
        addConnection(player, address);
        excessAccounts.get(slot).add(player);
    }

    public void removeExcessAccount(IMatsuSlotCluster slot, String player) {
        removeConnection(player);
        excessAccounts.get(slot).remove(player);
    }

    public boolean hasExcess(IMatsuSlotCluster slot) {
        return excessAccounts.get(slot).size() > 0;
    }

    public boolean isExcess(String player) {
        IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromUsername(player);
        return excessAccounts.get(slot).contains(player);
    }

    public String getLastExcessAccount(IMatsuSlotCluster slot) {
        return excessAccounts.get(slot).getLast();
    }
}
