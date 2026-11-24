package model;

import java.util.Random;

public enum SurpriseOutcome {
    DIRECT_FINISH, OPPONENT_BACKWARD, RETURN_TO_CASTLE,
    BACKWARD_THREE, LOSE_NEXT_TURN, ULTRABOOST;

    /** SRS probabilities: 10%, 10%, 10%, 30%, 30%, 10%. */
    public static SurpriseOutcome draw(Random random) {
        int r = random.nextInt(100);
        if (r < 10) return DIRECT_FINISH;
        if (r < 20) return OPPONENT_BACKWARD;
        if (r < 30) return RETURN_TO_CASTLE;
        if (r < 60) return BACKWARD_THREE;
        if (r < 90) return LOSE_NEXT_TURN;
        return ULTRABOOST;
    }
}
