package me.someonelove.matsuqueue.bungee;

import me.someonelove.matsuqueue.bungee.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.bungee.queue.impl.MatsuSlotCluster;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ReconnectHandler;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class EventReactions implements Listener {

    private static List<UUID> toDo = Collections.synchronizedList(new ArrayList<>());

    @EventHandler
    public void onLeave(PlayerDisconnectEvent e) {
        Matsu.CONFIG.slotsMap.forEach((name, slot) -> {
            slot.onPlayerLeave(e.getPlayer());
        });
    }

    @EventHandler
    public void onPreJoin(PreLoginEvent e) {
        ProxyServer.getInstance().setReconnectHandler(new ReconnectHandler() {
            @Override
            public ServerInfo getServer(ProxiedPlayer player) {
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

            @Override
            public void setServer(ProxiedPlayer player) {

            }

            @Override
            public void save() {

            }

            @Override
            public void close() {

            }
        });
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
