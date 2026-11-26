package view;

import static view.Theme.Tone.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;

import model.Question;

import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Neon question modal: choose, submit, see outcome, then apply movement. */
public final class QuestionDialog {
    private QuestionDialog(){}
    public static boolean ask(JFrame owner,Question question){
        return ask(owner,question,-1);
    }
    public static boolean ask(JFrame owner,Question question,int automaticAnswer){
        JDialog dialog=new JDialog(owner,"QUESTION SQUARE",true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        UiKit.GradientPage root=new UiKit.GradientPage(new BorderLayout(0,13));
        root.setBorder(new EmptyBorder(22,27,24,27));
        JPanel heading=UiKit.transparent(new GridLayout(3,1,0,6));
        heading.add(UiKit.title("?  QUESTION CHALLENGE",24));
        heading.add(UiKit.sub(question.category()+"  •  "+question.difficulty().name()+" QUESTION"));
        heading.add(UiKit.sub("Correct: +"+question.movementReward()+" squares     Incorrect: -"+question.movementReward()+" squares"));
        root.add(heading,BorderLayout.NORTH);
        JPanel center=new UiKit.GlassCard(new BorderLayout(0,13));center.setBorder(new EmptyBorder(15,18,17,18));
        JTextArea prompt=UiKit.area(question.text(),4);prompt.setEditable(false);prompt.setFocusable(false);
        prompt.setFont(Theme.font(17,Font.BOLD));
        center.add(prompt,BorderLayout.NORTH);
        JPanel answers=UiKit.transparent(new GridLayout(4,1,0,9));
        JRadioButton[] radio=new JRadioButton[4];ButtonGroup group=new ButtonGroup();
        for(int i=0;i<4;i++){
            int index=i;
            JRadioButton option=new JRadioButton();
            radio[i]=option;
            Messages.bind(option, () -> option.setText("  "
                    + (char)((Messages.isHebrew() ? 'א' : 'A')+index) + "    " + question.choices().get(index)));
            Theme.background(radio[i],SECONDARY);Theme.foreground(radio[i],TEXT);
            radio[i].setFont(Theme.font(13,Font.PLAIN));radio[i].setFocusPainted(false);
            radio[i].setOpaque(true);radio[i].setBorder(new EmptyBorder(12,10,12,10));
            group.add(radio[i]);answers.add(radio[i]);
        }
        center.add(answers,BorderLayout.CENTER);
        JLabel feedback=Theme.label("Select one answer to continue.",12,Font.BOLD,MUTED);
        JTextArea explanation=UiKit.area("",3);explanation.setEditable(false);explanation.setFocusable(false);
        explanation.setVisible(false);
        JPanel bottom=UiKit.transparent(new BorderLayout(0,10));
        bottom.add(feedback,BorderLayout.NORTH);bottom.add(explanation,BorderLayout.CENTER);
        JButton apply=Theme.button("CHECK ANSWER",true);
        bottom.add(apply,BorderLayout.SOUTH);center.add(bottom,BorderLayout.SOUTH);
        root.add(center,BorderLayout.CENTER);
        AtomicBoolean correct=new AtomicBoolean(false),answered=new AtomicBoolean(false);
        Runnable submit=()->{
            if(answered.get()){dialog.dispose();return;}
            int selected=-1;for(int i=0;i<4;i++)if(radio[i].isSelected()){selected=i;break;}
            if(selected<0){Messages.set(feedback,"Please select A, B, C or D.");return;}
            boolean success=selected==question.correctIndex();correct.set(success);answered.set(true);
            for(int i=0;i<4;i++){
                radio[i].setEnabled(false);
                if(i==question.correctIndex()){Theme.foreground(radio[i],SUCCESS);radio[i].setFont(Theme.font(13,Font.BOLD));}
                else if(i==selected)Theme.foreground(radio[i],DANGER);
            }
            Theme.foreground(feedback,success?SUCCESS:DANGER);
            Messages.set(feedback,success?"✓  CORRECT!  MOVE "+question.movementReward()+" SQUARES FORWARD":
                 "✕  INCORRECT. MOVE "+question.movementReward()+" SQUARES BACK.");
            explanation.setText(question.explanation());explanation.setVisible(true);
            Messages.set(apply,"APPLY MOVEMENT & CONTINUE  →");
            dialog.pack();dialog.setSize(Math.max(dialog.getWidth(),600),Math.min(720,dialog.getHeight()));
            dialog.setLocationRelativeTo(owner);
        };
        apply.addActionListener(e->submit.run());
        GlobalHeader.install(dialog,root);dialog.setSize(670,660);dialog.setMinimumSize(new Dimension(580,500));
        Timer finish=new Timer(1450,event -> dialog.dispose());
        finish.setRepeats(false);
        Timer think=new Timer(850,event -> {
            radio[automaticAnswer].setSelected(true);
            submit.run();
            finish.start();
        });
        think.setRepeats(false);
        if (automaticAnswer>=0) {
            for (JRadioButton option:radio) option.setEnabled(false);
            apply.setEnabled(false);
            Messages.set(feedback,"AI is thinking…");
            think.start();
        }
        dialog.setLocationRelativeTo(owner);
        try { dialog.setVisible(true); } finally { think.stop(); finish.stop(); }
        return correct.get();
    }
}
