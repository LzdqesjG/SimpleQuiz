package com.diamondpie.simplequiz;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuizListener implements Listener {

    private final Main plugin;

    public QuizListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        if (!plugin.getQuizManager().isQuizRunning()) return;

        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());

        // Check logic needs to run properly regarding sync/async if modifying world state,
        // but QuizManager logic for rewards schedules tasks to main thread.
        boolean correct = plugin.getQuizManager().checkAnswer(e.getPlayer(), msg);

        if (correct) {
            e.setCancelled(true); // Don't show the correct answer in raw text to avoid clutter
        } else {
            // Wrong answer
            if (plugin.getConfig().getBoolean("notify-wrong-answer", false)) {
                // To avoid spamming chat for everyone, we might just send a message to the player.
                // However, standard chat event handles the broadcasting of the message itself.
                // We just append a little hint only to the player?
                // Requirement says: "if config false, no response".
                // If config true: "give separate hint".
                // Since this is AsyncChatEvent, we shouldn't modify the event message for just one player easily without side effects.
                // We send a separate message.
                e.getPlayer().sendMessage(Component.text("回答错误", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // Make sure getOnlinePlayers is updated
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getQuizManager().checkPlayerCount();
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // Make sure getOnlinePlayers is updated
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getQuizManager().checkPlayerCount();
        }, 1L);
    }
}