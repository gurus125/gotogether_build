package com.gotogether.profile.entity;

import com.gotogether.common.jpa.NativeEnumJdbcType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Personal, display-facing, and compatibility-matching data — split from the
 * {@code users} table because it changes far more often and is read on
 * nearly every screen, while {@code users} is read mainly at auth-check time
 * (DB Schema Part 1).
 *
 * <p>Shares its primary key with {@code users} (1:1, {@code user_id} is both
 * PK and FK — enforced at the DB level, see the V2 migration). Deliberately
 * <b>not</b> a JPA {@code @OneToOne} to {@code com.gotogether.user.entity.User}:
 * {@code profile} and {@code user} are different modules, and the
 * architecture rule (enforced by {@code ArchitectureTest}) is that modules
 * reference each other's data by plain id, never by a mapped entity
 * relationship — the FK integrity is Postgres's job, not JPA's.
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "bio")
    private String bio;

    @Column(name = "city")
    private String city;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "languages", nullable = false)
    private List<String> languages = new ArrayList<>();

    @Column(name = "travel_style")
    private String travelStyle;

    @Column(name = "food_preference")
    private String foodPreference;

    @Column(name = "smoking_preference")
    private String smokingPreference;

    @Column(name = "drinking_preference")
    private String drinkingPreference;

    @Column(name = "preferred_budget_style")
    private String preferredBudgetStyle;

    @Column(name = "adventure_level")
    private Short adventureLevel;

    @Column(name = "emergency_contact_name")
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone")
    private String emergencyContactPhone;

    @JdbcType(NativeEnumJdbcType.class)
    @Column(name = "visibility", nullable = false, columnDefinition = "profile_visibility")
    private ProfileVisibility visibility = ProfileVisibility.PUBLIC;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected UserProfile() {
        // JPA
    }

    /**
     * Created at signup with just the required display name (Chapter 1
     * Section 18). {@code userId} must be an id that already exists in
     * {@code users} — the caller (profile.service.ProfileService, invoked
     * from the auth module's signup flow) is responsible for that ordering;
     * the DB FK constraint is the actual backstop.
     */
    public static UserProfile createFor(UUID userId, String displayName) {
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.displayName = displayName;
        return profile;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    public String getTravelStyle() {
        return travelStyle;
    }

    public void setTravelStyle(String travelStyle) {
        this.travelStyle = travelStyle;
    }

    public String getFoodPreference() {
        return foodPreference;
    }

    public void setFoodPreference(String foodPreference) {
        this.foodPreference = foodPreference;
    }

    public String getSmokingPreference() {
        return smokingPreference;
    }

    public void setSmokingPreference(String smokingPreference) {
        this.smokingPreference = smokingPreference;
    }

    public String getDrinkingPreference() {
        return drinkingPreference;
    }

    public void setDrinkingPreference(String drinkingPreference) {
        this.drinkingPreference = drinkingPreference;
    }

    public String getPreferredBudgetStyle() {
        return preferredBudgetStyle;
    }

    public void setPreferredBudgetStyle(String preferredBudgetStyle) {
        this.preferredBudgetStyle = preferredBudgetStyle;
    }

    public Short getAdventureLevel() {
        return adventureLevel;
    }

    public void setAdventureLevel(Short adventureLevel) {
        this.adventureLevel = adventureLevel;
    }

    public String getEmergencyContactName() {
        return emergencyContactName;
    }

    public void setEmergencyContactName(String emergencyContactName) {
        this.emergencyContactName = emergencyContactName;
    }

    public String getEmergencyContactPhone() {
        return emergencyContactPhone;
    }

    public void setEmergencyContactPhone(String emergencyContactPhone) {
        this.emergencyContactPhone = emergencyContactPhone;
    }

    public ProfileVisibility getVisibility() {
        return visibility;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
