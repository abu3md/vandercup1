package com.worldcup.listeners;

import com.worldcup.config.BotConfig;
import com.worldcup.database.DatabaseManager;
import com.worldcup.models.*;
import com.worldcup.services.DiscordBotService;
import com.worldcup.services.MatchPollingService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandListener extends ListenerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final BotConfig config;

    public CommandListener(BotConfig config) {
        this.config = config;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) return;

        String rawContent = event.getMessage().getContentRaw().trim();
        
        // Match only commands starting with !po, !test, !testr, or !testend
        if (!rawContent.startsWith("!po") && !rawContent.startsWith("!test") && !rawContent.startsWith("!testr") && !rawContent.startsWith("!testend")) return;

        // Verify authorization: Administrator permission or Owner ID
        boolean isOwner = event.getAuthor().getId().equals(config.getOwnerId());
        boolean isAdmin = event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!isOwner && !isAdmin) {
            // Silently ignore unauthorized command attempts
            return;
        }

        String[] parts = rawContent.split("\\s+");

        try {
            // ==========================================
            // 1. Leaderboard & Points Commands (!po)
            // ==========================================
            if (rawContent.startsWith("!po")) {
                if (parts.length == 1) {
                    // Command: !po - Display leaderboard (top 10)
                    List<UserStats> top10 = DatabaseManager.getInstance().getTop10Users();
                    if (top10.isEmpty()) {
                        event.getChannel().sendMessage("قائمة المتصدرين فارغة حاليا.").queue();
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    int rank = 1;
                    for (UserStats stats : top10) {
                        sb.append(String.format("**#%d** | <@%s>\n" +
                                                "└ النقاط: `%.1f` | توقعات: `صح %d / خطا %d`\n" +
                                                "└ القدرات: `Gambling %d/%d` | `Sabotage %d/%d` | `Scouting %d`\n\n",
                                rank++,
                                stats.getUserId(),
                                stats.getPoints(),
                                stats.getCorrectPredictions(),
                                stats.getIncorrectPredictions(),
                                stats.getGamblingUsed(), stats.getGamblingWins(),
                                stats.getSabotageUsed(), stats.getSabotageWins(),
                                stats.getScoutingUsed()
                        ));
                    }

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("قائمة افضل 10 اعضاء (Leaderboard)")
                            .setColor(new Color(255, 215, 0)) // Gold Color
                            .setDescription(sb.toString())
                            .setFooter("تم تحديث لوحة المتصدرين تلقائيا")
                            .setTimestamp(Instant.now());

                    event.getChannel().sendMessageEmbeds(embed.build()).queue();
                } else if (parts.length >= 4 && (parts[1].equals("+") || parts[1].equals("-"))) {
                    // Command: !po + number @mention OR !po - number @mention
                    double amount = Double.parseDouble(parts[2]);
                    if (amount < 0) {
                        event.getChannel().sendMessage("يجب ان تكون القيمة موجبة.").queue();
                        return;
                    }

                    List<User> mentions = event.getMessage().getMentions().getUsers();
                    if (mentions.isEmpty()) {
                        event.getChannel().sendMessage("يرجى منشن العضو المستهدف. مثال: `!po + 5 @User`").queue();
                        return;
                    }

                    User target = mentions.get(0);
                    boolean isAddition = parts[1].equals("+");

                    if (isAddition) {
                        DatabaseManager.getInstance().addPoints(target.getId(), amount);
                        event.getChannel().sendMessage(String.format("تم اضافة %.1f نقطة لـ <@%s> بنجاح.", amount, target.getId())).queue();
                        logger.info("Admin {} added {} points to user {}", event.getAuthor().getId(), amount, target.getId());
                    } else {
                        DatabaseManager.getInstance().deductPoints(target.getId(), amount);
                        event.getChannel().sendMessage(String.format("تم خصم %.1f نقطة من <@%s> بنجاح.", amount, target.getId())).queue();
                        logger.info("Admin {} deducted {} points from user {}", event.getAuthor().getId(), amount, target.getId());
                    }
                } else {
                    event.getChannel().sendMessage("صيغة الامر غير صحيحة. استخدم `!po` لعرض المتصدرين او `!po + 5 @User` لتعديل النقاط.").queue();
                }
            }
            // ==========================================
            // 2. Resolve Test Match Result Command (!testr)
            // ==========================================
            else if (rawContent.startsWith("!testr")) {
                if (parts.length != 3) {
                    event.getChannel().sendMessage("صيغة الامر غير صحيحة. مثال: `!testr sa4 usa3`").queue();
                    return;
                }

                Pattern scorePattern = Pattern.compile("^([a-zA-Z]+)(\\d+)$");
                Matcher m1 = scorePattern.matcher(parts[1]);
                Matcher m2 = scorePattern.matcher(parts[2]);

                if (!m1.matches() || !m2.matches()) {
                    event.getChannel().sendMessage("صيغة النتيجة غير صحيحة. يرجى ادخال اسم كود الفريق ملتصقا بالهدف. مثال: `!testr sa4 usa3`").queue();
                    return;
                }

                String t1Code = m1.group(1).toUpperCase();
                int score1 = Integer.parseInt(m1.group(2));

                String t2Code = m2.group(1).toUpperCase();
                int score2 = Integer.parseInt(m2.group(2));

                // Find active matching match
                Match targetMatch = null;
                boolean reversed = false;
                for (Match m : DatabaseManager.getInstance().getAllMatches()) {
                    if (!m.isProcessed()) {
                        if (m.getTeam1().equalsIgnoreCase(t1Code) && m.getTeam2().equalsIgnoreCase(t2Code)) {
                            targetMatch = m;
                            reversed = false;
                            break;
                        } else if (m.getTeam1().equalsIgnoreCase(t2Code) && m.getTeam2().equalsIgnoreCase(t1Code)) {
                            targetMatch = m;
                            reversed = true;
                            break;
                        }
                    }
                }

                if (targetMatch == null) {
                    event.getChannel().sendMessage(String.format("لم يتم العثور على مباراة نشطة تحت الاجراء بين المنتخبين **%s** و **%s**.", t1Code, t2Code)).queue();
                    return;
                }

                // Set scores correctly, taking ordering into account
                if (!reversed) {
                    targetMatch.setScore1(score1);
                    targetMatch.setScore2(score2);
                } else {
                    targetMatch.setScore1(score2);
                    targetMatch.setScore2(score1);
                }

                // Determine winner
                String winner;
                if (targetMatch.getScore1() > targetMatch.getScore2()) {
                    winner = "TEAM_1";
                } else if (targetMatch.getScore1() < targetMatch.getScore2()) {
                    winner = "TEAM_2";
                } else {
                    winner = "DRAW";
                }

                targetMatch.setWinner(winner);
                targetMatch.setStatus("FINISHED");

                // Execute resolution logic (distribute points, process Gambling/Sabotage, etc.)
                MatchPollingService.getInstance().resolveMatchPredictions(targetMatch);
                
                // Send result embed
                DiscordBotService.getInstance().sendResultEmbed(targetMatch);

                targetMatch.setProcessed(true);
                DatabaseManager.getInstance().putMatch(targetMatch);

                event.getChannel().sendMessage(String.format("تم انهاء المباراة وتوزيع النقاط بنجاح: **%s** (%d) ضد **%s** (%d).",
                        targetMatch.getTeam1(), targetMatch.getScore1(),
                        targetMatch.getTeam2(), targetMatch.getScore2())).queue();
            }
            // ==========================================
            // 3. Test Match Creation & Control Command (!test)
            // ==========================================
            else if (rawContent.startsWith("!test")) {
                if (parts.length == 2 && parts[1].equalsIgnoreCase("start")) {
                    // Subcommand: !test start - Set the latest scheduled match to LIVE (starts the game, disabling voting)
                    Match lastScheduled = null;
                    for (Match m : DatabaseManager.getInstance().getAllMatches()) {
                        if ("SCHEDULED".equalsIgnoreCase(m.getStatus())) {
                            lastScheduled = m;
                        }
                    }

                    if (lastScheduled == null) {
                        event.getChannel().sendMessage("لا توجد مباراة تجريبية نشطة ومجدولة حاليا لتغيير حالتها الى LIVE.").queue();
                        return;
                    }

                    lastScheduled.setStatus("LIVE");
                    DatabaseManager.getInstance().putMatch(lastScheduled);

                    // Disable voting buttons on prediction embed
                    if (lastScheduled.getMatchMessageId() != null) {
                        DiscordBotService.getInstance().disableButtons(lastScheduled.getMatchMessageId());
                    }

                    event.getChannel().sendMessage(String.format("بدات المباراة التجريبية: **%s** ضد **%s**! تم ايقاف استقبال التوقعات والقدرات.",
                            lastScheduled.getTeam1(), lastScheduled.getTeam2())).queue();
                    logger.info("Test match {} set to LIVE by admin.", lastScheduled.getId());
                } else if (parts.length == 3) {
                    // Subcommand: !test sa usa - Create a new test match and send prediction embed immediately
                    String team1 = parts[1].toUpperCase();
                    String team2 = parts[2].toUpperCase();

                    Match match = new Match();
                    match.setId("test_" + System.currentTimeMillis());
                    match.setTeam1(team1);
                    match.setTeam2(team2);
                    match.setStartTime(Instant.now().plus(1, ChronoUnit.HOURS).toString()); // Starts in 1 hour
                    match.setStatus("SCHEDULED");
                    match.setStage("GROUP_STAGE");
                    match.setProcessed(false);

                    DatabaseManager.getInstance().putMatch(match);
                    
                    // Immediately trigger prediction embed
                    DiscordBotService.getInstance().sendMatchEmbed(match);
                    DatabaseManager.getInstance().putMatch(match); // Save with message ID updated

                    event.getChannel().sendMessage(String.format("تم انشاء مباراة تجريبية: **%s** ضد **%s** (معرف المباراة: `%s`) وارسال كرت التوقع بنجاح!",
                            team1, team2, match.getId())).queue();
                    logger.info("Admin created test match: {} vs {}", team1, team2);
                } else {
                    event.getChannel().sendMessage("صيغة الامر غير صحيحة.\n- للبدء: `!test sa usa` (السعودية ضد امريكا)\n- لبدء اللعب واغلاق التصويت: `!test start`").queue();
                }
            }
            // ==========================================
            // 4. Resolve Test Match Result via !testend <score1> <score2>
            // ==========================================
            else if (rawContent.startsWith("!testend")) {
                if (parts.length != 3) {
                    event.getChannel().sendMessage("صيغة الامر غير صحيحة. مثال: `!testend 1 3`").queue();
                    return;
                }

                int score1 = Integer.parseInt(parts[1]);
                int score2 = Integer.parseInt(parts[2]);

                // Find the latest active (not processed) match (either test match or scheduled/live match)
                Match targetMatch = null;
                for (Match m : DatabaseManager.getInstance().getAllMatches()) {
                    if (!m.isProcessed()) {
                        targetMatch = m;
                    }
                }

                if (targetMatch == null) {
                    event.getChannel().sendMessage("لا توجد مباراة نشطة غير معالجة لإنهاء نتائجها.").queue();
                    return;
                }

                // Set scores
                targetMatch.setScore1(score1);
                targetMatch.setScore2(score2);

                // Determine winner
                String winner;
                if (score1 > score2) {
                    winner = "TEAM_1";
                } else if (score2 > score1) {
                    winner = "TEAM_2";
                } else {
                    winner = "DRAW";
                }

                targetMatch.setWinner(winner);
                targetMatch.setStatus("FINISHED");

                // Execute resolution logic
                MatchPollingService.getInstance().resolveMatchPredictions(targetMatch);
                
                // Send result embed
                DiscordBotService.getInstance().sendResultEmbed(targetMatch);

                targetMatch.setProcessed(true);
                DatabaseManager.getInstance().putMatch(targetMatch);

                event.getChannel().sendMessage(String.format("تم انهاء المباراة **%s** ضد **%s** وتوزيع النقاط بنجاح بنتيجة (%d - %d).",
                        targetMatch.getTeam1(), targetMatch.getTeam2(), score1, score2)).queue();
                logger.info("Test match {} resolved via !testend with score {} - {}", targetMatch.getId(), score1, score2);
            }
        } catch (NumberFormatException e) {
            event.getChannel().sendMessage("القيمة المدخلة غير صالحة. يرجى ادخال ارقام صحيحة للاهداف.").queue();
        } catch (Exception e) {
            logger.error("Error handling command: " + rawContent, e);
            event.getChannel().sendMessage("حدث خطا غير متوقع اثناء معالجة الامر.").queue();
        }
    }
}
