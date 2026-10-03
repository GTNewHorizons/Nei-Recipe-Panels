package com.gtnewhorizons.neirecipepanel.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;

import com.gtnewhorizons.neirecipepanel.NEIRecipePanelsMod;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ServerTasks {

    public static final ServerTasks INSTANCE = new ServerTasks();

    private static final int MAX_PENDING_TASKS = 128;
    private static final int MAX_PENDING_TASKS_PER_PLAYER = 8;
    private static final int MAX_TASKS_PER_TICK = 16;

    private static final BoundedPlayerTaskQueue<UUID, PendingTask> QUEUE = new BoundedPlayerTaskQueue<>(
        MAX_PENDING_TASKS,
        MAX_PENDING_TASKS_PER_PLAYER);

    private ServerTasks() {}

    public static boolean submit(EntityPlayerMP player, Runnable task) {
        if (!hasOpenConnection(player) || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }
        return QUEUE.offer(player.getUniqueID(), new PendingTask(player, player.worldObj, task));
    }

    public static void clear() {
        QUEUE.clear();
    }

    private static boolean hasOpenConnection(EntityPlayerMP player) {
        return player != null && !player.isDead
            && player.playerNetServerHandler != null
            && player.playerNetServerHandler.netManager.isChannelOpen();
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        for (int completed = 0; completed < MAX_TASKS_PER_TICK; completed++) {
            PendingTask task = QUEUE.poll();
            if (task == null) {
                return;
            }
            MinecraftServer server = MinecraftServer.getServer();
            if (!hasOpenConnection(task.player) || task.player.worldObj != task.world
                || server == null
                || server.getConfigurationManager() == null
                || !server.getConfigurationManager().playerEntityList.contains(task.player)) {
                continue;
            }
            try {
                task.work.run();
            } catch (RuntimeException exception) {
                NEIRecipePanelsMod.LOG
                    .warn("Recipe panel request failed for player {}", task.player.getUniqueID(), exception);
            }
        }
    }

    private static final class PendingTask {

        private final EntityPlayerMP player;
        private final World world;
        private final Runnable work;

        private PendingTask(EntityPlayerMP player, World world, Runnable work) {
            this.player = player;
            this.world = world;
            this.work = Objects.requireNonNull(work, "work");
        }
    }
}

final class BoundedPlayerTaskQueue<P, T> {

    private final int maximumSize;
    private final int maximumPerPlayer;
    private final Map<P, Deque<T>> playerTasks = new HashMap<>();
    private final Deque<P> playerOrder = new ArrayDeque<>();
    private int size;

    BoundedPlayerTaskQueue(int maximumSize, int maximumPerPlayer) {
        if (maximumSize <= 0 || maximumPerPlayer <= 0 || maximumPerPlayer > maximumSize) {
            throw new IllegalArgumentException("Invalid task queue limits");
        }
        this.maximumSize = maximumSize;
        this.maximumPerPlayer = maximumPerPlayer;
    }

    synchronized boolean offer(P player, T task) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        Deque<T> tasks = playerTasks.get(player);
        if (size >= maximumSize || tasks != null && tasks.size() >= maximumPerPlayer) {
            return false;
        }
        if (tasks == null) {
            tasks = new ArrayDeque<>();
            playerTasks.put(player, tasks);
            playerOrder.addLast(player);
        }
        tasks.addLast(task);
        size++;
        return true;
    }

    synchronized T poll() {
        P player = playerOrder.pollFirst();
        if (player == null) {
            return null;
        }
        Deque<T> tasks = playerTasks.get(player);
        T task = tasks.removeFirst();
        size--;
        if (tasks.isEmpty()) {
            playerTasks.remove(player);
        } else {
            playerOrder.addLast(player);
        }
        return task;
    }

    synchronized int size() {
        return size;
    }

    synchronized void clear() {
        playerTasks.clear();
        playerOrder.clear();
        size = 0;
    }
}
