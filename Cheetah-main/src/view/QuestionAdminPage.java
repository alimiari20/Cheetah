package view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import model.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Editable question administration screen; validation and persistence stay in service/controller. */
public final class QuestionAdminPage extends UiKit.GradientPage {
    private final DefaultListModel<Question> entries=new DefaultListModel<>();
    private final JList<Question> list=new JList<>(entries);
    private final JTextField search=UiKit.field("");
    private final JTextField category=UiKit.field("Software Engineering");
    private final JTextArea prompt=UiKit.area("",3),explanation=UiKit.area("",2);
    private final JTextField[] options=new JTextField[4];
    private final JComboBox<Difficulty> difficulty=new JComboBox<>(Difficulty.values());
    private final JComboBox<String> correct=new JComboBox<>(new String[]{"Option A","Option B","Option C","Option D"});
    private final JLabel total=UiKit.goldLabel("32 QUESTIONS IN THE BANK");
    private final JLabel note=Theme.label("Minimum: 30 questions enforced",11,Font.BOLD,Theme.MUTED);
    private final Consumer<Question> saveAction;
    private final Consumer<String> deleteAction;
    private List<Question> all=List.of();
    private String editId;

    public QuestionAdminPage(Runnable back,Consumer<Question> saveAction,Consumer<String> deleteAction) {
        super(new BorderLayout(12,15));this.saveAction=saveAction;this.deleteAction=deleteAction;
        setBorder(new EmptyBorder(17,28,19,28));
        JPanel header=UiKit.transparent(new BorderLayout(12,0));
        JButton returnButton=Theme.button("← BACK TO MENU",false);returnButton.addActionListener(e->back.run());
        returnButton.setPreferredSize(new Dimension(190,44));header.add(returnButton,BorderLayout.WEST);
        JPanel heading=UiKit.transparent(new GridLayout(2,1,0,4));
        heading.add(UiKit.title("QUESTION ADMINISTRATION",23));
        heading.add(UiKit.sub("SRS 3.2.9  •  Question bank maintenance  •  Minimum 30 questions enforced"));
        header.add(heading,BorderLayout.CENTER);
        header.add(Box.createHorizontalStrut(150),BorderLayout.EAST);add(header,BorderLayout.NORTH);
        JPanel content=UiKit.transparent(new GridBagLayout());
        GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.fill=GridBagConstraints.BOTH;c.weighty=1;c.insets=new Insets(3,7,3,7);
        c.gridx=0;c.weightx=.46;content.add(buildListPane(),c);
        c.gridx=1;c.weightx=.54;content.add(buildEditor(),c);
        add(content,BorderLayout.CENTER);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting() && list.getSelectedValue()!=null)fill(list.getSelectedValue());});
        search.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}
            public void changedUpdate(DocumentEvent e){filter();}
        });
        beginNew();
    }
    private JPanel buildListPane(){
        JPanel outer=new UiKit.GlassCard(new BorderLayout(0,11));outer.setBorder(new EmptyBorder(15,14,16,14));
        JPanel header=UiKit.transparent(new BorderLayout(0,7));header.add(total,BorderLayout.NORTH);
        search.setToolTipText("Filter by category, difficulty or question text");
        header.add(search,BorderLayout.CENTER);header.add(note,BorderLayout.SOUTH);
        outer.add(header,BorderLayout.NORTH);
        list.setBackground(new Color(12,35,90));list.setForeground(Theme.WHITE);
        list.setSelectionBackground(new Color(42,92,196));list.setSelectionForeground(Color.WHITE);
        list.setFixedCellHeight(66);list.setFont(Theme.font(12,Font.BOLD));
        list.setCellRenderer((lst,q,index,selected,focus)->{
            JPanel row=UiKit.transparent(new BorderLayout(0,3));row.setBorder(new EmptyBorder(8,11,8,8));
            row.setOpaque(true);row.setBackground(selected?new Color(44,99,207):new Color(15,41,105));
            String text=q.text().replaceAll("\\s+"," ");if(text.length()>86)text=text.substring(0,83)+"…";
            row.add(Theme.label(text,12,Font.BOLD,Theme.WHITE),BorderLayout.CENTER);
            row.add(Theme.label(q.difficulty().name()+"   •   "+q.category(),10,Font.PLAIN,Theme.MUTED),BorderLayout.SOUTH);
            return row;
        });
        JScrollPane scroll=new JScrollPane(list);scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(scroll,BorderLayout.CENTER);
        return outer;
    }
    private JPanel buildEditor(){
        JPanel outer=new UiKit.GlassCard(new BorderLayout(0,12));outer.setBorder(new EmptyBorder(15,18,14,18));
        JPanel upper=UiKit.transparent(new BorderLayout());upper.add(UiKit.goldLabel("ADD / EDIT QUESTION"),BorderLayout.WEST);
        JButton add=Theme.button("+ NEW QUESTION",true);add.addActionListener(e->beginNew());add.setPreferredSize(new Dimension(170,40));upper.add(add,BorderLayout.EAST);
        outer.add(upper,BorderLayout.NORTH);
        JPanel form=UiKit.transparent();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));
        form.add(label("DIFFICULTY"));form.add(difficulty);form.add(Box.createVerticalStrut(6));
        form.add(label("CATEGORY"));form.add(category);form.add(Box.createVerticalStrut(6));
        form.add(label("QUESTION PROMPT"));form.add(scroll(prompt,75));form.add(Box.createVerticalStrut(6));
        for(int i=0;i<4;i++){
            form.add(label("OPTION "+(char)('A'+i)));options[i]=UiKit.field("");form.add(options[i]);form.add(Box.createVerticalStrut(5));
        }
        form.add(label("CORRECT ANSWER"));form.add(correct);form.add(Box.createVerticalStrut(6));
        form.add(label("EXPLANATION"));form.add(scroll(explanation,62));
        JScrollPane formScroll=new JScrollPane(form);formScroll.setOpaque(false);formScroll.getViewport().setOpaque(false);
        formScroll.setBorder(null);formScroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(formScroll,BorderLayout.CENTER);
        JPanel buttons=UiKit.transparent(new GridLayout(1,2,10,0));
        JButton delete=Theme.button("DELETE",false),save=Theme.button("SAVE QUESTION",true);
        delete.addActionListener(e->{
            if(editId==null){JOptionPane.showMessageDialog(this,"Select an existing question to delete.");return;}
            if(all.size()<=30){JOptionPane.showMessageDialog(this,"Cannot delete: the bank must contain at least 30 questions.");return;}
            int answer=JOptionPane.showConfirmDialog(this,"Delete this question? This cannot be undone.","Confirm deletion",JOptionPane.YES_NO_OPTION);
            if(answer==JOptionPane.YES_OPTION)deleteAction.accept(editId);
        });
        save.addActionListener(e->save());buttons.add(delete);buttons.add(save);outer.add(buttons,BorderLayout.SOUTH);
        return outer;
    }
    private JLabel label(String t){JLabel l=UiKit.goldLabel(t);l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
    private JScrollPane scroll(JTextArea area,int height){JScrollPane sc=new JScrollPane(area);sc.setPreferredSize(new Dimension(150,height));
       sc.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));sc.setBorder(BorderFactory.createLineBorder(Theme.LINE));return sc;}
    public void showQuestions(List<Question> questions){
        this.all=List.copyOf(questions);total.setText("QUESTION BANK  •  "+all.size()+" QUESTIONS");
        note.setText(all.size()==30?"Minimum 30: deletion disabled":"Minimum 30 • "+(all.size()-30)+" above minimum");
        filter();
    }
    private void filter(){
        String needle=search.getText().trim().toLowerCase(Locale.ROOT);
        entries.clear();for(Question q:all){
            String haystack=(q.text()+" "+q.category()+" "+q.difficulty().name()).toLowerCase(Locale.ROOT);
            if(haystack.contains(needle))entries.addElement(q);
        }
    }
    private void beginNew(){
        editId=null;list.clearSelection();difficulty.setSelectedItem(Difficulty.EASY);
        category.setText("Software Engineering");prompt.setText("");explanation.setText("");
        for(JTextField f:options)if(f!=null)f.setText("");correct.setSelectedIndex(0);
    }
    private void fill(Question q){
        editId=q.id();difficulty.setSelectedItem(q.difficulty());category.setText(q.category());
        prompt.setText(q.text());explanation.setText(q.explanation());correct.setSelectedIndex(q.correctIndex());
        for(int i=0;i<4;i++)options[i].setText(q.choices().get(i));
    }
    private void save(){
        List<String> values=new ArrayList<>();for(JTextField f:options)values.add(f.getText().trim());
        Question q=new Question(editId==null?"q-local-"+UUID.randomUUID():editId,(Difficulty)difficulty.getSelectedItem(),
             category.getText().trim(),prompt.getText().trim(),values,correct.getSelectedIndex(),explanation.getText().trim());
        saveAction.accept(q);
    }
}
