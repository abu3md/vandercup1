package com.worldcup.database;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.worldcup.models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static DatabaseManager instance;

    private final ObjectMapper objectMapper;
    
    // Data files
    private final File pointsFile = new File("points.json");
    private final File votesFile = new File("votes.json");
    private final File abilitiesFile = new File("abilities.json");
    private final File matchesFile = new File("matches.json");

    // In-memory caches (synchronized via ConcurrentHashMap)
    private final Map<String, UserStats> points = new ConcurrentHashMap<>();
    private final Map<String, Map<String, VoteType>> votes = new ConcurrentHashMap<>();
    private final Map<String, Map<String, UserAbility>> abilities = new ConcurrentHashMap<>();
    private final Map<String, Match> matches = new ConcurrentHashMap<>();

    private DatabaseManager() {
        this.objectMapper = new ObjectMapper();
        loadAllData();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    // --- Loading Data ---
    private void loadAllData() {
        try {
            if (pointsFile.exists()) {
                Map<String, UserStats> loaded = objectMapper.readValue(pointsFile, new TypeReference<Map<String, UserStats>>() {});
                points.putAll(loaded);
            }
            logger.info("Loaded {} user stats profiles.", points.size());
        } catch (IOException e) {
            logger.error("Failed to load points.json", e);
        }

        try {
            if (votesFile.exists()) {
                Map<String, Map<String, VoteType>> loaded = objectMapper.readValue(votesFile, new TypeReference<Map<String, Map<String, VoteType>>>() {});
                votes.putAll(loaded);
            }
            logger.info("Loaded votes for {} matches.", votes.size());
        } catch (IOException e) {
            logger.error("Failed to load votes.json", e);
        }

        try {
            if (abilitiesFile.exists()) {
                Map<String, Map<String, UserAbility>> loaded = objectMapper.readValue(abilitiesFile, new TypeReference<Map<String, Map<String, UserAbility>>>() {});
                abilities.putAll(loaded);
            }
            logger.info("Loaded abilities for {} matches.", abilities.size());
        } catch (IOException e) {
            logger.error("Failed to load abilities.json", e);
        }

        try {
            if (matchesFile.exists()) {
                Map<String, Match> loaded = objectMapper.readValue(matchesFile, new TypeReference<Map<String, Match>>() {});
                matches.putAll(loaded);
            }
            logger.info("Loaded {} matches from database.", matches.size());
        } catch (IOException e) {
            logger.error("Failed to load matches.json", e);
        }
    }

    // --- Atomic Disk Persistence ---
    private synchronized <T> void saveToFile(File file, T data) {
        Path filePath = file.toPath();
        Path tmpPath = Path.of(file.getAbsolutePath() + ".tmp");
        try {
            // Ensure parent directories exist
            if (file.getParentFile() != null) {
                Files.createDirectories(file.getParentFile().toPath());
            }
            
            // Write to tmp file
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tmpPath.toFile(), data);
            
            // Atomically replace target file
            try {
                Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                // Fallback for filesystems that do not support ATOMIC_MOVE (e.g. some virtual folders)
                Files.move(tmpPath, filePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.error("Error atomically saving to file " + file.getName(), e);
        }
    }

    // --- Public Mutators & Accessors ---

    // Points / Stats
    public UserStats getOrCreateUserStats(String userId) {
        return points.computeIfAbsent(userId, UserStats::new);
    }

    public synchronized void savePoints() {
        saveToFile(pointsFile, points);
    }

    public synchronized void addPoints(String userId, double amount) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.setPoints(stats.getPoints() + amount);
        savePoints();
    }

    public synchronized void deductPoints(String userId, double amount) {
        UserStats stats = getOrCreateUserStats(userId);
        stats.setPoints(stats.getPoints() - amount);
        savePoints();
    }

    public List<UserStats> getTop10Users() {
        return points.values().stream()
                .sorted(Comparator.comparingDouble(UserStats::getPoints).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    // Votes
    public Map<String, VoteType> getVotesForMatch(String matchId) {
        return votes.getOrDefault(matchId, Collections.emptyMap());
    }

    public synchronized void setVote(String matchId, String userId, VoteType voteType) {
        votes.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>()).put(userId, voteType);
        saveToFile(votesFile, votes);
    }

    // Abilities
    public Map<String, UserAbility> getAbilitiesForMatch(String matchId) {
        return abilities.getOrDefault(matchId, Collections.emptyMap());
    }

    public synchronized void setAbility(String matchId, String userId, UserAbility ability) {
        abilities.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>()).put(userId, ability);
        saveToFile(abilitiesFile, abilities);
    }

    // Matches
    public Match getMatch(String matchId) {
        return matches.get(matchId);
    }

    public Collection<Match> getAllMatches() {
        return matches.values();
    }

    public synchronized void putMatch(Match match) {
        matches.put(match.getId(), match);
        saveToFile(matchesFile, matches);
    }

    public synchronized void saveMatches() {
        saveToFile(matchesFile, matches);
    }

    // Clear match votes/abilities in case of reschedule/cancellation to allow clean revote
    public synchronized void clearMatchPredictions(String matchId) {
        votes.remove(matchId);
        abilities.remove(matchId);
        saveToFile(votesFile, votes);
        saveToFile(abilitiesFile, abilities);
    }

    // --- Backup Service ---
    public synchronized void performBackup() {
        backupFile(pointsFile, new File("points_backup.json"));
        backupFile(votesFile, new File("votes_backup.json"));
        backupFile(abilitiesFile, new File("abilities_backup.json"));
        backupFile(matchesFile, new File("matches_backup.json"));
        logger.info("Hourly backup completed successfully.");
    }

    private void backupFile(File source, File dest) {
        if (!source.exists()) return;
        try {
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.error("Failed to backup file: " + source.getName(), e);
        }
    }
}
