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
    	if (!Files.exists(file)) return readExcel();
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
    /**
     * Reads the bundled Excel question bank (/data/questions_Cheetah.xlsx, sheet "Questions").
     * An .xlsx file is a ZIP archive of XML files, so it is read with the JDK only
     * (java.util.zip + javax.xml) - no external library such as Apache POI is needed.
     * Columns: A=ID, B=Difficulty, C=Category, D=Question, E-H=Options A-D,
     *          I=Correct answer (A-D), J=Explanation. Row 1 is the header.
     */
    public List<Question> readExcel() throws IOException {
        List<Question> result = new ArrayList<>();
        try (InputStream input = QuestionRepository.class
                .getResourceAsStream("/data/questions_Cheetah.xlsx")) {

            if (input == null)
                throw new IOException("Excel file not found");

            SortedMap<Integer, Map<Integer, String>> rows = readSheet(input, "Questions");

            for (Map.Entry<Integer, Map<Integer, String>> entry : rows.entrySet()) {
                int rowNumber = entry.getKey();
                if (rowNumber < 2) continue;                 // row 1 is the header
                Map<Integer, String> row = entry.getValue();

                String id = cell(row, 0);
                if (id.isBlank()) continue;

                Difficulty difficulty = Difficulty.valueOf(
                        cell(row, 1).toUpperCase(Locale.ROOT));

                List<String> choices = new ArrayList<>();
                for (int c = 4; c <= 7; c++)
                    choices.add(cell(row, c));

                String answer = cell(row, 8).toUpperCase(Locale.ROOT);
                if (!answer.matches("[A-D]"))
                    throw new IOException("Invalid answer at row " + rowNumber);

                result.add(new Question(
                        id,
                        difficulty,
                        cell(row, 2),
                        cell(row, 3),
                        choices,
                        answer.charAt(0) - 'A',
                        cell(row, 9)));
            }
        } catch (RuntimeException ex) {
            throw new IOException("Could not read Excel questions", ex);
        }

        validate(result);
        return List.copyOf(result);
    }

    private static String cell(Map<Integer, String> row, int column) {
        String value = row.get(column);
        return value == null ? "" : value.trim();
    }

    // ---------- minimal .xlsx reader (JDK only) ----------

    private static final String REL_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";

    /** Returns row number (1-based) -> (column index (0-based) -> cell text) for the named sheet. */
    private static SortedMap<Integer, Map<Integer, String>> readSheet(InputStream xlsx, String sheetName)
            throws IOException {
        Map<String, byte[]> parts = new HashMap<>();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(xlsx)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("/")) name = name.substring(1);
                if (!entry.isDirectory()) parts.put(name, zip.readAllBytes());
            }
        }

        // 1. workbook.xml: sheet name -> relationship id
        org.w3c.dom.Document workbook = xml(parts, "xl/workbook.xml");
        String relId = null;
        for (org.w3c.dom.Element sheet : elements(workbook.getDocumentElement(), "sheet"))
            if (sheetName.equals(sheet.getAttribute("name"))) relId = sheet.getAttributeNS(REL_NS, "id");
        if (relId == null || relId.isEmpty())
            throw new IOException(sheetName + " sheet not found");

        // 2. workbook.xml.rels: relationship id -> worksheet file
        String target = null;
        for (org.w3c.dom.Element rel : elements(xml(parts, "xl/_rels/workbook.xml.rels").getDocumentElement(), "Relationship"))
            if (relId.equals(rel.getAttribute("Id"))) target = rel.getAttribute("Target");
        if (target == null)
            throw new IOException(sheetName + " sheet not found");
        String sheetPath = target.startsWith("/") ? target.substring(1) : "xl/" + target;

        // 3. shared strings (used when the file is saved by Excel)
        List<String> shared = new ArrayList<>();
        if (parts.containsKey("xl/sharedStrings.xml"))
            for (org.w3c.dom.Element si : elements(xml(parts, "xl/sharedStrings.xml").getDocumentElement(), "si"))
                shared.add(text(si));

        // 4. cells of the worksheet
        SortedMap<Integer, Map<Integer, String>> rows = new TreeMap<>();
        int nextRow = 1;
        for (org.w3c.dom.Element row : elements(xml(parts, sheetPath).getDocumentElement(), "row")) {
            int rowNumber = row.hasAttribute("r") ? Integer.parseInt(row.getAttribute("r")) : nextRow;
            nextRow = rowNumber + 1;
            Map<Integer, String> cells = new HashMap<>();
            int nextColumn = 0;
            for (org.w3c.dom.Element c : elements(row, "c")) {
                int column = c.hasAttribute("r") ? columnIndex(c.getAttribute("r")) : nextColumn;
                nextColumn = column + 1;
                String type = c.getAttribute("t");
                String value;
                if ("inlineStr".equals(type)) {
                    List<org.w3c.dom.Element> is = elements(c, "is");
                    value = is.isEmpty() ? "" : text(is.get(0));
                } else {
                    List<org.w3c.dom.Element> v = elements(c, "v");
                    value = v.isEmpty() ? "" : v.get(0).getTextContent();
                    if ("s".equals(type) && !value.isEmpty()) value = shared.get(Integer.parseInt(value.trim()));
                    else if (type.isEmpty() || "n".equals(type)) value = number(value);
                }
                cells.put(column, value);
            }
            rows.put(rowNumber, cells);
        }
        return rows;
    }

    private static org.w3c.dom.Document xml(Map<String, byte[]> parts, String name) throws IOException {
        byte[] bytes = parts.get(name);
        if (bytes == null) throw new IOException("Excel part missing: " + name);
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (javax.xml.parsers.ParserConfigurationException | org.xml.sax.SAXException ex) {
            throw new IOException("Invalid Excel part: " + name, ex);
        }
    }

    /** Child elements with the given local name, whatever namespace prefix the file uses. */
    private static List<org.w3c.dom.Element> elements(org.w3c.dom.Element parent, String localName) {
        org.w3c.dom.NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        List<org.w3c.dom.Element> list = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) list.add((org.w3c.dom.Element) nodes.item(i));
        return list;
    }

    /** Text of a string item: joins all <t> runs (rich text), ignoring phonetic hints. */
    private static String text(org.w3c.dom.Element item) {
        StringBuilder sb = new StringBuilder();
        for (org.w3c.dom.Element t : elements(item, "t")) {
            org.w3c.dom.Node parent = t.getParentNode();
            if (parent != null && "rPh".equals(parent.getLocalName())) continue;
            sb.append(t.getTextContent());
        }
        return sb.toString();
    }

    /** "B12" -> 1 (zero-based column). */
    private static int columnIndex(String reference) {
        int column = 0;
        for (char ch : reference.toCharArray()) {
            if (!Character.isLetter(ch)) break;
            column = column * 26 + (Character.toUpperCase(ch) - 'A' + 1);
        }
        return column - 1;
    }

    /** Shows whole numbers without ".0", like Excel does (e.g. an ID typed as 1). */
    private static String number(String raw) {
        try {
            java.math.BigDecimal d = new java.math.BigDecimal(raw.trim());
            return d.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return raw;
        }
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
    public static void validate(List<Question> questions){
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
