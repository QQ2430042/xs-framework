package com.xsframework.adapter.user.http;

/**
 * Wire shape of ApiResponse, kept local to the adapter so it does not depend on xs-starter-web.
 */
public record RemoteResponse<T>(String code, String message, T data, String traceId) {
    public boolean isSuccess() {
        return "0".equals(code);
    }
}
