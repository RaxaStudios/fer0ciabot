package com.twitchbotx.bot;

import com.twitchbotx.bot.client.TwitchMessenger;
import com.twitchbotx.bot.handlers.TwitchStatusHandler;
import com.twitchbotx.gui.DashboardController;
import com.twitchbotx.gui.guiHandler;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javafx.application.Platform;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;

/*
** Roughly 15 minute intervals between command sendMessage, 
** OR based on number of messages between sendMessage
**
** RE-ADD online check timer foro online-only timer functionality 
** as well as for future discord stream is live function
 */
/**
 * This class is responsible for timer management.
 */
public final class TimerManagement {

    static final LinkedHashMap<String, Task> LHM = new LinkedHashMap<>();
    private final Datastore store;
    private static final Logger LOGGER = Logger.getLogger(TwitchBotX.class.getSimpleName());

    /*
** This takes the information parsed in the start of the program under 
** elements "repeating" and "interval"
** "repeating" = True commands need to start when the bot starts
** all commands set to repeat need to start (including created/edited commands)
     */
    public TimerManagement() {
        this.store = guiHandler.bot.getStore();
        //this.outstream = stream;
    }

    public synchronized LinkedHashMap<String, Task> getLHM() {
        return LHM;
    }

    
    public volatile boolean repeat;

    public void setupPeriodicBroadcast() {
        for (int i = 0; i < store.getCommands().size(); i++) {
            final ConfigParameters.Command command = guiHandler.bot.getStore().getCommands().get(i);
            if (Boolean.parseBoolean(command.repeating)) {
                int interval = Integer.parseInt(command.interval);
                if (interval < 600) {
                    String event = "Repeating interval too short for command " + command.name;
                    Platform.runLater(() -> {
                        store.getEventList().addList(event);
                    });
                } else {
                    if (schedule(command.name)) {
                        String event = "Started repeating command: " + command.name;
                        Platform.runLater(() -> {
                            store.getEventList().addList(event);
                        });
                    } else {
                        String event = "Failed to start repeating command: " + command.name;
                        Platform.runLater(() -> {
                            store.getEventList().addList(event);
                        });
                    };
                }
            }
        }
    }

    public boolean schedule(String commandToSchedule) {
        for (int i = 0; i < store.getCommands().size(); i++) {
            final ConfigParameters.Command command = guiHandler.bot.getStore().getCommands().get(i);
            if (command.name.equals(commandToSchedule)) {
                int iDelay = Integer.parseInt(command.initialDelay);
                int interval = Integer.parseInt(command.interval);
                try {
                    if (getLHM().containsKey(command.name)) {
                        System.out.println("Duplicate found");
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                guiHandler.bot.getStore().getEventList().addList("Repeating command " + command.name + " already in progress");
                            }
                        });
                        return false;
                    } else {
                        Task task = new Task(command.name, command.text, iDelay, interval);
                        getLHM().put(command.name, task);
                        task.start();
                        return true;
                    }
                } catch (NullPointerException ne) {
                    ne.printStackTrace();
                }
                return false;
            }
        }
        return false;
    }

    public boolean stop(String commandToStop) {
        try {
            getLHM().get(commandToStop).shutdown();
            getLHM().remove(commandToStop);
            return true;
        } catch (Exception e) {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    guiHandler.bot.getStore().getEventList().addList("Repeating command " + commandToStop + " not found to stop");
                }
            });
            return false;
        }
    }

    public static class Task {

        private final TwitchStatusHandler tH = new TwitchStatusHandler(guiHandler.bot.getStore());
        private volatile boolean dead;
        private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
        private final String commandName;
        private final String commandText;
        private final int initialDelay;
        private final int commandInterval;
        TwitchMessenger tM = guiHandler.messenger;

        public Task(String name, String content, int initD, int interval) {
            commandName = name;
            commandText = content;
            initialDelay = initD;
            commandInterval = interval;
            dead = false;
        }

        public void start() {
            Runnable runnable = () -> {
                if (!getDead()) {
                    // check for stream online/offline
                    if (tH.uptime("").equals("Stream is not currently live.")) {
                        System.out.println("Stream offline while attemping to print: " + commandName);
                    } else {
                        System.out.println("Text of " + commandName + ": " + commandText);
                        tM.sendMessage(commandText);
                    }
                } else {
                    System.out.println("Still alive");
                }
            };
            ses.scheduleWithFixedDelay(runnable, initialDelay, commandInterval, TimeUnit.SECONDS);
        }

        private boolean getDead() {
            return dead;
        }

        public void shutdown() {
            System.out.println("Current shutdown thread: " + Thread.currentThread().getName());
            System.out.println("Shutting down command: " + commandName);
            ses.shutdown();
            try {
                if (!ses.awaitTermination(800, TimeUnit.MILLISECONDS)) {
                    ses.shutdownNow();
                    Thread.currentThread().join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                ses.shutdownNow();
            }
            dead = true;
        }
    }

    private void sendMessage(final String msg) {
        final String sendMessage = "/me > " + msg;
        DashboardController.twitchWSIRC.sendMessage(sendMessage);
    }

    @ThreadSafe
    public static class pongHandler {

        @GuardedBy("this")
        private int pongCounter = 1;

        public synchronized int getPong() {
            return pongCounter;
        }

        public synchronized void addPong() {
            pongCounter++;
        }

        public synchronized void resetPong() {
            pongCounter = 0;
        }

    }
}