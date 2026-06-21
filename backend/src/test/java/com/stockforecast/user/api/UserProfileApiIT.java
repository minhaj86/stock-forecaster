package com.stockforecast.user.api;

import com.stockforecast.support.AuthTestClient;
import com.stockforecast.support.AuthTestClient.Session;
import com.stockforecast.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class UserProfileApiIT {

    private static final String ME = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthTestClient auth;

    @Test
    void returnsTheProfileCreatedAtRegistration() throws Exception {
        String email = AuthTestClient.uniqueEmail();
        Session session = auth.register(email.toUpperCase(), "Grace Hopper");

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(session.userId().toString()))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.displayName").value("Grace Hopper"))
                .andExpect(jsonPath("$.timeZone").value("UTC"))
                .andExpect(jsonPath("$.memberSince").isNotEmpty());
    }

    @Test
    void updatesOnlyTheProvidedFields() throws Exception {
        Session session = auth.register(AuthTestClient.uniqueEmail(), "Grace");

        mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timeZone": "Europe/Berlin"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.displayName").value("Grace"));

        mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "  Amazing Grace  "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Amazing Grace"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"));

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, session.bearer()))
                .andExpect(jsonPath("$.displayName").value("Amazing Grace"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"));
    }

    @Test
    void rejectsInvalidUpdates() throws Exception {
        Session session = auth.registerNewUser();

        mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"timeZone": "Mars/Olympus_Mons"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIME_ZONE"));

        mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, session.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "   "}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("displayName"));
    }

    @Test
    void usersOnlyEverSeeAndChangeTheirOwnProfile() throws Exception {
        Session alice = auth.register(AuthTestClient.uniqueEmail(), "Alice");
        Session bob = auth.register(AuthTestClient.uniqueEmail(), "Bob");

        mockMvc.perform(patch(ME).header(HttpHeaders.AUTHORIZATION, alice.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName": "Alice Updated"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bob.bearer()))
                .andExpect(jsonPath("$.id").value(bob.userId().toString()))
                .andExpect(jsonPath("$.displayName").value("Bob"));
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, alice.bearer()))
                .andExpect(jsonPath("$.id").value(alice.userId().toString()))
                .andExpect(jsonPath("$.displayName").value("Alice Updated"));
    }
}
