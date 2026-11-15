package model;

import java.util.*;

public record Question(String id, Difficulty difficulty, String category, String text,
                       List<String> choices, int correctIndex, String explanation) {
    public Question {
        choices = List.copyOf(choices);
        if (correctIndex < 0 || correctIndex >= choices.size()) throw new IllegalArgumentException("Bad answer index");
    }
    public int movementReward() { return switch (difficulty) { case EASY -> 1; case MEDIUM -> 2; case HARD -> 3; }; }
}
