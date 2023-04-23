package me.someonelove.matsuqueue.queue.impl;

import com.velocitypowered.api.proxy.Player;
import me.someonelove.matsuqueue.Matsu;
import me.someonelove.matsuqueue.queue.IMatsuQueue;

import java.util.*;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

public class MatsuQueue implements IMatsuQueue {
    public final String name;
    public final int priority;
    public final String permission;
    public final String slots;
    private boolean boostEnabled;
    private float boostRatio;

    private String boostPermission;

    private Random generator = new Random();

    private String header;
    private String footer;
    private String boostFooter;

    private LinkedList<UUID> queue = new LinkedList<>();

    public MatsuQueue(String name, int priority, String slots, @Nullable String permission, boolean boostEnabled, float boostRatio, String boostPermission) {
        this.name = name;
        this.priority = priority;
        this.permission = permission;
        this.slots = slots;

        this.boostEnabled = boostEnabled;
        this.boostRatio = boostRatio;
        this.boostPermission = boostPermission;
    }

    @Override
    public void addPlayerToQueue(Player player) {
        if (queue.contains(player.getUniqueId())) {
            if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(String.format("Tried to assign %s to a queue they are already in!", player.getUsername()));}
            return;
        }

        queue.add(player.getUniqueId());
        player.sendMessage(Component.text(Matsu.CONFIG.serverFullMessage.replace("&", "\247")));
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
        if (boostEnabled && GenerateBoost()) {
            LinkedList<UUID> boostQueue = new LinkedList<>();
            for (UUID uuid : queue) {
                Player unfilteredPlayer = Matsu.INSTANCE.getProxy().getPlayer(uuid).get();
                if (unfilteredPlayer.hasPermission(boostPermission)) {
                    boostQueue.add(unfilteredPlayer.getUniqueId());
                }
            }

            if (!boostQueue.isEmpty()) {
                player = Matsu.INSTANCE.getProxy().getPlayer(boostQueue.getFirst()).get();
                player.sendMessage(Component.text(Matsu.CONFIG.boostMessage.replace("&", "\247")));
                if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(String.format("Queue %s was boosted!", name));}
            }
        }

        player.sendMessage(Component.text(Matsu.CONFIG.connectingMessage.replace("&", "\247")));
        Matsu.CONFIG.slotsMap.get(slots).occupySlot(player);
        queue.remove(player.getUniqueId());
        player.createConnectionRequest(Matsu.destinationServerInfo).connect();
        if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(player.getUsername() + " transferred to destination server");}
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
    public String getBoostPermission() {
        return boostPermission;
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
    public void setBoostFooter(String boostFooter) {
        this.boostFooter = boostFooter.replace("\\n", "\n");
    }

    @Override
    public String getTabHeader() {
        return header.replace("&", "\247");
    }

    @Override
    public String getTabFooter() {
        return footer.replace("&", "\247");
    }

    @Override
    public String getBoostFooter() {
        return boostFooter.replace("&", "\247");
    }

    public Boolean GenerateBoost() {
        float random = generator.nextFloat();
        if (Matsu.CONFIG.verbose) {
            Matsu.INSTANCE.getLogger().info(String.format("Boost Generator \nrandom float = %s \n1/Boost Ratio = %s \nBoost: %s", random, 1 / boostRatio, random > (1 / boostRatio)));
        }
        return random > (1 / boostRatio);
    }
}
