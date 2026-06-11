package com.worldcup.listeners;

import com.worldcup.database.DatabaseManager;
import com.worldcup.models.*;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;

public class InteractionListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(InteractionListener.class);

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        String userId = event.getUser().getId();

        // 1. Handle Prediction Voting Button Clicks
        if (buttonId.startsWith("predict_t1_") || buttonId.startsWith("predict_draw_") || buttonId.startsWith("predict_t2_")) {
            String matchId = getMatchIdFromButton(buttonId);
            VoteType voteType = getVoteTypeFromButton(buttonId);

            synchronized (DatabaseManager.getInstance()) {
                DatabaseManager db = DatabaseManager.getInstance();
                Match match = db.getMatch(matchId);

                if (match == null) {
                    event.reply("لم يتم العثور على هذه المباراة في قاعدة البيانات.").setEphemeral(true).queue();
                    return;
                }

                // Check if match started
                if (isMatchStarted(match)) {
                    event.reply("لقد بدات هذه المباراة بالفعل! لا يمكنك التصويت او تعديل توقعك.").setEphemeral(true).queue();
                    return;
                }

                // Check if user has already voted
                Map<String, VoteType> matchVotes = db.getVotesForMatch(matchId);
                if (matchVotes.containsKey(userId)) {
                    event.reply("لقد قمت بالتصويت بالفعل لهذه المباراة! لا يمكنك تغيير تصويتك.").setEphemeral(true).queue();
                    return;
                }

                // Record vote
                db.setVote(matchId, userId, voteType);
                event.reply("تم تسجيل تصويتك بنجاح! (" + formatVoteType(voteType) + ")").setEphemeral(true).queue();
                logger.info("User {} voted {} for match {}", userId, voteType, matchId);
            }
            return;
        }

        // 2. Handle main "Abilities" button click
        if (buttonId.startsWith("abilities_btn_")) {
            String matchId = buttonId.substring("abilities_btn_".length());

            synchronized (DatabaseManager.getInstance()) {
                DatabaseManager db = DatabaseManager.getInstance();
                Match match = db.getMatch(matchId);

                if (match == null) {
                    event.reply("لم يتم العثور على هذه المباراة.").setEphemeral(true).queue();
                    return;
                }

                if (isMatchStarted(match)) {
                    event.reply("لقد بدات هذه المباراة بالفعل! لا يمكنك استخدام القدرات.").setEphemeral(true).queue();
                    return;
                }

                // Check if already used an ability
                Map<String, UserAbility> matchAbilities = db.getAbilitiesForMatch(matchId);
                if (matchAbilities.containsKey(userId)) {
                    event.reply("لقد قمت باستخدام قدرة بالفعل في هذه المباراة! لا يمكن استخدام اكثر من قدرة واحدة لكل مباراة.").setEphemeral(true).queue();
                    return;
                }

                // Present ephemeral abilities choices
                event.reply("اختر القدرة التي تريد تفعيلها لهذه المباراة (يمكنك استخدام قدرة واحدة فقط):")
                        .addActionRow(
                                Button.primary("ability_gambling_" + matchId, "Gambling"),
                                Button.danger("ability_sabotage_" + matchId, "Sabotage"),
                                Button.secondary("ability_scouting_" + matchId, "Scouting")
                        )
                        .setEphemeral(true)
                        .queue();
            }
            return;
        }

        // 3. Handle specific ability buttons (Gambling, Sabotage, Scouting)
        if (buttonId.startsWith("ability_gambling_") || buttonId.startsWith("ability_sabotage_") || buttonId.startsWith("ability_scouting_")) {
            String matchId = getMatchIdFromAbilityButton(buttonId);
            AbilityType abilityType = getAbilityTypeFromButton(buttonId);

            synchronized (DatabaseManager.getInstance()) {
                DatabaseManager db = DatabaseManager.getInstance();
                Match match = db.getMatch(matchId);

                if (match == null) {
                    event.reply("لم يتم العثور على المباراة.").setEphemeral(true).queue();
                    return;
                }

                if (isMatchStarted(match)) {
                    event.reply("لقد بدات المباراة بالفعل! لا يمكنك تفعيل القدرات.").setEphemeral(true).queue();
                    return;
                }

                Map<String, UserAbility> matchAbilities = db.getAbilitiesForMatch(matchId);
                if (matchAbilities.containsKey(userId)) {
                    event.reply("لقد قمت باستخدام قدرة بالفعل في هذه المباراة!").setEphemeral(true).queue();
                    return;
                }

                UserStats stats = db.getOrCreateUserStats(userId);

                if (abilityType == AbilityType.GAMBLING) {
                    if (stats.getGamblingUsed() >= 3) {
                        event.reply("لقد استنفدت جميع استخدامات Gambling المتاحة لك (3 مرات بحد اقصى).").setEphemeral(true).queue();
                        return;
                    }

                    // Record Gambling immediately
                    stats.setGamblingUsed(stats.getGamblingUsed() + 1);
                    db.setAbility(matchId, userId, new UserAbility(AbilityType.GAMBLING, null));
                    db.savePoints();

                    int remaining = 3 - stats.getGamblingUsed();
                    event.reply("تم تفعيل قدرة Gambling لهذه المباراة بنجاح! اذا كان توقعك صحيحا ستحصل على ضعف النقاط. (الاستخدامات المتبقية: " + remaining + "/3)").setEphemeral(true).queue();
                    logger.info("User {} activated Gambling on match {}", userId, matchId);

                } else if (abilityType == AbilityType.SABOTAGE) {
                    if (stats.getSabotageUsed() >= 5) {
                        event.reply("لقد استنفدت جميع استخدامات Sabotage المتاحة لك (5 مرات بحد اقصى).").setEphemeral(true).queue();
                        return;
                    }

                    // Present user selector menu
                    EntitySelectMenu userSelect = EntitySelectMenu.create("sabotage_menu_" + matchId, EntitySelectMenu.SelectTarget.USER)
                            .setPlaceholder("اختر العضو المستهدف بالـ Sabotage...")
                            .build();

                    event.reply("اختر العضو الذي تريد تخريب نقاطه في هذه المباراة (خصم نصف نقاط المباراة سواء توقع صح او خطا):")
                            .setComponents(ActionRow.of(userSelect))
                            .setEphemeral(true)
                            .queue();

                } else if (abilityType == AbilityType.SCOUTING) {
                    if (stats.getScoutingUsed() >= 7) {
                        event.reply("لقد استنفدت جميع استخدامات Scouting المتاحة لك (7 مرات بحد اقصى).").setEphemeral(true).queue();
                        return;
                    }

                    // Present user selector menu
                    EntitySelectMenu userSelect = EntitySelectMenu.create("scouting_menu_" + matchId, EntitySelectMenu.SelectTarget.USER)
                            .setPlaceholder("اختر العضو المراد كشف تصويته...")
                            .build();

                    event.reply("اختر العضو الذي تريد كشف تصويته في هذه المباراة:")
                            .setComponents(ActionRow.of(userSelect))
                            .setEphemeral(true)
                            .queue();
                }
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String menuId = event.getComponentId();
        String userId = event.getUser().getId();

        if (menuId.startsWith("sabotage_menu_") || menuId.startsWith("scouting_menu_")) {
            String matchId = menuId.startsWith("sabotage_menu_") ? 
                    menuId.substring("sabotage_menu_".length()) : 
                    menuId.substring("scouting_menu_".length());
            boolean isSabotage = menuId.startsWith("sabotage_menu_");

            if (event.getMentions().getUsers().isEmpty()) {
                event.reply("لم تقم باختيار اي عضو.").setEphemeral(true).queue();
                return;
            }

            User targetUser = event.getMentions().getUsers().get(0);

            synchronized (DatabaseManager.getInstance()) {
                DatabaseManager db = DatabaseManager.getInstance();
                Match match = db.getMatch(matchId);

                if (match == null) {
                    event.reply("لم يتم العثور على المباراة.").setEphemeral(true).queue();
                    return;
                }

                if (isMatchStarted(match)) {
                    event.reply("لقد بدات المباراة بالفعل!").setEphemeral(true).queue();
                    return;
                }

                Map<String, UserAbility> matchAbilities = db.getAbilitiesForMatch(matchId);
                if (matchAbilities.containsKey(userId)) {
                    event.reply("لقد قمت باستخدام قدرة بالفعل في هذه المباراة!").setEphemeral(true).queue();
                    return;
                }

                UserStats stats = db.getOrCreateUserStats(userId);

                if (isSabotage) {
                    if (stats.getSabotageUsed() >= 5) {
                        event.reply("لقد استنفدت جميع استخدامات Sabotage المتاحة لك (5 مرات بحد اقصى).").setEphemeral(true).queue();
                        return;
                    }

                    // Record Sabotage
                    stats.setSabotageUsed(stats.getSabotageUsed() + 1);
                    db.setAbility(matchId, userId, new UserAbility(AbilityType.SABOTAGE, targetUser.getId()));
                    db.savePoints();

                    int remaining = 5 - stats.getSabotageUsed();
                    event.reply("تم تفعيل Sabotage ضد <@" + targetUser.getId() + "> لهذه المباراة بنجاح! اذا صوت هذا اللاعب، سيتم خصم نصف نقاط المباراة من رصيده الاجمالي. (الاستخدامات المتبقية: " + remaining + "/5)")
                            .setEphemeral(true)
                            .queue();
                    logger.info("User {} activated Sabotage targeting {} on match {}", userId, targetUser.getId(), matchId);

                } else {
                    if (stats.getScoutingUsed() >= 7) {
                        event.reply("لقد استنفدت جميع استخدامات Scouting المتاحة لك (7 مرات بحد اقصى).").setEphemeral(true).queue();
                        return;
                    }

                    // Record Scouting
                    stats.setScoutingUsed(stats.getScoutingUsed() + 1);
                    db.setAbility(matchId, userId, new UserAbility(AbilityType.SCOUTING, targetUser.getId()));
                    db.savePoints();

                    // Check target's vote
                    Map<String, VoteType> matchVotes = db.getVotesForMatch(matchId);
                    VoteType targetVote = matchVotes.get(targetUser.getId());
                    int remaining = 7 - stats.getScoutingUsed();

                    if (targetVote == null) {
                        event.reply("لقد قمت باستخدام Scouting بنجاح على <@" + targetUser.getId() + "> ولكن لم يصوت هذا العضو بعد، فلن تتمكن من رؤية شيء! (الاستخدامات المتبقية: " + remaining + "/7)")
                                .setEphemeral(true)
                                .queue();
                    } else {
                        String formattedVote = formatScoutedVote(targetVote);
                        event.reply("لقد كشف Scouting ان <@" + targetUser.getId() + "> قد صوت لـ **" + formattedVote + "**. (الاستخدامات المتبقية: " + remaining + "/7)")
                                .setEphemeral(true)
                                .queue();
                    }
                    logger.info("User {} activated Scouting targeting {} on match {}", userId, targetUser.getId(), matchId);
                }
            }
        }
    }

    // --- Helper Methods ---
    private String getMatchIdFromButton(String buttonId) {
        if (buttonId.startsWith("predict_t1_")) {
            return buttonId.substring("predict_t1_".length());
        } else if (buttonId.startsWith("predict_draw_")) {
            return buttonId.substring("predict_draw_".length());
        } else if (buttonId.startsWith("predict_t2_")) {
            return buttonId.substring("predict_t2_".length());
        }
        return buttonId;
    }

    private VoteType getVoteTypeFromButton(String buttonId) {
        if (buttonId.startsWith("predict_t1_")) return VoteType.TEAM_1;
        if (buttonId.startsWith("predict_draw_")) return VoteType.DRAW;
        return VoteType.TEAM_2;
    }

    private String getMatchIdFromAbilityButton(String buttonId) {
        if (buttonId.startsWith("ability_gambling_")) {
            return buttonId.substring("ability_gambling_".length());
        } else if (buttonId.startsWith("ability_sabotage_")) {
            return buttonId.substring("ability_sabotage_".length());
        } else if (buttonId.startsWith("ability_scouting_")) {
            return buttonId.substring("ability_scouting_".length());
        }
        return buttonId;
    }

    private AbilityType getAbilityTypeFromButton(String buttonId) {
        if (buttonId.startsWith("ability_gambling_")) return AbilityType.GAMBLING;
        if (buttonId.startsWith("ability_sabotage_")) return AbilityType.SABOTAGE;
        return AbilityType.SCOUTING;
    }

    private boolean isMatchStarted(Match match) {
        if ("LIVE".equalsIgnoreCase(match.getStatus()) || "FINISHED".equalsIgnoreCase(match.getStatus())) {
            return true;
        }
        return Instant.parse(match.getStartTime()).isBefore(Instant.now());
    }

    private String formatVoteType(VoteType vote) {
        if (vote == VoteType.TEAM_1) return "المنتخب الاول";
        if (vote == VoteType.DRAW) return "التعادل";
        return "المنتخب الثاني";
    }

    private String formatScoutedVote(VoteType vote) {
        if (vote == VoteType.TEAM_1) return "Team 1";
        if (vote == VoteType.DRAW) return "Draw";
        return "Team 2";
    }
}
