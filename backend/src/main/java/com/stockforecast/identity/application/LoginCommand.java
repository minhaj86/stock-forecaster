package com.stockforecast.identity.application;

public record LoginCommand(String email, String password, boolean rememberMe) {

    @Override
    public String toString() {
        return "LoginCommand[email=" + email + ", password=***, rememberMe=" + rememberMe + "]";
    }
}
