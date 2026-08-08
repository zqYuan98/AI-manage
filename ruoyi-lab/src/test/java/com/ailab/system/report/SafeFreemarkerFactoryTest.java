package com.ailab.system.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailab.system.report.config.SafeFreemarkerFactory;
import com.ailab.system.report.config.SafeFreemarkerFactory.SafeTemplateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeFreemarkerFactoryTest {

    private final SafeFreemarkerFactory factory = new SafeFreemarkerFactory();

    @Test
    void rendersOnlySimplePredefinedValuesAndSafeBuiltins() {
        Map<String, Object> model = new LinkedHashMap<String, Object>();
        model.put("period", "2026-08");
        model.put("tasks", Arrays.asList("Research", "Delivery"));
        assertEquals("2026-08: 2", factory.render("${period}: ${tasks?size}", model));
    }

    @Test
    void rejectsJavaAccessDangerousBuiltinsAndExternalTemplateLoading() {
        Map<String, Object> model = new LinkedHashMap<String, Object>();
        model.put("value", "safe");
        model.put("map", Collections.<String, Object>singletonMap("key", "value"));
        for (String unsafe : Arrays.asList(
                "${value?api}", "${'java.lang.Runtime'?new()}", "${value?eval}", "<#assign x='x'?interpret>${x()}",
                "${value.class}", "${value?has_api}", "${value?eval_json}", "${value?keys}", "<#include 'file:///secret'>", "<#import 'https://example.test/a.ftl' as a>")) {
            SafeTemplateException error = assertThrows(SafeTemplateException.class, () -> factory.render(unsafe, model));
            assertTrue(error.getMessage().matches("Safe template (validation|rendering) failed"));
        }
        assertThrows(SafeTemplateException.class, () -> factory.render("${map?keys?join(',')}", model));
        SafeTemplateException recursive = assertThrows(SafeTemplateException.class,
                () -> factory.render("<#macro loop><@loop/></#macro><@loop/>", model));
        assertEquals("Safe template validation failed", recursive.getMessage());
        assertEquals("Safe template validation failed", assertThrows(SafeTemplateException.class,
                () -> factory.render("<#list 1..1 as n>x</#list>", model)).getMessage());
        assertEquals("Safe template validation failed", assertThrows(SafeTemplateException.class,
                () -> factory.render("${map ? keys ? join(',')}", model)).getMessage());
        assertEquals("Safe template validation failed", assertThrows(SafeTemplateException.class,
                () -> factory.render("[#list 1..1 as n]x[/#list]", model)).getMessage());
    }

    @Test
    void lexesFtlBoundariesInsteadOfTreatingQuotedTextOrCommentsAsExpressions() {
        Map<String, Object> model = new LinkedHashMap<String, Object>();
        model.put("map", Collections.<String, Object>singletonMap("key", "value"));
        model.put("value", "ok");

        // The first > is part of the string literal.  A truncated directive scanner would miss ?keys.
        assertEquals("Safe template validation failed", assertThrows(SafeTemplateException.class,
                () -> factory.render("<#assign x=\">\" + map?keys?size>${x}", model)).getMessage());
        assertEquals("?literal", factory.render("${'?literal'}", model));
        assertEquals("ok", factory.render("<#-- ${map?keys} -->${value}", model));
        assertEquals("Safe template validation failed", assertThrows(SafeTemplateException.class,
                () -> factory.render("<#assign x=\"${map?keys}\">${x}", model)).getMessage());
    }

    @Test
    void rejectsLegacyNumericInterpolationWithoutMisclassifyingStringLiterals() {
        Map<String, Object> model = new LinkedHashMap<String, Object>();
        model.put("map", Collections.<String, Object>singletonMap("key", "value"));
        for (String unsafe : Arrays.asList("#{\"1+1\"?eval}", "#{map?keys?size}", "#{\"1\"?eval_json}")) {
            assertEquals("Safe template validation failed", assertThrows(SafeTemplateException.class,
                    () -> factory.render(unsafe, model)).getMessage());
        }
        assertEquals("#{literal}", factory.render("${'#{literal}'}", model));
    }

    @Test
    void rejectsObjectsAndBoundsInputOutputAndNestedData() {
        Map<String, Object> unsafe = new LinkedHashMap<String, Object>();
        unsafe.put("object", new Object());
        assertThrows(SafeTemplateException.class, () -> factory.render("${object}", unsafe));
        assertThrows(SafeTemplateException.class, () -> factory.render(repeat("x", SafeFreemarkerFactory.MAX_TEMPLATE_CHARS + 1), new LinkedHashMap<String, Object>()));
        assertThrows(SafeTemplateException.class, () -> factory.render("<#list 1..300000 as n>x</#list>", new LinkedHashMap<String, Object>()));

        Map<String, Object> nested = new LinkedHashMap<String, Object>();
        Object value = "leaf";
        for (int i = 0; i < SafeFreemarkerFactory.MAX_MODEL_DEPTH + 1; i++) value = Arrays.asList(value);
        nested.put("nested", value);
        assertThrows(SafeTemplateException.class, () -> factory.render("ok", nested));
    }

    private String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(count * value.length());
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
