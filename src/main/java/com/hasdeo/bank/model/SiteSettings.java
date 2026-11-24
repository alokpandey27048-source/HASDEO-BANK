package com.hasdeo.bank.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="site_settings")
public class SiteSettings {
    @Id private Long id;
    @Column(name="maintenance_enabled",nullable=false) private boolean maintenanceEnabled;
    @Column(name="maintenance_message",length=300) private String maintenanceMessage;
    @Column(name="site_title",nullable=false,length=100) private String siteTitle;
    @Column(name="announcement",length=240) private String announcement;
    @Column(name="announcement_updated_at") private Instant announcementUpdatedAt;
    @Column(name="ai_creator_name",length=80) private String aiCreatorName;
    @Column(name="ai_custom_knowledge",length=5000) private String aiCustomKnowledge;
    @Column(name="customer_signup_enabled",nullable=false,columnDefinition="boolean default true") private boolean customerSignupEnabled=true;
    @Column(name="customer_login_enabled",nullable=false,columnDefinition="boolean default true") private boolean customerLoginEnabled=true;
    protected SiteSettings() {}
    public SiteSettings(Long id){this.id=id;this.maintenanceEnabled=false;this.maintenanceMessage="HASDEO BANK is temporarily unavailable for scheduled maintenance. Please check back soon.";this.siteTitle="HASDEO BANK";this.announcement="";this.aiCreatorName="Alok";this.aiCustomKnowledge="";}
    public Long getId(){return id;} public boolean isMaintenanceEnabled(){return maintenanceEnabled;} public String getMaintenanceMessage(){return maintenanceMessage;}
    public String getSiteTitle(){return siteTitle;} public String getAnnouncement(){return announcement;} public Instant getAnnouncementUpdatedAt(){return announcementUpdatedAt;}
    public String getAiCreatorName(){return aiCreatorName;} public String getAiCustomKnowledge(){return aiCustomKnowledge;}
    public boolean isCustomerSignupEnabled(){return customerSignupEnabled;} public boolean isCustomerLoginEnabled(){return customerLoginEnabled;}
    public void setMaintenanceEnabled(boolean v){maintenanceEnabled=v;} public void setMaintenanceMessage(String v){maintenanceMessage=v;}
    public void setSiteTitle(String v){siteTitle=v;} public void setAnnouncement(String v){announcement=v;announcementUpdatedAt=Instant.now();}
    public void setAiCreatorName(String v){aiCreatorName=v;} public void setAiCustomKnowledge(String v){aiCustomKnowledge=v;}
    public void setCustomerSignupEnabled(boolean v){customerSignupEnabled=v;} public void setCustomerLoginEnabled(boolean v){customerLoginEnabled=v;}
}
