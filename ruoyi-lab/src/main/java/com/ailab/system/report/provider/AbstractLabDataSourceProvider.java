package com.ailab.system.report.provider;

import com.ailab.system.mapper.LabReportDataMapper;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportAccessScope;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportQueryCriteria;
import com.ailab.system.report.model.ReportPeriod;
import com.ailab.system.report.model.ReportSectionData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;

/** Common contract guard: period, provider/section compatibility and configuration fields are verified
 * before a provider reaches MyBatis. */
public abstract class AbstractLabDataSourceProvider implements DataSourceProvider {
    @Autowired(required = false) private LabReportDataMapper mapper;
    private final String id; private final ReportFactClassification classification; private final List<ReportFieldSpec> fieldSpecs; private final List<String> schema; private final Map<String,ReportFieldSpec> specsByName; private final Set<String> fields;
    /**
     * The schema is both the sole query-field allow-list and the published row contract.  Keeping a
     * copied insertion-order list prevents a JDBC driver's sparse Map (it may omit NULL columns)
     * from changing the shape or field order of a report section.
     */
    protected AbstractLabDataSourceProvider(String id, ReportFactClassification classification, Set<String> fields) {
        if (classification == null) throw new IllegalArgumentException("Report fact classification is required");
        this.id = id; this.classification = classification;
        this.fieldSpecs = ReportFieldSpec.fromNames(fields);
        List<String> names=new ArrayList<String>(); Map<String,ReportFieldSpec> specs=new LinkedHashMap<String,ReportFieldSpec>();
        for(ReportFieldSpec spec:fieldSpecs){names.add(spec.getName());specs.put(spec.getName(),spec);}
        this.schema = Collections.unmodifiableList(names); this.specsByName=Collections.unmodifiableMap(specs);
        this.fields = Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(this.schema));
    }
    protected AbstractLabDataSourceProvider(String id, Set<String> fields) {
        this(id, ReportFactClassification.requireForProvider(id), fields);
    }
    @Override public final String getId() { return id; }
    @Override public final ReportFactClassification getFactClassification() { return classification; }
    @Override public final boolean supports(String providerId) { return id.equals(providerId); }
    @Override public final ReportSectionData load(ReportContext context, ReportSectionConfig section) {
        if (context == null || section == null) throw new IllegalArgumentException("context and section are required");
        requireFormalPins(context);
        String requiredPermission = section.getSensitivePermission();
        if (requiredPermission != null && !requiredPermission.trim().isEmpty()
                && !context.getAccessScope().hasPermission(requiredPermission)) {
            throw new SecurityException("Missing report section permission: " + requiredPermission);
        }
        if ("MANUAL".equals(section.getSectionType())) { if (!ReportConfigCatalog.MANUAL_SUMMARY.equals(id)) throw new IllegalArgumentException("Only manual provider may load a manual section"); }
        else if (!id.equals(section.getDataSource()) || !ReportConfigCatalog.compatibleProviders(section.getSectionType()).contains(id)) throw new IllegalArgumentException("Provider is not compatible with section");
        validateConfig(section); ReportQueryCriteria criteria=ReportQueryCriteria.from(context, section); validateCriteria(criteria); if (!supports(criteria.getReportPeriod().getKind())) throw new IllegalArgumentException("Provider does not support this period kind"); return loadValidated(criteria, section);
    }
    private void requireFormalPins(ReportContext context) {
        if (!context.isFinalSnapshot()) return;
        if (classification == ReportFactClassification.FORMAL_CLOSE_SNAPSHOT && context.getSourceCloseRevision() == null)
            throw new IllegalStateException("Final report close revision is unavailable");
        if (classification == ReportFactClassification.FORMAL_SNAPSHOT && context.getSourceFormalRevision() == null)
            throw new IllegalStateException("Final report formal revision is unavailable");
        if (classification == ReportFactClassification.CONTEXT_SNAPSHOT && context.getExecutionCutoff() == null)
            throw new IllegalStateException("Final report execution cutoff is unavailable");
        if (classification == ReportFactClassification.MANUAL_REVISION
                && !Boolean.TRUE.equals(context.getAttributes().get("manualRevisionPinned")))
            throw new IllegalStateException("Final report manual revision is unavailable");
    }
    protected abstract ReportSectionData loadValidated(ReportQueryCriteria criteria, ReportSectionConfig section);
    protected boolean supports(ReportPeriod.Kind kind) { return kind == ReportPeriod.Kind.MONTH; }
    protected final LabReportDataMapper mapper() { if (mapper == null) throw new IllegalStateException("Report data mapper is unavailable"); return mapper; }
    protected final ReportSectionData section(ReportQueryCriteria criteria, ReportSectionConfig cfg, List<Map<String, Object>> rows, Map<String, Object> summary) {
        if (rows != null && rows.size() >= criteria.getSourceFetchLimit()) {
            throw sourceOverflow();
        }
        if (enforcesSourceRowLimit() && rows != null && rows.size() > ReportQueryCriteria.MAX_SOURCE_ROWS) {
            throw sourceOverflow();
        }
        List<Map<String, Object>> filtered = applyQueryConfig(criteria, normalizeRows(rows)); List<Map<String, Object>> preLimit = filtered; int matched = filtered.size();
        Object rawLimit = cfg.getQueryConfig().get("limit"); if (rawLimit instanceof Number && ((Number) rawLimit).intValue() < filtered.size()) filtered = new ArrayList<Map<String, Object>>(filtered.subList(0, ((Number) rawLimit).intValue()));
        Map<String, Object> summaryCopy = new LinkedHashMap<String, Object>(summary == null ? Collections.<String, Object>emptyMap() : summary);
        recomputeFilteredSummary(preLimit, summaryCopy);
        summaryCopy.put("matchedCount", matched); summaryCopy.put("returnedCount", filtered.size()); if (summaryCopy.containsKey("count")) summaryCopy.put("count", matched);
        if (criteria.getGroupBy() != null) summaryCopy.put("groups", groups(preLimit, criteria.getGroupBy()));
        return new ReportSectionData(cfg.getSectionCode(), cfg.getSectionType(), cfg.getTitle(), filtered, summaryCopy);
    }
    protected final Map<String, Object> summaryCount(List<Map<String, Object>> rows) { Map<String,Object> value = new LinkedHashMap<String,Object>(); value.put("count", rows.size()); return value; }
    /** Providers with aggregate metrics override this so summaries match the filtered, pre-limit rows. */
    protected void recomputeFilteredSummary(List<Map<String, Object>> rows, Map<String, Object> summary) { }
    /** Authoritative aggregate projections may opt out because they do not materialize detail rows. */
    protected boolean enforcesSourceRowLimit() { return true; }
    protected final List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) {
        if (enforcesSourceRowLimit()) requireSourceWithinLimit(rows == null ? 0 : rows.size());
        return rows == null ? Collections.<Map<String,Object>>emptyList() : new ArrayList<Map<String,Object>>(rows);
    }
    protected final void requireSourceWithinLimit(int sourceSize) { if (sourceSize > ReportQueryCriteria.MAX_SOURCE_ROWS) throw sourceOverflow(); }
    /** Normalizes every row before filtering so NULL output columns remain filterable and visible. */
    private List<Map<String, Object>> normalizeRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> raw : copyRows(rows)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (ReportFieldSpec spec : fieldSpecs) row.put(spec.getName(), spec.normalize(normalizeValue(spec.getName(), raw == null ? null : raw.get(spec.getName()))));
            result.add(row);
        }
        return result;
    }
    /** Provider-specific JDBC type normalization without mutating global MyBatis null-map behaviour. */
    protected Object normalizeValue(String field, Object value) {
        if (value instanceof java.sql.Date) return ((java.sql.Date) value).toLocalDate().toString();
        if (value instanceof java.sql.Timestamp) return ((java.sql.Timestamp) value).toInstant().toString();
        if (value instanceof java.util.Date) return ((java.util.Date) value).toInstant().toString();
        return value;
    }
    /** Exposed for contract tests and renderer integration; list order is the stable output order. */
    public final List<String> getOutputSchema() { return schema; }
    @Override public final List<ReportFieldSpec> getFieldSpecs() { return fieldSpecs; }
    protected final BigDecimal number(Object value) { return value instanceof BigDecimal ? (BigDecimal) value : value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
    private void validateConfig(ReportSectionConfig section) {
        checkField(section.getQueryConfig().get("sort")); checkField(section.getQueryConfig().get("groupBy"));
        checkField(section.getRenderConfig().get("groupBy")); Object columns = section.getRenderConfig().get("columns"); if (columns instanceof Iterable) for (Object item : (Iterable<?>) columns) { if (item instanceof String) checkField(item); else if (item instanceof Map) checkField(((Map<?, ?>) item).get("field")); else throw new IllegalArgumentException("Unsupported report column"); }
        Object metrics = section.getRenderConfig().get("metrics"); if (metrics instanceof Iterable) for (Object metric : (Iterable<?>) metrics) if (!(metric instanceof String) || !getSupportedMetrics().contains(metric)) throw new IllegalArgumentException("Unsupported report metric");
        Object filters = section.getQueryConfig().get("filters");
        if (filters instanceof Iterable) for (Object item : (Iterable<?>) filters) {
            if (!(item instanceof Map)) throw new IllegalArgumentException("Unsupported report filter");
            Object field = ((Map<?, ?>) item).get("field"); checkField(field);
        }
    }
    private void validateCriteria(ReportQueryCriteria criteria){for(ReportQueryCriteria.Filter filter:criteria.getFilters())specsByName.get(filter.getField()).validate(filter.getOperator(),filter.getValue());}
    private List<Map<String, Object>> applyQueryConfig(ReportQueryCriteria criteria, List<Map<String, Object>> source) {
        List<Map<String, Object>> rows = copyRows(source);
        for (ReportQueryCriteria.Filter filter : criteria.getFilters()) {
            String field = filter.getField(); String operator = filter.getOperator(); Object expected = filter.getValue();
            List<Map<String, Object>> next = new ArrayList<Map<String, Object>>();
            ReportFieldSpec spec=specsByName.get(field); for (Map<String, Object> row : rows) if (spec.matches(row.get(field), operator, expected)) next.add(row); rows = next;
        }
        if (criteria.getSort() != null) sort(rows, criteria.getSort());
        if (criteria.getGroupBy() != null) sort(rows, criteria.getGroupBy());
        return rows;
    }
    private void sort(List<Map<String, Object>> rows, final String field) { final ReportFieldSpec spec=specsByName.get(field); Collections.sort(rows, new Comparator<Map<String, Object>>() { @Override public int compare(Map<String, Object> left, Map<String, Object> right) { return spec.compare(left.get(field), right.get(field)); } }); }
    private List<Map<String, Object>> groups(List<Map<String, Object>> rows, String field) { Map<String, Integer> counts = new LinkedHashMap<String, Integer>(); for (Map<String, Object> row : rows) { String key = String.valueOf(row.get(field)); counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1); } List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(); for (Map.Entry<String, Integer> item : counts.entrySet()) { Map<String, Object> group = new LinkedHashMap<String, Object>(); group.put("field", field); group.put("key", item.getKey()); group.put("count", item.getValue()); result.add(group); } return result; }
    private void checkField(Object value) { if (value != null && (!(value instanceof String) || !fields.contains(value))) throw new IllegalArgumentException("Unsupported report field"); }
    @Override public final Set<String> getSupportedMetrics() { if (ReportConfigCatalog.TASK_STAT.equals(id)) return Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(java.util.Arrays.asList("count", "total", "average", "top"))); if (ReportConfigCatalog.GOAL_PROGRESS.equals(id)) return Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(java.util.Arrays.asList("count", "averageProgressRate"))); if (ReportConfigCatalog.PERF_SUMMARY.equals(id)) return Collections.unmodifiableSet(new java.util.LinkedHashSet<String>(java.util.Arrays.asList("count", "averageScore"))); return Collections.singleton("count"); }
    private static IllegalStateException sourceOverflow(){return new IllegalStateException("Report source exceeds "+ReportQueryCriteria.MAX_SOURCE_ROWS+" rows; narrow the report period or access scope");}
}
