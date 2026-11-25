package security;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

/** Separate local log. A shared lock prevents concurrent processes from interleaving records. */
final class SecurityEventRepository {
    private static final Object MONITOR = new Object();
    private final Path file;

    SecurityEventRepository() {
        this(Path.of(System.getProperty("user.home"), ".cheetah-ludo", "admin-audit.tsv"));
    }

    SecurityEventRepository(Path file) { this.file = file.toAbsolutePath(); }

    void append(AdminAudit.Event event) throws IOException {
        synchronized (MONITOR) {
            Files.createDirectories(file.getParent());
            try (var channel = lockChannel(); var lock = channel.lock()) {
                // A preceding newline also isolates a truncated final record after a crash.
                String row = "\n1\t" + event.time() + "\t" + event.type() + "\t"
                        + event.method() + "\t" + (event.success() ? "1" : "0") + "\n";
                Files.writeString(file, row, StandardCharsets.US_ASCII,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
    }

    AdminAudit.Snapshot read() throws IOException {
        synchronized (MONITOR) {
            if (Files.notExists(file)) return new AdminAudit.Snapshot(java.util.List.of(), false);
            var events = new ArrayList<AdminAudit.Event>();
            boolean incomplete = false;
            try (var channel = lockChannel(); var lock = channel.lock();
                 var reader = Files.newBufferedReader(file, StandardCharsets.ISO_8859_1)) {
                for (String row; (row = reader.readLine()) != null;) {
                    if (row.isEmpty()) continue;
                    String[] fields = row.split("\t", -1);
                    if (fields.length != 5 || !fields[0].equals("1")
                            || !(fields[4].equals("0") || fields[4].equals("1"))) {
                        incomplete = true;
                        continue;
                    }
                    try {
                        events.add(new AdminAudit.Event(Instant.parse(fields[1]),
                                AdminAudit.Type.valueOf(fields[2]), AdminAudit.Method.valueOf(fields[3]),
                                fields[4].equals("1")));
                    } catch (IllegalArgumentException | DateTimeParseException ex) {
                        incomplete = true;
                    }
                }
            }
            return new AdminAudit.Snapshot(AdminAudit.chronological(events), incomplete);
        }
    }

    private FileChannel lockChannel() throws IOException {
        return FileChannel.open(file.resolveSibling(file.getFileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    }
}
