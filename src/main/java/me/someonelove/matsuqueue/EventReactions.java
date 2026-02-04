package me.someonelove.matsuqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.Player;
import me.someonelove.matsuqueue.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.queue.impl.MatsuSlotCluster;
import net.kyori.adventure.text.Component;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventReactions {

    private static List<UUID> toDo = Collections.synchronizedList(new ArrayList<>());

    @Subscribe
    public void onLeave(DisconnectEvent e) {
        Player player = e.getPlayer();
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
            slot.onPlayerLeave(player);
        });

        if (Matsu.connectionManager.isExcess(player.getUsername())) {
            IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromUsername(player.getUsername());
            Matsu.connectionManager.removeExcessAccount(slot, player.getUsername());
            return;
        }

        Matsu.connectionManager.removeConnection(player.getUsername());
    }

    @Subscribe
    public void onPreJoin(PlayerChooseInitialServerEvent e) {
        Player player = e.getPlayer();
        IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromPlayer(player);

        if (slot == null) {
            player.disconnect(Component.text("\2476No valid queue server to connect to ;-;"));
            return;
        }

        if (!Matsu.destinationServerOk || !Matsu.queueServerOk) {
            player.disconnect(Component.text("\2474The server is unreachable."));
            return;
        }

        if (!Matsu.joinTracker.playerJoinedBefore(player)) {
            Matsu.joinTracker.playerJoin(player);
            handleFirstJoinBypass(player);
        }

        if (!slot.needsQueueing() || e.getPlayer().hasPermission(Matsu.CONFIG.bypassPermission)) {
            e.setInitialServer(Matsu.destinationServerInfo);
            if (Matsu.connectionManager.isExcess(player.getUsername())) {
                player.sendMessage(Component.text(Matsu.CONFIG.excessAccountWarningMessage.replace("&", "\247")));
            }
            return;
        }

        if (Matsu.connectionManager.hasExcess(slot)) {
            Player lastExcessPlayer = Matsu.INSTANCE.getProxy().getPlayer(Matsu.connectionManager.getLastExcessAccount(slot)).get();
            lastExcessPlayer.disconnect(Component.text(Matsu.CONFIG.excessAccountKickMessage.replace("&", "\247")));
            e.setInitialServer(Matsu.destinationServerInfo);
            return;
        }

        e.setInitialServer(Matsu.queueServerInfo);
    }

    public void handleFirstJoinBypass(Player player) {
        if (Matsu.CONFIG.firstJoinBypasshours < 1) return;

        LuckPerms luckPerms = LuckPermsProvider.get();
        luckPerms.getUserManager().loadUser(player.getUniqueId()).thenAccept(user -> {
            user.data().add(Node.builder(Matsu.CONFIG.bypassPermission)
                    .expiry(Matsu.CONFIG.firstJoinBypasshours, TimeUnit.HOURS)
                    .build());
            luckPerms.getUserManager().saveUser(user);
        });

        Matsu.INSTANCE.getLogger().info(String.format("Player %s received %d hours of queue bypass",
                player.getUsername(),
                Matsu.CONFIG.firstJoinBypasshours
        ));
    }

    @Subscribe
    public void preLogin(PreLoginEvent e) {
        String hostname = e.getConnection().getRemoteAddress().getHostName();
        InetAddress remoteAddress = e.getConnection().getRemoteAddress().getAddress();
        if (Matsu.connectionManager.getNumberConnected(remoteAddress) < Matsu.CONFIG.maxConcurrentAccounts) {
            Matsu.connectionManager.addConnection(e.getUsername(), remoteAddress);
            return;
        }

        IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromlPlayer(LuckPermsProvider.get().getUserManager().getUser(e.getUsername()));
        if (!slot.needsQueueing()) {
            Matsu.connectionManager.addExcessAccount(slot, e.getUsername(), remoteAddress);
            return;
        }

        e.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.text(Matsu.CONFIG.excessAccountKickMessage.replace("&", "\247"))));
    }

    @Subscribe
    public void postLogin(PostLoginEvent e) {
        Player player = e.getPlayer();
        toDo.add(player.getUniqueId());
    }

    @Subscribe
    public void onProxyJoin(ServerConnectedEvent e) {
        if (!toDo.contains(e.getPlayer().getUniqueId())) return;
        toDo.remove(e.getPlayer().getUniqueId());
        IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromPlayer(e.getPlayer());
        slot.queuePlayer(e.getPlayer());
    }


    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent e) {
        if (!e.getPlayer().getCurrentServer().isPresent() || !e.getOriginalServer().equals(Matsu.destinationServerInfo) || e.getPlayer().hasPermission(Matsu.CONFIG.bypassPermission)) {
            return;
        }

        AtomicBoolean playerHasSlot = new AtomicBoolean(false);
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
            if (slot.getSlots().contains(e.getPlayer().getUniqueId())) {
                playerHasSlot.set(true);
            }
        });

        if (!playerHasSlot.get()) {
            e.getPlayer().sendMessage(Component.text(Matsu.CONFIG.destinationJoinErrorMessage.replace("&", "\247")));
            e.setResult(ServerPreConnectEvent.ServerResult.denied());
        }
    }

    @Subscribe
    public void onServerChange(ServerConnectedEvent e) {
        if (e.getPreviousServer().isPresent() && e.getPreviousServer().get().equals(Matsu.destinationServerInfo)) {
            Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
                slot.onPlayerLeave(e.getPlayer());
                if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().info(String.format("Removed player: %s from slot: %s", e.getPlayer().getUsername(), slot.getSlotName()));}
            });
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        try  {
            Matsu.joinTracker.saveJoinedPlayers("plugins/MatsuQueue/joined_players.txt");
        } catch (IOException e) {
            Matsu.INSTANCE.getLogger().error("Failed to save joined_players.txt!");
        }
    }
}
