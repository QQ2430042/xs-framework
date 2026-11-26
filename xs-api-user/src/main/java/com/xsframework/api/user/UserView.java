package com.xsframework.api.user;

public record UserView(String userId, String tenantId, String userCode, String username, String displayName) {
}
