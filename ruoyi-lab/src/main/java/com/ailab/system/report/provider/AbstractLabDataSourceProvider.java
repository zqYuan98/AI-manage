package com.ailab.system.report.provider;

import com.ailab.system.mapper.LabReportDataMapper;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.report.config.ReportSectionConfig;
import com.ailab.system.report.model.ReportAccessScope;
import com.ailab.system.report.model.ReportContext;
import com.ailab.system.report.model.ReportQueryCriteria;
import com.ailab.system.report.model.ReportSectionData;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
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
    private final String id; private final Set<String> fields;
    protected AbstractLabDataSourceProvider(String id, Set<String> fields) { this.id = id; this.fields = fields; }
    @Override public final String getId() { return id; }
    @Override public final boolean supports(String providerId) { return id.equals(providerId); }
    @Override public final ReportSectionData load(ReportContext context, ReportSectionConfig section) {
        if (context == null || section == null) throw new IllegalArgumentException("context and section are required");
        if ("MANUAL".equals(section.getSectionType())) { if (!ReportConfigCatalog.MANUAL_SUMMARY.equals(id)) throw new IllegalArgumentException("Only manual provider may load a manual section"); }
        else if (!id.equals(section.getDataSource()) || !ReportConfigCatalog.compatibleProviders(section.getSectionType()).contains(id)) throw new IllegalArgumentException("Provider is not compatible with section");
        validateConfig(section); return loadValidated(ReportQueryCriteria.from(context, section), section);
    }
    protected abstract ReportSectionData loadValidated(ReportQueryCriteria criteria, ReportSectionConfig section);
    protected final LabReportDataMapper mapper() { if (mapper == null) throw new IllegalStateException("Report data mapper is unavailable"); return mapper; }
    protected final ReportSectionData section(ReportQueryCriteria criteria, ReportSectionConfig cfg, List<Map<String, Object>> rows, Map<String, Object> summary) {
        List<Map<String, Object>> filtered = applyQueryConfig(criteria, rows);
        Map<String, Object> summaryCopy = new LinkedHashMap<String, Object>(summary == null ? Collections.<String, Object>emptyMap() : summary);
        if (summaryCopy.containsKey("count")) summaryCopy.put("count", filtered.size());
        return new ReportSectionData(cfg.getSectionCode(), cfg.getSectionType(), cfg.getTitle(), filtered, summaryCopy);
    }
    protected final Map<String, Object> summaryCount(List<Map<String, Object>> rows) { Map<String,Object> value = new LinkedHashMap<String,Object>(); value.put("count", rows.size()); return value; }
    protected final List<Map<String, Object>> copyRows(List<Map<String, Object>> rows) { return rows == null ? Collections.<Map<String,Object>>emptyList() : new ArrayList<Map<String,Object>>(rows); }
    protected final BigDecimal number(Object value) { return value instanceof BigDecimal ? (BigDecimal) value : value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value)); }
    private void validateConfig(ReportSectionConfig section) {
        checkField(section.getQueryConfig().get("sort")); checkField(section.getQueryConfig().get("groupBy"));
        checkField(section.getRenderConfig().get("groupBy")); Object filters = section.getQueryConfig().get("filters");
        if (filters instanceof Iterable) for (Object item : (Iterable<?>) filters) {
            if (!(item instanceof Map)) throw new IllegalArgumentException("Unsupported report filter");
            Object field = ((Map<?, ?>) item).get("field"); checkField(field);
        }
    }
    private List<Map<String, Object>> applyQueryConfig(ReportQueryCriteria criteria, List<Map<String, Object>> source) {
        List<Map<String, Object>> rows = copyRows(source);
        for (ReportQueryCriteria.Filter filter : criteria.getFilters()) {
            String field = column(filter.getField()); String operator = filter.getOperator(); Object expected = filter.getValue();
            List<Map<String, Object>> next = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> row : rows) if (matches(row.get(field), operator, expected)) next.add(row); rows = next;
        }
        if (criteria.getSort() != null) sort(rows, column(criteria.getSort()));
        if (criteria.getGroupBy() != null) sort(rows, column(criteria.getGroupBy()));
        return rows;
    }
    private boolean matches(Object actual, String operator, Object expected) {
        if ("IN".equals(operator)) { if (!(expected instanceof Collection)) return false; for (Object value : (Collection<?>) expected) if (same(actual, value)) return true; return false; }
        if ("BETWEEN".equals(operator)) { if (!(expected instanceof List) || ((List<?>) expected).size() != 2) return false; return compare(actual, ((List<?>) expected).get(0)) >= 0 && compare(actual, ((List<?>) expected).get(1)) <= 0; }
        if ("EQ".equals(operator)) return same(actual, expected); if ("NE".equals(operator)) return !same(actual, expected);
        if ("GTE".equals(operator)) return compare(actual, expected) >= 0; if ("LTE".equals(operator)) return compare(actual, expected) <= 0;
        throw new IllegalArgumentException("Unsupported report operator");
    }
    private void sort(List<Map<String, Object>> rows, final String field) { Collections.sort(rows, new Comparator<Map<String, Object>>() { @Override public int compare(Map<String, Object> left, Map<String, Object> right) { return AbstractLabDataSourceProvider.this.compare(left.get(field), right.get(field)); } }); }
    private boolean same(Object actual, Object expected) { return compare(actual, expected) == 0; }
    private int compare(Object actual, Object expected) { if (actual == expected) return 0; if (actual == null) return -1; if (expected == null) return 1; try { return number(actual).compareTo(number(expected)); } catch (RuntimeException ignored) { return String.valueOf(actual).compareTo(String.valueOf(expected)); } }
    private String column(String field) { if ("owner".equals(field) || "memberId".equals(field)) return "ownerId"; return field; }
    private void checkField(Object value) { if (value != null && (!(value instanceof String) || !fields.contains(value))) throw new IllegalArgumentException("Unsupported report field"); }
}
