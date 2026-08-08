package com.ailab.system.report.model;

import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** One aggregate budget for the entire neutral report model, enforced before export. */
final class ReportDataBudget {
    private static final int MAX_SECTIONS=200, MAX_ROWS=25000, MAX_NODES=100000, MAX_DEPTH=64;
    private static final long MAX_TEXT_BYTES=1024L*1024L, MAX_IMAGE_BYTES=10L*1024L*1024L;
    private ReportDataBudget(){ }

    static void validate(ReportContext context,List<ReportSectionData> sections,Map<String,Object> metadata){
        State state=new State();
        if(sections!=null){
            if(sections.size()>MAX_SECTIONS)throw invalid("section limit");
            for(ReportSectionData section:sections){
                if(section==null)throw invalid("null section");
                state.rows+=section.getRows().size();if(state.rows>MAX_ROWS)throw invalid("total row limit");
                state.text(section.getSectionCode());state.text(section.getSectionType());state.text(section.getTitle());
                state.value(section.getRows(),null,0);state.value(section.getSummary(),null,0);
            }
        }
        if(context!=null)state.value(context.getAttributes(),null,0);
        state.value(metadata,null,0);
    }

    private static IllegalArgumentException invalid(String reason){return new IllegalArgumentException("Report exceeds "+reason);}
    private static final class State{
        int rows,nodes;long textBytes,imageBytes;final IdentityHashMap<Object,Boolean> active=new IdentityHashMap<Object,Boolean>();
        void value(Object value,String key,int depth){
            if(depth>MAX_DEPTH)throw invalid("depth limit");if(++nodes>MAX_NODES)throw invalid("node limit");
            if(value==null||value instanceof Number||value instanceof Boolean||value instanceof Character||value instanceof Enum)return;
            if(value instanceof String){String text=(String)value;text(text);if(imageKey(key)){imageBytes+=base64Bytes(text);if(imageBytes>MAX_IMAGE_BYTES)throw invalid("image byte limit");}return;}
            if(value instanceof byte[]){imageBytes+=((byte[])value).length;if(imageBytes>MAX_IMAGE_BYTES)throw invalid("image byte limit");return;}
            enter(value);try{
                if(value instanceof Map){for(Map.Entry<?,?> item:((Map<?,?>)value).entrySet()){if(!(item.getKey() instanceof String))throw invalid("map key type");text((String)item.getKey());value(item.getValue(),(String)item.getKey(),depth+1);}return;}
                if(value instanceof Collection){for(Object item:(Collection<?>)value)value(item,key,depth+1);return;}
                if(value.getClass().isArray()){for(int i=0;i<Array.getLength(value);i++)value(Array.get(value,i),key,depth+1);return;}
                throw invalid("value type");
            }finally{active.remove(value);}
        }
        void enter(Object value){if(active.put(value,Boolean.TRUE)!=null)throw invalid("cycle limit");}
        void text(String value){if(value==null)return;if(value.length()>MAX_TEXT_BYTES)throw invalid("text byte limit");textBytes+=value.getBytes(StandardCharsets.UTF_8).length;if(textBytes>MAX_TEXT_BYTES)throw invalid("text byte limit");}
        boolean imageKey(String key){if(key==null)return false;String value=key.toLowerCase(java.util.Locale.ROOT);return value.contains("image")||value.contains("chart")||value.contains("png");}
        long base64Bytes(String value){int comma=value.indexOf(',');int length=value.length()-(comma>=0?comma+1:0);return Math.max(0L,(long)length*3L/4L);}
    }
}
