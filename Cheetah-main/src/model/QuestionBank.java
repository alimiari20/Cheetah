package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Question rotation: every question is used once per difficulty before repeating. */
public final class QuestionBank {
    private final Map<Difficulty, List<Question>> bank = new EnumMap<>(Difficulty.class);
    private final Map<Difficulty, List<Question>> remaining = new EnumMap<>(Difficulty.class);
    private final Random random;

    public QuestionBank(Random random) {
        this.random = Objects.requireNonNull(random);
        for (Difficulty d : Difficulty.values()) bank.put(d, new ArrayList<>());
        load();
        for (Difficulty d : Difficulty.values()) {
            if (bank.get(d).isEmpty()) throw new IllegalStateException("Missing questions for " + d);
            remaining.put(d, new ArrayList<>(bank.get(d)));
        }
    }

    /** Alternative bank from the editable local question repository. */
    public QuestionBank(Random random, List<Question> questions) {
        this.random = Objects.requireNonNull(random);
        for (Difficulty d : Difficulty.values()) bank.put(d, new ArrayList<>());
        for (Question question : questions) bank.get(question.difficulty()).add(question);
        for (Difficulty d : Difficulty.values()) {
            if (bank.get(d).isEmpty()) throw new IllegalStateException("Missing questions for " + d);
            remaining.put(d, new ArrayList<>(bank.get(d)));
        }
    }

    public List<Question> all() {
        List<Question> result = new ArrayList<>();
        for (Difficulty difficulty : Difficulty.values()) result.addAll(bank.get(difficulty));
        return List.copyOf(result);
    }

    public Question next(Difficulty difficulty) {
        List<Question> available = remaining.get(difficulty);
        if (available.isEmpty()) available.addAll(bank.get(difficulty));
        return available.remove(random.nextInt(available.size()));
    }
    public int total() { return bank.values().stream().mapToInt(List::size).sum(); }

    private void load() {
        String resource = "/com/cheetahludo/model/data/questions.tsv";
        InputStream input = QuestionBank.class.getResourceAsStream(resource);
        if (input == null) throw new IllegalStateException("Missing resource " + resource);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] f = line.split("\t", -1);
                if (!"QUESTION".equals(f[0]) || f.length != 11) throw new IOException("Invalid question row");
                Difficulty difficulty = Difficulty.valueOf(f[2].toUpperCase(Locale.ROOT));
                List<String> choices = new ArrayList<>();
                for (int i = 7; i < 11; i++) choices.add(decode(f[i]));
                bank.get(difficulty).add(new Question(f[1], difficulty, decode(f[4]), decode(f[5]), choices,
                    Integer.parseInt(f[3]), decode(f[6])));
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Could not load questions", ex);
        }
    }
    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
