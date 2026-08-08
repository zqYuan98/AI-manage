package com.ailab.system.report.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Stable output type and provider-local operator contract for one projected field. */
public final class ReportFieldSpec {
    public enum Type { STRING, NUMBER, BOOLEAN, DATE }
    private static final Set<String> EQUALITY = set("EQ", "NE", "IN");
    private static final Set<String> ORDERED = set("EQ", "NE", "IN", "GTE", "LTE", "BETWEEN");
    private static final Set<String> NUMBER_FIELDS = set("id","owner","ownerId","memberId","goalId","year","backupOwnerId","score","revisionNo","total","progressRate","expectedProgress");
    private static final Set<String> BOOLEAN_FIELDS = set("block","criticalFlag","singlePointRisk","redLineFlag");
    private static final Set<String> DATE_FIELDS = set("planDate","blockStartTime","plannedSubmitDate","actualSubmitDate");

    private final String name; private final Type type; private final Set<String> operators;
    private ReportFieldSpec(String name, Type type) { this.name=name; this.type=type; this.operators=type==Type.NUMBER||type==Type.DATE?ORDERED:EQUALITY; }
    public String getName(){return name;} public Type getType(){return type;} public Set<String> getOperators(){return operators;}

    static List<ReportFieldSpec> fromNames(Collection<String> names) {
        List<ReportFieldSpec> result=new ArrayList<ReportFieldSpec>();
        for(String name:names) result.add(new ReportFieldSpec(name,typeOf(name)));
        return Collections.unmodifiableList(result);
    }
    private static Type typeOf(String name){if(NUMBER_FIELDS.contains(name))return Type.NUMBER;if(BOOLEAN_FIELDS.contains(name))return Type.BOOLEAN;if(DATE_FIELDS.contains(name))return Type.DATE;return Type.STRING;}

    void validate(String operator,Object expected){
        if(!operators.contains(operator))throw new IllegalArgumentException("Operator "+operator+" is not supported for "+name);
        if(expected==null)throw new IllegalArgumentException("Null report filter values are not supported");
        if("IN".equals(operator)){if(!(expected instanceof Collection)||((Collection<?>)expected).isEmpty())throw new IllegalArgumentException("IN requires values");for(Object item:(Collection<?>)expected)validateScalar(item);return;}
        if("BETWEEN".equals(operator)){if(!(expected instanceof List)||((List<?>)expected).size()!=2)throw new IllegalArgumentException("BETWEEN requires two values");for(Object item:(List<?>)expected)validateScalar(item);return;}
        validateScalar(expected);
    }
    private void validateScalar(Object value){
        if(value==null)throw new IllegalArgumentException("Null report filter values are not supported");
        if(type==Type.STRING&&value instanceof String)return;
        if(type==Type.NUMBER&&value instanceof Number)return;
        if(type==Type.BOOLEAN&&value instanceof Boolean)return;
        if(type==Type.DATE&&value instanceof String&&validDateForField((String)value))return;
        throw new IllegalArgumentException("Invalid "+type+" value for "+name);
    }
    Object normalize(Object value){
        if(value==null)return null;
        if(value instanceof java.sql.Date)value=((java.sql.Date)value).toLocalDate().toString();
        else if(value instanceof java.sql.Timestamp)value=((java.sql.Timestamp)value).toInstant().toString();
        else if(value instanceof java.util.Date)value=((java.util.Date)value).toInstant().toString();
        if(type==Type.BOOLEAN){if(value instanceof Boolean)return value;if(value instanceof Number)return ((Number)value).intValue()!=0;if("1".equals(value)||"true".equalsIgnoreCase(String.valueOf(value)))return Boolean.TRUE;if("0".equals(value)||"false".equalsIgnoreCase(String.valueOf(value)))return Boolean.FALSE;}
        if(type==Type.NUMBER&&value instanceof Number)return value;
        if(type==Type.STRING&&value instanceof String)return value;
        if(type==Type.DATE&&value instanceof String&&validDateForField((String)value))return value;
        throw new IllegalArgumentException("Unexpected projected "+type+" value for "+name);
    }
    boolean matches(Object actual,String operator,Object expected){
        if(actual==null){if("NE".equals(operator))return true;return false;}
        if("IN".equals(operator)){for(Object item:(Collection<?>)expected)if(compare(actual,item)==0)return true;return false;}
        if("BETWEEN".equals(operator))return compare(actual,((List<?>)expected).get(0))>=0&&compare(actual,((List<?>)expected).get(1))<=0;
        int comparison=compare(actual,expected);if("EQ".equals(operator))return comparison==0;if("NE".equals(operator))return comparison!=0;if("GTE".equals(operator))return comparison>=0;if("LTE".equals(operator))return comparison<=0;
        throw new IllegalArgumentException("Unsupported report operator");
    }
    int compare(Object left,Object right){
        if(left==right)return 0;if(left==null)return -1;if(right==null)return 1;
        if(type==Type.NUMBER)return decimal(left).compareTo(decimal(right));
        if(type==Type.BOOLEAN)return Boolean.compare((Boolean)left,(Boolean)right);
        return ((String)left).compareTo((String)right);
    }
    private static BigDecimal decimal(Object value){if(!(value instanceof Number))throw new IllegalArgumentException("Incompatible numeric report value");return value instanceof BigDecimal?(BigDecimal)value:new BigDecimal(String.valueOf(value));}
    private boolean validDateForField(String value){try{if("blockStartTime".equals(name))Instant.parse(value);else LocalDate.parse(value);return true;}catch(RuntimeException invalid){return false;}}
    private static Set<String> set(String... values){return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(values)));}
}
