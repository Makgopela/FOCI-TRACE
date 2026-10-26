package za.co.focigroup.focitrace;

import java.util.*;

public final class Html {
    private Html() {}

    public static String layout(String title, SessionManager.Session user, String body, String flash) {
        String nav = user == null ? "" : """
            <aside class='sidebar'>
              <div class='brand'><span class='mark'>FT</span><div><b>FOCI TRACE</b><small>Procurement Traceability</small></div></div>
              <nav>
                <a href='/'>Dashboard</a>
                <a href='/rfqs'>RFQs</a>
                <a href='/requirements'>Requirements</a>
                <a href='/suppliers'>Suppliers</a>
                <a href='/quotations'>Quotations</a>
                <a href='/documents'>Documents</a>
                <a href='/traceability'>Traceability</a>
                <a href='/audit'>Audit Trail</a>
                %s
              </nav>
              <div class='userbox'><b>%s</b><span>%s</span><a href='/change-password'>Change password</a><a href='/logout'>Sign out</a></div>
            </aside>
            """.formatted(user.hasRole("ADMIN") ? "<a href='/users'>Users</a>" : "", Web.e(user.displayName()), Web.e(user.role()));
        return """
        <!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>
        <title>%s - FOCI TRACE</title><style>%s</style></head><body>
        %s<div class='%s'><header class='topbar'><div><h1>%s</h1><p>RFQ, quotation, document and technical-review traceability</p></div></header>
        %s<main>%s</main></div></body></html>
        """.formatted(Web.e(title), css(), nav, user==null?"auth-shell":"content", Web.e(title), flash==null?"":"<div class='flash'>"+Web.e(flash)+"</div>", body);
    }

    public static String login(String error) {
        return layout("Sign in", null, """
            <section class='login-card'>
              <div class='login-logo'>FT</div><h2>FOCI TRACE</h2><p>Sign in to continue</p>
              %s
              <form method='post' action='/login'>
                <label>Username<input name='username' autocomplete='username' required></label>
                <label>Password<input type='password' name='password' autocomplete='current-password' required></label>
                <button class='primary' type='submit'>Sign in</button>
              </form>
            </section>
        """.formatted(error==null?"":"<div class='error'>"+Web.e(error)+"</div>"), null);
    }

    public static String dashboard(Database.Dashboard d, List<Database.TraceRow> attention) {
        StringBuilder cards = new StringBuilder();
        cards.append(card("Open RFQs", d.rfqs(), "RFQs in the working dataset", "/rfqs"));
        cards.append(card("Requirements", d.requirements(), "Tracked material requirements", "/requirements"));
        cards.append(card("Complete", d.complete(), "All checks passed", "/traceability?status=Complete"));
        cards.append(card("Pending Review", d.pendingReview(), "Engineering review required", "/traceability?status=Pending+Review"));
        cards.append(card("Incomplete", d.incomplete(), "Missing quote/documents", "/traceability?status=Incomplete"));
        cards.append(card("Clarification", d.clarificationRequired(), "Specification issue", "/traceability?status=Clarification+Required"));
        cards.append(card("Missing Quotes", d.missingQuotations(), "Supplier pricing outstanding", "/traceability"));
        cards.append(card("Missing Specs", d.missingSpecifications(), "Specification clarification needed", "/traceability"));

        StringBuilder rows = new StringBuilder();
        for (Database.TraceRow t : attention) {
            rows.append("<tr><td><b>").append(Web.e(t.requirementId())).append("</b><small>").append(Web.e(t.materialNo())).append("</small></td>")
                    .append("<td>").append(Web.e(shortText(t.description(), 90))).append("</td>")
                    .append("<td>").append(badge(t.overallStatus())).append("</td><td>").append(Web.e(t.outstandingAction())).append("</td></tr>");
        }
        return """
            <section class='hero'><div><span class='eyebrow'>WORKING SYSTEM</span><h2>Procurement traceability at a glance</h2><p>The dashboard is calculated from the current RFQ, requirement, quotation and document records.</p></div><a class='button secondary' href='/traceability'>Open traceability</a></section>
            <section class='cards'>%s</section>
            <section class='panel'><div class='panel-head'><div><h2>Items requiring attention</h2><p>Live status calculated by the traceability engine.</p></div></div>
              <div class='table-wrap'><table><thead><tr><th>Requirement</th><th>Description</th><th>Status</th><th>Outstanding action</th></tr></thead><tbody>%s</tbody></table></div>
            </section>
        """.formatted(cards, rows);
    }

    private static String card(String label, int value, String hint, String href) {
        return "<a class='card' href='"+href+"'><span>"+Web.e(label)+"</span><strong>"+value+"</strong><small>"+Web.e(hint)+"</small></a>";
    }

    public static String rfqs(List<Map<String,Object>> rows, SessionManager.Session user) {
        StringBuilder trs = new StringBuilder();
        for (Map<String,Object> r: rows) {
            trs.append("<tr><td><b>").append(Web.e(r.get("id"))).append("</b></td><td>").append(Web.e(r.get("title")))
               .append("</td><td>").append(Web.e(r.get("owner"))).append("</td><td>").append(badge(Web.e(r.get("priority"))))
               .append("</td><td>").append(badge(Web.e(r.get("status")))).append("</td><td>").append(Web.e(r.get("source"))).append("</td></tr>");
        }
        String form = user.hasRole("ADMIN","PROCUREMENT") ? """
            <details class='panel form-panel'><summary>Add RFQ</summary><form method='post' action='/rfqs/create'>%s
              <div class='grid'><label>Project / opportunity<input name='title' required></label><label>Owner<input name='owner' value='Procurement'></label>
              <label>Priority<select name='priority'><option>High</option><option>Medium</option><option>Low</option></select></label>
              <label>Status<select name='status'><option>Open</option><option>Clarification Required</option><option>Under Review</option><option>Closed</option></select></label></div>
              <label>Source / notes<textarea name='source'></textarea></label><button class='primary'>Create RFQ</button></form></details>
        """.formatted(csrf(user)) : "";
        return tablePage("RFQs","Create and track procurement opportunities.", form,
                "<tr><th>ID</th><th>Project / Opportunity</th><th>Owner</th><th>Priority</th><th>Status</th><th>Source</th></tr>", trs.toString());
    }

    public static String requirements(List<Map<String,Object>> rows, List<Map<String,Object>> rfqs, SessionManager.Session user, String search) {
        StringBuilder trs = new StringBuilder();
        for (Map<String,Object> r: rows) {
            trs.append("<tr><td><b>").append(Web.e(r.get("id"))).append("</b><small>").append(Web.e(r.get("rfq_id"))).append("</small></td>")
                    .append("<td><b>").append(Web.e(r.get("material_no"))).append("</b></td><td>").append(Web.e(r.get("description")))
                    .append("</td><td>").append(Web.e(r.get("qty"))).append("</td><td>").append(Web.e(r.get("tech_class")))
                    .append("</td><td>").append(Web.e(r.get("spec_ref"))).append("</td><td>").append(badge(Web.e(r.get("technical_review_status")))).append("</td>");
            if (user.hasRole("ADMIN","ENGINEERING")) {
                trs.append("<td><form class='inline' method='post' action='/requirements/review'>").append(csrf(user))
                        .append("<input type='hidden' name='id' value='").append(Web.e(r.get("id"))).append("'><select name='review'><option>Pending</option><option>Reviewed</option><option>Rejected</option></select><button>Update</button></form></td>");
            } else trs.append("<td>—</td>");
            trs.append("</tr>");
        }
        StringBuilder rfqOpts = new StringBuilder(); for (Map<String,Object> r: rfqs) rfqOpts.append("<option value='").append(Web.e(r.get("id"))).append("'>").append(Web.e(r.get("id"))).append(" - ").append(Web.e(r.get("title"))).append("</option>");
        String form = user.hasRole("ADMIN","PROCUREMENT") ? """
            <details class='panel form-panel'><summary>Add requirement</summary><form method='post' action='/requirements/create'>%s
              <div class='grid'><label>RFQ<select name='rfq_id' required>%s</select></label><label>Material number<input name='material_no' required></label>
              <label>Quantity<input type='number' step='0.001' min='0' name='qty'></label><label>Technical class<input name='tech_class'></label>
              <label>Specification ref<input name='spec_ref' placeholder='e.g. DSG-315-200 or -'></label><label>Technical review<select name='review'><option>Pending</option><option>Reviewed</option></select></label></div>
              <label>Description<textarea name='description' required></textarea></label><label>Supplier documents required<textarea name='supplier_docs_required'></textarea></label><label>Source / evidence ref<textarea name='source_ref'></textarea></label>
              <button class='primary'>Create requirement</button></form></details>
        """.formatted(csrf(user), rfqOpts) : "";
        String searchBox = "<form class='search' method='get'><input name='q' value='"+Web.e(search)+"' placeholder='Search ID, material or description'><button>Search</button></form>";
        return "<section class='page-intro'><div><h2>Requirements</h2><p>BOQ/material requirements linked to their RFQs.</p></div>"+searchBox+"</section>"+form+
                "<section class='panel'><div class='table-wrap'><table><thead><tr><th>ID</th><th>Material</th><th>Description</th><th>Qty</th><th>Class</th><th>Specification</th><th>Review</th><th>Action</th></tr></thead><tbody>"+trs+"</tbody></table></div></section>";
    }

    public static String suppliers(List<Map<String,Object>> rows, SessionManager.Session user) {
        StringBuilder trs = new StringBuilder();
        for (Map<String,Object> r: rows) trs.append("<tr><td><b>").append(Web.e(r.get("id"))).append("</b></td><td>").append(Web.e(r.get("name"))).append("</td><td>").append(Web.e(r.get("category"))).append("</td><td>").append(Web.e(r.get("contact_role"))).append("</td><td>").append(Web.e(r.get("notes"))).append("</td></tr>");
        String form = user.hasRole("ADMIN","PROCUREMENT") ? """
            <details class='panel form-panel'><summary>Add supplier</summary><form method='post' action='/suppliers/create'>%s<div class='grid'>
              <label>Name<input name='name' required></label><label>Category<input name='category'></label><label>Contact role<input name='contact_role'></label></div>
              <label>Notes<textarea name='notes'></textarea></label><button class='primary'>Create supplier</button></form></details>
        """.formatted(csrf(user)) : "";
        return tablePage("Suppliers","Supplier and manufacturer references.",form,"<tr><th>ID</th><th>Supplier / Manufacturer</th><th>Category</th><th>Contact role</th><th>Notes</th></tr>",trs.toString());
    }

    public static String quotations(List<Map<String,Object>> quotes, List<Map<String,Object>> rfqs, List<Map<String,Object>> suppliers, List<Map<String,Object>> reqs, SessionManager.Session user) {
        StringBuilder trs=new StringBuilder();
        for (Map<String,Object> r:quotes) trs.append("<tr><td><b>").append(Web.e(r.get("id"))).append("</b></td><td>").append(Web.e(r.get("rfq_id"))).append("</td><td>").append(Web.e(r.get("supplier_name"))).append("</td><td>").append(Web.e(r.get("quotation_ref"))).append("</td><td>").append(badge(Web.e(r.get("status")))).append("</td><td>").append(Web.e(r.get("evidence_basis"))).append("</td></tr>");
        String form="";
        if(user.hasRole("ADMIN","PROCUREMENT")){
            form="""
            <details class='panel form-panel'><summary>Add quotation / quotation item</summary><form method='post' action='/quotations/create'>%s
              <div class='grid'><label>RFQ<select name='rfq_id'>%s</select></label><label>Supplier<select name='supplier_id'>%s</select></label>
              <label>Requirement<select name='requirement_id'>%s</select></label><label>Quotation reference<input name='quotation_ref' required></label>
              <label>Status<select name='status'><option>Received</option><option>Partial</option><option>Not Quoted</option><option>Outstanding</option></select></label>
              <label>Quoted item?<select name='quoted'><option value='true'>Yes</option><option value='false'>No</option></select></label>
              <label>Unit price<input type='number' step='0.01' min='0' name='unit_price'></label><label>Currency<input name='currency' value='ZAR'></label><label>Lead time days<input type='number' min='0' name='lead_time_days'></label></div>
              <label>Evidence basis / notes<textarea name='evidence_basis'></textarea></label><button class='primary'>Save quotation</button></form></details>
            """.formatted(csrf(user),options(rfqs,"id","title"),options(suppliers,"id","name"),options(reqs,"id","material_no"));
        }
        return tablePage("Quotations","Supplier quotation records and pricing completeness.",form,"<tr><th>ID</th><th>RFQ</th><th>Supplier</th><th>Reference</th><th>Status</th><th>Evidence / notes</th></tr>",trs.toString());
    }

    public static String documents(List<Map<String,Object>> docs, List<Map<String,Object>> reqs, List<Map<String,Object>> suppliers, List<Map<String,Object>> quotes, SessionManager.Session user) {
        StringBuilder trs=new StringBuilder();
        for(Map<String,Object> r:docs){
            String file = r.get("file_path") == null ? "—" : "<a href='/documents/download?id="+Web.e(r.get("id"))+"'>"+Web.e(r.get("original_filename"))+"</a>";
            trs.append("<tr><td><b>").append(Web.e(r.get("id"))).append("</b></td><td>").append(Web.e(r.get("requirement_id"))).append("</td><td>").append(Web.e(r.get("doc_type"))).append("</td><td>").append(Web.e(r.get("reference_name"))).append("</td><td>").append(badge(Web.e(r.get("status")))).append("</td><td>").append(Web.e(r.get("reviewer"))).append("</td><td>").append(file).append("</td></tr>");
        }
        String form="";
        if(user.hasRole("ADMIN","PROCUREMENT","ENGINEERING")){
            form="""
            <details class='panel form-panel'><summary>Add / upload document evidence</summary><form method='post' action='/documents/upload' enctype='multipart/form-data'>%s
             <div class='grid'><label>Requirement<select name='requirement_id'>%s</select></label><label>Supplier<select name='supplier_id'><option value=''>None</option>%s</select></label>
             <label>Quotation<select name='quotation_id'><option value=''>None</option>%s</select></label><label>Document type<input name='doc_type' required></label>
             <label>Reference / filename<input name='reference_name' required></label><label>Version<input name='version'></label>
             <label>Status<select name='status'><option>Received</option><option>Outstanding</option><option>Under Review</option></select></label><label>Reviewer<input name='reviewer' value='%s'></label></div>
             <label>Source / notes<textarea name='source_notes'></textarea></label><label>Attachment (PDF, Office, image, CSV; max 10MB)<input type='file' name='attachment'></label><button class='primary'>Save document</button></form></details>
            """.formatted(csrf(user),options(reqs,"id","material_no"),options(suppliers,"id","name"),options(quotes,"id","quotation_ref"),Web.e(user.displayName()));
        }
        return tablePage("Documents","Supporting specifications, datasheets, COCs and evidence.",form,"<tr><th>ID</th><th>Requirement</th><th>Type</th><th>Reference</th><th>Status</th><th>Reviewer</th><th>Attachment</th></tr>",trs.toString());
    }

    public static String traceability(List<Database.TraceRow> rows, String filter, String search) {
        StringBuilder trs=new StringBuilder();
        for(Database.TraceRow t:rows){
            if(filter!=null&&!filter.isBlank()&&!filter.equalsIgnoreCase(t.overallStatus())) continue;
            trs.append("<tr><td><b>").append(Web.e(t.requirementId())).append("</b><small>").append(Web.e(t.rfqId())).append("</small></td><td><b>").append(Web.e(t.materialNo())).append("</b><small>").append(Web.e(shortText(t.description(),75))).append("</small></td>")
               .append("<td>").append(Web.e(t.supplierName())).append("</td><td>").append(Web.e(t.quotationId())).append("</td><td>").append(badge(t.pricingStatus())).append("</td><td>").append(Web.e(t.specificationRef())).append("<small>").append(badge(t.specificationAvailability())).append("</small></td><td>").append(badge(t.documentStatus())).append("</td><td>").append(badge(t.technicalReview())).append("</td><td>").append(badge(t.overallStatus())).append("</td><td>").append(Web.e(t.outstandingAction())).append("</td></tr>");
        }
        String filters = "<form class='search' method='get'><input name='q' value='"+Web.e(search)+"' placeholder='Search requirement/material'><select name='status'><option value=''>All statuses</option><option>Complete</option><option>Pending Review</option><option>Incomplete</option><option>Clarification Required</option></select><button>Filter</button></form>";
        return "<section class='page-intro'><div><h2>Traceability</h2><p>Calculated from quotation items, specification availability, supplier evidence and technical review.</p></div>"+filters+"</section><section class='panel'><div class='table-wrap'><table><thead><tr><th>Requirement</th><th>Material</th><th>Supplier</th><th>Quotation</th><th>Pricing</th><th>Specification</th><th>Documents</th><th>Review</th><th>Overall</th><th>Outstanding action</th></tr></thead><tbody>"+trs+"</tbody></table></div></section>";
    }

    public static String audit(List<Map<String,Object>> rows) {
        StringBuilder trs=new StringBuilder(); for(Map<String,Object> r:rows) trs.append("<tr><td>").append(Web.e(r.get("created_at"))).append("</td><td>").append(Web.e(r.get("username"))).append("</td><td><b>").append(Web.e(r.get("action"))).append("</b></td><td>").append(Web.e(r.get("entity_type"))).append(" ").append(Web.e(r.get("entity_id"))).append("</td><td>").append(Web.e(r.get("details"))).append("</td></tr>");
        return tablePage("Audit Trail","Who changed what and when.","","<tr><th>Time</th><th>User</th><th>Action</th><th>Record</th><th>Details</th></tr>",trs.toString());
    }

    public static String users(List<Map<String,Object>> rows, SessionManager.Session user) {
        StringBuilder trs=new StringBuilder(); for(Map<String,Object> r:rows) trs.append("<tr><td>").append(Web.e(r.get("username"))).append("</td><td>").append(Web.e(r.get("display_name"))).append("</td><td>").append(badge(Web.e(r.get("role")))).append("</td><td>").append(Web.e(r.get("active"))).append("</td><td>").append(Web.e(r.get("must_change_password"))).append("</td></tr>");
        String form="""
        <details class='panel form-panel'><summary>Create user</summary><form method='post' action='/users/create'>%s<div class='grid'><label>Username<input name='username' required></label><label>Display name<input name='display_name' required></label><label>Role<select name='role'><option>VIEWER</option><option>PROCUREMENT</option><option>ENGINEERING</option><option>ADMIN</option></select></label><label>Temporary password<input type='password' name='password' minlength='12' required></label></div><button class='primary'>Create user</button></form></details>
        """.formatted(csrf(user));
        return tablePage("Users","Application roles and local accounts.",form,"<tr><th>Username</th><th>Name</th><th>Role</th><th>Active</th><th>Must change password</th></tr>",trs.toString());
    }

    public static String changePassword(SessionManager.Session user, String error) {
        return "<section class='panel narrow'><h2>Change password</h2><p>Use at least 12 characters. This is required for seeded accounts before normal use.</p>"+(error==null?"":"<div class='error'>"+Web.e(error)+"</div>")+"<form method='post' action='/change-password'>"+csrf(user)+"<label>New password<input type='password' name='password' minlength='12' required></label><label>Confirm password<input type='password' name='confirm' minlength='12' required></label><button class='primary'>Update password</button></form></section>";
    }

    private static String tablePage(String title,String desc,String before,String header,String rows){return "<section class='page-intro'><div><h2>"+Web.e(title)+"</h2><p>"+Web.e(desc)+"</p></div></section>"+before+"<section class='panel'><div class='table-wrap'><table><thead>"+header+"</thead><tbody>"+rows+"</tbody></table></div></section>";}
    private static String csrf(SessionManager.Session user){return "<input type='hidden' name='csrf' value='"+Web.e(user.csrf())+"'>";}
    private static String options(List<Map<String,Object>> rows,String value,String label){StringBuilder b=new StringBuilder();for(Map<String,Object> r:rows)b.append("<option value='").append(Web.e(r.get(value))).append("'>").append(Web.e(r.get(value))).append(" - ").append(Web.e(r.get(label))).append("</option>");return b.toString();}
    public static String badge(String status){String s=status==null?"":status;String cls=s.toLowerCase(Locale.ROOT).replace(" ","-").replace("/","-");return "<span class='badge "+Web.e(cls)+"'>"+Web.e(s)+"</span>";}
    private static String shortText(String s,int max){if(s==null)return"";return s.length()<=max?s:s.substring(0,max-1)+"…";}

    private static String css(){return """
      :root{--ink:#172033;--muted:#64748b;--line:#e2e8f0;--bg:#f5f7fb;--panel:#fff;--brand:#253858;--brand2:#476582;--accent:#7d2532;--good:#166534;--warn:#92400e;--bad:#991b1b}
      *{box-sizing:border-box}body{margin:0;font-family:Inter,Segoe UI,Arial,sans-serif;background:var(--bg);color:var(--ink)}a{color:inherit}h1,h2,p{margin-top:0}.sidebar{position:fixed;inset:0 auto 0 0;width:245px;background:#172033;color:white;padding:24px 16px;display:flex;flex-direction:column;z-index:2}.brand{display:flex;gap:12px;align-items:center;padding:0 8px 24px}.brand small{display:block;color:#aeb9cc;margin-top:3px}.mark,.login-logo{width:44px;height:44px;border-radius:12px;background:#fff;color:#172033;display:grid;place-items:center;font-weight:800}.sidebar nav{display:grid;gap:5px}.sidebar nav a,.userbox a{padding:10px 12px;text-decoration:none;border-radius:8px;color:#d9e2ef}.sidebar nav a:hover,.userbox a:hover{background:#243149;color:white}.userbox{margin-top:auto;border-top:1px solid #334155;padding-top:16px;display:grid;gap:5px}.userbox b,.userbox span{padding:0 12px}.userbox span{font-size:12px;color:#aeb9cc}.content{margin-left:245px;min-height:100vh}.topbar{background:white;border-bottom:1px solid var(--line);padding:24px 34px}.topbar h1{font-size:24px;margin-bottom:4px}.topbar p{color:var(--muted);margin:0}.content main{padding:28px 34px 50px}.hero{background:linear-gradient(135deg,#172033,#3a506b);color:#fff;border-radius:18px;padding:28px;display:flex;justify-content:space-between;gap:20px;align-items:center;margin-bottom:22px}.hero h2{font-size:28px;margin:6px 0}.hero p{color:#d8e0ec;margin:0;max-width:680px}.eyebrow{font-size:11px;letter-spacing:.18em;font-weight:800;color:#c8d4e6}.cards{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:14px;margin-bottom:22px}.card{background:white;border:1px solid var(--line);border-radius:14px;padding:18px;text-decoration:none;display:grid;gap:6px}.card span{color:var(--muted);font-size:13px}.card strong{font-size:30px}.card small{color:#94a3b8}.panel{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:20px;margin-bottom:20px;box-shadow:0 8px 24px rgba(15,23,42,.035)}.panel-head,.page-intro{display:flex;justify-content:space-between;gap:20px;align-items:flex-end;margin-bottom:18px}.page-intro h2,.panel h2{margin-bottom:5px}.page-intro p,.panel p{color:var(--muted);margin-bottom:0}.form-panel summary{cursor:pointer;font-weight:700}.form-panel form{margin-top:18px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px}label{display:grid;gap:6px;font-size:13px;font-weight:650;margin-bottom:14px}input,select,textarea{width:100%;padding:10px 11px;border:1px solid #cbd5e1;border-radius:8px;background:white;color:var(--ink);font:inherit}textarea{min-height:82px;resize:vertical}button,.button{border:0;border-radius:8px;padding:10px 15px;font:inherit;font-weight:700;cursor:pointer;text-decoration:none;display:inline-block}.primary{background:var(--brand);color:white}.secondary{background:white;color:var(--brand)}.table-wrap{overflow:auto}table{width:100%;border-collapse:collapse;font-size:13px}th{text-align:left;background:#f8fafc;color:#475569;font-size:11px;text-transform:uppercase;letter-spacing:.04em}th,td{padding:11px;border-bottom:1px solid var(--line);vertical-align:top}td small{display:block;color:var(--muted);margin-top:4px}.badge{display:inline-block;padding:4px 8px;border-radius:999px;background:#e2e8f0;font-size:11px;font-weight:750;white-space:nowrap}.badge.complete,.badge.received,.badge.reviewed,.badge.high{background:#dcfce7;color:#166534}.badge.pending,.badge.pending-review,.badge.partially-received,.badge.under-review{background:#fef3c7;color:#92400e}.badge.incomplete,.badge.outstanding,.badge.not-quoted,.badge.rejected{background:#fee2e2;color:#991b1b}.badge.clarification-required,.badge.missing{background:#ffedd5;color:#9a3412}.inline{display:flex;gap:6px}.inline select{min-width:110px}.search{display:flex;gap:8px;min-width:360px}.flash{margin:18px 34px -4px;background:#e0f2fe;color:#075985;border:1px solid #bae6fd;padding:11px 14px;border-radius:9px}.error{background:#fee2e2;color:#991b1b;padding:10px 12px;border-radius:8px;margin-bottom:14px}.auth-shell{min-height:100vh;display:grid;place-items:center;padding:25px;background:linear-gradient(145deg,#172033,#334155)}.auth-shell .topbar{display:none}.auth-shell main{width:min(430px,100%)}.login-card{background:white;border-radius:18px;padding:34px;box-shadow:0 24px 80px rgba(0,0,0,.25)}.login-card h2{text-align:center;margin:12px 0 4px}.login-card>p{text-align:center;color:var(--muted);margin-bottom:24px}.login-logo{margin:auto;background:#172033;color:white}.narrow{max-width:620px}.button.secondary{box-shadow:0 1px 4px rgba(0,0,0,.08)}
      @media(max-width:1000px){.cards{grid-template-columns:repeat(2,1fr)}.sidebar{width:205px}.content{margin-left:205px}.content main,.topbar{padding-left:20px;padding-right:20px}.grid{grid-template-columns:1fr}}
      @media(max-width:720px){.sidebar{position:static;width:auto}.content{margin-left:0}.cards{grid-template-columns:1fr}.hero,.page-intro{align-items:flex-start;flex-direction:column}.search{min-width:0;width:100%}}
    """;}
}
