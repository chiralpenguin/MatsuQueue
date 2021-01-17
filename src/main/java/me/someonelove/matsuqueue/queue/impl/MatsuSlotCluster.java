package me.someonelove.matsuqueue.queue.impl;

import com.velocitypowered.api.proxy.Player;
import me.someonelove.matsuqueue.Matsu;
import me.someonelove.matsuqueue.queue.IMatsuQueue;
import me.someonelove.matsuqueue.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.queue.IQueuePool;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.query.QueryOptions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class MatsuSlotCluster implements IMatsuSlotCluster {

    public final String name;
    public final String permission;
    private int max;
    private List<UUID> slots = Collections.synchronizedList(new LinkedList<>());
    private ConcurrentHashMap<String, IMatsuQueue> associatedQueues = new ConcurrentHashMap<>();
    private IQueuePool queuePool;
    public MatsuSlotCluster(String name, int capacity, String permission) {
        this.name = name;
        this.max = capacity;
        this.permission = permission;
    }

    @Override
    public String getSlotName() {
    	return name;
    }
    
    @Override
    public int getAvailableSlots() {
        return max - slots.size();
    }

    @Override
    public int getTotalSlots(boolean global) {
        return max;
    }
    
    @Override
    public void setTotalSlots(boolean global, int newSlots) {
        max = newSlots;
    }

    @Override
    public void queuePlayer(Player player) {
        if (player.hasPermission(Matsu.CONFIG.bypassPermission)) {
            player.sendMessage(Component.text(Matsu.CONFIG.connectingMessage.replace("&", "\247")));
            player.createConnectionRequest(Matsu.destinationServerInfo).connect();
            if (Matsu.CONFIG.verbose) {
                Matsu.INSTANCE.getLogger().info(player.getUsername() + " bypassed the queue");
            }
            return;
        }

        if (!needsQueueing()) {
            occupySlot(player);
            return;
        }

        List<IMatsuQueue> queues = new ArrayList<IMatsuQueue>();
        for (Map.Entry<String, IMatsuQueue> entry : associatedQueues.entrySet()) {
            User lplayer = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
            Collection<Node> permissionNodes = lplayer.resolveInheritedNodes(QueryOptions.nonContextual());
            Set<String> perms = permissionNodes.stream().filter(NodeType.PERMISSION::matches).map(NodeType.PERMISSION::cast).map(PermissionNode::getKey).collect(Collectors.toSet());
            Set<String> negatedPerms = permissionNodes.stream().filter(Node::isNegated).filter(NodeType.PERMISSION::matches).map(NodeType.PERMISSION::cast).map(PermissionNode::getKey).collect(Collectors.toSet());
            perms.removeAll(negatedPerms);

            for (String perm : perms) {
                if (!perm.contains(".") || !perm.startsWith("matsuqueue")) continue;
                String[] broken = perm.split("\\.");
                if (broken.length != 3) continue;
                if (entry.getValue().getPermission().equals(broken[2])) {
                    queues.add(entry.getValue());
                    if (Matsu.CONFIG.verbose) {
                        Matsu.INSTANCE.getLogger().info(String.format("Player: %s | Node: %s | Queue: %s", player.getUsername(), entry.getValue().getPermission(), entry.getValue().getName()));
                    }
                }
            }
        }

        if (queues.size() != 0) {
            IMatsuQueue topQueue = queues.get(0);

            for (IMatsuQueue queue : queues) {
                if (queue.getPriority() < topQueue.getPriority()) {
                    topQueue = queue;
                }
            }
            topQueue.addPlayerToQueue(player);
            broadcast(Matsu.CONFIG.positionMessage.replace("&", "\247"), player);
            return;
        }

        // code quality goes to shit after my brain goes numb ;-;
        for (Map.Entry<String, IMatsuQueue> entry : associatedQueues.entrySet()) {
            if (entry.getValue().getPermission().equals("default")) {
                entry.getValue().addPlayerToQueue(player);
                broadcast(Matsu.CONFIG.positionMessage.replace("&", "\247"), player);
            }
        }
    }

    @Override
    public boolean needsQueueing() {
        return getAvailableSlots() < 1;
    }

    @Override
    public void onPlayerLeave(Player player) {
        if (slots.contains(player.getUniqueId())) {
            releaseSlot(player);
            return;
        }
        this.getAssociatedQueues().forEach((name, queue) -> {
            queue.removePlayerFromQueue(player);
        });
    }

    @Override
    public void onPlayerLeave(UUID player) {
        if (slots.contains(player)) {
            releaseSlot(player);
            return;
        }
        this.getAssociatedQueues().forEach((name, queue) -> {
            queue.removePlayerFromQueue(player);
        });
    }

    @Override
    public void occupySlot(Player player) {
        this.occupySlot(player.getUniqueId());
    }

    protected void occupySlot(UUID player) {
        if (slots.contains(player)) {
            if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(String.format("Tried to assign %s to a slot they are already in!", Matsu.INSTANCE.getProxy().getPlayer(player).get().getUsername()));}
            return;
        }

        slots.add(player);
    }

    protected void releaseSlot(Player player) {
        this.releaseSlot(player.getUniqueId());
    }

    protected void releaseSlot(UUID player) {
        slots.remove(player);
        if (this.getAvailableSlots() > 0) {
        	connectNextPlayer();
        }
    }

    public HashSet<UUID> removeDuplicateSlots() {
        List<UUID> duplicates = new ArrayList<>();

        for (UUID slot : slots) {
            if (Collections.frequency(slots, slot) > 1) {
                duplicates.add(slot);
            }
        }

        slots.removeAll(duplicates);
        HashSet<UUID> duplicateSet = new HashSet<>(duplicates);
        slots.addAll(duplicateSet);

        while (!needsQueueing()) {
            connectNextPlayer();
        }

        return duplicateSet;
    }

    public void connectNextPlayer() {
        if (Matsu.CONFIG.usePriorityWeighting) {
            IMatsuQueue queue = queuePool.getNextQueue();
            if (queue.getQueue().isEmpty()) {
                if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(this.getSlotName()+ "'s pool has no players waiting in queue!");}
                return;
            }
            if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(this.getSlotName()+ "'s pool selected queue: " + queue.getName() + " with weighting: " + queue.getPriority() + "/" + this.queuePool.getCombinedWeight());}
            queue.connectFirstPlayerToDestinationServer();
            return;
        }

        // Original logic: will only send players in the highest priority queue.
        List<IMatsuQueue> sorted = associatedQueues.values().stream().sorted(Comparator.comparingInt(IMatsuQueue::getPriority)).collect(Collectors.toList());
        for (IMatsuQueue iMatsuQueue : sorted) {
            if (iMatsuQueue.getQueue().isEmpty()) continue;
            iMatsuQueue.connectFirstPlayerToDestinationServer();
            break;
        }
    }

    @Override
    public void associateQueue(IMatsuQueue queue) {
        if (queue == null) throw new IllegalStateException("null queue! this shouldn't happen.");
        associatedQueues.put(queue.getName(), queue);
    }

    @Override
    public ConcurrentHashMap<String, IMatsuQueue> getAssociatedQueues() {
        return associatedQueues;
    }

    @Override
    public String getPermission() {
        return permission;
    }

    @Override
    public void broadcast(String str) {
        AtomicInteger pos = new AtomicInteger(0);
        associatedQueues.values().stream().sorted(Comparator.comparingInt(IMatsuQueue::getPriority)).forEach(queue -> {
            for (UUID uuid : queue.getQueue()) {
                Player player = Matsu.INSTANCE.getProxy().getPlayer(uuid).get();
                if (player != null) {
                    player.sendMessage(Component.text(str.replace("{pos}", (pos.get() + 1) + "")));

                    // Only update the player's tab header if they are still in the queue server, otherwise, only send the chat message
                    if (player.getCurrentServer().get().getServer().equals(Matsu.queueServerInfo)) {
                        player.getTabList().setHeaderAndFooter(Component.text(queue.getTabHeader().replace("{pos}", (pos.get() + 1) + "")),
                                Component.text(queue.getTabFooter().replace("{pos}", (pos.get() + 1) + "")));
                    }

                }
                pos.getAndIncrement();
            }
            if (Matsu.CONFIG.perQueuePos) {
                pos.set(0);
            }
        });
    }

    @Override
    public void broadcast(String str, Player targetPlayer) {
        AtomicInteger pos = new AtomicInteger(0);
        associatedQueues.values().stream().sorted(Comparator.comparingInt(IMatsuQueue::getPriority)).forEach(queue -> {
            for (UUID uuid : queue.getQueue()) {
                Player player = Matsu.INSTANCE.getProxy().getPlayer(uuid).get();
                if (player.equals(targetPlayer)) {
                    player.sendMessage(Component.text(str.replace("{pos}", (pos.get() + 1) + "")));
                    player.getTabList().setHeaderAndFooter(Component.text(queue.getTabHeader().replace("{pos}", (pos.get() + 1) + "")),
                            Component.text(queue.getTabFooter().replace("{pos}", (pos.get() + 1) + "")));
                }
                pos.getAndIncrement();
            }
            if (Matsu.CONFIG.perQueuePos) {
                pos.set(0);
            }
        });
    }

    @Override
    public List<UUID> getSlots() {
        return slots;
    }

    @Override
    public void initQueuePool() {
        this.queuePool = new QueuePool(associatedQueues);
    }

    public static IMatsuSlotCluster getSlotFromPlayer(Player player) {
        IMatsuSlotCluster slot = null;
        User lplayer = LuckPermsProvider.get().getUserManager().getUser(player.getUniqueId());
        Collection<Node> permissionNodes = lplayer.resolveInheritedNodes(QueryOptions.nonContextual());
        Set<String> perms = permissionNodes.stream().filter(NodeType.PERMISSION::matches).map(NodeType.PERMISSION::cast).map(PermissionNode::getKey).collect(Collectors.toSet());
        Set<String> negatedPerms = permissionNodes.stream().filter(Node::isNegated).filter(NodeType.PERMISSION::matches).map(NodeType.PERMISSION::cast).map(PermissionNode::getKey).collect(Collectors.toSet());
        perms.removeAll(negatedPerms);

        for (String permission : perms) {
            if (!permission.matches("matsuqueue\\..*\\..*")) continue;
            String[] broken = permission.split("\\.");
            if (broken.length != 3) continue;
            String cache = broken[0] + "." + broken[1] + ".";
            slot = Matsu.CONFIG.slotsMap.get(Matsu.slotPermissionCache.get(cache));
            if (slot == null) {
                System.err.println(permission + " returns a null slot tier");
            }
        }

        if (slot == null) {
            slot = Matsu.CONFIG.slotsMap.get(Matsu.slotPermissionCache.get("matsuqueue.default."));
        }

        return slot;
    }
}
