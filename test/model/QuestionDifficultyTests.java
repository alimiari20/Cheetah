package model;

import java.util.List;

public final class QuestionDifficultyTests {
    public static void main(String[] args) throws Exception {
        var analysis=new QuestionAnalysis();
        var bank=new QuestionRepository().readExcel();
        check(analysis,bank,Difficulty.EASY,"What is the purpose of a functional requirement?",
                List.of("To describe system behavior","To paint a mural","To plant trees","To record a song"));
        check(analysis,bank,Difficulty.MEDIUM,"Compare a functional requirement with a quality constraint. Which rule would you apply to categorize the response time in this example?",
                List.of("Treat it as a performance constraint","Use it as a feature name","Record it as a user role","Treat it as a database key"));
        check(analysis,bank,Difficulty.HARD,"Analyze competing constraints for a functional requirement. Given conflicting evidence, diagnose the failure and justify a fix that preserves the invariants.",
                List.of("Isolate writes and verify consistency during recovery",
                        "Retry writes and verify consistency after recovery",
                        "Resume writes and verify consistency after recovery",
                        "Buffer writes and verify consistency during recovery"));
        try {
            analysis.suggest(null,"Incomplete?",List.of("a","b","","d"),0,bank);
            throw new AssertionError("Missing answers should be rejected");
        } catch(IllegalArgumentException expected) {
            // Incomplete drafts are rejected before model inference.
        }
    }

    private static void check(QuestionAnalysis analysis,List<Question> bank,Difficulty expected,String prompt,
                              List<String> choices) throws Exception {
        var result=analysis.suggest(null,prompt,choices,0,bank);
        System.out.println(result.difficulty()+" — "+prompt);
        if(result.difficulty()!=expected) throw new AssertionError("Expected "+expected+", got "+result);
    }
}
