package me.someonelove.matsuqueue.queue;

public interface IQueuePool {

    IMatsuQueue getNextQueue();

    int getCombinedWeight();
}
