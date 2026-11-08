package com.jason.plugins.antibadwords.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Plain-data mirror of {@code antibadwords.json}. Gson fills the fields by reflection, so the names
 * (and the {@code @SerializedName} spellings) must match the JSON keys.
 */
public final class ModConfig {
    @com.google.gson.annotations.SerializedName("protection-level")
    public int protectionLevel = 3;
    @com.google.gson.annotations.SerializedName("enabled-word-lists")
    public List<String> enabledWordLists = new ArrayList<>();
    public Matching matching = new Matching();
    public List<String> whitelist = new ArrayList<>();
    public Scan scan = new Scan();
    public Messages messages = new Messages();
    public Punishment punishment = new Punishment();
    @com.google.gson.annotations.SerializedName("custom-words")
    public List<String> customWords = new ArrayList<>();
    @com.google.gson.annotations.SerializedName("word-lists")
    public Map<String, Map<String, List<String>>> wordLists = new java.util.LinkedHashMap<>();

    public static final class Matching {
        @com.google.gson.annotations.SerializedName("collapse-repeated-letters")
        public boolean collapseRepeatedLetters = true;
        @com.google.gson.annotations.SerializedName("repeated-letter-threshold")
        public int repeatedLetterThreshold = 3;
        @com.google.gson.annotations.SerializedName("detect-spaced-letters")
        public boolean detectSpacedLetters = true;
        @com.google.gson.annotations.SerializedName("join-adjacent-words")
        public boolean joinAdjacentWords = false;
        @com.google.gson.annotations.SerializedName("partial-match")
        public boolean partialMatch = false;
        @com.google.gson.annotations.SerializedName("partial-min-length")
        public int partialMinLength = 4;
    }

    public static final class Scan {
        public boolean chat = true;
        public boolean commands = true;
    }

    public static final class Messages {
        @com.google.gson.annotations.SerializedName("broadcast-warnings")
        public boolean broadcastWarnings = true;
        @com.google.gson.annotations.SerializedName("warning-broadcast-format")
        public String warningBroadcastFormat = "&C{PLAYER} SAID THE WORD &F{WORD}&C WHICH IS NOT ALLOWED.";
        @com.google.gson.annotations.SerializedName("warning-player")
        public String warningPlayer = "&EWATCH YOUR WORDS. YOU ARE PUNISHED IF YOU SAY &F{REMAINING}&E BADWORDS AGAIN.";
        @com.google.gson.annotations.SerializedName("punishment-broadcast-format")
        public String punishmentBroadcastFormat = "&C{PLAYER} WAS PUNISHED FOR SAYING &F{WORD}&C.";
        @com.google.gson.annotations.SerializedName("punishment-player")
        public String punishmentPlayer = "&CFOR YOUR BADWORDS, YOU ARE PUNISHED.";
        @com.google.gson.annotations.SerializedName("punishment-disabled-player")
        public String punishmentDisabledPlayer = "&EYOUR MESSAGE WAS BLOCKED. PUNISHMENT IS CURRENTLY DISABLED.";
        @com.google.gson.annotations.SerializedName("notify-staff")
        public boolean notifyStaff = true;
        @com.google.gson.annotations.SerializedName("staff-notify-format")
        public String staffNotifyFormat = "&8[&cAntiBadWords&8] &7Blocked '&f{word}&7' from &f{player}&7 in &f{source}&7.";
    }

    public static final class Punishment {
        public boolean enabled = true;
        @com.google.gson.annotations.SerializedName("violations-before-punish")
        public int violationsBeforePunish = 2;
        @com.google.gson.annotations.SerializedName("clear-violations-on-quit")
        public boolean clearViolationsOnQuit = true;
        @com.google.gson.annotations.SerializedName("final-command-enabled")
        public boolean finalCommandEnabled = false;
        @com.google.gson.annotations.SerializedName("final-command")
        public String finalCommand = "";
        /** Server commands run (as the console) when a player is punished; {player} is replaced. */
        public List<Action> commands = new ArrayList<>();
    }

    public static final class Action {
        public String id = "";
        public boolean enabled = false;
        public String command = "";
    }
}
