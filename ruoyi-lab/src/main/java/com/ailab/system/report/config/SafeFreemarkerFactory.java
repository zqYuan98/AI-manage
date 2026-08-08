package com.ailab.system.report.config;

import freemarker.cache.StringTemplateLoader;
import freemarker.core.TemplateClassResolver;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.StringReader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * FreeMarker boundary for user-authored snippets. Security is primarily supplied by
 * FreeMarker's no-class resolver, API disablement and no-exposure object wrapper;
 * lexical rejection is a deliberate second layer for builtins that have no allowlist API.
 */
public final class SafeFreemarkerFactory {
    public static final int MAX_TEMPLATE_CHARS = 16 * 1024;
    public static final int MAX_OUTPUT_CHARS = 256 * 1024;
    public static final int MAX_MODEL_DEPTH = 12;
    public static final long MAX_RENDER_MILLIS = 250L;
    private static final Set<String> SAFE_BUILTINS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "size", "has_content", "upper_case", "lower_case", "cap_first", "uncap_first", "trim", "string",
            "join", "html", "xhtml", "xml", "json_string", "url", "c", "then", "if_exists", "default")));
    private final Configuration configuration;

    public SafeFreemarkerFactory() {
        configuration = new Configuration(Configuration.VERSION_2_3_31);
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTagSyntax(Configuration.ANGLE_BRACKET_TAG_SYNTAX);
        configuration.setLocalizedLookup(false);
        configuration.setAPIBuiltinEnabled(false);
        configuration.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);
        configuration.setFallbackOnNullLoopVariable(false);
        configuration.setTemplateLoader(new StringTemplateLoader());
        DefaultObjectWrapperBuilder wrapper = new DefaultObjectWrapperBuilder(Configuration.VERSION_2_3_31);
        wrapper.setExposureLevel(BeansWrapper.EXPOSE_NOTHING);
        wrapper.setExposeFields(false);
        configuration.setObjectWrapper(wrapper.build());
    }

    public String render(String source, Map<String, Object> model) {
        validateSource(source);
        final Map<String, Object> safeModel = sanitizeModel(model);
        ExecutorService executor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
        try {
            Future<String> result = executor.submit(new Callable<String>() {
                @Override public String call() throws Exception { return process(source, safeModel); }
            });
            return result.get(MAX_RENDER_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new SafeTemplateException("Safe template rendering failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SafeTemplateException("Safe template rendering failed");
        } catch (ExecutionException ex) {
            throw new SafeTemplateException("Safe template rendering failed");
        } finally {
            executor.shutdownNow();
        }
    }

    private String process(String source, Map<String, Object> model) throws IOException, TemplateException {
        Template template = new Template("inline-safe-template", new StringReader(source), configuration);
        BoundedWriter writer = new BoundedWriter(MAX_OUTPUT_CHARS);
        template.process(model, writer);
        return writer.toString();
    }

    private void validateSource(String source) {
        if (source == null || source.length() > MAX_TEMPLATE_CHARS) throw validationFailure();
        String normalized = source.toLowerCase(Locale.ROOT);
        String[] forbidden = { "?api", "?has_api", "?new", "?eval", "?interpret", "<#include", "<#import", "<#list", "<#macro", "<#function", "<@", "[#include", "[#import", "[#list", "[#macro", "[#function", "[@", ".class", "getclass", "classloader", "?static" };
        for (String token : forbidden) if (normalized.contains(token)) throw validationFailure();
        validateBuiltinRegions(normalized, "${", "}"); validateBuiltinRegions(normalized, "<#", ">");
    }

    private void validateBuiltinRegions(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        while (start >= 0) { int end = source.indexOf(endToken, start + startToken.length()); if (end < 0) throw validationFailure(); validateBuiltins(source.substring(start + startToken.length(), end)); start = source.indexOf(startToken, end + endToken.length()); }
    }
    private void validateBuiltins(String expression) {
        for (int index = 0; index < expression.length(); index++) if (expression.charAt(index) == '?') { int nameStart = index + 1; while (nameStart < expression.length() && Character.isWhitespace(expression.charAt(nameStart))) nameStart++; int end = nameStart; while (end < expression.length() && (Character.isLetter(expression.charAt(end)) || expression.charAt(end) == '_')) end++; if (end <= nameStart || !SAFE_BUILTINS.contains(expression.substring(nameStart, end))) throw validationFailure(); }
    }

    private Map<String, Object> sanitizeModel(Map<String, Object> model) {
        Map<String, Object> source = model == null ? Collections.<String, Object>emptyMap() : model;
        if (source.size() > 1000) throw validationFailure();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isEmpty() || entry.getKey().length() > 100) throw validationFailure();
            result.put(entry.getKey(), copySimple(entry.getValue(), 0));
        }
        return Collections.unmodifiableMap(result);
    }

    private Object copySimple(Object value, int depth) {
        if (depth > MAX_MODEL_DEPTH) throw validationFailure();
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            if (value instanceof String && ((String) value).length() > MAX_TEMPLATE_CHARS) throw validationFailure();
            return value;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value; if (map.size() > 1000) throw validationFailure();
            Map<String, Object> copy = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> entry : map.entrySet()) { if (!(entry.getKey() instanceof String)) throw validationFailure(); copy.put((String) entry.getKey(), copySimple(entry.getValue(), depth + 1)); }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection) {
            Collection<?> values = (Collection<?>) value; if (values.size() > 1000) throw validationFailure();
            List<Object> copy = new ArrayList<Object>(); for (Object item : values) copy.add(copySimple(item, depth + 1)); return Collections.unmodifiableList(copy);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value); if (length > 1000) throw validationFailure();
            List<Object> copy = new ArrayList<Object>(); for (int i = 0; i < length; i++) copy.add(copySimple(Array.get(value, i), depth + 1)); return Collections.unmodifiableList(copy);
        }
        throw validationFailure();
    }

    private SafeTemplateException validationFailure() { return new SafeTemplateException("Safe template validation failed"); }

    public static final class SafeTemplateException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        public SafeTemplateException(String message) { super(message); }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override public Thread newThread(Runnable runnable) { Thread thread = new Thread(runnable, "safe-freemarker"); thread.setDaemon(true); return thread; }
    }

    private static final class BoundedWriter extends Writer {
        private final StringBuilder result = new StringBuilder(); private final int maximum;
        BoundedWriter(int maximum) { this.maximum = maximum; }
        @Override public void write(char[] buffer, int offset, int length) throws IOException { if (length > maximum - result.length()) throw new IOException("Template output exceeds limit"); result.append(buffer, offset, length); }
        @Override public void flush() { }
        @Override public void close() { }
        @Override public String toString() { return result.toString(); }
    }
}
