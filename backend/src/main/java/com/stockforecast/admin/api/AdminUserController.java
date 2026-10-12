package com.stockforecast.admin.api;

import com.stockforecast.admin.api.AdminResponses.AdminUserDetailsResponse;
import com.stockforecast.admin.api.AdminResponses.AdminUserResponse;
import com.stockforecast.admin.application.AdminUserService;
import com.stockforecast.admin.application.AdminUserService.CreateUser;
import com.stockforecast.admin.application.AdminUserService.UpdateUser;
import com.stockforecast.admin.application.AdminUserService.UserPage;
import com.stockforecast.admin.application.AdminUserService.UserQuery;
import com.stockforecast.identity.AccountStatistics;
import com.stockforecast.identity.AccountStatus;
import com.stockforecast.identity.AuthenticatedUser;
import com.stockforecast.identity.CurrentUser;
import com.stockforecast.identity.Role;
import com.stockforecast.shared.api.ApiError;
import com.stockforecast.shared.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

@Tag(name = "Administration")
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final AdminUserService userService;

    AdminUserController(AdminUserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Search users by e-mail or name, filter by role, status and creation date (newest first)")
    @GetMapping
    PageResponse<AdminUserResponse> search(@RequestParam(required = false) @Size(max = 100) String query,
                                           @RequestParam(required = false) Role role,
                                           @RequestParam(required = false) AccountStatus status,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        UserPage result = userService.search(new UserQuery(query, role, status, createdFrom, createdTo, page, size));
        return PageResponse.of(result.content(), result.page(), result.size(), result.totalElements(),
                AdminUserResponse::from);
    }

    @Operation(summary = "Aggregate user statistics")
    @GetMapping("/statistics")
    UserStatisticsResponse statistics() {
        return UserStatisticsResponse.from(userService.statistics());
    }

    @Operation(summary = "User details")
    @ApiResponse(responseCode = "404", description = "USER_NOT_FOUND",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @GetMapping("/{id}")
    AdminUserDetailsResponse get(@PathVariable UUID id) {
        return AdminUserDetailsResponse.from(userService.get(id));
    }

    @Operation(summary = "Create a user with a temporary password",
            description = "With requirePasswordChange the user must choose a new password at the first sign-in.")
    @ApiResponse(responseCode = "201", description = "Created")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PostMapping
    ResponseEntity<AdminUserDetailsResponse> create(@CurrentUser AuthenticatedUser actor,
                                                    @Valid @RequestBody CreateUserRequest request) {
        AdminUserDetailsResponse created = AdminUserDetailsResponse.from(userService.create(actor, request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/admin/users/" + created.id())).body(created);
    }

    @Operation(summary = "Edit display name, e-mail, role or status",
            description = "Administrators cannot demote, disable or lock themselves; the last active administrator "
                    + "cannot lose that role (LAST_ADMINISTRATOR, SELF_ADMINISTRATION_NOT_ALLOWED).")
    @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_REGISTERED, LAST_ADMINISTRATOR or "
            + "SELF_ADMINISTRATION_NOT_ALLOWED", content = @Content(schema = @Schema(implementation = ApiError.class)))
    @PatchMapping("/{id}")
    AdminUserDetailsResponse update(@CurrentUser AuthenticatedUser actor, @PathVariable UUID id,
                                    @Valid @RequestBody UpdateUserRequest request) {
        return AdminUserDetailsResponse.from(userService.update(actor, id, request.toCommand()));
    }

    @Operation(summary = "Disable a user", description = "The user can no longer sign in and is signed out "
            + "everywhere; all data is kept and the account can be re-enabled.")
    @PostMapping("/{id}/disable")
    AdminUserDetailsResponse disable(@CurrentUser AuthenticatedUser actor, @PathVariable UUID id) {
        return AdminUserDetailsResponse.from(userService.changeStatus(actor, id, AccountStatus.DISABLED));
    }

    @Operation(summary = "Re-enable a disabled or locked user")
    @PostMapping("/{id}/enable")
    AdminUserDetailsResponse enable(@CurrentUser AuthenticatedUser actor, @PathVariable UUID id) {
        return AdminUserDetailsResponse.from(userService.changeStatus(actor, id, AccountStatus.ACTIVE));
    }

    @Operation(summary = "Set a temporary password", description = "Signs the user out everywhere.")
    @PostMapping("/{id}/password-reset")
    AdminUserDetailsResponse resetPassword(@CurrentUser AuthenticatedUser actor, @PathVariable UUID id,
                                           @Valid @RequestBody ResetPasswordRequest request) {
        return AdminUserDetailsResponse.from(userService.resetPassword(actor, id, request.temporaryPassword(),
                request.requirePasswordChange() == null || request.requirePasswordChange()));
    }

    @Operation(summary = "Delete a user and all of their data",
            description = "Removes the account, profile and settings, watchlists and sessions. Cannot be undone; "
                    + "prefer disabling.")
    @ApiResponse(responseCode = "204", description = "Deleted")
    @ApiResponse(responseCode = "409", description = "LAST_ADMINISTRATOR or SELF_ADMINISTRATION_NOT_ALLOWED",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser actor, @PathVariable UUID id) {
        userService.delete(actor, id);
        return ResponseEntity.noContent().build();
    }

    record CreateUserRequest(
            @NotBlank @Size(max = 100) String displayName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull Role role,
            @NotNull AccountStatus status,
            @NotBlank @Size(min = 12, max = 72) String temporaryPassword,
            Boolean requirePasswordChange) {

        CreateUserRequest {
            email = email == null ? null : email.strip();
            displayName = displayName == null ? null : displayName.strip();
        }

        CreateUser toCommand() {
            return new CreateUser(displayName, email, role, status, temporaryPassword,
                    requirePasswordChange == null || requirePasswordChange);
        }

        @Override
        public String toString() {
            return "CreateUserRequest[email=" + email + ", role=" + role + ", status=" + status + ", password=***]";
        }
    }

    record UpdateUserRequest(@Size(min = 1, max = 100) String displayName, @Email @Size(max = 320) String email,
                             Role role, AccountStatus status) {

        UpdateUserRequest {
            email = email == null ? null : email.strip();
        }

        UpdateUser toCommand() {
            return new UpdateUser(displayName, email, role, status);
        }
    }

    record ResetPasswordRequest(@NotBlank @Size(min = 12, max = 72) String temporaryPassword,
                                Boolean requirePasswordChange) {

        @Override
        public String toString() {
            return "ResetPasswordRequest[password=***]";
        }
    }

    record UserStatisticsResponse(long totalUsers, long administrators, long registeredLast30Days, long activeUsers,
                                  long disabledUsers, long lockedUsers, long registeredLast7Days) {

        static UserStatisticsResponse from(AccountStatistics statistics) {
            return new UserStatisticsResponse(statistics.total(), statistics.administrators(),
                    statistics.registeredLast30Days(), statistics.active(), statistics.disabled(),
                    statistics.locked(), statistics.registeredLast7Days());
        }
    }
}
