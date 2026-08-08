package com.ailab.system.service.impl;

import com.ailab.system.domain.LabReportSection;
import com.ailab.system.domain.LabReportTemplate;
import com.ailab.system.mapper.LabReportMapper;
import com.ailab.system.report.config.ReportConfigValidator;
import com.ailab.system.report.config.ReportConfigCatalog;
import com.ailab.system.service.LabAccessService;
import com.ailab.system.service.LabReportTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LabReportTemplateServiceImpl implements LabReportTemplateService {
    private static final int MAX_IMPORT_BYTES = 2 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final LabReportMapper mapper; private final LabAccessService access; private final ReportConfigValidator validator;
    public LabReportTemplateServiceImpl(LabReportMapper mapper, LabAccessService access, ReportConfigValidator validator) {
        this.mapper = mapper; this.access = access; this.validator = validator;
    }

    @Override public List<LabReportTemplate> list(Long actorUserId) { access.requireManager(actorUserId); return safe(mapper.selectTemplates()); }
    @Override public LabReportTemplate get(Long id, Long actorUserId) { access.requireManager(actorUserId); return required(mapper.selectTemplateById(id)); }
    @Override public List<LabReportSection> sections(Long templateId, Long actorUserId) { access.requireManager(actorUserId); required(mapper.selectTemplateById(templateId)); return safe(mapper.selectSections(templateId)); }

    @Override @Transactional
    public LabReportTemplate saveRevision(Long sourceTemplateId, LabReportTemplate draft, List<LabReportSection> input,
            boolean saveAsNewFamily, int expectedVersion, Long actorUserId) {
        access.requireManager(actorUserId); if (draft == null) throw new ServiceException("Template configuration is required");
        String actor = actor(actorUserId); LabReportTemplate source = null; boolean familyDefault = false; Long securityBaselineTemplateId=null;List<LabReportTemplate> lockedDestination=null;
        LabReportTemplate snapshot=sourceTemplateId==null?null:required(mapper.selectTemplateById(sourceTemplateId));
        if(saveAsNewFamily){lockedDestination=lockTemplateTypes(draft.getPeriodType(),snapshot==null?null:snapshot.getPeriodType());if(sourceTemplateId!=null){source=required(mapper.selectTemplateForUpdate(sourceTemplateId));if(!same(snapshot.getPeriodType(),source.getPeriodType())||source.getVersion()==null||source.getVersion().intValue()!=expectedVersion)throw new ServiceException("Template changed concurrently; reload before saving");}}
        else if (sourceTemplateId != null) {
                List<LabReportTemplate> lockedType = safe(mapper.lockTemplateType(snapshot.getPeriodType()));
                source = required(mapper.selectTemplateForUpdate(sourceTemplateId));
                if (!same(snapshot.getPeriodType(), source.getPeriodType())) throw new ServiceException("Template changed concurrently; reload before saving");
                familyDefault = familyHasDefault(lockedType, source.getTemplateCode());
                LabReportTemplate latest=latestFamily(lockedType,source.getTemplateCode());securityBaselineTemplateId=latest==null?source.getId():latest.getId();
        }
        LabReportTemplate next;
        if (saveAsNewFamily) {
            if (source != null && same(source.getTemplateCode(), draft.getTemplateCode())) throw new ServiceException("Save-as-new requires a different template code");
            next = copy(draft); next.setRevisionNo(1); next.setLatestFlag("1"); next.setDefaultFlag("0"); next.setVersion(0);
        } else {
            if (source == null) throw new ServiceException("Published source template is required");
            if (source.getVersion() == null || source.getVersion().intValue() != expectedVersion) throw new ServiceException("Template changed concurrently; reload before saving");
            Integer maximum = mapper.selectMaxTemplateRevisionForUpdate(source.getTemplateCode());
            next = copy(draft); next.setTemplateCode(source.getTemplateCode()); next.setPeriodType(source.getPeriodType());
            next.setRevisionNo((maximum == null ? source.getRevisionNo() : maximum) + 1); next.setLatestFlag("1");
            next.setDefaultFlag(familyDefault ? "1" : "0"); next.setVersion(0);
            if (familyDefault && !"ENABLED".equals(next.getStatus())) throw new ServiceException("A default template family must publish an enabled latest revision");
        }
        if(saveAsNewFamily&&!hasValidTypeDefault(lockedDestination)){if(!"ENABLED".equals(next.getStatus()))throw new ServiceException("A report type without a default requires an enabled template");next.setDefaultFlag("1");}
        next.setId(null); next.setDelFlag("0"); next.setCreateBy(actor); validator.validateTemplate(next);
        List<LabReportSection> sections = validateSections(input,next.getPeriodType());
        if (!saveAsNewFamily && source != null) validateSecurityUpdates(safe(mapper.selectSections(securityBaselineTemplateId==null?source.getId():securityBaselineTemplateId)), sections);
        if (!saveAsNewFamily) mapper.clearLatestTemplate(source.getTemplateCode(), actor);
        affected(mapper.insertTemplate(next), "Template revision was not created");
        for (LabReportSection section : sections) { section.setId(null); section.setTemplateId(next.getId()); section.setVersion(0); section.setDelFlag("0"); section.setCreateBy(actor); }
        if (!sections.isEmpty() && mapper.insertSections(next.getId(), sections) != sections.size()) throw new ServiceException("Template sections changed concurrently");
        if (next.isDefaultTemplate()) mapper.clearDefaultTemplate(next.getPeriodType(),next.getId(),actor);
        return next;
    }

    @Override @Transactional
    public void setDefault(Long templateId, int expectedVersion, Long actorUserId) {
        access.requireManager(actorUserId); String actor = actor(actorUserId);
        LabReportTemplate snapshot = required(mapper.selectTemplateById(templateId));
        mapper.lockTemplateType(snapshot.getPeriodType());
        LabReportTemplate template = required(mapper.selectTemplateForUpdate(templateId));
        if (!same(snapshot.getPeriodType(), template.getPeriodType())) throw new ServiceException("Template type changed concurrently");
        if (template.getVersion() == null || template.getVersion().intValue() != expectedVersion || !template.isLatest() || !"ENABLED".equals(template.getStatus())) {
            throw new ServiceException("Only the current enabled template revision can become default");
        }
        mapper.clearDefaultTemplate(template.getPeriodType(), templateId, actor);
        affected(mapper.markDefaultTemplate(templateId, expectedVersion, actor), "Template default changed concurrently");
    }

    private void validateSecurityUpdates(List<LabReportSection> existing, List<LabReportSection> candidate) {
        java.util.Map<String,LabReportSection> prior = new java.util.LinkedHashMap<String,LabReportSection>();
        for (LabReportSection value : existing) prior.put(value.getSectionCode(), value);
        for (LabReportSection value : candidate) if (prior.containsKey(value.getSectionCode())) {
            validator.validateUpdate(prior.get(value.getSectionCode()), value);
        }
    }

    private boolean familyHasDefault(List<LabReportTemplate> values, String templateCode) {
        for (LabReportTemplate value : values) if (same(templateCode, value.getTemplateCode()) && value.isDefaultTemplate()&&value.isLatest()&&"ENABLED".equals(value.getStatus())) return true;
        return false;
    }

    private boolean hasValidTypeDefault(List<LabReportTemplate> values) {
        for (LabReportTemplate value : values) if (value.isDefaultTemplate() && value.isLatest()
                && "ENABLED".equals(value.getStatus()) && !"2".equals(value.getDelFlag())) return true;
        return false;
    }

    private List<LabReportTemplate> lockTemplateTypes(String destination,String source){java.util.Set<String> types=new java.util.TreeSet<String>();if(destination!=null)types.add(destination);if(source!=null)types.add(source);List<LabReportTemplate> destinationRows=Collections.emptyList();for(String type:types){List<LabReportTemplate> rows=safe(mapper.lockTemplateType(type));if(same(type,destination))destinationRows=rows;}return destinationRows;}

    private LabReportTemplate latestFamily(List<LabReportTemplate> values,String templateCode){for(LabReportTemplate value:values)if(same(templateCode,value.getTemplateCode())&&value.isLatest())return value;return null;}

    @Override public String previewMarkdown(Long templateId, Long actorUserId) {
        access.requireManager(actorUserId); LabReportTemplate template = required(mapper.selectTemplateById(templateId));
        StringBuilder value = new StringBuilder(512); value.append("# ").append(plain(template.getTemplateName())).append("\n\n");
        value.append("> ").append(plain(template.getPeriodType())).append(" · v").append(template.getRevisionNo()).append("\n");
        for (LabReportSection section : safe(mapper.selectSections(templateId))) {
            if ("0".equals(section.getVisibleFlag())) continue;
            value.append("\n## ").append(plain(section.getSectionName())).append("\n\n");
            value.append("_").append("MANUAL".equals(section.getSectionType()) ? "待填写的手工摘要" : "预览时不读取业务数据").append("_\n");
        }
        return value.toString();
    }

    @Override public String exportJson(Long templateId, Long actorUserId) {
        access.requireManager(actorUserId); LabReportTemplate template = required(mapper.selectTemplateById(templateId));
        ObjectNode root = JSON.createObjectNode(); ObjectNode value = root.putObject("template");
        value.put("templateCode", template.getTemplateCode()); value.put("templateName", template.getTemplateName());
        value.put("reportType", template.getPeriodType()); value.put("revisionNo", template.getRevisionNo()); value.put("status", template.getStatus());
        json(value, "header", template.getHeaderJson()); json(value, "style", template.getStyleJson());
        ArrayNode sections = root.putArray("sections"); for (LabReportSection section : safe(mapper.selectSections(templateId))) sections.add(sectionJson(section));
        try { return JSON.writeValueAsString(root); } catch (JsonProcessingException ex) { throw new ServiceException("Could not export template JSON"); }
    }

    @Override @Transactional public LabReportTemplate importJson(String source, String newTemplateCode, Long actorUserId) {
        access.requireManager(actorUserId);
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_IMPORT_BYTES) throw new ServiceException("Template JSON is missing or too large");
        validateImportJsonStream(source);
        try {
            JsonNode root = JSON.readTree(source); only(root, "template", "sections"); JsonNode rawTemplate = root.get("template");
            if (rawTemplate == null || !rawTemplate.isObject() || root.get("sections") == null || !root.get("sections").isArray() || root.get("sections").size() > 200) throw new ServiceException("Invalid template JSON document");
            only(rawTemplate, "templateCode", "templateName", "reportType", "revisionNo", "status", "header", "style");
            try { validator.validateTemplateForImport(rawTemplate.toString()); }
            catch (RuntimeException ex) { throw new ServiceException(ex.getMessage()); }
            LabReportTemplate draft = new LabReportTemplate(); draft.setTemplateCode(newTemplateCode); draft.setTemplateName(text(rawTemplate, "templateName"));
            draft.setPeriodType(text(rawTemplate, "reportType")); draft.setRevisionNo(1); draft.setStatus(text(rawTemplate, "status"));
            draft.setHeaderJson(rawTemplate.has("header") ? rawTemplate.get("header").toString() : "{}");
            draft.setStyleJson(rawTemplate.has("style") ? rawTemplate.get("style").toString() : "{}"); draft.setLatestFlag("1"); draft.setDefaultFlag("0"); draft.setVersion(0);
            List<LabReportSection> sections = new ArrayList<LabReportSection>(); int sort = 0;
            for (JsonNode raw : root.get("sections")) sections.add(parseSection(raw, ++sort));
            return saveRevision(null, draft, sections, true, 0, actorUserId);
        } catch (ServiceException ex) { throw ex; }
        catch (Exception ex) { throw new ServiceException("Invalid template JSON document"); }
    }

    private LabReportSection parseSection(JsonNode raw, int fallbackSort) {
        if (raw == null || !raw.isObject()) throw new ServiceException("Invalid report section");
        only(raw, "sectionCode", "sectionName", "sectionType", "sortNo", "dataSource", "queryConfig", "renderConfig", "styleConfig", "manual", "visible", "sensitive", "sensitivePermission");
        LabReportSection section = new LabReportSection(); section.setSectionCode(text(raw, "sectionCode")); section.setSectionName(text(raw, "sectionName"));
        section.setSectionType(text(raw, "sectionType"));
        if (raw.has("sortNo") && (!raw.get("sortNo").isIntegralNumber() || !raw.get("sortNo").canConvertToInt())) throw new ServiceException("Invalid sortNo");
        section.setSortNo(raw.has("sortNo") ? raw.get("sortNo").intValue() : fallbackSort * 10);
        section.setDataSource(optionalText(raw, "dataSource"));
        section.setQueryConfigJson(raw.has("queryConfig") ? raw.get("queryConfig").toString() : "{}");
        section.setRenderConfigJson(raw.has("renderConfig") ? raw.get("renderConfig").toString() : "{}");
        section.setStyleConfigJson(raw.has("styleConfig") ? raw.get("styleConfig").toString() : "{}");
        section.setManualFlag(bool(raw, "manual", false) ? "1" : "0"); section.setVisibleFlag(bool(raw, "visible", true) ? "1" : "0");
        section.setSensitiveFlag(bool(raw, "sensitive", false) ? "1" : "0");
        section.setSensitivePermission(optionalText(raw, "sensitivePermission")); return section;
    }
    private void validateImportJsonStream(String source){
        try{JsonParser parser=JSON.getFactory().createParser(source);int depth=0,tokens=0,totalStrings=0,roots=0;try{JsonToken token;while((token=parser.nextToken())!=null){if(++tokens>100000)throw new ServiceException("Template JSON has too many tokens");if(depth==0&&token!=JsonToken.END_OBJECT&&token!=JsonToken.END_ARRAY&&token!=JsonToken.FIELD_NAME){if(++roots>1)throw new ServiceException("Template JSON must contain exactly one document");}if(token==JsonToken.START_OBJECT||token==JsonToken.START_ARRAY){if(++depth>64)throw new ServiceException("Template JSON nesting is too deep");}else if(token==JsonToken.END_OBJECT||token==JsonToken.END_ARRAY)depth--;if(token==JsonToken.FIELD_NAME||token==JsonToken.VALUE_STRING){int length=parser.getTextLength();if(length>262144||(totalStrings+=length)>1048576)throw new ServiceException("Template JSON string budget exceeded");}}if(roots!=1||depth!=0)throw new ServiceException("Template JSON must contain exactly one document");}finally{parser.close();}}catch(ServiceException ex){throw ex;}catch(Exception ex){throw new ServiceException("Invalid template JSON document");}
    }
    private ObjectNode sectionJson(LabReportSection section) {
        ObjectNode value = JSON.createObjectNode(); value.put("sectionCode", section.getSectionCode()); value.put("sectionName", section.getSectionName());
        value.put("sectionType", section.getSectionType()); value.put("sortNo", section.getSortNo());
        if (section.getDataSource() != null) value.put("dataSource", section.getDataSource());
        json(value, "queryConfig", section.getQueryConfigJson()); json(value, "renderConfig", section.getRenderConfigJson()); json(value, "styleConfig", section.getStyleConfigJson());
        value.put("manual", "1".equals(section.getManualFlag())); value.put("visible", !"0".equals(section.getVisibleFlag())); value.put("sensitive", section.isSensitive());
        if (section.getSensitivePermission() != null) value.put("sensitivePermission", section.getSensitivePermission()); return value;
    }
    private void json(ObjectNode target, String field, String source) { try { target.set(field, JSON.readTree(source == null ? "{}" : source)); } catch (Exception ex) { throw new ServiceException("Invalid template JSON configuration"); } }
    private List<LabReportSection> validateSections(List<LabReportSection> values,String periodType) {
        if (values != null && values.size() > 200) throw new ServiceException("Too many report sections");
        List<LabReportSection> result = new ArrayList<LabReportSection>(); Set<String> codes = new HashSet<String>(); Set<Integer> sorts = new HashSet<Integer>();
        if (values != null) for (LabReportSection value : values) {
            LabReportSection copy = copy(value);
            if (copy.getSectionCode() == null || !copy.getSectionCode().matches("[A-Za-z0-9_-]{1,64}") || !codes.add(copy.getSectionCode())) throw new ServiceException("Invalid or duplicate report section code");
            if (copy.getSectionName() == null || copy.getSectionName().trim().isEmpty() || copy.getSectionName().length() > 200) throw new ServiceException("Invalid report section name");
            if (copy.getSortNo() == null || copy.getSortNo() < 1 || copy.getSortNo() > 10000 || !sorts.add(copy.getSortNo())) throw new ServiceException("Invalid or duplicate report section order");
            if (!flag(copy.getManualFlag()) || !flag(copy.getVisibleFlag()) || !flag(copy.getSensitiveFlag())) throw new ServiceException("Invalid report section flag");
            try { validator.validateSection(copy); } catch (RuntimeException ex) { throw new ServiceException(ex.getMessage()); }
            String provider="MANUAL".equals(copy.getSectionType())?ReportConfigCatalog.MANUAL_SUMMARY:copy.getDataSource();if(!ReportConfigCatalog.supportsPeriod(provider,periodType))throw new ServiceException("Report provider is not supported for the template period type");
            result.add(copy);
        }
        return result;
    }
    private String text(JsonNode node, String field) { if (node == null || !node.has(field) || !node.get(field).isTextual() || node.get(field).asText().length() > 500) throw new ServiceException("Invalid " + field); return node.get(field).asText(); }
    private String optionalText(JsonNode node, String field) { if (!node.has(field) || node.get(field).isNull()) return null; return text(node, field); }
    private boolean bool(JsonNode node, String field, boolean fallback) { if (!node.has(field)) return fallback; if (!node.get(field).isBoolean()) throw new ServiceException("Invalid " + field); return node.get(field).booleanValue(); }
    private void only(JsonNode node, String... fields) { if (node == null || !node.isObject()) throw new ServiceException("Invalid template JSON document"); java.util.Set<String> allowed = new java.util.HashSet<String>(java.util.Arrays.asList(fields)); Iterator<String> names = node.fieldNames(); while (names.hasNext()) if (!allowed.contains(names.next())) throw new ServiceException("Unknown template JSON field"); }
    private boolean flag(String value) { return "0".equals(value) || "1".equals(value); }
    private String plain(String value) { if (value == null) return ""; return value.replaceAll("[\\r\\n\\p{Cntrl}]", " ").replace("#", "").trim(); }
    private LabReportTemplate required(LabReportTemplate value) { if (value == null) throw new ServiceException("Report template does not exist"); return value; }
    private void affected(int count, String message) { if (count != 1) throw new ServiceException(message); }
    private String actor(Long id) { return String.valueOf(id); }
    private boolean same(Object a, Object b) { return a == null ? b == null : a.equals(b); }
    private <T> List<T> safe(List<T> values) { return values == null ? Collections.<T>emptyList() : values; }
    private LabReportTemplate copy(LabReportTemplate source) { LabReportTemplate value = new LabReportTemplate(); value.setId(source.getId()); value.setTemplateCode(source.getTemplateCode()); value.setTemplateName(source.getTemplateName()); value.setPeriodType(source.getPeriodType()); value.setRevisionNo(source.getRevisionNo()); value.setLatestFlag(source.getLatestFlag()); value.setDefaultFlag(source.getDefaultFlag()); value.setStatus(source.getStatus()); value.setHeaderJson(source.getHeaderJson()); value.setStyleJson(source.getStyleJson()); value.setVersion(source.getVersion()); value.setRemark(source.getRemark()); return value; }
    private LabReportSection copy(LabReportSection source) { LabReportSection value = new LabReportSection(); value.setSectionCode(source.getSectionCode()); value.setSectionName(source.getSectionName()); value.setSectionType(source.getSectionType()); value.setSortNo(source.getSortNo()); value.setDataSource(source.getDataSource()); value.setQueryConfigJson(source.getQueryConfigJson()); value.setRenderConfigJson(source.getRenderConfigJson()); value.setStyleConfigJson(source.getStyleConfigJson()); value.setManualFlag(source.getManualFlag()); value.setVisibleFlag(source.getVisibleFlag()); value.setSensitiveFlag(source.getSensitiveFlag()); value.setSensitivePermission(source.getSensitivePermission()); value.setRemark(source.getRemark()); return value; }
}
