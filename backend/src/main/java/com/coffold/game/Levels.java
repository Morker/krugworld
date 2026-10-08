package com.coffold.game;

import java.util.List;

/**
 * Level n needs 100 × (n − 1) more XP than level n − 1: 0, 100, 300, 600,
 * 1000, 1500, ... Early levels come quickly; later ones take real habits.
 */
public final class Levels {

    private static final List<String> TITLES = List.of(
            "Peasant", "Copper Counter", "Apprentice Merchant", "Merchant", "Thrifty Merchant",
            "Guild Treasurer", "Master of Coin", "Keeper of the Coffers", "Lord of Gold");

    private Levels() {
    }

    /** Total XP needed to reach {@code level}. */
    public static int threshold(int level) {
        return 50 * level * (level - 1);
    }

    public static int levelFor(int xp) {
        int level = 1;
        while (threshold(level + 1) <= xp) {
            level++;
        }
        return level;
    }

    public static String title(int level) {
        return TITLES.get(Math.min(level, TITLES.size()) - 1);
    }
}
