package com.worldcup.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BotConfig {
    private String discordToken;
    private String apiToken;
    private String apiUrl;
    private String channelId;
    private String mentionRoleId;
    private String ownerId;

    public BotConfig() {}

    public String getDiscordToken() { return discordToken; }
    public void setDiscordToken(String discordToken) { this.discordToken = discordToken; }

    public String getApiToken() { return apiToken; }
    public void setApiToken(String apiToken) { this.apiToken = apiToken; }

    public String getApiUrl() { return apiUrl; }
    public void setApiUrl(String apiUrl) { this.apiUrl = apiUrl; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getMentionRoleId() { return mentionRoleId; }
    public void setMentionRoleId(String mentionRoleId) { this.mentionRoleId = mentionRoleId; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
