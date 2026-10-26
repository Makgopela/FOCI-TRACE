package za.co.focigroup.focitrace;

public final class TraceabilityEngine {
    private TraceabilityEngine() {}

    public record Input(boolean quoted, boolean specificationRequired, boolean specificationReceived,
                        boolean supplierEvidenceReceived, String technicalReview) {}
    public record Outcome(String pricingStatus, String specificationAvailability, String documentStatus,
                          String overallStatus, String outstandingAction, String statusCalculation) {}

    public static Outcome evaluate(Input in, String specRef) {
        String pricing = in.quoted() ? "Priced" : "Not Quoted";
        String specAvailability = !in.specificationRequired() ? "Not Applicable" : (in.specificationReceived() ? "Available" : "Missing");
        String docStatus;
        if (in.specificationRequired() && !in.specificationReceived()) docStatus = "Outstanding";
        else if (in.supplierEvidenceReceived()) docStatus = "Received";
        else if (in.specificationRequired() && in.specificationReceived()) docStatus = "Partially Received";
        else docStatus = "Outstanding";

        if (in.specificationRequired() && !in.specificationReceived()) {
            return new Outcome(pricing, specAvailability, docStatus, "Clarification Required",
                    "Request missing " + specRef + " clarification; obtain quotation and supplier technical evidence.",
                    "Missing technical specification");
        }
        if (!in.quoted()) {
            return new Outcome(pricing, specAvailability, docStatus, "Incomplete",
                    "Obtain supplier quotation" + (in.supplierEvidenceReceived() ? "." : " and required supplier technical evidence."),
                    "Quotation outstanding");
        }
        if (!in.supplierEvidenceReceived()) {
            return new Outcome(pricing, specAvailability, docStatus, "Incomplete",
                    "Obtain required supplier technical evidence.", "Supplier technical evidence outstanding");
        }
        if (!"Reviewed".equalsIgnoreCase(in.technicalReview())) {
            return new Outcome(pricing, specAvailability, docStatus, "Pending Review",
                    "Engineering technical review required.", "Technical review pending");
        }
        return new Outcome(pricing, specAvailability, docStatus, "Complete", "None", "All required traceability checks passed");
    }
}
