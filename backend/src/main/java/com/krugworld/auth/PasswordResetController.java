package com.krugworld.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Both calls are public (you can't log in, that's the point) — see {@link SecurityConfig}. */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    public record ResetRequest(@NotBlank @Email @Size(max = 254) String email) {
    }

    public record ResetConfirm(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 100, message = "at least 8 characters") String password) {
    }

    private final PasswordResetService resets;

    public PasswordResetController(PasswordResetService resets) {
        this.resets = resets;
    }

    /** Always 204 for a well-formed email, whether or not an account has it. */
    @PostMapping("/request")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void request(@Valid @RequestBody ResetRequest body) {
        resets.request(body.email());
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody ResetConfirm body) {
        resets.confirm(body.token(), body.password());
    }
}
