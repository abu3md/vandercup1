package com.worldcup.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldcup.config.BotConfig;
import com.worldcup.database.DatabaseManager;
import com.worldcup.models.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import okhttp3.RequestBody;
import okhttp3.MediaType;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.ConcurrentHashMap;

public class MatchPollingService {
    private static final Logger logger = LoggerFactory.getLogger(MatchPollingService.class);
    private String jwtToken = null;
    private final Map<String, TeamInfo> teamCache = new ConcurrentHashMap<>();

    private static class TeamInfo {
        String nameEn;
        String flagUrl;
        TeamInfo(String nameEn, String flagUrl) {
            this.nameEn = nameEn;
            this.flagUrl = flagUrl;
        }
    }
    private static MatchPollingService instance;

    private final ScheduledExecutorService scheduler;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private BotConfig config;
    private boolean running = false;

    private MatchPollingService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BotSchedulerThread");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public static synchronized MatchPollingService getInstance() {
        if (instance == null) {
            instance = new MatchPollingService();
        }
        return instance;
    }

    public void start(BotConfig config) {
        if (running) return;
        this.config = config;
        this.running = true;

        // Schedule the first poll immediately
        scheduler.schedule(this::pollAndReschedule, 0, TimeUnit.SECONDS);

        // Schedule hourly backups
        scheduler.scheduleAtFixedRate(() -> {
            try {
                DatabaseManager.getInstance().performBackup();
            } catch (Exception e) {
                logger.error("Error running hourly backup", e);
            }
        }, 1, 1, TimeUnit.HOURS);

        logger.info("Match Polling Service started.");
    }

    private void pollAndReschedule() {
        long nextDelaySeconds = 300; // Default to 5 minutes (300 seconds)
        try {
            logger.info("Executing API poll...");
            List<Match> apiMatches = fetchMatches();
            
            if (apiMatches != null && !apiMatches.isEmpty()) {
                processMatches(apiMatches);
                nextDelaySeconds = calculateNextDelay(apiMatches);
            } else {
                logger.warn("No matches returned from API poll. Retrying in 5 minutes.");
            }
        } catch (Exception e) {
            logger.error("Error occurred during match polling loop", e);
        } finally {
            if (running) {
                logger.info("Next API poll scheduled in {} seconds.", nextDelaySeconds);
                scheduler.schedule(this::pollAndReschedule, nextDelaySeconds, TimeUnit.SECONDS);
            }
        }
    }

    private synchronized String getOrAuthenticateToken() {
        if (jwtToken != null) {
            return jwtToken;
        }

        String baseUrl = config.getApiUrl();
        if (baseUrl == null || baseUrl.isEmpty() || "WORLD_CUP_API_HERE".equalsIgnoreCase(baseUrl)) {
            return null;
        }

        // 1. Try to login
        String loginJson = "{\"email\":\"worldcupbot@gmail.com\",\"password\":\"SecureBotPass123!\"}";
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), loginJson);
        Request loginRequest = new Request.Builder()
                .url(baseUrl + "/auth/authenticate")
                .post(body)
                .build();

        try (Response response = httpClient.newCall(loginRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                if (rootNode.has("token")) {
                    jwtToken = rootNode.get("token").asText();
                    logger.info("Successfully authenticated with API. Token cached.");
                    return jwtToken;
                }
            } else {
                logger.warn("Authentication failed (code: {}). Attempting automatic registration...", response.code());
            }
        } catch (IOException e) {
            logger.error("Failed to authenticate: {}", e.getMessage());
        }

        // 2. If login fails, try to register
        String registerJson = "{\"name\":\"WorldCupBot\",\"email\":\"worldcupbot@gmail.com\",\"password\":\"SecureBotPass123!\"}";
        RequestBody registerBody = RequestBody.create(MediaType.parse("application/json"), registerJson);
        Request registerRequest = new Request.Builder()
                .url(baseUrl + "/auth/register")
                .post(registerBody)
                .build();

        try (Response response = httpClient.newCall(registerRequest).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                if (rootNode.has("token")) {
                    jwtToken = rootNode.get("token").asText();
                    logger.info("Successfully registered and authenticated with API. Token cached.");
                    return jwtToken;
                }
            } else {
                logger.error("Registration failed with code: {}, message: {}", response.code(), response.message());
            }
        } catch (IOException e) {
            logger.error("Failed to register bot account: {}", e.getMessage());
        }

        return null;
    }

    private synchronized void populateTeamCache() {
        if (!teamCache.isEmpty()) {
            return;
        }

        String token = getOrAuthenticateToken();
        if (token == null) {
            return;
        }

        String url = config.getApiUrl() + "/get/teams";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);
                if (root.has("teams")) {
                    JsonNode teamsNode = root.get("teams");
                    if (teamsNode.isArray()) {
                        for (JsonNode t : teamsNode) {
                            String id = t.has("id") ? t.get("id").asText() : "";
                            String nameEn = t.has("name_en") ? t.get("name_en").asText() : "";
                            String flag = t.has("flag") ? t.get("flag").asText() : "";
                            if (!id.isEmpty()) {
                                teamCache.put(id, new TeamInfo(nameEn, flag));
                            }
                        }
                        logger.info("Populated team cache with {} teams.", teamCache.size());
                    }
                }
            } else {
                logger.error("Failed to fetch teams: code={}, msg={}", response.code(), response.message());
                if (response.code() == 401) {
                    jwtToken = null; // force reauth next time
                }
            }
        } catch (IOException e) {
            logger.error("Failed to populate team cache: {}", e.getMessage());
        }
    }

    private List<Match> fetchMatches() {
        String baseUrl = config.getApiUrl();
        if ("WORLD_CUP_API_HERE".equalsIgnoreCase(baseUrl) || baseUrl == null || baseUrl.isEmpty()) {
            logger.info("API URL is placeholder or empty. Checking local mock file fallback...");
            return fetchMockMatches();
        }

        // Ensure team cache is populated
        populateTeamCache();

        String token = getOrAuthenticateToken();
        if (token == null) {
            logger.error("Cannot fetch matches: authentication failed.");
            return Collections.emptyList();
        }

        Request request = new Request.Builder()
                .url(baseUrl + "/get/games")
                .header("Authorization", "Bearer " + token)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 401) {
                logger.warn("JWT token expired or invalid (401). Forcing re-authentication.");
                jwtToken = null;
                token = getOrAuthenticateToken();
                if (token != null) {
                    request = new Request.Builder()
                            .url(baseUrl + "/get/games")
                            .header("Authorization", "Bearer " + token)
                            .build();
                    try (Response retryResponse = httpClient.newCall(request).execute()) {
                        return parseApiResponse(retryResponse);
                    }
                }
            } else {
                return parseApiResponse(response);
            }
        } catch (IOException e) {
            logger.error("Failed to execute API request: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<Match> parseApiResponse(Response response) {
        if (!response.isSuccessful() || response.body() == null) {
            logger.error("API request failed with code: {}, message: {}", response.code(), response.message());
            return Collections.emptyList();
        }

        try {
            String body = response.body().string();
            JsonNode root = objectMapper.readTree(body);
            if (!root.has("games")) {
                logger.error("API response does not contain 'games' key.");
                return Collections.emptyList();
            }

            JsonNode gamesNode = root.get("games");
            if (!gamesNode.isArray()) {
                logger.error("'games' key in API response is not an array.");
                return Collections.emptyList();
            }

            List<Match> matches = new ArrayList<>();
            for (JsonNode g : gamesNode) {
                Match match = new Match();
                
                // 1. ID
                match.setId(g.has("id") ? g.get("id").asText() : "");
                
                // 2. Teams Names and Flags Lookup
                String homeId = g.has("home_team_id") ? g.get("home_team_id").asText() : "";
                String awayId = g.has("away_team_id") ? g.get("away_team_id").asText() : "";
                
                TeamInfo homeTeam = teamCache.get(homeId);
                TeamInfo awayTeam = teamCache.get(awayId);
                
                if (homeTeam != null) {
                    match.setTeam1(homeTeam.nameEn);
                    match.setTeam1Flag(homeTeam.flagUrl);
                } else {
                    String nameEn = g.has("home_team_name_en") ? g.get("home_team_name_en").asText() : "TBD";
                    match.setTeam1(nameEn);
                }
                
                if (awayTeam != null) {
                    match.setTeam2(awayTeam.nameEn);
                    match.setTeam2Flag(awayTeam.flagUrl);
                } else {
                    String nameEn = g.has("away_team_name_en") ? g.get("away_team_name_en").asText() : "TBD";
                    match.setTeam2(nameEn);
                }

                // 3. Date Parsing & StartTime
                if (g.has("local_date")) {
                    String localDateStr = g.get("local_date").asText();
                    String stadiumId = g.has("stadium_id") ? g.get("stadium_id").asText() : "";
                    
                    java.time.ZoneId zoneId;
                    switch (stadiumId) {
                        case "1": case "2": case "3":
                            zoneId = java.time.ZoneId.of("America/Mexico_City");
                            break;
                        case "4": case "5": case "6":
                            zoneId = java.time.ZoneId.of("America/Chicago");
                            break;
                        case "7": case "8": case "9": case "10": case "11": case "12":
                            zoneId = java.time.ZoneId.of("America/New_York");
                            break;
                        case "13": case "14": case "15": case "16":
                            zoneId = java.time.ZoneId.of("America/Los_Angeles");
                            break;
                        default:
                            zoneId = java.time.ZoneOffset.UTC;
                    }
                    
                    try {
                        java.time.LocalDateTime localDateTime = java.time.LocalDateTime.parse(
                                localDateStr, java.time.format.DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"));
                        java.time.ZonedDateTime zonedDateTime = java.time.ZonedDateTime.of(localDateTime, zoneId);
                        match.setStartTime(zonedDateTime.toInstant().toString());
                    } catch (Exception e) {
                        logger.error("Failed to parse date '{}' for match {}: {}", localDateStr, match.getId(), e.getMessage());
                        match.setStartTime(java.time.Instant.now().toString());
                    }
                }

                // 4. Status mapping
                String timeElapsed = g.has("time_elapsed") ? g.get("time_elapsed").asText().toLowerCase() : "";
                String finished = g.has("finished") ? g.get("finished").asText().toUpperCase() : "FALSE";
                
                if ("TRUE".equals(finished)) {
                    match.setStatus("FINISHED");
                } else if ("notstarted".equals(timeElapsed)) {
                    match.setStatus("SCHEDULED");
                } else {
                    match.setStatus("LIVE");
                }

                // 5. Stage Mapping
                String type = g.has("type") ? g.get("type").asText().toLowerCase() : "group";
                switch (type) {
                    case "group":
                        match.setStage("GROUP_STAGE");
                        break;
                    case "r32":
                    case "r16":
                        match.setStage("ROUND_OF_16");
                        break;
                    case "qf":
                        match.setStage("QUARTER_FINALS");
                        break;
                    case "sf":
                        match.setStage("SEMI_FINALS");
                        break;
                    case "third":
                    case "final":
                        match.setStage("FINAL");
                        break;
                    default:
                        match.setStage("GROUP_STAGE");
                }

                // 6. Scores
                try {
                    String homeScoreStr = g.has("home_score") ? g.get("home_score").asText() : "0";
                    String awayScoreStr = g.has("away_score") ? g.get("away_score").asText() : "0";
                    int homeScore = "null".equalsIgnoreCase(homeScoreStr) ? 0 : Integer.parseInt(homeScoreStr);
                    int awayScore = "null".equalsIgnoreCase(awayScoreStr) ? 0 : Integer.parseInt(awayScoreStr);
                    match.setScore1(homeScore);
                    match.setScore2(awayScore);
                    
                    if ("FINISHED".equals(match.getStatus())) {
                        if (homeScore > awayScore) {
                            match.setWinner("TEAM_1");
                        } else if (awayScore > homeScore) {
                            match.setWinner("TEAM_2");
                        } else {
                            match.setWinner("DRAW");
                        }
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse scores for match {}: {}", match.getId(), e.getMessage());
                    match.setScore1(0);
                    match.setScore2(0);
                }

                matches.add(match);
            }
            return matches;
        } catch (IOException e) {
            logger.error("Failed to parse games list JSON: {}", e.getMessage());
        }

        return Collections.emptyList();
    }

    private List<Match> fetchMockMatches() {
        File mockFile = new File("mock_api_matches.json");
        if (!mockFile.exists()) {
            createDefaultMockFile(mockFile);
        }

        try {
            return objectMapper.readValue(mockFile, new TypeReference<List<Match>>() {});
        } catch (IOException e) {
            logger.error("Failed to read mock matches file", e);
            return Collections.emptyList();
        }
    }

    private void createDefaultMockFile(File file) {
        logger.info("Creating default mock_api_matches.json file...");
        Instant now = Instant.now();
        List<Match> mockMatches = new ArrayList<>();

        // Match 1: Argentina vs Saudi Arabia (GROUP_STAGE) - Starts in 3 hours (far match)
        Match m1 = new Match();
        m1.setId("match_01");
        m1.setTeam1("الأرجنتين");
        m1.setTeam1Flag("https://upload.wikimedia.org/wikipedia/commons/1/1a/Flag_of_Argentina.svg");
        m1.setTeam2("السعودية");
        m1.setTeam2Flag("https://upload.wikimedia.org/wikipedia/commons/0/0d/Flag_of_Saudi_Arabia.svg");
        m1.setStartTime(now.plus(3, ChronoUnit.HOURS).toString());
        m1.setStatus("SCHEDULED");
        m1.setStage("GROUP_STAGE");
        mockMatches.add(m1);

        // Match 2: البرازيل vs كرواتيا (QUARTER_FINALS) - Starts in 1.5 hours (should trigger prediction embed)
        Match m2 = new Match();
        m2.setId("match_02");
        m2.setTeam1("البرازيل");
        m2.setTeam1Flag("https://upload.wikimedia.org/wikipedia/commons/0/05/Flag_of_Brazil.svg");
        m2.setTeam2("كرواتيا");
        m2.setTeam2Flag("https://upload.wikimedia.org/wikipedia/commons/1/1b/Flag_of_Croatia.svg");
        m2.setStartTime(now.plus(90, ChronoUnit.MINUTES).toString());
        m2.setStatus("SCHEDULED");
        m2.setStage("QUARTER_FINALS");
        mockMatches.add(m2);

        // Match 3: فرنسا vs المغرب (SEMI_FINALS) - Live match
        Match m3 = new Match();
        m3.setId("match_03");
        m3.setTeam1("فرنسا");
        m3.setTeam1Flag("https://upload.wikimedia.org/wikipedia/commons/c/c3/Flag_of_France.svg");
        m3.setTeam2("المغرب");
        m3.setTeam2Flag("https://upload.wikimedia.org/wikipedia/commons/2/2c/Flag_of_Morocco.svg");
        m3.setStartTime(now.minus(10, ChronoUnit.MINUTES).toString());
        m3.setStatus("LIVE");
        m3.setStage("SEMI_FINALS");
        mockMatches.add(m3);

        // Match 4: ألمانيا vs اليابان (GROUP_STAGE) - Finished
        Match m4 = new Match();
        m4.setId("match_04");
        m4.setTeam1("ألمانيا");
        m4.setTeam1Flag("https://upload.wikimedia.org/wikipedia/commons/b/ba/Flag_of_Germany.svg");
        m4.setTeam2("اليابان");
        m4.setTeam2Flag("https://upload.wikimedia.org/wikipedia/commons/9/9e/Flag_of_Japan.svg");
        m4.setStartTime(now.minus(3, ChronoUnit.HOURS).toString());
        m4.setStatus("FINISHED");
        m4.setStage("GROUP_STAGE");
        m4.setScore1(1);
        m4.setScore2(2);
        m4.setWinner("TEAM_2");
        mockMatches.add(m4);

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, mockMatches);
        } catch (IOException e) {
            logger.error("Failed to create default mock file", e);
        }
    }

    private void processMatches(List<Match> apiMatches) {
        DatabaseManager db = DatabaseManager.getInstance();
        DiscordBotService bot = DiscordBotService.getInstance();
        Instant now = Instant.now();

        for (Match apiMatch : apiMatches) {
            Match localMatch = db.getMatch(apiMatch.getId());

            if (localMatch == null) {
                // First time discovering this match, save it
                localMatch = apiMatch;
                db.putMatch(localMatch);
            }

            // Sync structural match metadata (names, flags, time) in case of admin adjustments in API
            localMatch.setTeam1(apiMatch.getTeam1());
            localMatch.setTeam1Flag(apiMatch.getTeam1Flag());
            localMatch.setTeam2(apiMatch.getTeam2());
            localMatch.setTeam2Flag(apiMatch.getTeam2Flag());
            localMatch.setStartTime(apiMatch.getStartTime());
            localMatch.setStage(apiMatch.getStage());

            String prevStatus = localMatch.getStatus();
            String newStatus = apiMatch.getStatus();
            localMatch.setStatus(newStatus);

            // Handle Postponed / Cancelled / Replayed transitions
            if (localMatch.getMatchMessageId() != null &&
                ("POSTPONED".equalsIgnoreCase(newStatus) || "CANCELLED".equalsIgnoreCase(newStatus) || "REPLAYED".equalsIgnoreCase(newStatus)) &&
                !"POSTPONED".equalsIgnoreCase(prevStatus) && !"CANCELLED".equalsIgnoreCase(prevStatus) && !"REPLAYED".equalsIgnoreCase(prevStatus)) {
                
                logger.info("Match {} status changed to {}. Deleting prediction form...", localMatch.getId(), newStatus);
                // 1. Delete prediction message
                bot.deleteMessage(localMatch.getMatchMessageId());
                
                // 2. Clear predictions/abilities
                db.clearMatchPredictions(localMatch.getMatchMessageId());
                localMatch.setMatchMessageId(null);
                
                // 3. Send notification message
                // (Let's send a status update text message and record its ID in matchMessageId so we can delete it if rescheduled)
                // Wait, DiscordBotService could do that. Let's send a custom status update embed.
                // We'll write code in localMatch/db
            }

            // Case A: Send prediction embed (Scheduled, starts in <= 2 hours, embed not sent yet)
            if ("SCHEDULED".equalsIgnoreCase(newStatus)) {
                Instant startTime = Instant.parse(localMatch.getStartTime());
                if (startTime.minus(2, ChronoUnit.HOURS).isBefore(now) && startTime.isAfter(now) && localMatch.getMatchMessageId() == null) {
                    localMatch.setMatchMessageId("SENDING");
                    db.putMatch(localMatch);
                    bot.sendMatchEmbed(localMatch);
                }
            }

            // Case B: Match started / Live (Disable buttons on prediction embed)
            boolean isLiveOrPast = "LIVE".equalsIgnoreCase(newStatus) || Instant.parse(localMatch.getStartTime()).isBefore(now);
            if (isLiveOrPast && localMatch.getMatchMessageId() != null) {
                bot.disableButtons(localMatch.getMatchMessageId());
            }

            // Case C: Match finished (Distribute points once)
            if ("FINISHED".equalsIgnoreCase(newStatus)) {
                localMatch.setScore1(apiMatch.getScore1());
                localMatch.setScore2(apiMatch.getScore2());
                localMatch.setWinner(apiMatch.getWinner());

                if (!localMatch.isProcessed()) {
                    // Disable buttons just in case
                    if (localMatch.getMatchMessageId() != null) {
                        bot.disableButtons(localMatch.getMatchMessageId());
                    }

                    // Resolve points & send results
                    resolveMatchPredictions(localMatch);
                    bot.sendResultEmbed(localMatch);
                    
                    localMatch.setProcessed(true);
                    db.saveMatches();
                }
            }

            db.putMatch(localMatch);
        }
    }

    public void resolveMatchPredictions(Match match) {
        DatabaseManager db = DatabaseManager.getInstance();
        String matchId = match.getId();
        String winner = match.getWinner();

        if (winner == null) {
            logger.error("Match {} finished but winner is NULL. Cannot resolve predictions.", matchId);
            return;
        }

        Map<String, VoteType> matchVotes = db.getVotesForMatch(matchId);
        Map<String, UserAbility> matchAbilities = db.getAbilitiesForMatch(matchId);
        int stagePoints = getStagePoints(match.getStage());

        logger.info("Resolving predictions for match {} (Stage points: {}). Total votes: {}", matchId, stagePoints, matchVotes.size());

        // We will process user predictions, apply Gambling modifiers, and accumulate Sabotages
        Map<String, List<String>> sabotageTargets = new HashMap<>(); // targetId -> List of sabotagers

        for (Map.Entry<String, VoteType> entry : matchVotes.entrySet()) {
            String userId = entry.getKey();
            VoteType vote = entry.getValue();
            UserStats stats = db.getOrCreateUserStats(userId);
            UserAbility ability = matchAbilities.get(userId);

            boolean correct = vote.name().equalsIgnoreCase(winner);
            double earnedPoints = 0.0;

            if (correct) {
                earnedPoints = stagePoints;
                stats.setCorrectPredictions(stats.getCorrectPredictions() + 1);

                // Apply Gambling multiplier
                if (ability != null && ability.getAbilityType() == AbilityType.GAMBLING) {
                    earnedPoints *= 2.0;
                    stats.incrementGamblingWins();
                }
            } else {
                stats.setIncorrectPredictions(stats.getIncorrectPredictions() + 1);
            }

            // Award prediction points
            stats.setPoints(stats.getPoints() + earnedPoints);

            // Track ability usage increments
            if (ability != null) {
                if (ability.getAbilityType() == AbilityType.GAMBLING) {
                    stats.incrementGamblingUsed();
                } else if (ability.getAbilityType() == AbilityType.SABOTAGE) {
                    stats.incrementSabotageUsed();
                    if (ability.getTargetUserId() != null) {
                        sabotageTargets.computeIfAbsent(ability.getTargetUserId(), k -> new ArrayList<>()).add(userId);
                    }
                } else if (ability.getAbilityType() == AbilityType.SCOUTING) {
                    stats.incrementScoutingUsed();
                }
            }
        }

        // Process Sabotages
        for (Map.Entry<String, List<String>> entry : sabotageTargets.entrySet()) {
            String targetId = entry.getKey();
            List<String> sabotagers = entry.getValue();

            // Check if target actually voted
            if (matchVotes.containsKey(targetId)) {
                double sabotageDeduction = stagePoints / 2.0;
                UserStats targetStats = db.getOrCreateUserStats(targetId);

                for (String sabotagerId : sabotagers) {
                    // Deduct points from target
                    targetStats.setPoints(targetStats.getPoints() - sabotageDeduction);
                    
                    // Award win to sabotager
                    UserStats sabotagerStats = db.getOrCreateUserStats(sabotagerId);
                    sabotagerStats.incrementSabotageWins();

                    logger.info("User {} successfully sabotaged user {} on match {}. Target lost {} points.", 
                            sabotagerId, targetId, matchId, sabotageDeduction);
                }
            } else {
                logger.info("Sabotage target {} did not vote in match {}. Sabotages by {} wasted.", 
                        targetId, matchId, sabotagers);
            }
        }

        // Persist points
        db.savePoints();
    }

    private int getStagePoints(String stage) {
        if (stage == null) return 1;
        switch (stage.toUpperCase()) {
            case "GROUP_STAGE": return 1;
            case "ROUND_OF_16": return 2;
            case "QUARTER_FINALS": return 3;
            case "SEMI_FINALS": return 4;
            case "FINAL": return 5;
            default: return 1;
        }
    }

    private long calculateNextDelay(List<Match> apiMatches) {
        Instant now = Instant.now();
        boolean hasLive = false;
        boolean hasUpcomingSoon = false;

        for (Match match : apiMatches) {
            String status = match.getStatus();
            if ("LIVE".equalsIgnoreCase(status)) {
                hasLive = true;
                break; // 30 seconds delay takes highest precedence
            }

            if ("SCHEDULED".equalsIgnoreCase(status)) {
                Instant startTime = Instant.parse(match.getStartTime());
                if (startTime.minus(2, ChronoUnit.HOURS).isBefore(now) && startTime.isAfter(now)) {
                    hasUpcomingSoon = true;
                }
            }
        }

        if (hasLive) {
            return 30; // 30 seconds
        } else if (hasUpcomingSoon) {
            return 60; // 1 minute
        } else {
            return 300; // 5 minutes
        }
    }
}
