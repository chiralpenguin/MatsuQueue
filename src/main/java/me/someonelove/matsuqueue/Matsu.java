package me.someonelove.matsuqueue;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.someonelove.matsuqueue.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.queue.impl.MatsuSlotCluster;
import net.kyori.adventure.text.TextComponent;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(id = "matsuqueue", name = "MatsuQueue", version = "1.0",
        description = "Fork of MastuQueue adapted for Purity Vanilla",
        authors = "Sasha, updated by nitricspace",
        dependencies = {@Dependency(id = "luckperms", optional = false)})
public class Matsu {
    private final ProxyServer server;
    private final Logger logger;

    public static ConfigurationFile CONFIG;
    public static Matsu INSTANCE;
    public static LinkedHashMap<String, String> slotPermissionCache = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> queuePermissionCache = new LinkedHashMap<>();
    public static RegisteredServer destinationServerInfo;
    public static RegisteredServer queueServerInfo;

    public static boolean queueServerOk = true;
    public static boolean destinationServerOk = true;

    public ScheduledTask UpdateQueueTask = null;
    
    @Inject
    public Matsu(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        INSTANCE = this;
        logger.info("MatsuQueue is loading.");
    }

    @Subscribe
    public void onProxyInitialisation(ProxyInitializeEvent e) {
        slotPermissionCache.clear();

        CONFIG = new ConfigurationFile();

        try {
            if (CONFIG.verbose) {getLogger().info("Attempting to open API connection to LuckPerms");}
            LuckPerms api = LuckPermsProvider.get();
            getLogger().info("LuckPerms API connection successfully established!");
        } catch (Exception luckPermsException) {
            getLogger().info("Error during loading LuckPerms API - perhaps the plugin isn't installed? - " + e);
        }

        // TODO Need to move these to their own class and register with constructors
        CommandManager manager = getProxy().getCommandManager();
        manager.register(manager.metaBuilder("queueupdate").build(), new UpdateSlotsCommand());
        manager.register(manager.metaBuilder("queuedebug").build(), new DebugQueuesCommand());
        manager.register(manager.metaBuilder("queueleave").build(), new LeaveQueueCommand());
        manager.register(manager.metaBuilder("queuejoin").build(), new JoinQueueCommand());

        getProxy().getEventManager().register(this, new EventReactions());
        // Instantiate queue update task on startup with 30 second delay
        UpdateQueueTask = this.server.getScheduler().buildTask(INSTANCE, new UpdateQueues()).delay(30L, TimeUnit.SECONDS).repeat(10L, TimeUnit.SECONDS).schedule();
        if (CONFIG.verbose) {getLogger().info("Verbose logging ENABLED. Disable this in config.yml to reduce messages.");}

        getLogger().info("MatsuQueue has loaded.");

    }

    private void purgeSlots() {
        List<UUID> removalList = new ArrayList<>();
        CONFIG.slotsMap.forEach((str, cluster) -> {
            for (UUID slot : cluster.getSlots()) {
                // Need to handle NoSuchElementException instead of null check as is always false
                Player player = this.getProxy().getPlayer(slot).get();
                if (player == null || !player.isActive() || !player.getCurrentServer().get().getServer().equals(destinationServerInfo)) {
                    removalList.add(slot);
                    if (player != null) {
                    	if (CONFIG.verbose) {getLogger().info("Purging Player: " + player.getUsername() + player.getCurrentServer().get().getServer().getServerInfo().getName() + player.getCurrentServer().get().getServer().equals(destinationServerInfo));}
                    }
                }
            }
            removalList.forEach(cluster::onPlayerLeave);
        });
    }
    
    private void purgeQueues() {
    	List<UUID> removalList = new ArrayList<>();
    	CONFIG.slotsMap.forEach((str, cluster) -> {
    		cluster.getAssociatedQueues().forEach((name, queue) -> {
    			for (UUID id : queue.getQueue()) {
    				Player player = this.getProxy().getPlayer(id).get();
    				if (player == null || !player.isActive() || player.getCurrentServer().get().getServer().equals(destinationServerInfo)) {
    					removalList.add(id);
    					if (player != null) {
    						if (CONFIG.verbose) {getLogger().info("Purging Player: " + player.getUsername() + player.getCurrentServer().get().getServer().getServerInfo().getName() + player.getCurrentServer().get().getServer().equals(queueServerInfo));}
    					}
    				}
    			}
    			removalList.forEach(queue::removePlayerFromQueue);
    		});
    	});
    }

    public class UpdateQueues implements Runnable {
		@Override
		public void run() {
			purgeSlots();
            purgeQueues();

            queueServerOk = isServerUp(queueServerInfo);
            if (!queueServerOk) {
                for (Player player : getProxy().getAllPlayers()) {
                    player.disconnect(TextComponent.of("\2474The queue server is no longer reachable."));
                }
                return;
            }

            destinationServerOk = isServerUp(destinationServerInfo);
            if (!destinationServerOk) {
                for (Player player : getProxy().getAllPlayers()) {
                    player.disconnect(TextComponent.of("\2474The main server is no longer reachable."));
                }
                return;
            }

            /* TODO Test and implement as part of the destination server restart handling
            Matsu.CONFIG.slotsMap.forEach((str, cluster) -> {
                while (!cluster.needsQueueing()) {
                    cluster.connectHighestPriorityPlayer();
                }
            });
             */

            CONFIG.slotsMap.forEach((name, slot) -> slot.broadcast(CONFIG.positionMessage.replace("&", "\247")));
            if (CONFIG.verbose) {getLogger().info("Purged queues and updated position messages.");}
		}
    	
    }
    
    public class UpdateSlotsCommand implements SimpleCommand {

        @Override
    	public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();


    		if (sender instanceof Player) {
    			sender.sendMessage(TextComponent.of("You must run this command from console."));
    			return;
    		}

    		UpdateQueueTask.cancel();

            ConfigurationFile newConfig = new ConfigurationFile();
            newConfig.slotsMap.forEach((str, cluster)-> {
                int oldSlots = CONFIG.slotsMap.get(cluster.getSlotName()).getTotalSlots(true);
                int newSlots = cluster.getTotalSlots(true);
                int change = newSlots - oldSlots;
                if (CONFIG.verbose) {getLogger().info(String.format("Slot Type %s: Old Slots: %d New Slots: %d", cluster.getSlotName(), oldSlots, newSlots));}
                CONFIG.slotsMap.get(cluster.getSlotName()).setTotalSlots(true, newSlots);
                if (oldSlots != newSlots) {
                    if (change > 0) { // If slot capacity has increased
                        getLogger().info(String.format("Capacity of slot %s increased by %d", CONFIG.slotsMap.get(cluster.getSlotName()).getSlotName(), change));
                        for (int i=0; i < change && !CONFIG.slotsMap.get(cluster.getSlotName()).needsQueueing(); i++) {
                            CONFIG.slotsMap.get(cluster.getSlotName()).connectHighestPriorityPlayer();
                            }
                        }
                    else { // If slot capacity has decreased
                        getLogger().info(String.format("Capacity of slot %s decreased by %d", cluster.getSlotName(), Math.abs(change)));
                    }
                }
            });
            // Instantiate queue update task on startup with 10 second delay
            UpdateQueueTask = getProxy().getScheduler().buildTask(INSTANCE, new UpdateQueues()).delay(10L, TimeUnit.SECONDS).repeat(10L, TimeUnit.SECONDS).schedule();
            getLogger().info("Slots updated.");
    	}
    }

    public class DebugQueuesCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();

            if (sender instanceof Player) {
                sender.sendMessage(TextComponent.of("You must run this command from console."));
                return;
            }

            CONFIG.slotsMap.forEach((slotName, cluster) -> {
                INSTANCE.getLogger().info(String.format("Slot: %s (%s)", cluster.getSlotName(), cluster.getSlots().size()));
                for(UUID slot : cluster.getSlots()) {
                    INSTANCE.getLogger().info(String.format("- %s", INSTANCE.getProxy().getPlayer(slot).get().getUsername()));
                }

                cluster.getAssociatedQueues().forEach((queueName, queue) -> {
                    INSTANCE.getLogger().info(String.format("Queue: %s (%s)", queue.getName(), queue.getQueue().size()));
                    for (UUID player : queue.getQueue()) {
                        INSTANCE.getLogger().info(String.format("- %s", INSTANCE.getProxy().getPlayer(player).get().getUsername()));
                    }
                    INSTANCE.getLogger().info("\n");
                });
                INSTANCE.getLogger().info("\n");
            });
        }

    }

    public class LeaveQueueCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();

            if (!(sender instanceof Player)) {
                getLogger().info("Only a player may use this command");
                return;
            }

            Player player = (Player) sender;
            AtomicBoolean success = new AtomicBoolean(false);

            CONFIG.slotsMap.forEach((slotName, cluster) ->{
                cluster.getAssociatedQueues().forEach((queueName, queue) -> {
                    if (queue.getQueue().contains(player.getUniqueId())) {
                        queue.removePlayerFromQueue(player);
                        player.sendMessage(TextComponent.of(CONFIG.leaveMessage.replace("&", "\247")));
                        if (CONFIG.verbose) {getLogger().info(String.format("Player %s left queue %s", player.getUsername(), queueName));}
                        success.set(true);
                    }
                });
            });

            if (!success.get()) {
                player.sendMessage(TextComponent.of(CONFIG.leaveErrorMessage.replace("&", "\247")));
            }
        }
    }

    public class JoinQueueCommand implements SimpleCommand {

        @Override
        public void execute(Invocation invocation) {
            CommandSource sender = invocation.source();

            if (!(sender instanceof Player)) {
                getLogger().info("Only a player may use this command");
                return;
            }

            Player player = (Player) sender;
            AtomicBoolean error = new AtomicBoolean(false);

            CONFIG.slotsMap.forEach((slotName, cluster) ->{
                if (cluster.getSlots().contains(player.getUniqueId())) {
                    error.set(true);
                    if (CONFIG.verbose) {getLogger().info(String.format("Player %s is already in slot %s!", player.getUsername(), slotName));}
                    return;
                }

                cluster.getAssociatedQueues().forEach((queueName, queue) -> {
                    if (queue.getQueue().contains(player.getUniqueId())) {
                        if (CONFIG.verbose) {getLogger().info(String.format("Player %s is already in queue %s!", player.getUsername(), queueName));}
                        error.set(true);
                    }
                });
            });

            if (error.get()) {
                player.sendMessage(TextComponent.of(CONFIG.joinErrorMessage.replace("&", "\247")));
                return;
            }

            player.sendMessage(TextComponent.of(CONFIG.joinMessage.replace("&", "\247")));
            IMatsuSlotCluster slot = MatsuSlotCluster.getSlotFromPlayer(player);

            if (!slot.needsQueueing()) {
                player.createConnectionRequest(destinationServerInfo).connect();
            }
            else if (!player.getCurrentServer().get().getServer().equals(queueServerInfo)) {
                player.createConnectionRequest(queueServerInfo).connect();
            }

            slot.queuePlayer(player);
        }
    }

    public Logger getLogger() {
        return this.logger;
    }

    public ProxyServer getProxy() {
        return this.server;
    }

    public static boolean isServerUp(RegisteredServer server) {
        AtomicBoolean serverOnline = new AtomicBoolean(false);
        try {
            server.ping().get();
            serverOnline.set(true);
        } catch (InterruptedException | ExecutionException e) {
            if (CONFIG.verbose) {INSTANCE.logger.info("Connection refused by " + server.getServerInfo().getName() + ". Is it offline?");}
        }

        return serverOnline.get();
    }
}
