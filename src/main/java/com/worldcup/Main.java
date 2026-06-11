package com.worldcup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldcup.config.BotConfig;
import com.worldcup.listeners.CommandListener;
import com.worldcup.listeners.InteractionListener;
import com.worldcup.services.DiscordBotService;
import com.worldcup.services.MatchPollingService;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting World Cup 2026 Discord Bot...");

        // 1. Load config.json configuration
        BotConfig config = loadConfig();
        if (config == null) {
            logger.error("Failed to load config.json. Exiting application.");
            System.exit(1);
        }

        try {
            // 2. Build JDA with minimal intents and caches disabled to meet constraints
            EnumSet<GatewayIntent> intents = EnumSet.of(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT
            );

            JDA jda = JDABuilder.createDefault(config.getDiscordToken(), intents)
                    // Disable all unnecessary memory caches
                    .disableCache(
                            CacheFlag.VOICE_STATE,
                            CacheFlag.ACTIVITY,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.ONLINE_STATUS,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.ROLE_TAGS,
                            CacheFlag.FORUM_TAGS,
                            CacheFlag.MEMBER_OVERRIDES,
                            CacheFlag.SCHEDULED_EVENTS
                    )
                    // Register JDA Listeners
                    .addEventListeners(new CommandListener(config))
                    .addEventListeners(new InteractionListener())
                    .build();

            // Await ready state
            jda.awaitReady();
            logger.info("JDA initialization complete. Bot is online.");

            // 3. Initialize Services (Singletons)
            // Initialize JDA wrapper service
            DiscordBotService.getInstance().init(jda, config);

            // Start Smart Match Polling & Hourly Backups Service
            MatchPollingService.getInstance().start(config);

            logger.info("World Cup Bot is fully operational and listening for matches/events.");
        } catch (InterruptedException e) {
            logger.error("Bot startup was interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Critical error during bot startup", e);
        }
    }

    private static BotConfig loadConfig() {
        File configFile = new File("config.json");
        if (!configFile.exists()) {
            logger.error("config.json file not found in execution directory.");
            return null;
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(configFile, BotConfig.class);
        } catch (IOException e) {
            logger.error("Failed to parse config.json file", e);
            return null;
        }
    }
}
