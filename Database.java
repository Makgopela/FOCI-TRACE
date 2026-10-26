package za.co.focigroup.focitrace;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

public final class Database implements AutoCloseable {
    private final String url;
    private Connection keepAlive;

    public Database(Path dataDir) throws Exception {
        Files.createDirectories(dataDir);
        Class.forName("org.hsqldb.jdbcDriver");
        this.url = "jdbc:hsqldb:file:" + dataDir.resolve("focitrace").toAbsolutePath() + ";shutdown=true";
        this.keepAlive = DriverManager.getConnection(url, "sa", "");
        initialize();
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    private void initialize() throws SQLException {
        try (Statement st = keepAlive.createStatement()) {
            createTable(st, "USERS", "CREATE TABLE users (" +
                    "id INTEGER IDENTITY PRIMARY KEY, username VARCHAR(80) NOT NULL, display_name VARCHAR(120) NOT NULL," +
                    "role VARCHAR(30) NOT NULL, password_hash VARCHAR(300) NOT NULL, salt VARCHAR(200) NOT NULL," +
                    "iterations INTEGER NOT NULL, must_change_password BOOLEAN DEFAULT TRUE NOT NULL, active BOOLEAN DEFAULT TRUE NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            createTable(st, "RFQS", "CREATE TABLE rfqs (" +
                    "id VARCHAR(30) PRIMARY KEY, title VARCHAR(500) NOT NULL, owner VARCHAR(120), priority VARCHAR(30), status VARCHAR(80)," +
                    "closing_date DATE, source VARCHAR(800), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            createTable(st, "SUPPLIERS", "CREATE TABLE suppliers (" +
                    "id VARCHAR(30) PRIMARY KEY, name VARCHAR(300) NOT NULL, category VARCHAR(200), contact_role VARCHAR(120)," +
                    "active BOOLEAN DEFAULT TRUE NOT NULL, notes VARCHAR(1200), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
            createTable(st, "REQUIREMENTS", "CREATE TABLE requirements (" +
                    "id VARCHAR(30) PRIMARY KEY, rfq_id VARCHAR(30) NOT NULL, material_no VARCHAR(80), description VARCHAR(3000) NOT NULL," +
                    "qty DECIMAL(18,3), mandatory BOOLEAN DEFAULT TRUE NOT NULL, tech_class VARCHAR(80), spec_ref VARCHAR(160)," +
                    "pricing_required BOOLEAN DEFAULT TRUE NOT NULL, supplier_docs_required VARCHAR(1200), technical_review_status VARCHAR(50) DEFAULT 'Pending' NOT NULL," +
                    "source_ref VARCHAR(1200), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL," +
                    "FOREIGN KEY (rfq_id) REFERENCES rfqs(id))");
            createTable(st, "QUOTATIONS", "CREATE TABLE quotations (" +
                    "id VARCHAR(30) PRIMARY KEY, rfq_id VARCHAR(30) NOT NULL, supplier_id VARCHAR(30), quotation_ref VARCHAR(300)," +
                    "date_received DATE, validity_date DATE, status VARCHAR(60) NOT NULL, evidence_basis VARCHAR(1600)," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL," +
                    "FOREIGN KEY (rfq_id) REFERENCES rfqs(id), FOREIGN KEY (supplier_id) REFERENCES suppliers(id))");
            createTable(st, "QUOTATION_ITEMS", "CREATE TABLE quotation_items (" +
                    "id INTEGER IDENTITY PRIMARY KEY, quotation_id VARCHAR(30) NOT NULL, requirement_id VARCHAR(30) NOT NULL," +
                    "quoted BOOLEAN DEFAULT FALSE NOT NULL, unit_price DECIMAL(18,2), currency VARCHAR(12), lead_time_days INTEGER, notes VARCHAR(1000)," +
                    "FOREIGN KEY (quotation_id) REFERENCES quotations(id), FOREIGN KEY (requirement_id) REFERENCES requirements(id))");
            createTable(st, "DOCUMENTS", "CREATE TABLE documents (" +
                    "id VARCHAR(30) PRIMARY KEY, rfq_id VARCHAR(30) NOT NULL, requirement_id VARCHAR(30) NOT NULL, supplier_id VARCHAR(30), quotation_id VARCHAR(30)," +
                    "doc_type VARCHAR(300) NOT NULL, reference_name VARCHAR(500), version VARCHAR(80), status VARCHAR(60) NOT NULL, date_received DATE," +
                    "reviewer VARCHAR(120), source_notes VARCHAR(2000), file_path VARCHAR(1200), original_filename VARCHAR(500), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL," +
                    "FOREIGN KEY (rfq_id) REFERENCES rfqs(id), FOREIGN KEY (requirement_id) REFERENCES requirements(id)," +
                    "FOREIGN KEY (supplier_id) REFERENCES suppliers(id), FOREIGN KEY (quotation_id) REFERENCES quotations(id))");
            createTable(st, "AUDIT_LOG", "CREATE TABLE audit_log (" +
                    "id INTEGER IDENTITY PRIMARY KEY, username VARCHAR(80), action VARCHAR(120) NOT NULL, entity_type VARCHAR(80), entity_id VARCHAR(80)," +
                    "details VARCHAR(3000), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)");
        }
        seed();
    }

    private void createTable(Statement st, String tableName, String ddl) throws SQLException {
        try (ResultSet rs = keepAlive.getMetaData().getTables(null, null, tableName.toUpperCase(Locale.ROOT), new String[]{"TABLE"})) {
            if (rs.next()) return;
        }
        st.executeUpdate(ddl);
    }

    private boolean exists(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = keepAlive.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private void insert(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = keepAlive.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        }
    }

    private void seed() throws SQLException {
        seedUser("admin", "FOCI TRACE Administrator", "ADMIN", "ChangeMe!2026");
        seedUser("procurement", "Procurement User", "PROCUREMENT", "Procurement!2026");
        seedUser("engineering", "Engineering User", "ENGINEERING", "Engineering!2026");
        seedUser("viewer", "Read-only Viewer", "VIEWER", "Viewer!2026");

        String[][] rfqs = {
                {"RFQ-001","E3509GXNOU - Cables & Wires - Koeberg Nuclear Power Station","Procurement","High","Clarification Required","E3509GXNOU Supplier RFQ Pack / Tender Control Tracker"},
                {"RFQ-002","E3509GXNOU - Automation / Electronic Control Components - Koeberg","Engineering","High","Open","E3509GXNOU Supplier RFQ Pack / Tender Control Tracker"},
                {"RFQ-003","E3509GXNOU - EST3 Fire Detection Control Components - Koeberg","Engineering","High","Open","E3509GXNOU Supplier RFQ Pack / Tender Control Tracker"}
        };
        for (String[] r : rfqs) {
            if (!exists("SELECT 1 FROM rfqs WHERE id=?", r[0])) {
                insert("INSERT INTO rfqs(id,title,owner,priority,status,source) VALUES(?,?,?,?,?,?)", (Object[]) r);
            }
        }

        String[][] suppliers = {
                {"SUP-001","Springbok Electric","Cables & Wires","Technical Sales","Manufacturer referenced in source requirement for material 144751."},
                {"SUP-002","ATC","Control Cables","Technical Sales","Manufacturer referenced in source requirement for material 144834."},
                {"SUP-003","Westinghouse Electric Company / NAMCO","Cable Assemblies","Sales","Source requirement identifies Westinghouse as supplier and NAMCO as manufacturer for material 502724, P/N EC290-44020."},
                {"SUP-004","York / Johnson Controls (JCI)","Electronic Control Components","Technical Sales","Manufacturer referenced for material 717834, P/N 031-03601-002."},
                {"SUP-005","Multivid","Electronic Control / PCB","Sales","Supplier referenced for material 554089, P/N H101429."},
                {"SUP-006","Edwards / UTC Fire & Security","Fire Detection Control Components","Technical Sales","Edwards manufacturer / UTC Fire & Security suggested supplier referenced for EST3 components."}
        };
        for (String[] r : suppliers) {
            if (!exists("SELECT 1 FROM suppliers WHERE id=?", r[0])) {
                insert("INSERT INTO suppliers(id,name,category,contact_role,active,notes) VALUES(?,?,?,?,TRUE,?)", (Object[]) r);
            }
        }

        Object[][] reqs = {
                {"REQ-001","RFQ-001","144746","CABLE ELECT:250 V;3;COPPER;2.5 MM2;PVC - cabtyre cable, 250 V / 20 A, 3 copper conductors, 2.5 mm², PVC conductor insulation and PVC covering; part number K1F2 50033C203S3BBC1.",new BigDecimal("4500"),"Q4/COC","-","Product datasheet + Certificate of Conformance","Pending","E3509GXNOU Supplier RFQ Pack p.3"},
                {"REQ-002","RFQ-001","144751","WIRE ELECT:BUILDING;1.5 MM2;BLUE;CU;PVC - blue PVC insulated building wire, 1.5 mm², 600/1000 V, 7 strands of 0.53 mm high-conductivity annealed copper; manufacturer referenced as Springbok Electric.",new BigDecimal("2300"),"Q3/1E","DSG-315-200","Product datasheet + applicable Certificate of Conformance / test certificate","Pending","E3509GXNOU Supplier RFQ Pack p.3"},
                {"REQ-003","RFQ-001","144834","CABLE ELECT:1000;4;COPPER;1.5 MM2;4 CORE - non-armoured control cable, 1000 V, PVC insulation, 4-core copper, 1.5 mm²; manufacturer referenced as ATC.",new BigDecimal("300"),"Q3/L3","DSG-311-104","Product datasheet + applicable Certificate of Conformance / test certificate","Pending","E3509GXNOU Supplier RFQ Pack p.3"},
                {"REQ-004","RFQ-001","502724","CABLE ASSY:0.36 IN;LG 20 FT - plug-in cable assembly with 4-pin female quick connector; Westinghouse Electric Company / NAMCO, P/N EC290-44020, 0.36 in diameter, 20 ft length, radiation-resistant elastomer.",new BigDecimal("24"),"Q3/1E","DSG-312-201","Product datasheet + applicable Certificate of Conformance / test certificate","Pending","E3509GXNOU tracker: DSG-312-201 referenced but missing from supplied tender ZIP"},
                {"REQ-005","RFQ-002","717834","CARD, ELEC:MICROBOARD;PROGRAM STORAGE - York/JCI P/N 031-03601-002; electronic microboard for program storage, programmed OptiView YK.",new BigDecimal("3"),"Q4/L3","-","Product datasheet + Certificate of Conformance / test certificate","Pending","E3509GXNOU Supplier RFQ Pack p.5"},
                {"REQ-006","RFQ-002","554089","PROCESSOR:ZONE;11-14 VDC - Sabrifonic PCB zone processor/controller; supplier referenced as Multivid, P/N H101429; 11-14 VDC, 300 mA, PCB construction.",new BigDecimal("1"),"Q4/COC","-","Product datasheet + Certificate of Conformance / test certificate","Pending","E3509GXNOU Tender Control Tracker / grouped BOQ"},
                {"REQ-007","RFQ-003","746993","PLUG:EST3 CENTRAL PROCESSOR;FR-4 - Edwards EST3 central processor for fire detection; code 3-CPU3; FR-4 construction.",new BigDecimal("6"),"Q4/L3","-","Product datasheet + Certificate of Conformance / test certificate","Pending","E3509GXNOU Supplier RFQ Pack p.5"},
                {"REQ-008","RFQ-003","746994","PLUG:EST3 POWER SUPPLY;FR-4 - Edwards EST3 power supply for fire detection; code 3-PPS/M; FR-4 construction.",new BigDecimal("6"),"Q4/L3","-","Product datasheet + Certificate of Conformance / test certificate","Pending","E3509GXNOU Supplier RFQ Pack p.5"}
        };
        for (Object[] r : reqs) {
            if (!exists("SELECT 1 FROM requirements WHERE id=?", r[0])) {
                insert("INSERT INTO requirements(id,rfq_id,material_no,description,qty,mandatory,tech_class,spec_ref,pricing_required,supplier_docs_required,technical_review_status,source_ref) VALUES(?,?,?,?,?,TRUE,?,?,TRUE,?,?,?)", r);
            }
        }

        String[][] quotes = {
                {"QUO-001","RFQ-001","SUP-001","NQ - no supplier quote captured","Not Quoted","Tender control tracker records the applicable line as NOT QUOTED."},
                {"QUO-002","RFQ-001","SUP-002","NQ - no supplier quote captured","Not Quoted","Tender control tracker records the applicable line as NOT QUOTED."},
                {"QUO-003","RFQ-001","SUP-003","NQ - no supplier quote captured","Not Quoted","Tender control tracker records material 502724 as NOT QUOTED; specification DSG-312-201 also missing from supplied pack."},
                {"QUO-004","RFQ-002","SUP-004","NQ - no supplier quote captured","Not Quoted","Tender control tracker records the applicable line as NOT QUOTED."},
                {"QUO-005","RFQ-002","SUP-005","NQ - no supplier quote captured","Not Quoted","Tender control tracker records the applicable line as NOT QUOTED."},
                {"QUO-006","RFQ-003","SUP-006","NQ - no supplier quote captured","Not Quoted","Supplier RFQ pack contains the requirements; no supplier quotation evidence was available in the source records used here."}
        };
        for (String[] r : quotes) {
            if (!exists("SELECT 1 FROM quotations WHERE id=?", r[0])) {
                insert("INSERT INTO quotations(id,rfq_id,supplier_id,quotation_ref,status,evidence_basis) VALUES(?,?,?,?,?,?)", (Object[]) r);
            }
        }
        seedQuoteItem("QUO-001","REQ-002");
        seedQuoteItem("QUO-002","REQ-003");
        seedQuoteItem("QUO-003","REQ-004");
        seedQuoteItem("QUO-004","REQ-005");
        seedQuoteItem("QUO-005","REQ-006");
        seedQuoteItem("QUO-006","REQ-007");
        seedQuoteItem("QUO-006","REQ-008");

        Object[][] docs = {
                {"DOC-001","RFQ-001","REQ-001",null,null,"Certificate of Conformance / Product Datasheet","Required by Q4/COC",null,"Outstanding",null,"Procurement","Supplier evidence required; not present in source records."},
                {"DOC-002","RFQ-001","REQ-002","SUP-001","QUO-001","Technical Specification","DSG-315-200","Tender pack","Received",null,"Technical","Specification is referenced and included in tender pack; supplier datasheet/COC remains outstanding."},
                {"DOC-003","RFQ-001","REQ-003","SUP-002","QUO-002","Technical Specification","DSG-311-104","Tender pack","Received",null,"Technical","Specification is referenced and included in tender pack; supplier datasheet/COC remains outstanding."},
                {"DOC-004","RFQ-001","REQ-004","SUP-003","QUO-003","Technical Specification","DSG-312-201",null,"Outstanding",null,"Technical","Tender tracker records this specification as referenced but NOT FOUND in supplied tender ZIP."},
                {"DOC-005","RFQ-002","REQ-005","SUP-004","QUO-004","Product Datasheet / Certificate of Conformance","York/JCI 031-03601-002",null,"Outstanding",null,"Engineering","Required supplier technical evidence; no received supplier document recorded."},
                {"DOC-006","RFQ-002","REQ-006","SUP-005","QUO-005","Product Datasheet / Certificate of Conformance","Multivid H101429",null,"Outstanding",null,"Engineering","Required supplier technical evidence; no received supplier document recorded."},
                {"DOC-007","RFQ-003","REQ-007","SUP-006","QUO-006","Product Datasheet / Certificate of Conformance","Edwards 3-CPU3",null,"Outstanding",null,"Engineering","Required supplier technical evidence; no received supplier document recorded."},
                {"DOC-008","RFQ-003","REQ-008","SUP-006","QUO-006","Product Datasheet / Certificate of Conformance","Edwards 3-PPS/M",null,"Outstanding",null,"Engineering","Required supplier technical evidence; no received supplier document recorded."}
        };
        for (Object[] r : docs) {
            if (!exists("SELECT 1 FROM documents WHERE id=?", r[0])) {
                insert("INSERT INTO documents(id,rfq_id,requirement_id,supplier_id,quotation_id,doc_type,reference_name,version,status,date_received,reviewer,source_notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", r);
            }
        }
    }

    private void seedUser(String username, String displayName, String role, String password) throws SQLException {
        if (exists("SELECT 1 FROM users WHERE username=?", username)) return;
        Passwords.Hash h = Passwords.hash(password);
        insert("INSERT INTO users(username,display_name,role,password_hash,salt,iterations,must_change_password,active) VALUES(?,?,?,?,?,?,TRUE,TRUE)",
                username, displayName, role, h.hashBase64(), h.saltBase64(), h.iterations());
    }

    private void seedQuoteItem(String quoteId, String reqId) throws SQLException {
        if (!exists("SELECT 1 FROM quotation_items WHERE quotation_id=? AND requirement_id=?", quoteId, reqId)) {
            insert("INSERT INTO quotation_items(quotation_id,requirement_id,quoted,notes) VALUES(?,?,FALSE,'Seeded from workbook: no supplier quote captured')", quoteId, reqId);
        }
    }

    public Optional<Map<String,Object>> user(String username) throws SQLException {
        List<Map<String,Object>> rows = query("SELECT * FROM users WHERE username=? AND active=TRUE", username);
        return rows.stream().findFirst();
    }

    public List<Map<String,Object>> query(String sql, Object... params) throws SQLException {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String,Object>> out = new ArrayList<>();
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    for (int i=1;i<=md.getColumnCount();i++) row.put(md.getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }

    public int update(String sql, Object... params) throws SQLException {
        try (Connection c = connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    public void audit(String username, String action, String entityType, String entityId, String details) {
        try { update("INSERT INTO audit_log(username,action,entity_type,entity_id,details) VALUES(?,?,?,?,?)", username, action, entityType, entityId, details); }
        catch (SQLException ignored) {}
    }

    public int nextSequence(String table, String prefix) throws SQLException {
        String allowed = switch (table) {
            case "rfqs" -> "rfqs";
            case "requirements" -> "requirements";
            case "suppliers" -> "suppliers";
            case "quotations" -> "quotations";
            case "documents" -> "documents";
            default -> throw new IllegalArgumentException("Invalid sequence table");
        };
        List<Map<String,Object>> rows = query("SELECT id FROM " + allowed);
        int max = 0;
        for (Map<String,Object> row : rows) {
            String id = Objects.toString(row.get("id"), "");
            if (id.startsWith(prefix + "-")) {
                try { max = Math.max(max, Integer.parseInt(id.substring(prefix.length()+1))); } catch (Exception ignored) {}
            }
        }
        return max + 1;
    }

    public String newId(String table, String prefix) throws SQLException {
        return "%s-%03d".formatted(prefix, nextSequence(table, prefix));
    }

    public List<TraceRow> traceability(String search) throws SQLException {
        String sql = "SELECT * FROM requirements ";
        List<Object> params = new ArrayList<>();
        if (search != null && !search.isBlank()) {
            sql += "WHERE UPPER(id) LIKE ? OR UPPER(material_no) LIKE ? OR UPPER(description) LIKE ? ";
            String term = "%" + search.toUpperCase(Locale.ROOT) + "%";
            params.add(term); params.add(term); params.add(term);
        }
        sql += "ORDER BY id";
        List<Map<String,Object>> reqRows = query(sql, params.toArray());
        List<TraceRow> out = new ArrayList<>();
        for (Map<String,Object> r : reqRows) {
            String reqId = s(r,"id");
            List<Map<String,Object>> quoteRows = query(
                    "SELECT qi.*, q.id quotation_id, q.status quotation_status, q.supplier_id, s.name supplier_name " +
                    "FROM quotation_items qi JOIN quotations q ON q.id=qi.quotation_id LEFT JOIN suppliers s ON s.id=q.supplier_id " +
                    "WHERE qi.requirement_id=? ORDER BY qi.quoted DESC, qi.id DESC", reqId);
            Map<String,Object> bestQuote = quoteRows.isEmpty() ? Map.of() : quoteRows.getFirst();
            boolean quoted = Boolean.TRUE.equals(bestQuote.get("quoted"));

            List<Map<String,Object>> docs = query("SELECT * FROM documents WHERE requirement_id=? ORDER BY id", reqId);
            String specRef = s(r,"spec_ref");
            boolean specRequired = specRef != null && !specRef.isBlank() && !"-".equals(specRef);
            boolean specReceived = !specRequired;
            boolean supplierEvidenceReceived = false;
            for (Map<String,Object> d : docs) {
                String status = s(d,"status");
                String type = s(d,"doc_type");
                String ref = s(d,"reference_name");
                if (specRequired && specRef.equalsIgnoreCase(ref) && "Received".equalsIgnoreCase(status)) specReceived = true;
                if ("Received".equalsIgnoreCase(status) && type != null && !type.toLowerCase(Locale.ROOT).contains("technical specification")) supplierEvidenceReceived = true;
            }

            String review = s(r,"technical_review_status");
            TraceabilityEngine.Outcome result = TraceabilityEngine.evaluate(
                    new TraceabilityEngine.Input(quoted, specRequired, specReceived, supplierEvidenceReceived, review), specRef);
            out.add(new TraceRow(reqId, s(r,"rfq_id"), s(r,"material_no"), s(r,"description"),
                    s(bestQuote,"supplier_id"), s(bestQuote,"supplier_name"), s(bestQuote,"quotation_id"),
                    result.pricingStatus(), specRef, result.specificationAvailability(), result.documentStatus(), review,
                    result.overallStatus(), result.outstandingAction(), result.statusCalculation()));
        }
        return out;
    }

    public Dashboard dashboard() throws SQLException {
        int rfqs = n("SELECT COUNT(*) c FROM rfqs");
        int reqs = n("SELECT COUNT(*) c FROM requirements");
        List<TraceRow> traces = traceability(null);
        int complete=0,pending=0,incomplete=0,clarification=0,missingQuotes=0,missingDocs=0,missingSpecs=0;
        for (TraceRow t : traces) {
            switch (t.overallStatus()) {
                case "Complete" -> complete++;
                case "Pending Review" -> pending++;
                case "Clarification Required" -> clarification++;
                default -> incomplete++;
            }
            if ("Not Quoted".equals(t.pricingStatus())) missingQuotes++;
            if (!"Received".equals(t.documentStatus())) missingDocs++;
            if ("Missing".equals(t.specificationAvailability())) missingSpecs++;
        }
        return new Dashboard(rfqs, reqs, complete, pending, incomplete, clarification, missingQuotes, missingDocs, missingSpecs);
    }

    private int n(String sql, Object... params) throws SQLException {
        Object v = query(sql, params).getFirst().values().iterator().next();
        return ((Number)v).intValue();
    }

    public void changePassword(String username, String newPassword) throws SQLException {
        Passwords.Hash h = Passwords.hash(newPassword);
        update("UPDATE users SET password_hash=?, salt=?, iterations=?, must_change_password=FALSE WHERE username=?",
                h.hashBase64(), h.saltBase64(), h.iterations(), username);
        audit(username, "PASSWORD_CHANGED", "USER", username, "Password changed by user");
    }

    public void shutdown() {
        try (Statement st = keepAlive.createStatement()) { st.execute("SHUTDOWN"); } catch (Exception ignored) {}
        try { keepAlive.close(); } catch (Exception ignored) {}
    }

    @Override public void close() { shutdown(); }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i=0;i<params.length;i++) {
            Object p = params[i];
            if (p instanceof LocalDate d) ps.setDate(i+1, java.sql.Date.valueOf(d));
            else ps.setObject(i+1, p);
        }
    }

    public static String s(Map<String,Object> row, String key) {
        Object v = row.get(key.toLowerCase(Locale.ROOT));
        return v == null ? null : v.toString();
    }

    public record TraceRow(String requirementId, String rfqId, String materialNo, String description,
                           String supplierId, String supplierName, String quotationId, String pricingStatus,
                           String specificationRef, String specificationAvailability, String documentStatus,
                           String technicalReview, String overallStatus, String outstandingAction, String statusCalculation) {}

    public record Dashboard(int rfqs, int requirements, int complete, int pendingReview, int incomplete,
                            int clarificationRequired, int missingQuotations, int missingDocuments, int missingSpecifications) {}
}
