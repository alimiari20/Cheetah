package view;

import static view.Theme.Tone.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;

import model.HistoryRepository;

import java.awt.*;
import java.util.*;
import java.util.List;

/** Full-page game history table, match details and CSV export. */
public final class HistoryPage extends UiKit.GradientPage {
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"DATE", "DIFFICULTY", "PLAYERS", "WINNER", "DURATION"},0){
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    private final JTable table=new JTable(model);
    private final JTextArea details=UiKit.area("",4);
    private final JLabel count=UiKit.goldLabel("COMPLETED MATCHES  •  0");
    private List<HistoryRepository.Entry> records=List.of();
    public HistoryPage(Runnable back,Runnable export){
        super(new BorderLayout(15,16));setBorder(new EmptyBorder(20,30,23,30));
        JPanel top=UiKit.transparent(new BorderLayout(10,0));
        JButton b=Theme.button("← BACK",false);b.addActionListener(e->back.run());top.add(b,BorderLayout.LINE_START);
        JPanel center=UiKit.transparent(new GridLayout(2,1));center.add(UiKit.title("GAME HISTORY",27));
        center.add(UiKit.sub("Completed games  •  Ranking and match results"));top.add(center,BorderLayout.CENTER);
        JButton exp=Theme.button("EXPORT CSV ↗",true);exp.addActionListener(e->export.run());top.add(exp,BorderLayout.LINE_END);
        add(top,BorderLayout.NORTH);
        JPanel body=new UiKit.GlassCard(new BorderLayout(12,13));body.setBorder(new EmptyBorder(15,16,16,16));
        body.add(count,BorderLayout.NORTH);
        table.setFont(Theme.font(13,Font.PLAIN));table.setRowHeight(38);table.setShowGrid(false);
        Theme.background(table,BACKGROUND);Theme.foreground(table,TEXT);
        Theme.tableSelection(table);
        table.getTableHeader().setFont(Theme.font(12,Font.BOLD));Theme.foreground(table.getTableHeader(),ACCENT);
        Theme.background(table.getTableHeader(),SECONDARY);
        TableCellRenderer headerRenderer=table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t,value,selected,focus,row,column) ->
                headerRenderer.getTableCellRendererComponent(t,Messages.get(value.toString()),selected,focus,row,column));
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                super.setValue(value == null ? null : Messages.get(value.toString()));
            }
        });
        Messages.set(details,"Choose a match to view final rankings.");
        table.setFillsViewportHeight(true);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e->showSelected());
        JScrollPane scroll=new JScrollPane(table);Theme.background(scroll.getViewport(),BACKGROUND);
        Theme.lineBorder(scroll,null);
        body.add(scroll,BorderLayout.CENTER);
        JPanel foot=UiKit.transparent(new BorderLayout(0,8));foot.add(UiKit.goldLabel("FINAL RANKINGS"),BorderLayout.NORTH);
        details.setEditable(false);details.setFocusable(false);Theme.background(details,BACKGROUND);
        foot.add(new JScrollPane(details),BorderLayout.CENTER);foot.setPreferredSize(new Dimension(350,130));
        body.add(foot,BorderLayout.SOUTH);add(body,BorderLayout.CENTER);
    }
    public void setEntries(List<HistoryRepository.Entry> entries){
        this.records=List.copyOf(entries);Messages.set(count,"COMPLETED MATCHES  •  "+records.size());
        model.setRowCount(0);
        for(HistoryRepository.Entry e:records) {
            String date=e.date().length()>19?e.date().substring(0,19).replace('T',' '):e.date();
            model.addRow(new Object[]{date,e.difficulty(),e.players(),e.winner(),String.format("%02d:%02d:%02d",e.seconds()/3600,(e.seconds()/60)%60,e.seconds()%60)});
        }
        if(!records.isEmpty())table.setRowSelectionInterval(0,0);
        else Messages.set(details,"No completed matches yet. Play a full game to create a history record.");
    }
    private void showSelected(){
        int row=table.getSelectedRow();if(row<0||row>=records.size())return;
        HistoryRepository.Entry e=records.get(table.convertRowIndexToModel(row));
        String[] names=e.ranking().split(" > ");
        Messages.bind(details, () -> {
            StringBuilder text=new StringBuilder();
            for(int i=0;i<names.length;i++) {
                text.append(i==0?"🏆  ":"#"+(i+1)+"  ").append(names[i]);
                if(i==0) text.append("  •  ").append(Messages.get("WINNER"));
                text.append("\n");
            }
            details.setText(text.toString());
            details.setCaretPosition(0);
        });
    }
}
