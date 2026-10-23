package com.fintrack.category;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import com.fintrack.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "categories_user_id_fkey")
    )
    private User user;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "category_type", nullable = false, length = 20)
    private String categoryType = "BOTH";

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Category() {
        // Required by JPA.
    }

    public Category(User user, String name, String categoryType, boolean system) {
        this.user = user;
        this.name = name;
        this.categoryType = categoryType;
        this.system = system;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getCategoryType() {
        return categoryType;
    }

    public boolean isSystem() {
        return system;
    }
    
    public void setName(String name) {
        this.name = name;
    }

    public void setCategoryType(String categoryType) {
        this.categoryType = categoryType;
    }
    
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
