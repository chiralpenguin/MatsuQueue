package me.someonelove.matsuqueue.bungee.queue;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Interface that sets up an easy way to manage this queue
 */
public interface IMatsuQueue {

    void addPlayerToQueue(ProxiedPlayer player);

    void removePlayerFromQueue(ProxiedPlayer player);

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
