package com.ailab.system.dto;

/** A stable, user-facing description for one internal business code. */
public final class BusinessStatusDescriptor {
    private final String code;
    private final String label;
    private final String description;
    private final String nextAction;
    private final String color;
    private final String riskLevel;

    public BusinessStatusDescriptor(String code, String label, String description,
                                    String nextAction, String color, String riskLevel) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.nextAction = nextAction;
        this.color = color;
        this.riskLevel = riskLevel;
    }

    public String getCode() { return code; }
    public String getLabel() { return label; }
    public String getDescription() { return description; }
    public String getNextAction() { return nextAction; }
    public String getColor() { return color; }
    public String getRiskLevel() { return riskLevel; }
}
