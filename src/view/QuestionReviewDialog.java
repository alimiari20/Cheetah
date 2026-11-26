package view;

import model.Question;
import model.QuestionAnalysis;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Locale;

import static view.Theme.Tone.*;

/** Keeps the submitted and existing prompts visible during an explicit save decision. */
public final class QuestionReviewDialog {
    private QuestionReviewDialog() {}

    public static boolean confirmDifficulty(JFrame owner,QuestionAnalysis.DifficultySuggestion suggestion) {
        JDialog dialog=new JDialog(owner,"Difficulty recommendation",true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root=new UiKit.GradientPage(new BorderLayout(0,12));
        root.setBorder(new EmptyBorder(18,20,18,20));
        JPanel info=UiKit.transparent(new GridLayout(0,1,0,5));
        info.add(UiKit.title("Difficulty recommendation",22));
        info.add(UiKit.title(suggestion.difficulty().name(),24));
        info.add(UiKit.sub(switch(suggestion.difficulty()) {
            case EASY -> "The prompt resembles basic recall and definition questions.";
            case MEDIUM -> "The prompt resembles interpretation, comparison, or application questions.";
            case HARD -> "The prompt resembles analysis or multi-step reasoning questions.";
        }));
        if(suggestion.closeChoices()) info.add(UiKit.sub("The answer choices are similar in meaning."));
        root.add(info,BorderLayout.CENTER);
        boolean[] accepted={false};
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.TRAILING,8,0));
        actions.add(MenuStyle.button("CANCEL",false,dialog::dispose));
        actions.add(MenuStyle.button("USE RECOMMENDATION",true,()->{
            accepted[0]=true;
            dialog.dispose();
        }));
        root.add(actions,BorderLayout.SOUTH);
        GlobalHeader.install(dialog,root);
        dialog.pack();
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return accepted[0];
    }

    public static boolean confirmDuplicate(JFrame owner,Question draft,QuestionAnalysis.Match match) {
        JDialog dialog = new JDialog(owner, "Duplicate question review", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel root = new UiKit.GradientPage(new BorderLayout(0,12));
        root.setBorder(new EmptyBorder(18,20,18,20));
        JPanel heading=UiKit.transparent(new GridLayout(0,1,0,5));
        heading.add(UiKit.title(match.exact()?"Exact duplicate":"Potential duplicate",22));
        JLabel score=UiKit.sub("");
        Messages.bind(score,()->score.setText(Messages.get("Similarity: "+
                String.format(Locale.ROOT,"%.3f",match.score())+" · Existing difficulty: "+match.existing().difficulty().name())));
        heading.add(score);
        heading.add(UiKit.sub(match.exact()?"This question already exists. Edit the prompt before saving."
                :"Review both prompts before deciding whether they are intentionally different."));
        root.add(heading,BorderLayout.NORTH);
        JPanel prompts=UiKit.transparent(new GridLayout(1,2,10,0));
        prompts.add(prompt("Existing question",match.existing().text()));
        prompts.add(prompt("Entered question",draft.text()));
        root.add(prompts,BorderLayout.CENTER);
        boolean[] accepted={false};
        JPanel actions=UiKit.transparent(new FlowLayout(FlowLayout.TRAILING,8,0));
        actions.add(MenuStyle.button("CANCEL",false,dialog::dispose));
        actions.add(MenuStyle.button("EDIT QUESTION",match.exact(),dialog::dispose));
        if (!match.exact()) {
            actions.add(MenuStyle.button("SAVE AS INTENTIONALLY DIFFERENT",true,()->{
                accepted[0]=true;
                dialog.dispose();
            }));
        }
        root.add(actions,BorderLayout.SOUTH);
        GlobalHeader.install(dialog,root);
        dialog.setSize(700,365);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return accepted[0];
    }

    private static JPanel prompt(String heading,String text) {
        JPanel panel=MenuStyle.card(new BorderLayout(0,6),10);
        panel.add(Theme.label(heading,13,Font.BOLD,ACCENT),BorderLayout.NORTH);
        JTextArea area=UiKit.area(text,5);
        area.setEditable(false);
        panel.add(new JScrollPane(area),BorderLayout.CENTER);
        return panel;
    }
}
