package com.worldcup.services;

import com.worldcup.models.Match;
import com.worldcup.config.BotConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dv8tion.jda.api.utils.FileUpload;
import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

public class DiscordBotService {
    private static final Logger logger = LoggerFactory.getLogger(DiscordBotService.class);
    private static DiscordBotService instance;

    private JDA jda;
    private BotConfig config;

    private DiscordBotService() {}

    public static synchronized DiscordBotService getInstance() {
        if (instance == null) {
            instance = new DiscordBotService();
        }
        return instance;
    }

    public void init(JDA jda, BotConfig config) {
        this.jda = jda;
        this.config = config;
    }

    private TextChannel getChannel() {
        if (jda == null) return null;
        return jda.getTextChannelById(config.getChannelId());
    }

    /**
     * Sends the match prediction card as a generated image with buttons and custom text.
     */
    public void sendMatchEmbed(Match match) {
        TextChannel channel = getChannel();
        if (channel == null) {
            logger.error("TextChannel is not available. Cannot send match prediction card.");
            return;
        }

        // Generate the custom match card image
        byte[] imageBytes = ImageGeneratorService.getInstance().generateMatchCard(match);
        if (imageBytes == null) {
            logger.error("Failed to generate match card image. Cannot send prediction card.");
            return;
        }

        // Create buttons
        Button btnTeam1 = Button.primary("predict_t1_" + match.getId(), match.getTeam1());
        Button btnDraw = Button.secondary("predict_draw_" + match.getId(), "التعادل");
        Button btnTeam2 = Button.primary("predict_t2_" + match.getId(), match.getTeam2());
        Button btnAbilities = Button.danger("abilities_btn_" + match.getId(), "القدرات Abilities");

        // Format message content as requested by the user
        String messageContent = String.format(
                "<@&%s>\nمباراة ضد %s و %s\nالوقت المتبقي لبدء المباراة: <t:%d:R>",
                config.getMentionRoleId(),
                match.getTeam1(),
                match.getTeam2(),
                java.time.Instant.parse(match.getStartTime()).getEpochSecond()
        );

        channel.sendFiles(FileUpload.fromData(imageBytes, "prediction.png"))
                .setContent(messageContent)
                .setComponents(ActionRow.of(btnTeam1, btnDraw, btnTeam2, btnAbilities))
                .queue(message -> {
                    match.setMatchMessageId(message.getId());
                    com.worldcup.database.DatabaseManager.getInstance().putMatch(match);
                    logger.info("Sent prediction card message for match {} (Msg ID: {})", match.getId(), message.getId());
                }, error -> {
                    match.setMatchMessageId(null);
                    com.worldcup.database.DatabaseManager.getInstance().putMatch(match);
                    logger.error("Failed to send prediction card for match " + match.getId(), error);
                });
    }

    /**
     * Sends the match results embed. No role mention.
     */
    public void sendResultEmbed(Match match) {
        TextChannel channel = getChannel();
        if (channel == null) {
            logger.error("TextChannel is not available. Cannot send result embed.");
            return;
        }

        String winnerText;
        if ("DRAW".equals(match.getWinner())) {
            winnerText = "التعادل";
        } else if ("TEAM_1".equals(match.getWinner())) {
            winnerText = match.getTeam1();
        } else if ("TEAM_2".equals(match.getWinner())) {
            winnerText = match.getTeam2();
        } else {
            winnerText = "غير معروف";
        }

        byte[] imageBytes = ImageGeneratorService.getInstance().generateResultCard(match);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("نتيجة المباراة الرسمية")
                .setDescription(String.format("قسم: **%s**", formatStage(match.getStage())))
                .addField(match.getTeam1(), String.valueOf(match.getScore1() != null ? match.getScore1() : 0), true)
                .addField("النهاية", "VS", true)
                .addField(match.getTeam2(), String.valueOf(match.getScore2() != null ? match.getScore2() : 0), true)
                .addField("الفائز الرسمي", "**" + winnerText + "**", false)
                .setColor(new Color(46, 204, 113))
                .setFooter("تم تحديث النقاط وتوزيع الجوائز والخصومات");

        if (imageBytes != null) {
            embed.setImage("attachment://result.png");
            channel.sendFiles(FileUpload.fromData(imageBytes, "result.png"))
                    .setEmbeds(embed.build())
                    .queue(message -> {
                        match.setResultMessageId(message.getId());
                        com.worldcup.database.DatabaseManager.getInstance().putMatch(match);
                        logger.info("Sent result card and embed for match {} (Msg ID: {})", match.getId(), message.getId());
                    }, error -> {
                        logger.error("Failed to send result card for match " + match.getId(), error);
                    });
        } else {
            channel.sendMessageEmbeds(embed.build()).queue(message -> {
                match.setResultMessageId(message.getId());
                com.worldcup.database.DatabaseManager.getInstance().putMatch(match);
                logger.info("Sent result embed (without image) for match {} (Msg ID: {})", match.getId(), message.getId());
            }, error -> {
                logger.error("Failed to send result embed for match " + match.getId(), error);
            });
        }
    }

    /**
     * Deletes a message from the channel.
     */
    public void deleteMessage(String messageId) {
        TextChannel channel = getChannel();
        if (channel == null || messageId == null) return;

        channel.deleteMessageById(messageId).queue(
                success -> logger.info("Deleted old match message {}", messageId),
                error -> logger.warn("Failed to delete message {}: {}", messageId, error.getMessage())
        );
    }

    /**
     * Disables all buttons on a prediction message.
     */
    public void disableButtons(String messageId) {
        TextChannel channel = getChannel();
        if (channel == null || messageId == null) return;

        channel.retrieveMessageById(messageId).queue(message -> {
            if (!message.getButtons().isEmpty() && !message.getButtons().get(0).isDisabled()) {
                List<ActionRow> disabledRows = message.getActionRows().stream()
                        .map(ActionRow::asDisabled)
                        .collect(Collectors.toList());
                message.editMessageComponents(disabledRows).queue(
                        success -> logger.info("Disabled buttons for message {}", messageId),
                        error -> logger.error("Failed to disable buttons for message {}: {}", messageId, error.getMessage())
                );
            }
        }, error -> logger.warn("Could not retrieve message {} to disable buttons: {}", messageId, error.getMessage()));
    }

    private String formatStage(String stage) {
        if (stage == null) return "";
        switch (stage) {
            case "GROUP_STAGE": return "دور المجموعات (1 نقطة)";
            case "ROUND_OF_16": return "دور الـ 16 (2 نقاط)";
            case "QUARTER_FINALS": return "ربع النهائي (3 نقاط)";
            case "SEMI_FINALS": return "نصف النهائي (4 نقاط)";
            case "FINAL": return "النهائي (5 نقاط)";
            default: return stage;
        }
    }
}
