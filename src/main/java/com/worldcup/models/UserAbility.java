package com.worldcup.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserAbility {
    private AbilityType abilityType;
    private String targetUserId; // Nullable, used for SABOTAGE and SCOUTING

    public UserAbility() {}

    public UserAbility(AbilityType abilityType, String targetUserId) {
        this.abilityType = abilityType;
        this.targetUserId = targetUserId;
    }

    public AbilityType getAbilityType() { return abilityType; }
    public void setAbilityType(AbilityType abilityType) { this.abilityType = abilityType; }

    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }
}
