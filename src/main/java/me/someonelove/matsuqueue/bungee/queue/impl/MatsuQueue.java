package me.someonelove.matsuqueue.bungee.queue.impl;

import com.velocitypowered.api.proxy.Player;
import me.someonelove.matsuqueue.bungee.Matsu;
import me.someonelove.matsuqueue.bungee.queue.IMatsuQueue;

import java.util.*;
import java.util.logging.Level;

import net.kyori.adventure.text.TextComponent;
import org.jetbrains.annotations.Nullable;

public class MatsuQueue implements IMatsuQueue {
    public final String name;
    public final int priority;
    public final String permission;
    public final String slots;

    private String header;
    private String footer;

    private LinkedList<UUID> queue = new LinkedList<>();

    public MatsuQueue(String name, int priority, String slots, @Nullable String permission) {
        this.name = name;
        this.priority = priority;
        this.permission = permission;
        this.slots = slots;
    }

    @Override
    public void addPlayerToQueue(Player player) {
        if (queue.contains(player.getUniqueId())) {
            if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(String.format("Tried to assign %s to a queue they are already in!", player.getUsername()));}
            return;
        }

        queue.add(player.getUniqueId());
        player.sendMessage(TextComponent.of(Matsu.CONFIG.serverFullMessage.replace("&", "\247")));
        if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(player.getUsername() + " placed in queue " + this.name);}

        /* TODO Reimplement this feature so that players are made aware of their queue position immediately after logging in. It displays an incorrect position atm.
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> slot.broadcast(Matsu.CONFIG.positionMessage.replace("&", "\247"), player));
         */
    }

    @Override
    public void removePlayerFromQueue(Player player) {
        queue.remove(player.getUniqueId());
    }

    @Override
    public void removePlayerFromQueue(UUID player) {
        queue.remove(player);
    }

    @Override
    public void connectFirstPlayerToDestinationServer() {
        if (queue.isEmpty()) return;
        Player player = Matsu.INSTANCE.getProxy().getPlayer(queue.getFirst()).get();
        player.sendMessage(TextComponent.of(Matsu.CONFIG.connectingMessage.replace("&", "\247")));
        player.createConnectionRequest(Matsu.destinationServerInfo);
        if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(player.getUsername() + " transferred to destination server");}
        Matsu.CONFIG.slotsMap.get(slots).occupySlot(player);
        queue.remove(queue.getFirst());
    }

    @Override
    public HashSet<UUID> removeDuplicateUUIDs() {
        HashSet<UUID> duplicates = new HashSet<>();
        HashSet<UUID> IDset = new HashSet<>();

        Iterator<UUID> it = getQueue().iterator();
        int i = 0;
        while (it.hasNext()) {
            UUID id = it.next();
            if (!IDset.contains(id)) {
                IDset.add(id);
            }
            else {
                duplicates.add(id);
                getQueue().remove(i);
            }

            i ++;
        }

        return duplicates;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public final LinkedList<UUID> getQueue() {
        return queue;
    }

    @Override
    public void setTabText(String header, String footer) {
        this.header = header.replace("\\n", "\n");
        this.footer = footer.replace("\\n", "\n");
    }

    @Override
    public String getTabHeader() {
        return header.replace("&", "\247");
    }

    @Override
    public String getTabFooter() {
        return footer.replace("&", "\247");
    }

}
