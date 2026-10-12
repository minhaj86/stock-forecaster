package com.stockforecast.identity.api;

import com.stockforecast.identity.application.AuthenticationResult;
import com.stockforecast.identity.application.AuthenticationService;
import com.stockforecast.identity.application.PasswordChangeService;
import com.stockforecast.identity.application.RegistrationService;
import com.stockforecast.identity.application.UserSessionService;
import com.stockforecast.identity.domain.InvalidRefreshTokenException;
import com.stockforecast.shared.api.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Clock;

@Tag(name = "Authentication", description = "Registration, login and session management")
@SecurityRequirements
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final UserSessionService sessionService;
    private final PasswordChangeService passwordChangeService;
    private final RefreshTokenCookies cookies;
    private final Clock clock;

    AuthController(RegistrationService registrationService, AuthenticationService authenticationService,
                   UserSessionService sessionService, PasswordChangeService passwordChangeService,
                   RefreshTokenCookies cookies, Clock clock) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.sessionService = sessionService;
        this.passwordChangeService = passwordChangeService;
        this.cookies = cookies;
        this.clock = clock;
    }

    @Operation(summary = "Create an account and sign in")
    @ApiResponse(responseCode = "201", description = "Account created; refresh token cookie set")
    @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "E-mail already registered",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthenticationResult result = registrationService.register(request.toCommand());
        return authenticated(ResponseEntity.created(URI.create("/api/v1/users/me")), result);
    }

    @Operation(summary = "Sign in with e-mail and password")
    @ApiResponse(responseCode = "200", description = "Signed in; refresh token cookie set")
    @ApiResponse(responseCode = "401", description = "Invalid credentials",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticated(ResponseEntity.ok(), authenticationService.login(request.toCommand()));
    }

    @Operation(summary = "Exchange the refresh token cookie for a new access token",
            description = "Rotates the refresh token. Replaying an already rotated token revokes the session.")
    @ApiResponse(responseCode = "200", description = "New access token; rotated refresh token cookie set")
    @ApiResponse(responseCode = "401", description = "Missing, expired, revoked or replayed refresh token",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String refreshToken = cookies.read(request).orElseThrow(InvalidRefreshTokenException::new);
        return authenticated(ResponseEntity.ok(), sessionService.refresh(refreshToken));
    }

    @Operation(summary = "Change the password of the signed-in user",
            description = "Authenticated by the refresh token cookie plus the current (or temporary) password. Ends "
                    + "every session of the user and starts a new one for the caller; also fulfils a forced "
                    + "password change.")
    @ApiResponse(responseCode = "200", description = "Password changed; new access token and refresh token cookie")
    @ApiResponse(responseCode = "400", description = "INVALID_CURRENT_PASSWORD, PASSWORD_POLICY_VIOLATION, "
            + "PASSWORD_UNCHANGED or VALIDATION_FAILED", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Missing, expired or revoked session",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping("/password")
    ResponseEntity<AuthResponse> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                                HttpServletRequest request) {
        String refreshToken = cookies.read(request).orElseThrow(InvalidRefreshTokenException::new);
        return authenticated(ResponseEntity.ok(),
                passwordChangeService.changePassword(refreshToken, body.currentPassword(), body.newPassword()));
    }

    @Operation(summary = "Sign out", description = "Revokes the session and clears the refresh token cookie")
    @ApiResponse(responseCode = "204", description = "Signed out (idempotent)")
    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.read(request).ifPresent(sessionService::end);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> authenticated(ResponseEntity.BodyBuilder builder,
                                                       AuthenticationResult result) {
        return builder
                .header(HttpHeaders.SET_COOKIE, cookies.create(result.refreshToken()).toString())
                .cacheControl(CacheControl.noStore())
                .body(AuthResponse.from(result, clock.instant()));
    }
}
