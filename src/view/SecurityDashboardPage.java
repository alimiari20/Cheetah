package view;

import security.AdminAudit;
import security.AdminAudit.Event;
import security.AdminAudit.Type;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static view.Theme.Tone.*;

final class SecurityDashboardPage extends MenuStyle.Page {
    private static final String[] METRICS = {"Total login attempts", "Successful attempts", "Failed attempts",
            "Login success rate", "Failures in the last 24 hours", "Last successful attempt", "Last failed attempt",
            "Last completed sign-in"};
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZONE);
    private final JLabel[] values = new JLabel[METRICS.length];
    private final JLabel feedback = Theme.label(" ", 12, Font.BOLD, DANGER);
    private final JLabel empty = UiKit.sub("");
    private final JComboBox<String> status = new JComboBox<>(new String[]{"All events", "Successful attempts", "Failed attempts"});
    private final JTextField from = UiKit.field(""), until = UiKit.field("");
    private final EventTable model = new EventTable();
    private final ActivityChart chart = new ActivityChart();
    private List<Event> events = List.of();

    SecurityDashboardPage(Runnable back) {
        super(new BorderLayout(0, 10));
        setBorder(new EmptyBorder(14, 22, 16, 22));
        JPanel heading = UiKit.transparent(new BorderLayout(12, 3));
        heading.add(MenuStyle.button("← DASHBOARD", false, back), BorderLayout.LINE_START);
        heading.add(Theme.label("Security Dashboard", 24, Font.BOLD, TEXT), BorderLayout.CENTER);
        heading.add(UiKit.sub("Login totals count submitted credential checks. Completed sign-ins appear separately."),
                BorderLayout.SOUTH);

        JPanel metrics = UiKit.transparent(new GridLayout(2, 4, 8, 6));
        for (int i = 0; i < METRICS.length; i++) {
            JPanel card = MenuStyle.card(new GridLayout(2, 1, 0, 2), 7);
            JLabel caption = UiKit.sub(METRICS[i]);
            values[i] = UiKit.title("—", 15);
            card.add(caption);
            card.add(values[i]);
            metrics.add(card);
        }
        JPanel upper = UiKit.transparent(new BorderLayout(0, 8));
        upper.add(heading, BorderLayout.NORTH);
        upper.add(metrics, BorderLayout.CENTER);
        upper.add(feedback, BorderLayout.SOUTH);
        add(upper, BorderLayout.NORTH);

        JPanel trend = MenuStyle.card(new BorderLayout(0, 3), 9);
        trend.add(UiKit.goldLabel("Login attempts over the last 7 days"), BorderLayout.NORTH);
        trend.add(chart, BorderLayout.CENTER);
        JPanel legend = UiKit.transparent(new FlowLayout(FlowLayout.CENTER, 12, 0));
        legend.add(Theme.label("■ Successful attempts", 11, Font.BOLD, SUCCESS));
        legend.add(Theme.label("■ Failed attempts", 11, Font.BOLD, DANGER));
        trend.add(legend, BorderLayout.SOUTH);

        JPanel filters = UiKit.transparent(new FlowLayout(FlowLayout.LEADING, 8, 0));
        Messages.renderCombo(status);
        status.addActionListener(event -> applyFilters());
        filters.add(status);
        addDate(filters, "From", from);
        addDate(filters, "To", until);
        filters.add(MenuStyle.button("Apply dates", false, this::applyFilters));
        filters.add(MenuStyle.button("Clear filters", false, this::resetFilters));
        JPanel activity = MenuStyle.card(new BorderLayout(0, 5), 9);
        JPanel tools = UiKit.transparent(new BorderLayout(0, 4));
        tools.add(UiKit.goldLabel("Security activity · newest first"), BorderLayout.NORTH);
        tools.add(filters, BorderLayout.CENTER);
        tools.add(empty, BorderLayout.SOUTH);
        activity.add(tools, BorderLayout.NORTH);
        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.setFillsViewportHeight(true);
        Theme.background(table, BACKGROUND);
        Theme.foreground(table, TEXT);
        Theme.tableSelection(table);
        Theme.background(table.getTableHeader(), SECONDARY);
        Theme.foreground(table.getTableHeader(), TEXT);
        int[] widths = {144, 140, 172, 83, 230};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        var headerRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, selected, focus, row, column) ->
                headerRenderer.getTableCellRendererComponent(t, Messages.get(value.toString()), selected, focus, row, column));
        activity.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel body = UiKit.transparent(new BorderLayout(0, 8));
        body.add(trend, BorderLayout.NORTH);
        body.add(activity, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        setSnapshot(new AdminAudit.Snapshot(List.of(), false));
    }

    private static void addDate(JPanel row, String title, JTextField field) {
        JLabel label = UiKit.sub(title);
        label.setLabelFor(field);
        field.setColumns(9);
        Messages.bind(field, () -> field.setToolTipText(Messages.get("Enter dates in YYYY-MM-DD format.")));
        field.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        row.add(label);
        row.add(field);
    }

    void clear() {
        setSnapshot(new AdminAudit.Snapshot(List.of(), false));
        resetFilters();
    }

    void setSnapshot(AdminAudit.Snapshot snapshot) {
        events = snapshot.events();
        var stats = AdminAudit.statistics(events, Instant.now());
        Instant lastSignIn = events.stream().filter(event -> event.type() == Type.SIGN_IN && event.success())
                .map(Event::time).max(Instant::compareTo).orElse(null);
        String[] numbers = {Long.toString(stats.total()), Long.toString(stats.successes()),
                Long.toString(stats.failures()), stats.total() == 0 ? "—" :
                String.format(Locale.ROOT, "%.1f%%", stats.successRate() * 100),
                Long.toString(stats.failuresLastDay()), date(stats.lastSuccess()), date(stats.lastFailure()), date(lastSignIn)};
        for (int i = 0; i < values.length; i++) Messages.set(values[i], numbers[i]);
        Messages.set(feedback, snapshot.incomplete() ? "Some security records could not be saved or read." : " ");
        feedback.setVisible(snapshot.incomplete());
        chart.repaint();
        applyFilters();
    }

    private static String date(Instant instant) { return instant == null ? "—" : DATE_TIME.format(instant); }

    private void resetFilters() {
        from.setText("");
        until.setText("");
        status.setSelectedIndex(0);
        applyFilters();
    }

    private void applyFilters() {
        try {
            LocalDate first = parseDate(from), last = parseDate(until);
            if (first != null && last != null && first.isAfter(last)) {
                showFilterMessage("The start date must be on or before the end date.");
                return;
            }
            var filtered = new ArrayList<Event>();
            for (int i = events.size() - 1; i >= 0; i--) {
                Event event = events.get(i);
                if (status.getSelectedIndex() == 1 && !event.success()
                        || status.getSelectedIndex() == 2 && event.success()) continue;
                LocalDate day = event.time().atZone(ZONE).toLocalDate();
                if (first != null && day.isBefore(first) || last != null && day.isAfter(last)) continue;
                filtered.add(event);
            }
            model.events = filtered;
            model.fireTableDataChanged();
            showFilterMessage(events.isEmpty() ? "No security activity has been recorded."
                    : filtered.isEmpty() ? "No events match these filters." : "");
        } catch (java.time.format.DateTimeParseException ex) {
            showFilterMessage("Enter dates in YYYY-MM-DD format.");
        }
    }

    private void showFilterMessage(String text) {
        Messages.set(empty, text);
        empty.setVisible(!text.isEmpty());
    }

    private static LocalDate parseDate(JTextField field) {
        return field.getText().isBlank() ? null : LocalDate.parse(field.getText().strip());
    }

    private static final class EventTable extends AbstractTableModel {
        private static final String[] COLUMNS = {"Date and time", "Event type", "Authentication method", "Outcome", "Description"};
        private List<Event> events = List.of();
        @Override public int getRowCount() { return events.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int column) { return COLUMNS[column]; }

        @Override public Object getValueAt(int row, int column) {
            Event event = events.get(row);
            return switch (column) {
                case 0 -> date(event.time());
                case 1 -> Messages.get(switch (event.type()) {
                    case LOGIN_CHECK -> "Login credential check";
                    case REAUTH_CHECK -> "Account re-verification";
                    case SIGN_IN -> "Completed sign-in";
                    case BLOCKED -> "Lockout blocked access";
                    case ERROR -> "Authentication error";
                });
                case 2 -> Messages.get(switch (event.method()) {
                    case PASSWORD -> "Password";
                    case CODE -> "Authentication Code";
                    case BOTH -> "Password + code";
                });
                case 3 -> Messages.get(event.success() ? "Successful" : "Failed");
                default -> Messages.get(switch (event.type()) {
                    case LOGIN_CHECK, REAUTH_CHECK -> event.success() ? "Credential accepted." : "Credential rejected.";
                    case SIGN_IN -> "Administrator session opened.";
                    case BLOCKED -> "Access was blocked by the active lockout.";
                    case ERROR -> "The credential check could not complete.";
                });
            };
        }
    }

    private final class ActivityChart extends JComponent {
        ActivityChart() { setPreferredSize(new Dimension(500, 93)); }

        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = MenuStyle.smooth(graphics);
            g.setFont(Theme.font(11, Font.PLAIN));
            LocalDate start = LocalDate.now(ZONE).minusDays(6);
            long[][] totals = new long[7][2];
            long max = 0;
            for (Event event : events) {
                if (event.type() != Type.LOGIN_CHECK) continue;
                long index = java.time.temporal.ChronoUnit.DAYS.between(start, event.time().atZone(ZONE).toLocalDate());
                if (index >= 0 && index < 7) max = Math.max(max, ++totals[(int) index][event.success() ? 0 : 1]);
            }
            if (max == 0) {
                String caption = Messages.get("No login attempts in this period.");
                g.setColor(TEXT.color());
                g.drawString(caption, (getWidth() - g.getFontMetrics().stringWidth(caption)) / 2,
                        getHeight() / 2);
                g.dispose();
                return;
            }
            int baseline = getHeight() - 20;
            int slot = getWidth() / 7;
            g.setColor(BORDER.color());
            g.drawLine(4, baseline, getWidth() - 4, baseline);
            for (int day = 0; day < 7; day++) {
                int visual = getComponentOrientation().isLeftToRight() ? day : 6 - day;
                int center = slot * visual + slot / 2;
                for (int result = 0; result < 2; result++) {
                    long total = totals[day][result];
                    if (total == 0) continue;
                    int height = Math.max(2, (int) (total * (baseline - 16.0) / max));
                    int x = center + (result == 0 ? -25 : 3);
                    g.setColor((result == 0 ? SUCCESS : DANGER).color());
                    g.fillRoundRect(x, baseline - height, 22, height, 5, 5);
                    g.setColor(TEXT.color());
                    String number = Long.toString(total);
                    g.drawString(number, x + (22 - g.getFontMetrics().stringWidth(number)) / 2, baseline-height-3);
                }
                String label = start.plusDays(day).format(DateTimeFormatter.ofPattern("MM-dd"));
                g.setColor(MUTED.color());
                g.drawString(label, center - g.getFontMetrics().stringWidth(label) / 2, getHeight() - 4);
            }
            g.dispose();
        }
    }
}
