package com.stockforecast.identity.application;

public record RegisterUserCommand(String email, String password, String displayName) {

    @Override
    public String toString() {
        return "RegisterUserCommand[email=" + email + ", password=***, displayName=" + displayName + "]";
    }
}
