package com.buildsmart.analytics.service;

import com.buildsmart.analytics.client.FinanceServiceClient;
import com.buildsmart.analytics.client.ProjectDTO;
import com.buildsmart.analytics.client.ProjectServiceClient;
import com.buildsmart.analytics.client.ResourceServiceClient;
import com.buildsmart.analytics.client.SiteEngineerServiceClient;
import com.buildsmart.analytics.client.SiteIssueDTO;
import com.buildsmart.analytics.client.SiteLogDTO;
import com.buildsmart.analytics.client.VendorContractDTO;
import com.buildsmart.analytics.client.VendorDeliveryDTO;
import com.buildsmart.analytics.client.VendorDocumentDTO;
import com.buildsmart.analytics.client.VendorInvoiceDTO;
import com.buildsmart.analytics.client.VendorServiceClient;
import com.buildsmart.analytics.client.SafetyServiceClient;
import com.buildsmart.analytics.dto.BudgetAlertRecord;
import com.buildsmart.analytics.dto.CashFlowRecord;
import com.buildsmart.analytics.dto.DashboardSummaryRecord;
import com.buildsmart.analytics.dto.ExportStatusRecord;
import com.buildsmart.analytics.dto.GenerateReportRequest;
import com.buildsmart.analytics.dto.HistoricalReportRecord;
import com.buildsmart.analytics.dto.LaborAllocationRecord;
import com.buildsmart.analytics.dto.ProjectHealthRecord;
import com.buildsmart.analytics.dto.ProjectSummaryRecord;
import com.buildsmart.analytics.dto.ReportResponseRecord;
import com.buildsmart.analytics.dto.ResourceUtilizationRecord;
import com.buildsmart.analytics.dto.SafetyInspectionSummaryRecord;
import com.buildsmart.analytics.dto.SafetyTrendRecord;
import com.buildsmart.analytics.dto.SiteEngineerDailyLogRecord;
import com.buildsmart.analytics.dto.SiteEngineerPerformanceRecord;
import com.buildsmart.analytics.dto.SiteProgressSummaryRecord;
import com.buildsmart.analytics.dto.VendorComplianceRecord;
import com.buildsmart.analytics.dto.VendorPerformanceRecord;
import com.buildsmart.analytics.entity.Report;
import com.buildsmart.analytics.entity.Scope;
import com.buildsmart.analytics.exception.AggregationException;
import com.buildsmart.analytics.exception.ReportNotFoundException;
import com.buildsmart.analytics.repository.ReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private final ReportRepository reportRepository;
    private final ObjectMapper objectMapper;
    private final ProjectServiceClient projectServiceClient;
    private final FinanceServiceClient financeServiceClient;
    private final ResourceServiceClient resourceServiceClient;
    private final SiteEngineerServiceClient siteEngineerServiceClient;
    private final VendorServiceClient vendorServiceClient;
    private final SafetyServiceClient safetyServiceClient;

    public AnalyticsService(ReportRepository reportRepository, ObjectMapper objectMapper,
                           ProjectServiceClient projectServiceClient,
                           FinanceServiceClient financeServiceClient,
                           ResourceServiceClient resourceServiceClient,
                           SiteEngineerServiceClient siteEngineerServiceClient,
                           VendorServiceClient vendorServiceClient,
                           SafetyServiceClient safetyServiceClient) {
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
        this.projectServiceClient = projectServiceClient;
        this.financeServiceClient = financeServiceClient;
        this.resourceServiceClient = resourceServiceClient;
        this.siteEngineerServiceClient = siteEngineerServiceClient;
        this.vendorServiceClient = vendorServiceClient;
        this.safetyServiceClient = safetyServiceClient;
    }

    public ReportResponseRecord generateReport(GenerateReportRequest request) {
        try {
            var metrics = switch (request.scope()) {
                case PROJECT -> buildProjectMetrics(request.targetId());
                case RESOURCE -> buildResourceMetrics();
                case SAFETY -> buildSafetyMetrics();
                case FINANCE -> buildFinanceMetrics();
                case VENDOR -> buildVendorMetrics();
                case SITE_ENGINEER -> buildSiteEngineerMetrics();
            };

            var metricsJson = objectMapper.writeValueAsString(metrics);
            var report = new Report(nextReportId(), request.scope(), metricsJson, OffsetDateTime.now());
            var saved = reportRepository.save(report);
            return new ReportResponseRecord(saved.getReportId(), saved.getScope(), saved.getMetrics(), saved.getGeneratedDate());
        } catch (JsonProcessingException ex) {
            throw new AggregationException("Failed to serialize report metrics", ex);
        }
    }

    public ReportResponseRecord getReport(String id) {
        var report = reportRepository.findById(id).orElseThrow(() -> new ReportNotFoundException(id));
        return new ReportResponseRecord(report.getReportId(), report.getScope(), report.getMetrics(), report.getGeneratedDate());
    }

    public DashboardSummaryRecord getDashboardSummary() {
        try {
            var projectSummaries = fetchProjectSummaries();
            var safetyCompliance = fetchSafetyCompliance();
            var usage = fetchResourceUsage();
            var resourceUtilization = usage.usedHours() / Math.max(usage.usedHours() + usage.idleHours(), 1.0);
            var avgBudgetVariance = fetchBudgetVarianceAverage();

            return new DashboardSummaryRecord(
                    projectSummaries.size(),
                    avgBudgetVariance,
                    safetyCompliance,
                    resourceUtilization
            );
        } catch (Exception ex) {
            throw new AggregationException("Failed to aggregate dashboard summary", ex);
        }
    }

    public ProjectHealthRecord getProjectHealth(String projectId) {
        try {
            var cpi = fetchProjectCostPerformance(projectId);
            var sv = fetchProjectScheduleVariance(projectId);
            return new ProjectHealthRecord(sv, cpi);
        } catch (Exception ex) {
            throw new AggregationException("Unable to compute project health", ex);
        }
    }

    public List<ProjectSummaryRecord> getProjectSummary() {
        return fetchProjectSummaries();
    }

    public List<SafetyTrendRecord> getSafetyTrends() {
        return fetchSafetyTrends();
    }

    public SafetyInspectionSummaryRecord getSafetyInspectionsSummary() {
        var data = fetchInspectionSummary();
        return new SafetyInspectionSummaryRecord(
                data.scheduled(), data.inProgress(), data.completed(),
                data.nonCompliant(), data.closed(), data.total());
    }

    public BudgetAlertRecord getBudgetVariance(String projectId) {
        var budget = fetchBudgetForProject(projectId);
        var variance = budget.plannedAmount() - budget.actualAmount();
        var thresholdExceeded = Math.abs(variance) / Math.max(budget.plannedAmount(), 1.0) > 0.10;
        return new BudgetAlertRecord(projectId, budget.plannedAmount(), budget.actualAmount(), variance, thresholdExceeded);
    }

    public List<CashFlowRecord> getCashFlow() {
        return fetchCashFlow();
    }

    public ResourceUtilizationRecord getResourceUtilization() {
        var usage = fetchResourceUsage();
        var utilizationRate = usage.usedHours() / Math.max(usage.usedHours() + usage.idleHours(), 1.0);
        return new ResourceUtilizationRecord(usage.usedHours(), usage.idleHours(), utilizationRate, usage.totalLabors());
    }

    public List<LaborAllocationRecord> getLaborAllocation() {
        return fetchLaborAllocations();
    }

    public List<HistoricalReportRecord> getHistoricalReports(Scope scope) {
        return reportRepository.findByScope(scope).stream()
                .map(report -> new HistoricalReportRecord(report.getReportId(), report.getScope(), report.getGeneratedDate()))
                .toList();
    }

    public ExportStatusRecord exportReport(String reportId) {
        if (!reportRepository.existsById(reportId)) {
            throw new ReportNotFoundException(reportId);
        }
        try {
            Thread.sleep(500);
            return new ExportStatusRecord(reportId, "COMPLETED", "Export simulated successfully.");
        } catch (Exception ex) {
            throw new AggregationException("Failed to export report", ex);
        }
    }

    // ===== Vendor Analytics =====

    public List<VendorPerformanceRecord> getVendorPerformance() {
        return fetchVendorPerformances();
    }

    public VendorPerformanceRecord getVendorPerformanceById(String vendorId) {
        return fetchVendorPerformances().stream()
                .filter(v -> v.vendorId().equals(vendorId))
                .findFirst()
                .orElseThrow(() -> new ReportNotFoundException("Vendor performance not found: " + vendorId));
    }

    public VendorComplianceRecord getVendorCompliance() {
        try {
            var vendors = vendorServiceClient.getAllVendors();
            var contracts = vendorServiceClient.getAllContracts();

            // Pull document approval data from the vendor module via Feign and use it
            // to refine compliance: a vendor is "compliant" only if their account is
            // ACTIVE AND they have at least one APPROVED document (or no documents
            // submitted yet). A vendor with any REJECTED document is "non-compliant".
            var approvedDocs = safeFetchDocuments("APPROVED");
            var rejectedDocs = safeFetchDocuments("REJECTED");
            var pendingDocs  = safeFetchDocuments("PENDING");
            var submittedDocs = safeFetchDocuments("SUBMITTED");

            java.util.Set<String> vendorsWithApprovedDocs = approvedDocs.stream()
                    .map(VendorDocumentDTO::vendorId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            java.util.Set<String> vendorsWithRejectedDocs = rejectedDocs.stream()
                    .map(VendorDocumentDTO::vendorId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            java.util.Set<String> vendorsWithDocsAwaitingReview = java.util.stream.Stream
                    .concat(pendingDocs.stream(), submittedDocs.stream())
                    .map(VendorDocumentDTO::vendorId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            int total = vendors.size();

            // Compliant: ACTIVE account AND no rejected docs AND has at least one approved doc
            int compliant = (int) vendors.stream()
                    .filter(v -> "ACTIVE".equalsIgnoreCase(v.status()))
                    .filter(v -> !vendorsWithRejectedDocs.contains(v.vendorId()))
                    .filter(v -> vendorsWithApprovedDocs.contains(v.vendorId()))
                    .count();

            // Non-compliant: BLACKLISTED account OR has any rejected document
            int nonCompliant = (int) vendors.stream()
                    .filter(v -> "BLACKLISTED".equalsIgnoreCase(v.status())
                            || vendorsWithRejectedDocs.contains(v.vendorId()))
                    .count();

            // Pending review: PENDING account OR has docs awaiting review
            int pending = (int) vendors.stream()
                    .filter(v -> "PENDING".equalsIgnoreCase(v.status())
                            || vendorsWithDocsAwaitingReview.contains(v.vendorId()))
                    .filter(v -> !vendorsWithRejectedDocs.contains(v.vendorId()))
                    .count();

            double complianceRate = total > 0 ? (compliant * 100.0 / total) : 0;

            LocalDate today = LocalDate.now();
            LocalDate cutoff = today.plusDays(30);
            int expiringSoon = (int) contracts.stream()
                    .filter(c -> c.endDate() != null)
                    .filter(c -> !c.endDate().isBefore(today) && !c.endDate().isAfter(cutoff))
                    .count();

            return new VendorComplianceRecord(total, compliant, nonCompliant, pending, complianceRate, expiringSoon);
        } catch (Exception ex) {
            return new VendorComplianceRecord(0, 0, 0, 0, 0, 0);
        }
    }

    /** Defensive wrapper — never propagates Feign failures up to the controller. */
    private List<VendorDocumentDTO> safeFetchDocuments(String status) {
        try {
            return vendorServiceClient.getDocumentsByStatus(status);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<VendorInvoiceDTO> safeFetchInvoices(String status) {
        try {
            return vendorServiceClient.getInvoicesByStatus(status);
        } catch (Exception ex) {
            return List.of();
        }
    }

    // ===== Site Engineer Analytics =====

    public List<SiteEngineerPerformanceRecord> getSiteEngineerPerformance() {
        return fetchSiteEngineerPerformances();
    }

    public SiteEngineerPerformanceRecord getSiteEngineerPerformanceById(String engineerId) {
        return fetchSiteEngineerPerformances().stream()
                .filter(e -> e.engineerId().equals(engineerId))
                .findFirst()
                .orElseThrow(() -> new ReportNotFoundException("Site engineer not found: " + engineerId));
    }

    public SiteProgressSummaryRecord getSiteProgressSummary() {
        var engineers = fetchSiteEngineerPerformances();
        int total = engineers.size();
        double avgCompletion = engineers.stream().mapToDouble(SiteEngineerPerformanceRecord::taskCompletionRate).average().orElse(0);
        double avgQuality = engineers.stream().mapToDouble(SiteEngineerPerformanceRecord::qualityScore).average().orElse(0);
        int openIssues = engineers.stream().mapToInt(SiteEngineerPerformanceRecord::issuesPending).sum();
        int resolvedIssues = engineers.stream().mapToInt(SiteEngineerPerformanceRecord::issuesResolved).sum();
        int totalInspections = engineers.stream().mapToInt(SiteEngineerPerformanceRecord::totalInspections).sum();
        long activeSites = engineers.stream().map(SiteEngineerPerformanceRecord::assignedProject).distinct().count();
        double efficiency = avgCompletion > 0 ? (avgCompletion * avgQuality) / 100.0 : 0;
        return new SiteProgressSummaryRecord(total, (int) activeSites, avgCompletion, avgQuality, openIssues, resolvedIssues, totalInspections, efficiency);
    }

    public List<SiteEngineerDailyLogRecord> getSiteEngineerDailyLogs() {
        return fetchSiteEngineerDailyLogs();
    }

    public List<SiteEngineerDailyLogRecord> getSiteEngineerDailyLogsByEngineer(String engineerId) {
        return fetchSiteEngineerDailyLogs().stream()
                .filter(log -> Objects.equals(log.engineerId(), engineerId))
                .toList();
    }

    private String nextReportId() {
        return reportRepository.findAll().stream()
                .map(Report::getReportId)
                .filter(id -> id.startsWith("BSRA"))
                .map(this::extractNumericPart)
                .max(Integer::compareTo)
                .map(max -> formatReportId(max + 1))
                .orElse(formatReportId(1));
    }

    private int extractNumericPart(String id) {
        try {
            return Integer.parseInt(id.substring(4));
        } catch (NumberFormatException | StringIndexOutOfBoundsException ex) {
            return 0;
        }
    }

    private String formatReportId(int value) {
        return String.format("BSRA%03d", value);
    }

    private Map<String, Object> buildProjectMetrics(String targetId) {
        var milestones = fetchMilestones(targetId);
        var progress = calculateProgress(milestones.plannedMilestones(), milestones.actualMilestones());
        var budget = fetchBudgetForProject(targetId);
        return Map.of(
                "projectId", targetId,
                "progressPercent", progress,
                "budgetVariance", budget.actualAmount() - budget.plannedAmount(),
                "generatedAt", OffsetDateTime.now().toString()
        );
    }

    private Map<String, Object> buildResourceMetrics() {
        var usage = fetchResourceUsage();
        return Map.of(
                "usedHours", usage.usedHours(),
                "idleHours", usage.idleHours(),
                "utilizationRate", usage.usedHours() / Math.max(usage.usedHours() + usage.idleHours(), 1.0)
        );
    }

    private Map<String, Object> buildSafetyMetrics() {
        var trend = fetchSafetyTrends();
        var summary = fetchInspectionSummary();
        return Map.of(
                "incidentTrend", trend,
                "scheduled", summary.scheduled(),
                "inProgress", summary.inProgress(),
                "completed", summary.completed(),
                "nonCompliant", summary.nonCompliant(),
                "closed", summary.closed(),
                "total", summary.total()
        );
    }

    private Map<String, Object> buildFinanceMetrics() {
        var cashFlow = fetchCashFlow();
        return Map.of(
                "monthlyCashFlow", cashFlow,
                "budgetAlerts", fetchProjectSummaries().stream().map(ProjectSummaryRecord::budgetVariancePercent).toList()
        );
    }

    private double calculateProgress(int planned, int actual) {
        if (planned == 0) {
            return 0.0;
        }
        return Math.min(100.0, actual * 100.0 / planned);
    }

    private double fetchProjectScheduleVariance(String projectId) {
        var milestones = fetchMilestones(projectId);
        return (milestones.actualMilestones() - milestones.plannedMilestones()) * 1.0;
    }

    private double fetchProjectCostPerformance(String projectId) {
        var budget = fetchBudgetForProject(projectId);
        if (budget.actualAmount() == 0) {
            return 1.0;
        }
        return budget.plannedAmount() / budget.actualAmount();
    }

    private List<ProjectSummaryRecord> fetchProjectSummaries() {
        try {
            var projects = projectServiceClient.getAllProjects();
            return projects.stream()
                    .map(project -> new ProjectSummaryRecord(
                            project.projectId(),
                            project.projectName(),
                            calculateProjectCompletion(project.totalMilestones(), project.completedMilestones()),
                            calculateBudgetVariance(project.budget()),
                            project.status()
                    ))
                    .toList();
        } catch (Exception ex) {
            // Fallback to dummy data if project service is unavailable
            return List.of(
                    new ProjectSummaryRecord("P-1001", "West Park Towers", 72.3, 8.5, "ON_TRACK"),
                    new ProjectSummaryRecord("P-1002", "Lakeview Residences", 54.1, 12.7, "AT_RISK"),
                    new ProjectSummaryRecord("P-1003", "Steel Bridge Crossing", 88.9, 3.9, "ON_TRACK")
            );
        }
    }

    private double calculateProjectCompletion(Integer total, Integer completed) {
        if (total == null || total == 0) {
            return 0.0;
        }
        if (completed == null) {
            return 0.0;
        }
        return Math.min(100.0, (completed * 100.0) / total);
    }

    private double calculateBudgetVariance(Double budget) {
        if (budget == null || budget <= 0) {
            return 0.0;
        }
        // Simulate budget variance calculation
        return Math.random() * 15 - 5;
    }

    private List<SafetyTrendRecord> fetchSafetyTrends() {
        try {
            var incidents = fetchAllIncidents();
            Map<LocalDate, Map<String, Long>> trendMap = new HashMap<>();

            for (var incident : incidents) {
                LocalDate date = incident.date() != null ? incident.date() : LocalDate.now();
                String severity = incident.severity() != null ? incident.severity() : "UNKNOWN";
                trendMap.computeIfAbsent(date, k -> new HashMap<>())
                        .merge(severity, 1L, Long::sum);
            }

            return trendMap.entrySet().stream()
                    .flatMap(dateEntry -> dateEntry.getValue().entrySet().stream()
                            .map(severityEntry -> new SafetyTrendRecord(
                                    dateEntry.getKey(),
                                    severityEntry.getKey(),
                                    severityEntry.getValue()
                            )))
                    .sorted((a, b) -> b.date().compareTo(a.date()))
                    .toList();
        } catch (Exception ex) {
            throw new AggregationException(
                    "Failed to fetch incident trends from safety-service. Provide a valid Bearer token when calling analytics endpoints.",
                    ex
            );
        }
    }

    private SafetyInspectionSummaryData fetchInspectionSummary() {
        try {
            var inspections = fetchAllInspections();
            long scheduled = inspections.stream().filter(i -> "SCHEDULED".equalsIgnoreCase(i.status())).count();
            long inProgress = inspections.stream().filter(i -> "IN_PROGRESS".equalsIgnoreCase(i.status())).count();
            long completed = inspections.stream().filter(i -> "COMPLETED".equalsIgnoreCase(i.status())).count();
            long nonCompliant = inspections.stream().filter(i -> "NON_COMPLIANT".equalsIgnoreCase(i.status())).count();
            long closed = inspections.stream().filter(i -> "CLOSED".equalsIgnoreCase(i.status())).count();
            long total = inspections.size();

            return new SafetyInspectionSummaryData(scheduled, inProgress, completed, nonCompliant, closed, total);
        } catch (Exception ex) {
            throw new AggregationException(
                    "Failed to fetch inspection summary from safety-service. Provide a valid Bearer token when calling analytics endpoints.",
                    ex
            );
        }
    }


    private BudgetData fetchBudgetForProject(String projectId) {
        try {
            var budgets = financeServiceClient.getBudgetsByProject(projectId);
            double planned = budgets.stream()
                    .mapToDouble(b -> b.plannedAmount() != null ? b.plannedAmount().doubleValue() : 0.0)
                    .sum();
            double actual = budgets.stream()
                    .mapToDouble(b -> b.actualAmount() != null ? b.actualAmount().doubleValue() : 0.0)
                    .sum();
            return new BudgetData(planned, actual);
        } catch (Exception ex) {
            return new BudgetData(0.0, 0.0);
        }
    }

    private List<CashFlowRecord> fetchCashFlow() {
        try {
            Map<YearMonth, double[]> totalsByMonth = new HashMap<>();
            var projects = projectServiceClient.getAllProjects();

            for (var project : projects) {
                if (project.projectId() == null || project.projectId().isBlank()) {
                    continue;
                }

                YearMonth month = project.startDate() != null
                        ? YearMonth.from(project.startDate())
                        : YearMonth.now();
                var budgets = financeServiceClient.getBudgetsByProject(project.projectId());

                double inflow = budgets.stream()
                        .mapToDouble(b -> b.plannedAmount() != null ? b.plannedAmount().doubleValue() : 0.0)
                        .sum();
                double outflow = budgets.stream()
                        .mapToDouble(b -> b.actualAmount() != null ? b.actualAmount().doubleValue() : 0.0)
                        .sum();

                totalsByMonth.computeIfAbsent(month, k -> new double[] {0.0, 0.0});
                totalsByMonth.get(month)[0] += inflow;
                totalsByMonth.get(month)[1] += outflow;
            }

            return totalsByMonth.entrySet().stream()
                    .map(entry -> new CashFlowRecord(
                            entry.getKey(),
                            entry.getValue()[0],
                            entry.getValue()[1],
                            entry.getValue()[0] - entry.getValue()[1]
                    ))
                    .sorted(Comparator.comparing(CashFlowRecord::month))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private ProjectMilestoneData fetchMilestones(String projectId) {
        try {
            var milestones = projectServiceClient.getMilestones(projectId);
            int total = milestones.size();
            int completed = (int) milestones.stream()
                    .filter(m -> "COMPLETED".equalsIgnoreCase(m.status()))
                    .count();
            return new ProjectMilestoneData(total, completed);
        } catch (Exception ex) {
            return new ProjectMilestoneData(18, 16);
        }
    }

    private ResourceUsageData fetchResourceUsage() {
        try {
            var resources = resourceServiceClient.getAllResources();
            var allocations = resourceServiceClient.getAllAllocations();

            // Calculate used hours from active allocations
            double usedHours = 0;
            int totalLabors = 0;
            for (var alloc : allocations) {
                int laborCount = alloc.resource() != null && alloc.resource().numberOfLabors() != null
                        ? alloc.resource().numberOfLabors() : 0;
                totalLabors += laborCount;
                if ("active".equalsIgnoreCase(alloc.status())) {
                    LocalDate start = alloc.assignedDate() != null ? alloc.assignedDate() : LocalDate.now();
                    LocalDate end = alloc.releasedDate() != null ? alloc.releasedDate() : LocalDate.now();
                    long days = Math.max(ChronoUnit.DAYS.between(start, end), 1);
                    usedHours += days * 8; // 8 hours per day
                }
            }

            // Calculate idle hours from available resources not actively allocated
            long activeResourceCount = allocations.stream()
                    .filter(a -> "active".equalsIgnoreCase(a.status()))
                    .map(a -> a.resource() != null ? a.resource().resourceId() : "")
                    .distinct()
                    .count();
            long totalResources = resources.size();
            long idleResourceCount = Math.max(totalResources - activeResourceCount, 0);
            double idleHours = idleResourceCount * 8.0 * 5; // 5 working days

            return new ResourceUsageData(usedHours, idleHours, totalLabors);
        } catch (Exception ex) {
            return new ResourceUsageData(1_760.0, 420.0, 0);
        }
    }

    private List<LaborAllocationRecord> fetchLaborAllocations() {
        try {
            var allocations = resourceServiceClient.getAllAllocations();

            // Group allocations by projectId and compute hours
            return allocations.stream()
                    .filter(a -> a.resource() != null)
                    .collect(Collectors.groupingBy(a -> a.projectId() != null ? a.projectId() : "Unknown"))
                    .entrySet().stream()
                    .map(entry -> {
                        String projectId = entry.getKey();
                        var projectAllocations = entry.getValue();

                        double allocatedHours = 0;
                        double availableHours = 0;
                        int numberOfLabors = 0;

                        for (var alloc : projectAllocations) {
                            LocalDate start = alloc.assignedDate() != null ? alloc.assignedDate() : LocalDate.now();
                            LocalDate end = alloc.releasedDate() != null ? alloc.releasedDate() : LocalDate.now();
                            long days = Math.max(ChronoUnit.DAYS.between(start, end), 1);
                            double hoursPerDay = 8.0;
                            int laborCount = alloc.resource().numberOfLabors() != null ? alloc.resource().numberOfLabors() : 1;
                            numberOfLabors += laborCount;

                            if ("active".equalsIgnoreCase(alloc.status())) {
                                allocatedHours += days * hoursPerDay * laborCount;
                            }
                            availableHours += days * hoursPerDay * laborCount;
                        }

                        return new LaborAllocationRecord(projectId, allocatedHours, availableHours, numberOfLabors);
                    })
                    .toList();
        } catch (Exception ex) {
            return List.of(
                    new LaborAllocationRecord("Site A", 820.0, 980.0, 0),
                    new LaborAllocationRecord("Site B", 610.0, 720.0, 0),
                    new LaborAllocationRecord("Site C", 340.0, 460.0, 0)
            );
        }
    }

    private double fetchBudgetVarianceAverage() {
        return 7.4;
    }

    private double fetchSafetyCompliance() {
        var summary = fetchInspectionSummary();
        if (summary.total() == 0) {
            return 0.0;
        }
        return (summary.completed() + summary.closed()) * 1.0 / summary.total();
    }

    private String categorizeSeverity(int incidentCount) {
        return switch (incidentCount) {
            case 0, 1 -> "LOW";
            case 2, 3 -> "MEDIUM";
            default -> "HIGH";
        };
    }

    private Map<String, Object> buildVendorMetrics() {
        // Vendor scope reports now expose performance + compliance only.
        // The spend slice was removed along with the /spend endpoint.
        var performances = fetchVendorPerformances();
        var compliance = getVendorCompliance();
        var avgRating = performances.stream()
                .mapToDouble(VendorPerformanceRecord::qualityScore)
                .average().orElse(0);
        return Map.of(
                "vendorCount", performances.size(),
                "averageQualityScore", avgRating,
                "compliance", compliance,
                "generatedAt", OffsetDateTime.now().toString()
        );
    }

    private List<VendorPerformanceRecord> fetchVendorPerformances() {
        try {
            var vendors = vendorServiceClient.getAllVendors();
            var contracts = vendorServiceClient.getAllContracts();
            var deliveries = vendorServiceClient.getAllDeliveries();

            // Pull invoices across the relevant statuses so we can compute
            // approval/rejection rates and real spend per vendor.
            var approvedInvoices = safeFetchInvoices("APPROVED");
            var paidInvoices     = safeFetchInvoices("PAID");
            var rejectedInvoices = safeFetchInvoices("REJECTED");
            var submittedInvoices = safeFetchInvoices("SUBMITTED");

            Map<String, List<VendorContractDTO>> contractsByVendor = contracts.stream()
                    .filter(c -> c.vendorId() != null)
                    .collect(Collectors.groupingBy(VendorContractDTO::vendorId));
            Map<String, String> contractToVendor = contracts.stream()
                    .filter(c -> c.contractId() != null && c.vendorId() != null)
                    .collect(Collectors.toMap(VendorContractDTO::contractId, VendorContractDTO::vendorId, (left, right) -> left));
            Map<String, List<VendorDeliveryDTO>> deliveriesByVendor = deliveries.stream()
                    .filter(d -> d.contractId() != null && contractToVendor.containsKey(d.contractId()))
                    .collect(Collectors.groupingBy(d -> contractToVendor.get(d.contractId())));

            // Group invoices per vendor via the contract → vendor mapping.
            java.util.function.Function<List<VendorInvoiceDTO>, Map<String, List<VendorInvoiceDTO>>> groupByVendor =
                    list -> list.stream()
                            .filter(i -> i.contractId() != null && contractToVendor.containsKey(i.contractId()))
                            .collect(Collectors.groupingBy(i -> contractToVendor.get(i.contractId())));

            Map<String, List<VendorInvoiceDTO>> approvedByVendor  = groupByVendor.apply(approvedInvoices);
            Map<String, List<VendorInvoiceDTO>> paidByVendor      = groupByVendor.apply(paidInvoices);
            Map<String, List<VendorInvoiceDTO>> rejectedByVendor  = groupByVendor.apply(rejectedInvoices);
            Map<String, List<VendorInvoiceDTO>> submittedByVendor = groupByVendor.apply(submittedInvoices);

            return vendors.stream()
                    .map(vendor -> {
                        var vendorContracts = contractsByVendor.getOrDefault(vendor.vendorId(), List.of());
                        var vendorDeliveries = deliveriesByVendor.getOrDefault(vendor.vendorId(), List.of());

                        int activeContracts = (int) vendorContracts.stream()
                                .filter(c -> "ACTIVE".equalsIgnoreCase(c.status()))
                                .count();

                        // Successful delivery states: DELIVERED + RECEIVED (Feature Set 2).
                        int totalDeliveries = vendorDeliveries.size();
                        int successful = (int) vendorDeliveries.stream()
                                .filter(d -> "DELIVERED".equalsIgnoreCase(d.status())
                                        || "RECEIVED".equalsIgnoreCase(d.status()))
                                .count();
                        int cancelled = (int) vendorDeliveries.stream()
                                .filter(d -> "CANCELLED".equalsIgnoreCase(d.status())
                                        || "NOT_RECEIVED".equalsIgnoreCase(d.status()))
                                .count();

                        double onTimeDeliveryRate = totalDeliveries > 0
                                ? (successful * 100.0 / totalDeliveries) : 0.0;

                        // Real invoice approval rate from the vendor module.
                        int approvedCount = approvedByVendor.getOrDefault(vendor.vendorId(), List.of()).size()
                                + paidByVendor.getOrDefault(vendor.vendorId(), List.of()).size();
                        int rejectedCount = rejectedByVendor.getOrDefault(vendor.vendorId(), List.of()).size();
                        int reviewedCount = approvedCount + rejectedCount;
                        double invoiceApprovalRate = reviewedCount > 0
                                ? (approvedCount * 100.0 / reviewedCount) : 100.0;

                        // Quality score blends delivery success and invoice approval.
                        double qualityScore = Math.max(0.0, Math.min(100.0,
                                (onTimeDeliveryRate * 0.5)
                                        + (invoiceApprovalRate * 0.4)
                                        - (cancelled * 2.0)
                                        + (activeContracts * 1.5)));

                        // Real spend = sum of APPROVED+PAID invoice amounts for this vendor.
                        double plannedValue = vendorContracts.stream()
                                .mapToDouble(c -> c.value() != null ? c.value().doubleValue() : 0.0)
                                .sum();
                        double actualSpend = sumInvoiceAmounts(approvedByVendor.getOrDefault(vendor.vendorId(), List.of()))
                                + sumInvoiceAmounts(paidByVendor.getOrDefault(vendor.vendorId(), List.of()));
                        // Fall back to extrapolated spend when no invoices have been raised yet.
                        if (actualSpend == 0.0) {
                            actualSpend = plannedValue * (1.0 + Math.max(0.0, 100.0 - onTimeDeliveryRate) / 500.0);
                        }
                        double costVariance = plannedValue > 0
                                ? ((actualSpend - plannedValue) / plannedValue) * 100.0 : 0.0;

                        return new VendorPerformanceRecord(
                                vendor.vendorId(),
                                vendor.name(),
                                onTimeDeliveryRate,
                                qualityScore,
                                costVariance,
                                activeContracts,
                                toVendorRating(qualityScore)
                        );
                    })
                    .sorted(Comparator.comparing(VendorPerformanceRecord::vendorId))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static double sumInvoiceAmounts(List<VendorInvoiceDTO> invoices) {
        return invoices.stream()
                .filter(i -> i.amount() != null)
                .mapToDouble(i -> i.amount().doubleValue())
                .sum();
    }

    private Map<String, Object> buildSiteEngineerMetrics() {
        var performances = fetchSiteEngineerPerformances();
        var avgCompletion = performances.stream().mapToDouble(SiteEngineerPerformanceRecord::taskCompletionRate).average().orElse(0);
        var avgQuality = performances.stream().mapToDouble(SiteEngineerPerformanceRecord::qualityScore).average().orElse(0);
        return Map.of(
                "engineerCount", performances.size(),
                "avgTaskCompletionRate", avgCompletion,
                "avgQualityScore", avgQuality,
                "generatedAt", OffsetDateTime.now().toString()
        );
    }

    private List<SiteEngineerPerformanceRecord> fetchSiteEngineerPerformances() {
        try {
            var projectMap = fetchProjectIdToNameMap();
            var issues = fetchAllSiteIssues();
            var logs = fetchAllSiteLogs();

            Map<String, List<SiteIssueDTO>> issuesByEngineer = issues.stream()
                    .filter(i -> i.reportedBy() != null && !i.reportedBy().isBlank())
                    .collect(Collectors.groupingBy(SiteIssueDTO::reportedBy));
            Map<String, List<SiteLogDTO>> logsByEngineer = logs.stream()
                    .filter(l -> l.submittedBy() != null && !l.submittedBy().isBlank())
                    .collect(Collectors.groupingBy(SiteLogDTO::submittedBy));

            var allEngineerIds = new java.util.HashSet<String>();
            allEngineerIds.addAll(issuesByEngineer.keySet());
            allEngineerIds.addAll(logsByEngineer.keySet());

            return allEngineerIds.stream()
                    .map(engineerId -> toPerformanceRecord(
                            engineerId,
                            issuesByEngineer.getOrDefault(engineerId, List.of()),
                            logsByEngineer.getOrDefault(engineerId, List.of()),
                            projectMap
                    ))
                    .sorted(Comparator.comparing(SiteEngineerPerformanceRecord::engineerId))
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<SiteEngineerDailyLogRecord> fetchSiteEngineerDailyLogs() {
        try {
            var projectMap = fetchProjectIdToNameMap();
            var issues = fetchAllSiteIssues();
            Map<String, Long> issuesByLogId = issues.stream()
                    .filter(i -> i.logId() != null && !i.logId().isBlank())
                    .collect(Collectors.groupingBy(SiteIssueDTO::logId, Collectors.counting()));

            return fetchAllSiteLogs().stream()
                    .map(log -> toDailyLogRecord(log, issuesByLogId, projectMap))
                    .sorted(Comparator.comparing(SiteEngineerDailyLogRecord::date).reversed())
                    .toList();
        } catch (Exception ex) {
            return List.of();
        }
    }

    private SiteEngineerPerformanceRecord toPerformanceRecord(
            String engineerId,
            List<SiteIssueDTO> engineerIssues,
            List<SiteLogDTO> engineerLogs,
            Map<String, String> projectMap
    ) {
        int totalIssues = engineerIssues.size();
        int resolved = (int) engineerIssues.stream()
                .filter(i -> "RESOLVED".equalsIgnoreCase(i.status()) || "CLOSED".equalsIgnoreCase(i.status()))
                .count();
        int pending = (int) engineerIssues.stream()
                .filter(i -> "OPEN".equalsIgnoreCase(i.status()) || "IN_PROGRESS".equalsIgnoreCase(i.status()) || "ESCALATED".equalsIgnoreCase(i.status()))
                .count();
        double completionRate = totalIssues > 0 ? (resolved * 100.0 / totalIssues) : 0.0;
        double avgHoursOnSite = engineerLogs.isEmpty() ? 0.0 : engineerLogs.stream()
                .map(SiteLogDTO::progressPercent)
                .filter(Objects::nonNull)
                .mapToDouble(p -> Math.max(4.0, Math.min(12.0, p.doubleValue() / 10.0)))
                .average()
                .orElse(8.0);
        double qualityScore = engineerLogs.isEmpty() ? 0.0 : engineerLogs.stream()
                .map(SiteLogDTO::progressPercent)
                .filter(Objects::nonNull)
                .mapToDouble(p -> Math.max(0.0, Math.min(100.0, p.doubleValue())))
                .average()
                .orElse(0.0);

        String assignedProjectId = engineerLogs.stream()
                .map(SiteLogDTO::projectId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(engineerIssues.stream().map(SiteIssueDTO::projectId).filter(Objects::nonNull).findFirst().orElse("UNKNOWN"));
        String assignedProject = projectMap.getOrDefault(assignedProjectId, assignedProjectId);

        return new SiteEngineerPerformanceRecord(
                engineerId,
                engineerNameFromId(engineerId),
                assignedProject,
                completionRate,
                avgHoursOnSite,
                totalIssues,
                resolved,
                pending,
                qualityScore,
                toPerformanceGrade(qualityScore)
        );
    }

    private SiteEngineerDailyLogRecord toDailyLogRecord(
            SiteLogDTO log,
            Map<String, Long> issuesByLogId,
            Map<String, String> projectMap
    ) {
        double progress = log.progressPercent() != null ? log.progressPercent().doubleValue() : 0.0;
        int tasksAssigned = 10;
        int tasksCompleted = (int) Math.round((Math.max(0.0, Math.min(100.0, progress)) / 100.0) * tasksAssigned);
        int issuesReported = issuesByLogId.getOrDefault(log.logId(), 0L).intValue();
        String projectName = projectMap.getOrDefault(log.projectId(), log.projectId());

        return new SiteEngineerDailyLogRecord(
                log.logId(),
                log.submittedBy(),
                engineerNameFromId(log.submittedBy()),
                log.projectId(),
                projectName,
                log.logDate() != null ? log.logDate().toString() : LocalDate.now().toString(),
                Math.max(4.0, Math.min(12.0, progress / 10.0)),
                tasksCompleted,
                tasksAssigned,
                issuesReported,
                "N/A",
                log.activities() != null && !log.activities().isBlank() ? log.activities() : log.issuesSummary()
        );
    }

    private List<SiteIssueDTO> fetchAllSiteIssues() {
        List<SiteIssueDTO> allIssues = new ArrayList<>();
        for (var project : projectServiceClient.getAllProjects()) {
            if (project.projectId() == null || project.projectId().isBlank()) {
                continue;
            }
            allIssues.addAll(siteEngineerServiceClient.getIssues(project.projectId(), null, null, null));
        }
        return allIssues;
    }

    private List<SiteLogDTO> fetchAllSiteLogs() {
        List<SiteLogDTO> allLogs = new ArrayList<>();
        for (var project : projectServiceClient.getAllProjects()) {
            if (project.projectId() == null || project.projectId().isBlank()) {
                continue;
            }
            allLogs.addAll(siteEngineerServiceClient.getSiteLogs(project.projectId(), null, null));
        }
        return allLogs;
    }

    private Map<String, String> fetchProjectIdToNameMap() {
        return projectServiceClient.getAllProjects().stream()
                .collect(Collectors.toMap(ProjectDTO::projectId, ProjectDTO::projectName, (left, right) -> left));
    }

    private String engineerNameFromId(String engineerId) {
        if (engineerId == null || engineerId.isBlank()) {
            return "Unknown Engineer";
        }
        return "Engineer " + engineerId;
    }

    private String toPerformanceGrade(double qualityScore) {
        if (qualityScore >= 90) {
            return "A";
        }
        if (qualityScore >= 80) {
            return "B+";
        }
        if (qualityScore >= 70) {
            return "B";
        }
        if (qualityScore >= 60) {
            return "C+";
        }
        return "C";
    }

    private String toVendorRating(double qualityScore) {
        if (qualityScore >= 90) {
            return "A+";
        }
        if (qualityScore >= 80) {
            return "A";
        }
        if (qualityScore >= 70) {
            return "B+";
        }
        if (qualityScore >= 60) {
            return "B";
        }
        return "C";
    }

    private record ProjectMilestoneData(int plannedMilestones, int actualMilestones) {
    }

    private record BudgetData(double plannedAmount, double actualAmount) {
    }

    private record ResourceUsageData(double usedHours, double idleHours, int totalLabors) {
    }

    private record SafetyInspectionSummaryData(long scheduled, long inProgress, long completed,
                                                long nonCompliant, long closed, long total) {
    }

    private List<com.buildsmart.analytics.client.IncidentDTO> fetchAllIncidents() {
        List<com.buildsmart.analytics.client.IncidentDTO> allIncidents = new ArrayList<>();
        int page = 0;
        int size = 200;

        while (true) {
            var response = safetyServiceClient.getIncidents(null, null, null, null, null, page, size);
            if (response == null || response.content() == null || response.content().isEmpty()) {
                break;
            }

            allIncidents.addAll(response.content());
            if (page + 1 >= response.totalPages()) {
                break;
            }
            page++;
        }

        return allIncidents;
    }

    private List<com.buildsmart.analytics.client.InspectionSummaryDTO> fetchAllInspections() {
        List<com.buildsmart.analytics.client.InspectionSummaryDTO> allInspections = new ArrayList<>();
        int page = 0;
        int size = 200;

        while (true) {
            var response = safetyServiceClient.getInspections(null, null, null, null, page, size);
            if (response == null || response.content() == null || response.content().isEmpty()) {
                break;
            }

            allInspections.addAll(response.content());
            if (page + 1 >= response.totalPages()) {
                break;
            }
            page++;
        }

        return allInspections;
    }
}
