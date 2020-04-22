package me.someonelove.matsuqueue.bungee.queue;

import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interface that defines and keeps track of the amount of slots on destination server
 */
public interface IMatsuSlotCluster {

    int getAvailableSlots();

    /**
     * Return the total amount of slots
     *
     * @param global should we include the slots from other instances of IMatsuSlots?
     */
    int getTotalSlots(boolean global);
    
	void setTotalSlots(boolean global, int newSlots);

    void queuePlayer(ProxiedPlayer player);

    boolean needsQueueing();

    void onPlayerLeave(ProxiedPlayer player);

    void onPlayerLeave(UUID player);

    void occupySlot(ProxiedPlayer player);

    void associateQueue(IMatsuQueue queue);

    HashSet<UUID> removeDuplicateSlots();

    void connectHighestPriorityPlayer();

    ConcurrentHashMap<String, IMatsuQueue> getAssociatedQueues();

    String getPermission();

    void broadcast(String str);

	void broadcast(String str, ProxiedPlayer targetPlayer);

    List<UUID> getSlots();

	String getSlotName();

}
