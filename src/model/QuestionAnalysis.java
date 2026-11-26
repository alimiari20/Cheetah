package model;

import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Compares actual question prompts. The engine is initialized only for semantic work. */
public final class QuestionAnalysis {
    public static final double DUPLICATE_THRESHOLD = .83;
    private static final Pattern COMPOUND = Pattern.compile("\\b\\p{L}{2,}(?:[-–]\\p{L}{2,}){1,5}\\b");
    private static final Pattern NEGATION = Pattern.compile(
            "(?iu)(?<![\\p{L}])(?:not|never|without|avoid|non[-–]|cannot|לא|בלי|אינו|אינה)(?![\\p{L}])");
    public record Match(Question existing,double score,boolean exact) {}
    public record DifficultySuggestion(Difficulty difficulty,boolean closeChoices) {}
    private static final List<List<String>> RUBRIC=List.of(
            List.of("What does this term mean? Recall the definition.",
                    "What is the purpose of this component? Identify its basic function.",
                    "Which term names the concept described here?",
                    "Recall a known fact or identify a standard example."),
            List.of("Compare the two concepts and explain how they differ.",
                    "Given a simple example, apply the appropriate concept to this situation.",
                    "Interpret this scenario and choose the appropriate method.",
                    "Explain why the expected result occurs when the rule is applied."),
            List.of("Analyze competing constraints and trade-offs and justify the best solution.",
                    "Diagnose a failure from conflicting evidence and select a fix that preserves the invariants.",
                    "Reason through multiple interacting steps to predict the result and explain it.",
                    "Evaluate alternative designs under several constraints and explain the consequences."));
    private QuestionEmbeddings embeddings;
    private final Map<String,float[]> cache = new LinkedHashMap<>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,float[]> entry) { return size()>512; }
    };

    public synchronized Optional<Match> duplicate(Question draft,List<Question> bank) throws IOException {
        String normalized = normalize(draft.text());
        List<Question> candidates=bank.stream().filter(q->!q.id().equals(draft.id())).toList();
        for (Question q:candidates) {
            if (normalize(q.text()).equals(normalized)) return Optional.of(new Match(q,1,true));
        }
        if (candidates.isEmpty()) return Optional.empty();
        vector(draft.text());
        Match best=null;
        for (Question q:candidates) {
            if (negative(draft.text())!=negative(q.text())) continue;
            double score=similarity(draft.text(),q.text());
            if (score>=DUPLICATE_THRESHOLD && (best==null || score>best.score())) best=new Match(q,score,false);
        }
        return Optional.ofNullable(best);
    }

    public synchronized double similarity(String a,String b) throws IOException {
        return cosine(vector(expand(a,b)),vector(expand(b,a)));
    }

    public synchronized DifficultySuggestion suggest(String editingId,String prompt,List<String> choices,
                                                       int correctIndex,List<Question> bank) throws IOException {
        if(prompt.isBlank()||choices.size()!=4||choices.stream().anyMatch(String::isBlank)
                ||correctIndex<0||correctIndex>=choices.size()) {
            throw new IllegalArgumentException("Enter a question, four answers, and select the correct answer.");
        }
        float[] question=vector(prompt);
        float[] context=vector(prompt+"\n"+choices.get(correctIndex));
        float[] correct=vector(choices.get(correctIndex));
        double closeness=0;
        for(int i=0;i<choices.size();i++) {
            if(i!=correctIndex) closeness+=Math.max(0,cosine(correct,vector(choices.get(i))))/3;
        }
        var neighbors=new EnumMap<Difficulty,List<Double>>(Difficulty.class);
        for(Difficulty level:Difficulty.values()) neighbors.put(level,new ArrayList<>());
        for(Question example:bank) {
            if(example.id().equals(editingId)) continue;
            double similarity=cosine(question,vector(example.text()));
            neighbors.get(example.difficulty()).add(similarity);
        }
        Difficulty best=Difficulty.EASY;
        double bestScore=Double.NEGATIVE_INFINITY;
        for(Difficulty level:Difficulty.values()) {
            var anchors=new ArrayList<Double>();
            for(String anchor:RUBRIC.get(level.ordinal())) {
                float[] embedding=vector(anchor);
                anchors.add(.85*cosine(question,embedding)+.15*cosine(context,embedding));
            }
            double evidence=.78*strongest(anchors,2)+.22*strongest(neighbors.get(level),3)
                    +.04*(level.ordinal()-1)*(closeness-.35);
            if(evidence>bestScore) {
                bestScore=evidence;
                best=level;
            }
        }
        return new DifficultySuggestion(best,closeness>=.55);
    }

    private static double strongest(List<Double> similarities,int count) {
        return similarities.stream().sorted(Comparator.reverseOrder()).limit(count)
                .mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static boolean negative(String text) {
        return NEGATION.matcher(text.replaceAll("(?iu)\\bnon[-–](?=\\p{L})"," not ")).find();
    }

    private static String expand(String text,String reference) {
        var compounds=COMPOUND.matcher(reference);
        while(compounds.find()) {
            String compound=compounds.group();
            StringBuilder initials=new StringBuilder();
            for(String word:compound.split("[-–]")) initials.append(Character.toUpperCase(word.charAt(0)));
            text=text.replaceAll("\\b"+Pattern.quote(initials.toString())+"\\b",
                    java.util.regex.Matcher.quoteReplacement(compound));
        }
        return text;
    }

    private float[] vector(String text) throws IOException {
        String key=Normalizer.normalize(text.strip(),Normalizer.Form.NFKC);
        float[] vector=cache.get(key);
        if(vector!=null)return vector;
        try {
            if(embeddings==null)embeddings=new QuestionEmbeddings();
            vector=embeddings.embed(key);
            cache.put(key,vector);
            return vector;
        } catch(Exception | LinkageError ex) {
            throw new IOException("Offline question analysis is unavailable.",ex);
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\p{M}]+"," ").strip();
    }

    private static double cosine(float[] a,float[] b) {
        double sum=0;
        for(int i=0;i<a.length;i++)sum+=(double)a[i]*b[i];
        return Math.max(-1,Math.min(1,sum));
    }
}
