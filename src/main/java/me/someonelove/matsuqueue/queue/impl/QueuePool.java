package me.someonelove.matsuqueue.queue.impl;

import me.someonelove.matsuqueue.Matsu;
import me.someonelove.matsuqueue.queue.IMatsuQueue;
import me.someonelove.matsuqueue.queue.IQueuePool;

import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class QueuePool implements IQueuePool {
    /*
    Full credit for this class goes to https://stackoverflow.com/users/1945631/andy-brown
    It has only been adapted slightly for this usecase.
     */
    private Random generator = new Random();
    private TreeMap<Integer, IMatsuQueue> pool;
    private int combinedWeight;

    public QueuePool(ConcurrentHashMap<String, IMatsuQueue> queues) {
        this.pool = new TreeMap<>();
        combinedWeight = 0;

        queues.forEach((name, queue) -> {
            combinedWeight += queue.getPriority();
            this.pool.put(combinedWeight, queue);
        });
    }

    public int getCombinedWeight() {
        return this.combinedWeight;
    }

    public IMatsuQueue getNextQueue() {
        int randInt = generator.nextInt(this.combinedWeight);
        int key = pool.ceilingKey(randInt);
        IMatsuQueue selectedQueue = pool.get(key);

        while (pool.lowerEntry(key) != null && selectedQueue.getQueue().isEmpty()) {
            Map.Entry<Integer, IMatsuQueue> entry = pool.lowerEntry(key);
            selectedQueue = entry.getValue();
            key = entry.getKey();
        }
        return selectedQueue;
    }

}
