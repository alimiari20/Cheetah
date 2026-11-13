package model;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free regression tests for editable bank and structured history. */
public final class RepositoryTests {
    private static int count;
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("cheetah-repository-tests-");
        try{
            QuestionRepository repo=new QuestionRepository(dir.resolve("questions.tsv"));
            List<Question> baseline=repo.list();
            yes(baseline.size()==32,"bundled questions available");
            List<Question> added=new ArrayList<>(baseline);
            Question bonus=new Question("my-new-question",Difficulty.HARD,"Software Engineering",
                "What does MVC mean?",List.of("Model View Controller","Move Value Copy","Main View Code","None"),0,"Separate game rules from GUI events.");
            added.add(bonus);repo.saveAll(added);
            yes(repo.list().size()==33,"new question persists after reload");
            Question unicode=new Question("my-new-question",Difficulty.HARD,"Software Engineering",
                "מהו MVC? ما هو؟",List.of("Model View Controller","Test 🏆","Text, with comma","Option D"),0,"Unicode and punctuation roundtrip");
            added.set(added.size()-1,unicode);repo.saveAll(added);
            yes(repo.list().get(32).text().equals(unicode.text()),"Unicode editing preserved");
            yes(repo.list().get(32).choices().get(2).equals("Text, with comma"),"punctuation preserved");
            try{repo.saveAll(added.subList(0,29));throw new AssertionError("minimum 30 rule not enforced");}
            catch(IllegalArgumentException expected){count++;System.out.println(" PASS minimum 30 questions enforced");}
            yes(repo.list().size()==33,"rejected update leaves saved bank intact");
            yes(new QuestionBank(new Random(7),repo.list()).total()==33,"game engine can load edited bank");
            Path history=dir.resolve("history.csv");
            Files.writeString(history,"date,difficulty,players,winner,ranking,duration_seconds\n"+
                "\"2026-11-09T13:00:00Z\",\"EASY\",2,\"Al, Lee\",\"Al, Lee > Ben\",95\n",StandardCharsets.UTF_8);
            var records=new HistoryRepository(history).readEntries();
            yes(records.size()==1 && records.get(0).winner().equals("Al, Lee"),"quoted CSV and commas parsed");
            yes(records.get(0).seconds()==95,"history duration parsed");
            System.out.println("Repository tests: "+count+" PASSED, 0 FAILED");
        }finally{
            try(var walk=Files.walk(dir)){walk.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}
        }
    }
    private static void yes(boolean condition,String name){
        if(!condition)throw new AssertionError(name);count++;System.out.println(" PASS "+name);
    }
}
