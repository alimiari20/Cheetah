package view;

import controller.GameController;
import model.*;
import security.AdminSession;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Drives the actual Swing buttons, controller authorization, analysis worker and repository. */
public final class QuestionEditorWorkflowTests {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("question-editor-");
        String home=System.getProperty("user.home");
        String cache=System.getProperty("DJL_CACHE_DIR");
        if(cache==null) System.setProperty("DJL_CACHE_DIR",Path.of(home,".djl.ai").toString());
        System.setProperty("user.home",root.toString());
        try {
            SwingUtilities.invokeAndWait(()->{
                Theme.install();
                Messages.apply(false);
                var frame=new GameFrame();
                try { verify(root,frame); }
                catch(Exception e){throw new AssertionError(e);}
                finally {frame.dispose();}
            });
        } finally {
            System.setProperty("user.home",home);
            if(cache==null) System.clearProperty("DJL_CACHE_DIR");
            try(var paths=Files.walk(root)){
                for(var file:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(file);
            }
        }
        System.out.println("Save Question workflow passed.");
    }

    private static void verify(Path root,GameFrame frame)throws Exception {
        var controller=new GameController(frame,new GameEngine(new BoardRepository()),new HistoryRepository(),new Random(1));
        ((AdminSession)get(controller,"adminSession")).signIn(true,()->true);
        frame.addNotify();
        frame.setAdminMode(true);
        controller.questionAdmin();
        var page=(QuestionAdminPage)get(frame,"adminPage");
        var prompt=(JTextArea)get(page,"prompt");
        ((JTextArea)get(page,"explanation")).setText("A fixture explanation.");
        var choices=(JTextField[])get(page,"options");
        for(int i=0;i<4;i++)choices[i].setText("Option "+i);
        Path local=root.resolve(".cheetah-ludo/questions.tsv");
        JButton save=(JButton)get(page,"save");
        prompt.setText(QuestionAnalysisTests.EXISTING.toUpperCase(java.util.Locale.ROOT));
        clickAndObserve(frame,save,"Duplicate question review",dialog->{
            check(button(dialog,"SAVE AS INTENTIONALLY DIFFERENT")==null,"Exact duplicates cannot be saved");
            button(dialog,"EDIT QUESTION").doClick(0);
        });
        check(!Files.exists(local),"Exact duplicate did not write");
        prompt.setText(QuestionAnalysisTests.PARAPHRASE);
        clickAndObserve(frame,save,"Duplicate question review",dialog->{
            check(find(dialog,JTextArea.class).stream().anyMatch(a->a.getText().equals(QuestionAnalysisTests.EXISTING)),
                    "Actual Excel prompt in warning");
            check(find(dialog,JTextArea.class).stream().anyMatch(a->a.getText().equals(QuestionAnalysisTests.PARAPHRASE)),
                    "Entered prompt in warning");
            check(find(dialog,JLabel.class).stream().anyMatch(a->a.getText().contains("0.877")
                    && a.getText().contains("Existing difficulty:")),"Measured score and source difficulty appear");
            button(dialog,"CANCEL").doClick(0);
        });
        check(!Files.exists(local),"Cancel prevents a write");
        JButton suggest=(JButton)get(page,"suggest");
        var difficulty=(JComboBox<?>)get(page,"difficulty");
        choices[0].setText("");
        clickAndObserve(frame,suggest,"CHEETAH LUDO  •  ERROR",JDialog::dispose);
        choices[0].setText("To identify the expected system behavior");
        choices[1].setText("To tune the radio");
        choices[2].setText("To plant flowers");
        choices[3].setText("To bake a cake");
        difficulty.setSelectedItem(Difficulty.HARD);
        clickAndObserve(frame,suggest,"Difficulty recommendation",dialog->
                button(dialog,"USE RECOMMENDATION").doClick(0));
        check(difficulty.getSelectedItem()==Difficulty.EASY,"Accept changes only the selected difficulty");
        check(!Files.exists(local),"Recommendation does not save");
        difficulty.setSelectedItem(Difficulty.HARD);
        clickAndObserve(frame,save,"Duplicate question review",dialog->
                button(dialog,"SAVE AS INTENTIONALLY DIFFERENT").doClick(0));
        var edited=new QuestionRepository().list().stream().filter(q->q.text().equals(QuestionAnalysisTests.PARAPHRASE)).findFirst().orElseThrow();
        check(edited.difficulty()==Difficulty.HARD,"Explicit save preserves the administrator's override");
        var list=(JList<?>)get(page,"list");
        list.setSelectedValue(edited,true);
        clickAndObserve(frame,suggest,"Difficulty recommendation",dialog->
                button(dialog,"CANCEL").doClick(0));
        check(difficulty.getSelectedItem()==Difficulty.HARD,"Dismissing a suggestion leaves the edited difficulty");
        check(new QuestionRepository().list().contains(edited),"Analyzing an edit does not change persisted data");
        try {
            for(boolean dark:new boolean[]{true,false}) {
                for(boolean hebrew:new boolean[]{false,true}) {
                    Theme.applyAppearance(dark);
                    Messages.apply(hebrew);
                    check(suggest.getText().equals(Messages.get("AI Suggest Difficulty")),"Suggestion button localized");
                    clickAndObserve(frame,suggest,Messages.get("Difficulty recommendation"),dialog->{
                        check(dialog.getContentPane().getComponentOrientation().isLeftToRight()!=hebrew,
                                "Recommendation direction matches language");
                        button(dialog,Messages.get("CANCEL")).doClick(0);
                    });
                }
            }
        } finally {
            Messages.apply(false);
        }
        prompt.setText("How should a gardener prune dormant rose bushes in winter?");
        clickAndObserve(frame,save,"Question bank",JDialog::dispose);
        check(new QuestionRepository().list().stream().anyMatch(q->q.id().equals(edited.id())
                && q.text().equals(prompt.getText())),"Unrelated prompt saves through the edit workflow");
        var repository=new QuestionRepository();
        repository.saveAll(repository.list().stream().filter(q->!q.text().equals(QuestionAnalysisTests.EXISTING)).toList());
        controller.questionAdmin();
        ((JButton)get(page,"add")).doClick(0);
        prompt.setText(QuestionAnalysisTests.PARAPHRASE);
        ((JTextArea)get(page,"explanation")).setText("An explanation for this draft.");
        for(int i=0;i<4;i++) choices[i].setText("Option "+i);
        clickAndObserve(frame,save,"Duplicate question review",dialog->{
            check(find(dialog,JTextArea.class).stream().anyMatch(a->a.getText().equals(QuestionAnalysisTests.EXISTING)),
                    "Original Excel question still participates after local deletion");
            button(dialog,"CANCEL").doClick(0);
        });
    }

    static void clickAndObserve(GameFrame frame,JButton trigger,String title,Consumer<JDialog> inspect) {
        var loop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        var failure=new AtomicReference<Throwable>();
        var complete=new AtomicBoolean();
        long limit=System.nanoTime()+25_000_000_000L;
        var timer=new Timer(15,event->{
            for(Window window:frame.getOwnedWindows()) {
                if(!(window instanceof JDialog dialog)||!dialog.isShowing())continue;
                try {
                    if (!complete.get() && title.equals(dialog.getTitle())) {
                        inspect.accept(dialog);
                        complete.set(true);
                        return;
                    }
                    if (!complete.get())throw new AssertionError("Unexpected dialog " + dialog.getTitle()+" "+find(dialog,JTextArea.class).stream().map(JTextArea::getText).toList());
                    dialog.dispose();
                } catch(Throwable error){failure.set(error);dialog.dispose();loop.exit();return;}
            }
            if(complete.get()&&trigger.isEnabled())loop.exit();
            if(System.nanoTime()>=limit){
                failure.set(new AssertionError("Timed out waiting for " + title));
                for(Window window:frame.getOwnedWindows())window.dispose();
                loop.exit();
            }
        });
        timer.start();
        trigger.doClick(0);
        loop.enter();
        timer.stop();
        if(failure.get()!=null)throw new AssertionError(failure.get());
        check(complete.get(),"Expected dialog appeared");
    }
    static JButton button(Container container,String title) {
        return find(container,JButton.class).stream().filter(b->title.equals(b.getText())).findFirst().orElse(null);
    }
    static <T> List<T> find(Container root,Class<T> type) {
        List<T> result=new ArrayList<>();
        for(Component c:root.getComponents()){
            if(type.isInstance(c))result.add(type.cast(c));
            if(c instanceof Container child)result.addAll(find(child,type));
        }
        return result;
    }
    static Object get(Object target,String name)throws Exception {
        Field field=target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
