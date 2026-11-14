package model;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Local question editing. Bundled 32 questions remain an immutable fallback. */
public final class QuestionRepository {
    public static final int MINIMUM_QUESTIONS=30;
    private final Path file;
    public QuestionRepository(){this(Path.of(System.getProperty("user.home"),".cheetah-ludo","questions.tsv"));}
    public QuestionRepository(Path file){this.file=Objects.requireNonNull(file);}
    public List<Question> list() throws IOException {
        if(!Files.exists(file))return new QuestionBank(new Random(1)).all();
        List<Question> result=new ArrayList<>();
        try(BufferedReader r=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            String line;
            while((line=r.readLine())!=null){
                if(line.isBlank()||line.startsWith("#"))continue;
                String[] f=line.split("\t",-1);
                if(f.length!=11||!f[0].equals("QUESTION"))throw new IOException("Malformed question record");
                List<String> choices=new ArrayList<>();for(int i=7;i<11;i++)choices.add(dec(f[i]));
                result.add(new Question(f[1],Difficulty.valueOf(f[2].toUpperCase(Locale.ROOT)),dec(f[4]),dec(f[5]),
                     choices,Integer.parseInt(f[3]),dec(f[6])));
            }
        }catch(IllegalArgumentException e){throw new IOException("Invalid question record: "+e.getMessage(),e);}
        validate(result);return List.copyOf(result);
    }
    public void saveAll(List<Question> questions) throws IOException {
        validate(questions);
        Path parent=file.toAbsolutePath().getParent();if(parent!=null)Files.createDirectories(parent);
        Path temp=Files.createTempFile(parent,"cheetah-questions-",".tmp");
        try{
            StringBuilder lines=new StringBuilder("# Cheetah Ludo locally edited questions\n");
            for(Question q:questions){
                lines.append("QUESTION\t").append(q.id()).append('\t')
                    .append(q.difficulty().name().toLowerCase(Locale.ROOT)).append('\t')
                    .append(q.correctIndex()).append('\t').append(enc(q.category())).append('\t')
                    .append(enc(q.text())).append('\t').append(enc(q.explanation()));
                for(String answer:q.choices())lines.append('\t').append(enc(answer));
                lines.append('\n');
            }
            Files.writeString(temp,lines,StandardCharsets.UTF_8);
            try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ex){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private static void validate(List<Question> questions){
        if(questions.size()<MINIMUM_QUESTIONS)throw new IllegalArgumentException("Question bank must contain at least 30 questions.");
        EnumMap<Difficulty,Integer> counts=new EnumMap<>(Difficulty.class);
        Set<String> ids=new HashSet<>();
        for(Question q:questions){
            if(q.id().isBlank()||q.id().contains("\t")||!ids.add(q.id()))throw new IllegalArgumentException("Question ID is missing or duplicated.");
            if(q.category().isBlank()||q.text().isBlank()||q.explanation().isBlank()||q.choices().size()!=4 ||
                  q.choices().stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Every question needs a category, prompt, explanation and four answers.");
            counts.merge(q.difficulty(),1,Integer::sum);
        }
        for(Difficulty diff:Difficulty.values()) if(counts.getOrDefault(diff,0)==0)
            throw new IllegalArgumentException("At least one question is required for "+diff.name()+".");
    }
    private static String enc(String v){return Base64.getEncoder().encodeToString(v.getBytes(StandardCharsets.UTF_8));}
    private static String dec(String v){return new String(Base64.getDecoder().decode(v),StandardCharsets.UTF_8);}
}
