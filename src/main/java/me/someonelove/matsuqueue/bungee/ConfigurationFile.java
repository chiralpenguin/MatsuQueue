package me.someonelove.matsuqueue.bungee;

import me.someonelove.matsuqueue.bungee.queue.IMatsuQueue;
import me.someonelove.matsuqueue.bungee.queue.IMatsuSlotCluster;
import me.someonelove.matsuqueue.bungee.queue.impl.MatsuQueue;
import me.someonelove.matsuqueue.bungee.queue.impl.MatsuSlotCluster;
import me.someonelove.quickyml.YMLParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * I'm really sorry but i strongly dislike BungeeCord's YML stuff.
 */
public class ConfigurationFile {

    public static final String FILE_NAME = "plugins/MatsuQueue/config.yml";

    public String queueServerKey;
    public String destinationServerKey;
    public String serverFullMessage;
    public String positionMessage;
    public String connectingMessage;
    public boolean useLuckPerms;
    public String bypassPermission;
    public boolean verbose;
    public ConcurrentHashMap<String, IMatsuSlotCluster> slotsMap = new ConcurrentHashMap<>();

    /**
     * this is a mess please forgive me as i have sinned.
     */
    protected ConfigurationFile() {
        File file = new File(FILE_NAME);
        file.getParentFile().mkdirs();
        if (!file.exists()) {
            try {
                file.createNewFile();
                YMLParser parser = new YMLParser(file);
                parser.set("serverFullMessage", "&6Server is full");
                parser.set("connectingMessage", "&6Connecting to the server...");
                parser.set("positionMessage", "&6Your position in queue is &l{pos}");
                parser.set("queueServerKey", "queue");
                parser.set("destinationServerKey", "main");
                parser.set("useLuckPerms", "false");
                Matsu.INSTANCE.getLogger().log(Level.INFO, "Currently not using LuckPerms as permission engine - change in config!");
                parser.set("bypassPermission", "matsuqueue.skip");
                parser.set("verbose", "false");
                // slots
                parser.set("slots.standard.capacity", 100);
                parser.set("slots.standard.permission", "default");
                parser.set("slots.reserved.capacity", 20);
                parser.set("slots.reserved.permission", "reserved");
                // standard queue
                parser.set("queues.standard.priority", 2);
                parser.set("queues.standard.slots", "standard");
                parser.set("queues.standard.permission", "default");
                parser.set("queues.standard.tabHeader", "\\n&dMatsuQueue\\n\\n&6Server is full\\n&6Position in queue: &l{pos}\\n");
                parser.set("queues.standard.tabFooter", "\\n&6You can donate at (Donation Link) for priority queue, or for a reserved slot.\\n");
                // priority queue (standard slots)
                parser.set("queues.priority.priority", 1);
                parser.set("queues.priority.slots", "standard");
                parser.set("queues.priority.permission", "priority");
                parser.set("queues.priority.tabHeader", "\\n&dMatsuQueue\\n\\n&6Server is full\\n&6Position in queue: &l{pos}\\n");
                parser.set("queues.priority.tabFooter", "\\n&6Thank you for donating for priority queue!\\n");
                // reserved queue (reserved slots)
                parser.set("queues.reserved.priority", 1);
                parser.set("queues.reserved.slots", "reserved");
                parser.set("queues.reserved.permission", "reserved");
                parser.set("queues.reserved.tabHeader", "\\n&dMatsuQueue\\n\\n&6Server is full\\n&6Position in queue: &l{pos}\\n");
                parser.set("queues.reserved.tabFooter", "\\n&6Thank yo ufor donating for a reserved slot!\\n");

                ArrayList<String> queues = new ArrayList<>();
                queues.add("standard");
                queues.add("priority");
                queues.add("reserved");
                parser.set("queuenames", queues);
                ArrayList<String> slots = new ArrayList<>();
                slots.add("standard");
                slots.add("reserved");
                parser.set("slotnames", slots);
                parser.save();
            } catch (IOException e) {
                e.printStackTrace();
                Matsu.INSTANCE.getLogger().log(Level.SEVERE, "Couldn't initialise default configuration file! Cannot continue!");
                Matsu.INSTANCE.getProxy().stop();
                return;
            }
        }
        YMLParser parser = new YMLParser(file);
        serverFullMessage = parser.getString("serverFullMessage", "&6Server is full");
        queueServerKey = parser.getString("queueServerKey", "queue");
        positionMessage = parser.getString("positionMessage", "&6Your position in queue is &l{pos}");
        connectingMessage = parser.getString("connectingMessage", "&6Connecting to the server...");
        destinationServerKey = parser.getString("destinationServerKey", "main");
        useLuckPerms = Boolean.parseBoolean(parser.getString("useLuckPerms", "false"));
        bypassPermission = parser.getString("bypassPermission");
        verbose = Boolean.parseBoolean(parser.getString("verbose", "false"));
        final List<String> slots = parser.getStringList("slotnames");
        for (final String slot : slots) {
            if (!parser.exists("slots." + slot)) continue;
            final int capacity = parser.getInt("slots." + slot + ".capacity");
            final String permission = parser.getString("slots." + slot + ".permission");
            slotsMap.put(slot, new MatsuSlotCluster(slot, capacity, permission));
            // "matsuqueue.<slot_tier>."
            Matsu.slotPermissionCache.put("matsuqueue." + permission + ".", slot);
            if (this.verbose) {Matsu.INSTANCE.getLogger().log(Level.INFO, "Discovered valid slot type " + slot);}
        }
        final List<String> queues = parser.getStringList("queuenames");
        for (final String queue : queues) {
            if (!parser.exists("queues." + queue)) continue;
            final int priority = parser.getInt("queues." + queue + ".priority");
            final String slot = parser.getString("queues." + queue + ".slots");
            if (!slots.contains(slot)) continue;
            final String permission = parser.getString("queues." + queue + ".permission");
            IMatsuQueue q = new MatsuQueue(queue, priority, slot, permission);
            Matsu.queuePermissionCache.put("." + permission, queue);
            q.setTabText(parser.getString("queues." + queue + ".tabHeader", "\\n&dMatsuQueue\\n\\n&6Server is full\\n&6Position in queue: &l{pos}\\n"),
                    parser.getString("queues." + queue + ".tabFooter", "\\n&6You can donate at https://paypal.me/eatsasha for priority access to the server.\\n"));
            slotsMap.get(slot).associateQueue(q);
            if (this.verbose) {Matsu.INSTANCE.getLogger().log(Level.INFO, "Discovered valid queue " + queue + " associated to slot type " + slot);}
        }
        Matsu.destinationServerInfo = Matsu.INSTANCE.getProxy().getServerInfo(this.destinationServerKey);
        Matsu.queueServerInfo = Matsu.INSTANCE.getProxy().getServerInfo(this.queueServerKey);
    }


}
