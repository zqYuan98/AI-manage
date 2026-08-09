package com.ailab.system.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Serialized period close/reopen state; row locking is the transaction mutex. */
public class LabPeriodClose extends BaseEntity {
    private static final long serialVersionUID=1L;
    private Long id; private String period; private String closeStatus; private String closeBy; @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date closeTime;
    private String closeReason; private String reopenBy; @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss") private Date reopenTime; private String reopenReason;
    private String reopenHistoryJson; private Integer periodVersion; private Integer version; private String delFlag;
    public Long getId(){return id;} public void setId(Long v){id=v;} public String getPeriod(){return period;} public void setPeriod(String v){period=v;}
    public String getCloseStatus(){return closeStatus;} public void setCloseStatus(String v){closeStatus=v;} public String getCloseBy(){return closeBy;} public void setCloseBy(String v){closeBy=v;}
    public Date getCloseTime(){return copy(closeTime);} public void setCloseTime(Date v){closeTime=copy(v);} public String getCloseReason(){return closeReason;} public void setCloseReason(String v){closeReason=v;}
    public String getReopenBy(){return reopenBy;} public void setReopenBy(String v){reopenBy=v;} public Date getReopenTime(){return copy(reopenTime);} public void setReopenTime(Date v){reopenTime=copy(v);}
    public String getReopenReason(){return reopenReason;} public void setReopenReason(String v){reopenReason=v;} public String getReopenHistoryJson(){return reopenHistoryJson;} public void setReopenHistoryJson(String v){reopenHistoryJson=v;}
    public Integer getPeriodVersion(){return periodVersion;} public void setPeriodVersion(Integer v){periodVersion=v;}
    public Integer getVersion(){return version;} public void setVersion(Integer v){version=v;} public String getDelFlag(){return delFlag;} public void setDelFlag(String v){delFlag=v;}
    private Date copy(Date value){return value==null?null:new Date(value.getTime());}
}
