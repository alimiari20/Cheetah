package model;

import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

public final class QuestionAnalysisTests {
    public static final String EXISTING="What does a functional requirement describe?";
    public static final String PARAPHRASE="What information is specified by a functional requirement?";

    public static void main(String[] args) throws Exception {
        var analysis=new QuestionAnalysis();
        var bank=new QuestionRepository().readExcel();
        check(bank.stream().map(Question::difficulty).distinct().count()==3,"Workbook spans all difficulties");
        var existing=bank.stream().filter(q->q.text().equals(EXISTING)).findFirst().orElseThrow();
        var duplicate=analysis.duplicate(draft(" EXACT: ", "WHAT does a functional requirement describe!!"),bank).orElseThrow();
        check(duplicate.exact(),"Case and punctuation normalize to exact");
        check(analysis.duplicate(draft(existing.id(),EXISTING),List.of(existing)).isEmpty(),"Edit excludes its ID");
        var paraphrase=analysis.duplicate(draft("new",PARAPHRASE),bank).orElseThrow();
        check(!paraphrase.exact() && paraphrase.existing().equals(existing),"Reported Excel paraphrase matches across difficulties");
        verify(analysis,EXISTING,"Define a functional requirement.",true);
        verify(analysis,"What does an operating system manage?","What resources does an operating system control?",true);
        verify(analysis,"What is the purpose of the MVC architecture?","Why do developers use the Model-View-Controller pattern?",true);
        verify(analysis,EXISTING,"How do you bake a sourdough loaf?",false);
        verify(analysis,PARAPHRASE,"What information is specified by a non-functional requirement?",false);
        verify(analysis,EXISTING,"What does a functional requirement not describe?",false);
        System.out.printf(Locale.ROOT,"Workbook questions: %d. Reported pair cosine %.6f; threshold %.3f.%n",
                bank.size(),paraphrase.score(),QuestionAnalysis.DUPLICATE_THRESHOLD);
        String model=System.getProperty("cheetah.questionModel");
        var missing=Files.createTempDirectory("missing-question-model-");
        try {
            System.setProperty("cheetah.questionModel",missing.toString());
            try {
                new QuestionAnalysis().duplicate(draft("new",PARAPHRASE),bank);
                throw new AssertionError("Missing model cannot silently skip the check");
            } catch (java.io.IOException expected) {
                check(expected.getMessage().equals("Offline question analysis is unavailable."),"Visible model error");
            }
        } finally {
            if(model==null)System.clearProperty("cheetah.questionModel");
            else System.setProperty("cheetah.questionModel",model);
            Files.delete(missing);
        }
    }

    static void verify(QuestionAnalysis model,String original,String entered,boolean expected) throws Exception {
        check(model.duplicate(draft("a",entered),List.of(draft("b",original))).isPresent()==expected,
                "Unexpected comparison for "+original+" | "+entered);
    }

    private static Question draft(String id,String prompt) {
        return new Question(id,Difficulty.HARD,"Test",prompt,List.of("a","b","c","d"),0,"Explanation.");
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
