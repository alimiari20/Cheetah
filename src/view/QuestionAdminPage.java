package view;

import static view.Theme.Tone.*;

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
public final class QuestionAdminPage extends MenuStyle.Page {
    private final DefaultListModel<Question> entries=new DefaultListModel<>();
    private final JList<Question> list=new JList<>(entries) {
        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public String getToolTipText(java.awt.event.MouseEvent event) {
            int index=locationToIndex(event.getPoint());
            return index>=0 && getCellBounds(index,index).contains(event.getPoint())
                    ? entries.get(index).text() : null;
        }
    };
    private final JTextField search=UiKit.field("");
    private final JTextField category=UiKit.field("Software Engineering");
    private final JTextArea prompt=UiKit.area("",3),explanation=UiKit.area("",2);
    private final JTextField[] options=new JTextField[4];
    private final JComboBox<Difficulty> difficulty=new JComboBox<>(Difficulty.values());
    private final JComboBox<String> correct=new JComboBox<>(new String[]{"Option A","Option B","Option C","Option D"});
    private final JLabel total=MenuStyle.label("QUESTION BANK",12,Font.BOLD,ACCENT);
    private final JLabel note=MenuStyle.label("Minimum: 30 questions enforced",11,Font.BOLD,MUTED);
    private final Consumer<Question> saveAction;
    private final Consumer<String> deleteAction;
    @FunctionalInterface
    interface SuggestAction { void analyze(String editingId,String prompt,List<String> choices,int correctIndex); }
    private final SuggestAction suggestAction;
    private JButton add,delete,save,suggest;
    private List<Question> all=List.of();
    private String editId;

    QuestionAdminPage(Runnable back,Consumer<Question> saveAction,Consumer<String> deleteAction,SuggestAction suggestAction) {
        super(new BorderLayout(12,15));this.saveAction=saveAction;this.deleteAction=deleteAction;
        this.suggestAction=suggestAction;
        setBorder(new EmptyBorder(17,28,19,28));
        JPanel header=UiKit.transparent(new BorderLayout(12,0));
        JButton returnButton=MenuStyle.button("← DASHBOARD",false,back);
        header.add(returnButton,BorderLayout.LINE_START);
        JPanel heading=UiKit.transparent(new GridLayout(2,1,0,4));
        heading.add(MenuStyle.label("Question Management",26,Font.BOLD,TEXT));
        heading.add(MenuStyle.label("Search, add, and edit the question bank",13,Font.PLAIN,MUTED));
        header.add(heading,BorderLayout.CENTER);
        add(header,BorderLayout.NORTH);
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
        Messages.renderCombo(difficulty);
        Messages.renderCombo(correct);
        beginNew();
    }
    private JPanel buildListPane(){
        JPanel outer=MenuStyle.card(new BorderLayout(0,11),15);
        JPanel header=UiKit.transparent(new BorderLayout(0,7));header.add(total,BorderLayout.NORTH);
        Messages.bind(search, () -> {
            search.setToolTipText(Messages.get("Filter by category, difficulty or question text"));
            search.getAccessibleContext().setAccessibleName(Messages.get("Search questions"));
        });
        MenuStyle.field(search);
        header.add(search,BorderLayout.CENTER);header.add(note,BorderLayout.SOUTH);
        outer.add(header,BorderLayout.NORTH);
        Theme.background(list,BACKGROUND);Theme.foreground(list,TEXT);
        Theme.listSelection(list);
        list.setFixedCellHeight(66);list.setFont(Theme.font(12,Font.BOLD));
        list.setToolTipText("");
        list.setCellRenderer((lst,q,index,selected,focus)->{
            JPanel row=UiKit.transparent(new BorderLayout(0,3));row.setBorder(new EmptyBorder(8,11,8,8));
            row.setOpaque(true);row.setBackground((selected?SECONDARY:BACKGROUND).color());
            String text=q.text().replaceAll("\\s+"," ");if(text.length()>86)text=text.substring(0,83)+"…";
            JLabel promptLabel=new JLabel(text);promptLabel.setFont(Theme.font(12,Font.BOLD));Theme.foreground(promptLabel,TEXT);
            row.add(promptLabel,BorderLayout.CENTER);
            row.add(MenuStyle.label(Messages.get(q.difficulty().name())+"   •   "+q.category(),10,Font.PLAIN,MUTED),BorderLayout.SOUTH);
            return row;
        });
        JScrollPane scroll=new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(scroll,BorderLayout.CENTER);
        return outer;
    }
    private JPanel buildEditor(){
        JPanel outer=MenuStyle.card(new BorderLayout(0,12),16);
        JPanel upper=UiKit.transparent(new BorderLayout(8,0));upper.add(label("ADD / EDIT QUESTION"),BorderLayout.LINE_START);
        add=MenuStyle.button("+ NEW QUESTION",false,this::beginNew);
        upper.add(add,BorderLayout.LINE_END);
        outer.add(upper,BorderLayout.NORTH);
        JPanel form=UiKit.transparent();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));
        form.add(label("DIFFICULTY"));
        form.add(difficulty);
        suggest=MenuStyle.button("AI Suggest Difficulty",false,()->suggestAction.analyze(
                editId,prompt.getText().strip(),answerValues(),correct.getSelectedIndex()));
        suggest.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(suggest);
        form.add(Box.createVerticalStrut(6));
        form.add(label("CATEGORY"));form.add(category);form.add(Box.createVerticalStrut(6));
        form.add(label("QUESTION PROMPT"));form.add(scroll(prompt,75));form.add(Box.createVerticalStrut(6));
        for(int i=0;i<4;i++){
            form.add(label("OPTION "+(char)('A'+i)));options[i]=UiKit.field("");form.add(options[i]);form.add(Box.createVerticalStrut(5));
        }
        form.add(label("CORRECT ANSWER"));form.add(correct);form.add(Box.createVerticalStrut(6));
        form.add(label("EXPLANATION"));form.add(scroll(explanation,62));
        JScrollPane formScroll=new JScrollPane(form);formScroll.setOpaque(false);formScroll.getViewport().setOpaque(false);
        formScroll.setBorder(BorderFactory.createEmptyBorder());formScroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(formScroll,BorderLayout.CENTER);
        JPanel buttons=UiKit.transparent(new GridLayout(1,2,10,0));
        delete=MenuStyle.button("DELETE",false);
        save=MenuStyle.button("SAVE QUESTION",true,this::save);
        delete.addActionListener(e->{
            if(editId==null){Dialogs.showMessageDialog(this,"Select an existing question to delete.");return;}
            if(all.size()<=30){Dialogs.showMessageDialog(this,"Cannot delete: the bank must contain at least 30 questions.");return;}
            int answer=Dialogs.showConfirmDialog(this,"Delete this question? This cannot be undone.","Confirm deletion",JOptionPane.YES_NO_OPTION);
            if(answer==JOptionPane.YES_OPTION)deleteAction.accept(editId);
        });
        buttons.add(delete);buttons.add(save);outer.add(buttons,BorderLayout.SOUTH);
        return outer;
    }
    private JLabel label(String t){JLabel l=MenuStyle.label(t,12,Font.BOLD,ACCENT);l.setAlignmentX(Component.LEFT_ALIGNMENT);return l;}
    private JScrollPane scroll(JTextArea area,int height){JScrollPane sc=new JScrollPane(area);sc.setPreferredSize(new Dimension(150,height));
       sc.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));Theme.lineBorder(sc,null);return sc;}
    public void showQuestions(List<Question> questions){
        this.all=List.copyOf(questions);Messages.set(total,"QUESTION BANK  •  "+all.size()+" QUESTIONS");
        Messages.set(note,all.size()==30?"Minimum 30: deletion disabled":"Minimum 30 • "+(all.size()-30)+" above minimum");
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
        Question q=new Question(editId==null?"q-local-"+UUID.randomUUID():editId,(Difficulty)difficulty.getSelectedItem(),
             category.getText().trim(),prompt.getText().trim(),answerValues(),correct.getSelectedIndex(),explanation.getText().trim());
        saveAction.accept(q);
    }

    private List<String> answerValues() {
        return Arrays.stream(options).map(field->field.getText().strip()).toList();
    }

    void applySuggestedDifficulty(Difficulty recommendation) { difficulty.setSelectedItem(recommendation); }

    void analysisBusy(boolean busy) {
        for(JComponent component:List.of(add,delete,save,suggest,list,search,category,prompt,explanation,difficulty,correct))
            component.setEnabled(!busy);
        for(JTextField option:options) option.setEnabled(!busy);
        if(busy) Messages.set(note,"Analyzing question offline…");
        else Messages.set(note,all.size()==30?"Minimum 30: deletion disabled":"Minimum 30 • "+(all.size()-30)+" above minimum");
    }
}
