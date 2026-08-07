package com.ailab.system.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

/** Business profile linked to one system user; identity fields are query-only joins. */
public class LabMember extends BaseEntity {
    private static final long serialVersionUID = 1L;
    private Long id;
    private Long userId;
    private String memberNo;
    private String position;
    private String bizLine;
    private String roleType;
    private Long leaderId;
    private String primaryResponsibilities;
    private String backupResponsibilities;
    private Date joinDate;
    private String memberStatus;
    private Integer version;
    private String delFlag;
    private String userName;
    private String nickName;
    private String leaderName;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getMemberNo() { return memberNo; }
    public void setMemberNo(String memberNo) { this.memberNo = memberNo; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public String getBizLine() { return bizLine; }
    public void setBizLine(String bizLine) { this.bizLine = bizLine; }
    public String getRoleType() { return roleType; }
    public void setRoleType(String roleType) { this.roleType = roleType; }
    public Long getLeaderId() { return leaderId; }
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    public String getPrimaryResponsibilities() { return primaryResponsibilities; }
    public void setPrimaryResponsibilities(String value) { this.primaryResponsibilities = value; }
    public String getBackupResponsibilities() { return backupResponsibilities; }
    public void setBackupResponsibilities(String value) { this.backupResponsibilities = value; }
    public Date getJoinDate() { return joinDate; }
    public void setJoinDate(Date joinDate) { this.joinDate = joinDate; }
    public String getMemberStatus() { return memberStatus; }
    public void setMemberStatus(String memberStatus) { this.memberStatus = memberStatus; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getNickName() { return nickName; }
    public void setNickName(String nickName) { this.nickName = nickName; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
}
