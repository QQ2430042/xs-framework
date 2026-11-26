package com.xsframework.module.user;

import com.xsframework.starter.data.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "st_user")
@SQLRestriction("deleted_flag = false")
@AttributeOverride(name = "id", column = @Column(name = "user_id", length = 36, nullable = false))
public class UserEntity extends BaseEntity {

    @Column(name = "user_code", length = 16, nullable = false)
    private String userCode;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    @Column(name = "display_name", length = 128)
    private String displayName;

    protected UserEntity() {
    }

    private UserEntity(String userCode, String username, String displayName) {
        this.userCode = userCode;
        this.username = username;
        this.displayName = displayName;
    }

    public static UserEntity create(String userCode, String username, String displayName) {
        return new UserEntity(userCode, username, displayName);
    }

    public String getUserCode() {
        return userCode;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }
}
