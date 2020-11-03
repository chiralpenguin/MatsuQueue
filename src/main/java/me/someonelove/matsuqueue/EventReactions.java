package me.someonelove.matsuqueue;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import me.someonelove.matsuqueue.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.queue.impl.MatsuSlotCluster;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class EventReactions {

    private static List<UUID> toDo = Collections.synchronizedList(new ArrayList<>());

    @Subscribe
    public void onLeave(DisconnectEvent e) {
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
            slot.onPlayerLeave(e.getPlayer());
        });
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
        }

        if (slot.needsQueueing() && !e.getPlayer().hasPermission(Matsu.CONFIG.bypassPermission)) {
            e.setInitialServer(Matsu.queueServerInfo);
        } else {
            e.setInitialServer(Matsu.destinationServerInfo);
        }

    }

    @Subscribe
    public void postLogin(PostLoginEvent e) {
        toDo.add(e.getPlayer().getUniqueId());
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
}
