package me.someonelove.matsuqueue.bungee;

import com.velocitypowered.api.event.EventHandler;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import me.someonelove.matsuqueue.bungee.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.bungee.queue.impl.MatsuSlotCluster

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class EventReactions {

    private static List<UUID> toDo = Collections.synchronizedList(new ArrayList<>());

    @Subscribe
    public void onLeave(DisconnectEvent e) {
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
            slot.onPlayerLeave(e.getPlayer());
        });
    }

    @Subscribe
    public void onPreJoin(ServerPreConnectEvent e) {
        Player player = e.getPlayer();


        IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromPlayer(player);
        if (slot == null) {
            player.disconnect(new TextComponent("\2476No valid queue server to connect to ;-;"));
            return null;
        }
        if (slot.needsQueueing()) {
            return Matsu.queueServerInfo;
        } else {
            return Matsu.destinationServerInfo;
        }

    }

    @EventHandler
    public void preLogin(PreLoginEvent e) {
        if (!Matsu.destinationServerOk) {
            e.setCancelReason(new TextComponent("\2474The main server is unreachable."));
            e.setCancelled(true);
        }
        if (!Matsu.queueServerOk) {
            e.setCancelReason(new TextComponent("\2474The queue server is unreachable."));
            e.setCancelled(true);
        }
    }


    @EventHandler
    public void postLogin(PostLoginEvent e) {
        toDo.add(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onProxyJoin(ServerConnectedEvent e) {
        if (!toDo.contains(e.getPlayer().getUniqueId())) return;
        toDo.remove(e.getPlayer().getUniqueId());
        IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromPlayer(e.getPlayer());
        slot.queuePlayer(e.getPlayer());
    }


    @EventHandler
    public void onServerChange(ServerSwitchEvent e) {
    	if (e.getFrom() != null) {
    		if (e.getFrom().equals(Matsu.destinationServerInfo)) {
        		Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
                    slot.onPlayerLeave(e.getPlayer());
                    if (Matsu.CONFIG.verbose) {Matsu.INSTANCE.getLogger().log(Level.INFO,String.format("Removed player: %s from slot: %s", e.getPlayer().getName(), slot.getSlotName()));}
                    return;
                });
        	}
    	}
    }
}
