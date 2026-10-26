package za.co.focigroup.focitrace;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;

public final class FociTraceApplication {
    private final Database db;
    private final SessionManager sessions;
    private final Path uploadDir;
    private final HttpServer server;

    public FociTraceApplication(int port, Path dataDir, Path uploadDir, boolean secureCookie) throws Exception {
        this.db = new Database(dataDir);
        this.sessions = new SessionManager(secureCookie);
        this.uploadDir = uploadDir.toAbsolutePath().normalize();
        Files.createDirectories(this.uploadDir);
        this.server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        routes();
        Runtime.getRuntime().addShutdownHook(new Thread(db::shutdown));
    }

    private void routes() {
        ctx("/login", this::login);
        ctx("/logout", this::logout);
        ctx("/change-password", this::changePassword);
        ctx("/rfqs/create", this::createRfq);
        ctx("/rfqs", this::rfqs);
        ctx("/requirements/create", this::createRequirement);
        ctx("/requirements/review", this::reviewRequirement);
        ctx("/requirements", this::requirements);
        ctx("/suppliers/create", this::createSupplier);
        ctx("/suppliers", this::suppliers);
        ctx("/quotations/create", this::createQuotation);
        ctx("/quotations", this::quotations);
        ctx("/documents/upload", this::uploadDocument);
        ctx("/documents/download", this::downloadDocument);
        ctx("/documents", this::documents);
        ctx("/traceability", this::traceability);
        ctx("/audit", this::audit);
        ctx("/users/create", this::createUser);
        ctx("/users", this::users);
        ctx("/health", this::health);
        ctx("/", this::dashboard);
    }

    private void ctx(String path, Handler h) {
        server.createContext(path, ex -> {
            try { h.handle(ex); }
            catch (Stop stop) { return; }
            catch (Exception e) {
                e.printStackTrace();
                try { Web.html(ex, 500, Html.layout("Error", sessions.get(ex).orElse(null), "<section class='panel'><h2>Something went wrong</h2><p>"+Web.e(e.getMessage())+"</p></section>", null)); }
                catch (Exception ignored) {}
            }
        });
    }

    public void start() {
        server.start();
        System.out.println("FOCI TRACE running on http://localhost:" + server.getAddress().getPort());
        System.out.println("Seed login: admin / ChangeMe!2026 (password change required)");
    }

    private void login(HttpExchange ex) throws Exception {
        if (isGet(ex)) { Web.html(ex, 200, Html.login(null)); return; }
        if (!isPost(ex)) { methodNotAllowed(ex); return; }
        Map<String,String> f = Web.form(ex);
        String username = Web.param(f,"username").toLowerCase(Locale.ROOT);
        String password = Web.param(f,"password");
        Optional<Map<String,Object>> rowOpt = db.user(username);
        if (rowOpt.isEmpty()) { Web.html(ex,401,Html.login("Invalid username or password.")); return; }
        Map<String,Object> row = rowOpt.get();
        int iterations = ((Number)row.get("iterations")).intValue();
        if (!Passwords.verify(password, Objects.toString(row.get("password_hash")), Objects.toString(row.get("salt")), iterations)) {
            db.audit(username,"LOGIN_FAILED","USER",username,"Invalid password");
            Web.html(ex,401,Html.login("Invalid username or password.")); return;
        }
        SessionManager.Session s = sessions.create(username, Objects.toString(row.get("display_name")), Objects.toString(row.get("role")), Boolean.TRUE.equals(row.get("must_change_password")));
        sessions.attach(ex,s);
        db.audit(username,"LOGIN","USER",username,"Successful sign in");
        Web.redirect(ex, s.mustChangePassword()?"/change-password":"/");
    }

    private void logout(HttpExchange ex) throws Exception {
        sessions.get(ex).ifPresent(s -> db.audit(s.username(),"LOGOUT","USER",s.username(),"Signed out"));
        sessions.destroy(ex); Web.redirect(ex,"/login");
    }

    private void changePassword(HttpExchange ex) throws Exception {
        SessionManager.Session s = auth(ex); if (s==null) return;
        if (isGet(ex)) { Web.html(ex,200,Html.layout("Change password",s,Html.changePassword(s,null),null)); return; }
        if (!isPost(ex)) { methodNotAllowed(ex); return; }
        Map<String,String> f=Web.form(ex); if(!csrf(s,f)){forbidden(ex);return;}
        String p=Web.param(f,"password"), c=Web.param(f,"confirm");
        if(p.length()<12){Web.html(ex,400,Html.layout("Change password",s,Html.changePassword(s,"Password must be at least 12 characters."),null));return;}
        if(!p.equals(c)){Web.html(ex,400,Html.layout("Change password",s,Html.changePassword(s,"Passwords do not match."),null));return;}
        db.changePassword(s.username(),p); sessions.markPasswordChanged(s); Web.redirect(ex,"/?msg=Password+updated");
    }

    private void dashboard(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex); if(s==null)return; if(!isGet(ex)){methodNotAllowed(ex);return;}
        Database.Dashboard d=db.dashboard();
        List<Database.TraceRow> attention=db.traceability(null).stream().filter(t->!"Complete".equals(t.overallStatus())).limit(8).toList();
        Web.html(ex,200,Html.layout("Dashboard",s,Html.dashboard(d,attention),msg(ex)));
    }

    private void rfqs(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex); if(s==null)return;
        List<Map<String,Object>> rows=db.query("SELECT * FROM rfqs ORDER BY id");
        Web.html(ex,200,Html.layout("RFQs",s,Html.rfqs(rows,s),msg(ex)));
    }

    private void createRfq(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN","PROCUREMENT"); if(s==null)return; requirePost(ex); Map<String,String> f=Web.form(ex); if(!csrf(s,f)){forbidden(ex);return;}
        String id=db.newId("rfqs","RFQ");
        db.update("INSERT INTO rfqs(id,title,owner,priority,status,source) VALUES(?,?,?,?,?,?)", id, req(f,"title"), Web.param(f,"owner"), Web.param(f,"priority"), Web.param(f,"status"), Web.param(f,"source"));
        db.audit(s.username(),"CREATE","RFQ",id,"Created RFQ: "+Web.param(f,"title")); Web.redirect(ex,"/rfqs?msg=RFQ+created");
    }

    private void requirements(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex); if(s==null)return; String q=Web.param(Web.query(ex),"q");
        String sql="SELECT * FROM requirements"; Object[] params=new Object[0];
        if(!q.isBlank()){sql+=" WHERE UPPER(id) LIKE ? OR UPPER(material_no) LIKE ? OR UPPER(description) LIKE ?";String term="%"+q.toUpperCase(Locale.ROOT)+"%";params=new Object[]{term,term,term};}
        sql+=" ORDER BY id";
        Web.html(ex,200,Html.layout("Requirements",s,Html.requirements(db.query(sql,params),db.query("SELECT id,title FROM rfqs ORDER BY id"),s,q),msg(ex)));
    }

    private void createRequirement(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN","PROCUREMENT");if(s==null)return;requirePost(ex);Map<String,String>f=Web.form(ex);if(!csrf(s,f)){forbidden(ex);return;}
        String id=db.newId("requirements","REQ"); BigDecimal qty=decimal(Web.param(f,"qty"));
        db.update("INSERT INTO requirements(id,rfq_id,material_no,description,qty,mandatory,tech_class,spec_ref,pricing_required,supplier_docs_required,technical_review_status,source_ref) VALUES(?,?,?,?,?,TRUE,?,?,TRUE,?,?,?)",
                id,req(f,"rfq_id"),req(f,"material_no"),req(f,"description"),qty,Web.param(f,"tech_class"),blankDash(Web.param(f,"spec_ref")),Web.param(f,"supplier_docs_required"),Web.param(f,"review"),Web.param(f,"source_ref"));
        db.audit(s.username(),"CREATE","REQUIREMENT",id,"Material "+Web.param(f,"material_no"));Web.redirect(ex,"/requirements?msg=Requirement+created");
    }

    private void reviewRequirement(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN","ENGINEERING");if(s==null)return;requirePost(ex);Map<String,String>f=Web.form(ex);if(!csrf(s,f)){forbidden(ex);return;}
        String id=req(f,"id"), review=req(f,"review");
        db.update("UPDATE requirements SET technical_review_status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?",review,id);
        db.audit(s.username(),"TECHNICAL_REVIEW","REQUIREMENT",id,"Technical review changed to "+review);Web.redirect(ex,"/requirements?msg=Technical+review+updated");
    }

    private void suppliers(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex);if(s==null)return;Web.html(ex,200,Html.layout("Suppliers",s,Html.suppliers(db.query("SELECT * FROM suppliers ORDER BY id"),s),msg(ex)));
    }

    private void createSupplier(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN","PROCUREMENT");if(s==null)return;requirePost(ex);Map<String,String>f=Web.form(ex);if(!csrf(s,f)){forbidden(ex);return;}
        String id=db.newId("suppliers","SUP");db.update("INSERT INTO suppliers(id,name,category,contact_role,active,notes) VALUES(?,?,?,?,TRUE,?)",id,req(f,"name"),Web.param(f,"category"),Web.param(f,"contact_role"),Web.param(f,"notes"));
        db.audit(s.username(),"CREATE","SUPPLIER",id,Web.param(f,"name"));Web.redirect(ex,"/suppliers?msg=Supplier+created");
    }

    private void quotations(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex);if(s==null)return;
        List<Map<String,Object>> q=db.query("SELECT q.*, s.name supplier_name FROM quotations q LEFT JOIN suppliers s ON s.id=q.supplier_id ORDER BY q.id");
        Web.html(ex,200,Html.layout("Quotations",s,Html.quotations(q,db.query("SELECT id,title FROM rfqs ORDER BY id"),db.query("SELECT id,name FROM suppliers ORDER BY id"),db.query("SELECT id,material_no FROM requirements ORDER BY id"),s),msg(ex)));
    }

    private void createQuotation(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN","PROCUREMENT");if(s==null)return;requirePost(ex);Map<String,String>f=Web.form(ex);if(!csrf(s,f)){forbidden(ex);return;}
        String rfqId=req(f,"rfq_id"), requirementId=req(f,"requirement_id");
        if(db.query("SELECT 1 FROM requirements WHERE id=? AND rfq_id=?",requirementId,rfqId).isEmpty()) throw new IllegalArgumentException("Requirement does not belong to selected RFQ");
        String id=db.newId("quotations","QUO"); String status=req(f,"status"); boolean quoted="true".equals(Web.param(f,"quoted"));
        LocalDate received="Received".equals(status)||"Partial".equals(status)?LocalDate.now():null;
        db.update("INSERT INTO quotations(id,rfq_id,supplier_id,quotation_ref,date_received,status,evidence_basis) VALUES(?,?,?,?,?,?,?)",id,rfqId,nullable(Web.param(f,"supplier_id")),req(f,"quotation_ref"),received,status,Web.param(f,"evidence_basis"));
        db.update("INSERT INTO quotation_items(quotation_id,requirement_id,quoted,unit_price,currency,lead_time_days,notes) VALUES(?,?,?,?,?,?,?)",id,requirementId,quoted,decimal(Web.param(f,"unit_price")),nullable(Web.param(f,"currency")),integer(Web.param(f,"lead_time_days")),Web.param(f,"evidence_basis"));
        db.audit(s.username(),"CREATE","QUOTATION",id,"Linked to "+requirementId+"; quoted="+quoted);Web.redirect(ex,"/quotations?msg=Quotation+saved");
    }

    private void documents(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex);if(s==null)return;
        List<Map<String,Object>> docs=db.query("SELECT * FROM documents ORDER BY id");
        Web.html(ex,200,Html.layout("Documents",s,Html.documents(docs,db.query("SELECT id,material_no FROM requirements ORDER BY id"),db.query("SELECT id,name FROM suppliers ORDER BY id"),db.query("SELECT id,quotation_ref FROM quotations ORDER BY id"),s),msg(ex)));
    }

    private void uploadDocument(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN","PROCUREMENT","ENGINEERING");if(s==null)return;requirePost(ex);
        Map<String,Multipart.Part> p=Multipart.parse(ex,10*1024*1024);
        if(!sessions.validCsrf(s,text(p,"csrf"))){forbidden(ex);return;}
        String requirementId=textReq(p,"requirement_id");
        Map<String,Object> reqRow=db.query("SELECT rfq_id FROM requirements WHERE id=?",requirementId).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Requirement not found"));
        String id=db.newId("documents","DOC"); String filePath=null, original=null;
        Multipart.Part attachment=p.get("attachment");
        if(attachment!=null && attachment.filename()!=null && !attachment.filename().isBlank() && attachment.data().length>0){
            original=safeFilename(attachment.filename()); validateExtension(original);
            String stored=UUID.randomUUID()+"_"+original; Path target=uploadDir.resolve(stored).normalize(); if(!target.startsWith(uploadDir))throw new SecurityException("Invalid upload path");
            Files.write(target,attachment.data(),StandardOpenOption.CREATE_NEW); filePath=target.toString();
        }
        String status=textReq(p,"status"); LocalDate received="Received".equals(status)?LocalDate.now():null;
        db.update("INSERT INTO documents(id,rfq_id,requirement_id,supplier_id,quotation_id,doc_type,reference_name,version,status,date_received,reviewer,source_notes,file_path,original_filename) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,Objects.toString(reqRow.get("rfq_id")),requirementId,nullable(text(p,"supplier_id")),nullable(text(p,"quotation_id")),textReq(p,"doc_type"),textReq(p,"reference_name"),nullable(text(p,"version")),status,received,text(p,"reviewer"),text(p,"source_notes"),filePath,original);
        db.audit(s.username(),"CREATE","DOCUMENT",id,"Requirement "+requirementId+"; status="+status+(original==null?"":"; file="+original)); Web.redirect(ex,"/documents?msg=Document+saved");
    }

    private void downloadDocument(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex);if(s==null)return;String id=Web.param(Web.query(ex),"id");
        Map<String,Object> row=db.query("SELECT file_path, original_filename FROM documents WHERE id=?",id).stream().findFirst().orElseThrow(()->new IllegalArgumentException("Document not found"));
        String fp=Objects.toString(row.get("file_path"),"");if(fp.isBlank()){Web.html(ex,404,Html.layout("Not found",s,"<section class='panel'><p>No file attachment is stored for this document record.</p></section>",null));return;}
        Path path=Path.of(fp).toAbsolutePath().normalize();if(!path.startsWith(uploadDir)||!Files.exists(path))throw new SecurityException("Attachment unavailable");
        Web.bytes(ex,200,Files.probeContentType(path),Files.readAllBytes(path),Objects.toString(row.get("original_filename"),"attachment"));
    }

    private void traceability(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex);if(s==null)return;Map<String,String>q=Web.query(ex);String search=Web.param(q,"q"),filter=Web.param(q,"status");
        Web.html(ex,200,Html.layout("Traceability",s,Html.traceability(db.traceability(search),filter,search),msg(ex)));
    }

    private void audit(HttpExchange ex) throws Exception {
        SessionManager.Session s=auth(ex);if(s==null)return;Web.html(ex,200,Html.layout("Audit Trail",s,Html.audit(db.query("SELECT * FROM audit_log ORDER BY id DESC LIMIT 300")),msg(ex)));
    }

    private void users(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN");if(s==null)return;Web.html(ex,200,Html.layout("Users",s,Html.users(db.query("SELECT username,display_name,role,active,must_change_password FROM users ORDER BY username"),s),msg(ex)));
    }

    private void createUser(HttpExchange ex) throws Exception {
        SessionManager.Session s=authRole(ex,"ADMIN");if(s==null)return;requirePost(ex);Map<String,String>f=Web.form(ex);if(!csrf(s,f)){forbidden(ex);return;}
        String username=req(f,"username").toLowerCase(Locale.ROOT); if(!db.query("SELECT 1 FROM users WHERE username=?",username).isEmpty()) throw new IllegalArgumentException("Username already exists");
        String password=req(f,"password");if(password.length()<12)throw new IllegalArgumentException("Temporary password must be at least 12 characters");Passwords.Hash h=Passwords.hash(password);
        db.update("INSERT INTO users(username,display_name,role,password_hash,salt,iterations,must_change_password,active) VALUES(?,?,?,?,?,?,TRUE,TRUE)",username,req(f,"display_name"),req(f,"role"),h.hashBase64(),h.saltBase64(),h.iterations());
        db.audit(s.username(),"CREATE","USER",username,"Role "+Web.param(f,"role"));Web.redirect(ex,"/users?msg=User+created");
    }

    private void health(HttpExchange ex) throws Exception {
        Web.html(ex,200,"<!doctype html><html><body><pre>FOCI TRACE OK</pre></body></html>");
    }

    private SessionManager.Session auth(HttpExchange ex) throws IOException {
        Optional<SessionManager.Session> opt=sessions.get(ex);if(opt.isEmpty()){Web.redirect(ex,"/login");return null;}SessionManager.Session s=opt.get();
        String path=ex.getRequestURI().getPath();if(s.mustChangePassword()&&!path.equals("/change-password")&&!path.equals("/logout")){Web.redirect(ex,"/change-password");return null;}return s;
    }
    private SessionManager.Session authRole(HttpExchange ex,String...roles)throws IOException{SessionManager.Session s=auth(ex);if(s==null)return null;if(!s.hasRole(roles)){forbidden(ex);return null;}return s;}
    private boolean csrf(SessionManager.Session s,Map<String,String>f){return sessions.validCsrf(s,Web.param(f,"csrf"));}
    private void forbidden(HttpExchange ex)throws IOException{Web.html(ex,403,"<!doctype html><html><body><h1>403 Forbidden</h1></body></html>");}
    private void methodNotAllowed(HttpExchange ex)throws IOException{Web.html(ex,405,"<!doctype html><html><body><h1>405 Method Not Allowed</h1></body></html>");}
    private void requirePost(HttpExchange ex)throws IOException{if(!isPost(ex)){methodNotAllowed(ex);throw new Stop();}}
    private static boolean isGet(HttpExchange ex){return "GET".equalsIgnoreCase(ex.getRequestMethod());}
    private static boolean isPost(HttpExchange ex){return "POST".equalsIgnoreCase(ex.getRequestMethod());}
    private static String msg(HttpExchange ex){return Web.param(Web.query(ex),"msg");}
    private static String req(Map<String,String>f,String k){String v=Web.param(f,k);if(v.isBlank())throw new IllegalArgumentException(k+" is required");return v;}
    private static String nullable(String v){return v==null||v.isBlank()?null:v;}
    private static String blankDash(String v){return v==null||v.isBlank()?"-":v;}
    private static BigDecimal decimal(String v){try{return v==null||v.isBlank()?null:new BigDecimal(v);}catch(Exception e){throw new IllegalArgumentException("Invalid decimal: "+v);}}
    private static Integer integer(String v){try{return v==null||v.isBlank()?null:Integer.valueOf(v);}catch(Exception e){throw new IllegalArgumentException("Invalid integer: "+v);}}
    private static String text(Map<String,Multipart.Part>p,String k){Multipart.Part x=p.get(k);return x==null?"":x.text();}
    private static String textReq(Map<String,Multipart.Part>p,String k){String v=text(p,k);if(v.isBlank())throw new IllegalArgumentException(k+" is required");return v;}
    private static String safeFilename(String n){String base=Path.of(n).getFileName().toString();return base.replaceAll("[^A-Za-z0-9._ -]","_");}
    private static void validateExtension(String n){String l=n.toLowerCase(Locale.ROOT);String[]ok={".pdf",".png",".jpg",".jpeg",".xlsx",".xls",".docx",".doc",".csv",".txt"};if(Arrays.stream(ok).noneMatch(l::endsWith))throw new IllegalArgumentException("Unsupported file type");}
    @FunctionalInterface private interface Handler{void handle(HttpExchange ex)throws Exception;}
    private static final class Stop extends RuntimeException {}

    public static void main(String[] args) throws Exception {
        String portValue=System.getenv().getOrDefault("FOCI_PORT", System.getenv().getOrDefault("PORT","8080"));
        int port=Integer.parseInt(portValue);
        Path data=Path.of(System.getenv().getOrDefault("FOCI_DATA_DIR","data"));
        Path uploads=Path.of(System.getenv().getOrDefault("FOCI_UPLOAD_DIR","uploads"));
        boolean secure=Boolean.parseBoolean(System.getenv().getOrDefault("FOCI_COOKIE_SECURE","false"));
        new FociTraceApplication(port,data,uploads,secure).start();
    }
}
