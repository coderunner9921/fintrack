
package com.fintrack.user.dto;

public class LoginResponse {

    private String token;
    private String tokenType;
    private long expiresIn;

    public LoginResponse(String token, long expiresIn) {
        this.token = token;
        this.tokenType = "Bearer";
        this.expiresIn = expiresIn;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresIn() {
        return expiresIn;
    }
}
