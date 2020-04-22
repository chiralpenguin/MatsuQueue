package me.someonelove.matsuqueue.bungee;

import me.someonelove.matsuqueue.bungee.queue.impl.MatsuSlotCluster;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import me.someonelove.matsuqueue.bungee.queue.IMatsuQueue;


public final class Matsu extends Plugin {

    public static ConfigurationFile CONFIG;
    public ConfigurationFile NEWCONFIG;
    public static Matsu INSTANCE;
    public static ScheduledExecutorService executorService = Executors.newScheduledThreadPool(4);

    /**
     * Used to (hopefully) make the process of choosing a server on-join faster.
     */
    public static LinkedHashMap<String, String> slotPermissionCache = new LinkedHashMap<>();
    public static LinkedHashMap<String, String> queuePermissionCache = new LinkedHashMap<>();
    public static ServerInfo destinationServerInfo;
    public static ServerInfo queueServerInfo;

    public static boolean queueServerOk = true;
    public static boolean destinationServerOk = true;
    @SuppressWarnings("unused")
	private static boolean isLuckPermsOk = false;

    public ScheduledTask UpdateQueueTask = null;
    
    @Override
    public void onEnable() {
        slotPermissionCache.clear();
        INSTANCE = this;
        getLogger().log(Level.INFO, "MatsuQueue is loading.");
        CONFIG = new ConfigurationFile();
        if (CONFIG.useLuckPerms) {
            try {
            	if (CONFIG.verbose) {getLogger().log(Level.INFO, "Detected value TRUE for LuckPerms, trying to open API connection...");}
                @SuppressWarnings("unused")
				LuckPerms api = LuckPermsProvider.get();
                getLogger().log(Level.INFO, "LuckPerms API connection successfully established!");
            } catch (Exception e) {
                    getLogger().log(Level.SEVERE, "Error during loading LuckPerms API - perhaps the plugin isn't installed? - " + e);
            }
        } else {
            getLogger().log(Level.INFO, "Currently using BungeeCord permissions system - switch to LuckPerms in config.");
        }
        
        this.getProxy().getPluginManager().registerCommand(INSTANCE, new UpdateSlotsCommand());
        this.getProxy().getPluginManager().registerCommand(INSTANCE, new DebugQueuesCommand());
        
        this.getProxy().getPluginManager().registerListener(this, new EventReactions());
        
        // Instantiate queue update task on startup with 30 second delay
        UpdateQueueTask = this.getProxy().getScheduler().schedule(INSTANCE, new UpdateQueues(), 30, 10, TimeUnit.SECONDS);
        if (CONFIG.verbose) {getLogger().log(Level.INFO, "Verbose logging ENABLED. Disable this in config.yml to reduce messages.");}
        
        getLogger().log(Level.INFO, "MatsuQueue has loaded.");
    }

    private void purgeSlots() {
        List<UUID> removalList = new ArrayList<>();
        CONFIG.slotsMap.forEach((str, cluster) -> {
            for (UUID slot : cluster.getSlots()) {
                ProxiedPlayer player = this.getProxy().getPlayer(slot);
                if (player == null || !player.isConnected() || !player.getServer().getInfo().getName().equals(destinationServerInfo.getName())) {
                    removalList.add(slot);
                    if (player != null) {
                    	if (CONFIG.verbose) {getLogger().log(Level.INFO, "Purging Player: " + player.getName() + player.getServer().getInfo().getName() + player.getServer().getInfo().getName().equals(destinationServerInfo.getName()));}
                    }
                }
            }
            removalList.forEach(cluster::onPlayerLeave);
            HashSet<UUID> duplicates = cluster.removeDuplicateSlots();
            if (duplicates.size() != 0) {
                for (UUID duplicate : duplicates) {
                    if (CONFIG.verbose) {getLogger().log(Level.INFO, String.format("Player %s was using multiple slots!", this.getProxy().getPlayer(duplicate).getName()));}
                }
            }
        });
    }
    
    private void purgeQueues() {
    	List<UUID> removalList = new ArrayList<>();
    	CONFIG.slotsMap.forEach((str, cluster) -> {
    		cluster.getAssociatedQueues().forEach((name, queue) -> {
    			for (UUID id : queue.getQueue()) {
    				ProxiedPlayer player = this.getProxy().getPlayer(id);
    				if (player == null || !player.isConnected() || !player.getServer().getInfo().getName().equals(queueServerInfo.getName())) {
    					removalList.add(id);
    					if (player != null) {
    						if (CONFIG.verbose) {getLogger().log(Level.INFO, "Purging Player: " + player.getName() + player.getServer().getInfo().getName() + player.getServer().getInfo().getName().equals(queueServerInfo.getName()));}
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
                for (ProxiedPlayer player : getProxy().getPlayers()) {
                    player.disconnect(new TextComponent("\2474The queue server is no longer reachable."));
                }
                return;
            }
            destinationServerOk = isServerUp(destinationServerInfo);
            if (!destinationServerOk) {
                for (ProxiedPlayer player : getProxy().getPlayers()) {
                    player.disconnect(new TextComponent("\2474The main server is no longer reachable."));
                }
                return;
            }
            CONFIG.slotsMap.forEach((name, slot) -> slot.broadcast(CONFIG.positionMessage.replace("&", "\247")));
            if (CONFIG.verbose) {getLogger().log(Level.INFO,"Purged queues and updated position messages.");}
		}
    	
    }
    
    public class UpdateSlotsCommand extends Command {
    	public UpdateSlotsCommand() {
    		super("updateslots", "matsuqueue.updateslots");
    	}

    	@SuppressWarnings("static-access")
		@Override
    	public void execute(CommandSender sender, String[] args) {
    		if (sender instanceof ProxiedPlayer) {
    			sender.sendMessage(new TextComponent("You must run this command from console."));
    			return;
    		}
            INSTANCE.getProxy().getScheduler().cancel(UpdateQueueTask);

            INSTANCE.NEWCONFIG = new ConfigurationFile();
            INSTANCE.NEWCONFIG.slotsMap.forEach((str, cluster)-> {
                int oldSlots = CONFIG.slotsMap.get(cluster.getSlotName()).getTotalSlots(true);
                int newSlots = cluster.getTotalSlots(true);
                int change = newSlots - oldSlots;
                if (CONFIG.verbose) {getLogger().log(Level.INFO, String.format("Slot Type %s: Old Slots: %d New Slots: %d", cluster.getSlotName(), oldSlots, newSlots));}
                INSTANCE.CONFIG.slotsMap.get(cluster.getSlotName()).setTotalSlots(true, newSlots);
                if (oldSlots != newSlots) {
                    if (change > 0) { // If slot capacity has increased
                        getLogger().log(Level.INFO, String.format("Capacity of slot %s increased by %d", CONFIG.slotsMap.get(cluster.getSlotName()).getSlotName(), change));
                        for (int i=0; i < change && !CONFIG.slotsMap.get(cluster.getSlotName()).needsQueueing(); i++) {
                            CONFIG.slotsMap.get(cluster.getSlotName()).connectHighestPriorityPlayer();
                            }
                        }
                    else { // If slot capacity has decreased
                        getLogger().log(Level.INFO, String.format("Capacity of slot %s decreased by %d", cluster.getSlotName(), Math.abs(change)));
                    }
                }
            });
            // Instantiate queue update task on startup with 10 second delay
            UpdateQueueTask = INSTANCE.getProxy().getScheduler().schedule(INSTANCE, new UpdateQueues(), 10, 10, TimeUnit.SECONDS);
            getLogger().log(Level.INFO, "Slots updated.");
    	}
    }

    public class DebugQueuesCommand extends Command {
        public DebugQueuesCommand() {
            super("queuedebug", "matsuqueue.debug");
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            if (sender instanceof ProxiedPlayer) {
                sender.sendMessage(new TextComponent("You must run this command from console."));
                return;
            }

            CONFIG.slotsMap.forEach((slotname, cluster) -> {
                INSTANCE.getLogger().log(Level.INFO, String.format("Slot: %s (%s)", cluster.getSlotName(), cluster.getSlots().size()));
                for(UUID slot : cluster.getSlots()) {
                    INSTANCE.getLogger().log(Level.INFO,String.format("- %s", INSTANCE.getProxy().getPlayer(slot)));
                }

                cluster.getAssociatedQueues().forEach((queuename, queue) -> {
                    INSTANCE.getLogger().log(Level.INFO, String.format("Queue: %s (%s)", queue.getName(), queue.getQueue().size()));
                    for (UUID player : queue.getQueue()) {
                        INSTANCE.getLogger().log(Level.INFO, String.format("- %s", INSTANCE.getProxy().getPlayer(player)));
                    }
                    INSTANCE.getLogger().log(Level.INFO, "\n");
                });
                INSTANCE.getLogger().log(Level.INFO, "\n");
            });
        }

    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    public static boolean isServerUp(ServerInfo info) {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] up = {true};
        info.ping((result, error) -> {
            if (error != null) {
                up[0] = false;
            }
            latch.countDown();
        });
        try {
            latch.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException exc) {
            exc.printStackTrace();
        }
        return up[0];
    }
}
