package me.someonelove.matsuqueue.bungee.queue;

import com.velocitypowered.api.proxy.Player;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.UUID;

/**
 * Interface that sets up an easy way to manage this queue
 */
public interface IMatsuQueue {

    void addPlayerToQueue(Player player);

    void removePlayerFromQueue(Player player);

    void removePlayerFromQueue(UUID player);

    void connectFirstPlayerToDestinationServer();

    HashSet<UUID> removeDuplicateUUIDs();

    String getName();

    int getPriority();

    String getPermission();

    LinkedList<UUID> getQueue();

    void setTabText(String header, String footer);

    String getTabHeader();

    String getTabFooter();

}
