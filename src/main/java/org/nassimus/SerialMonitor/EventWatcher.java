package org.nassimus.SerialMonitor;

import java.util.function.BooleanSupplier;

/**
 * Created by Optimus on 25/05/2017.
 */
public class EventWatcher implements Runnable {
    private Runnable task;
    private BooleanSupplier taskPredicate;
    private int delay;
    private boolean terminate = false;
    private boolean suspend = false;

    public static EventWatcher startEventWatcher(BooleanSupplier taskPredicate, int dealy, Runnable task){
        EventWatcher eventWatcher = new EventWatcher(taskPredicate, dealy, task);
        new Thread(eventWatcher).start();
        return eventWatcher;
    }

    public EventWatcher(BooleanSupplier taskPredicate, int dealy, Runnable task) {
        this.task = task;
        this.taskPredicate = taskPredicate;
        this.delay = dealy;

    }
    public void terminate(){
        this.terminate = true;
    }
    @Override
    public void run() {
        while (!terminate && !taskPredicate.getAsBoolean()){
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (!terminate)
            task.run();
    }
}
