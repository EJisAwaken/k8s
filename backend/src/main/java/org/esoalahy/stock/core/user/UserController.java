package org.esoalahy.stock.core.user;

import org.esoalahy.stock.core.security.CurrentUser;
import org.esoalahy.stock.core.user.dto.RoleDto;
import org.esoalahy.stock.core.user.dto.UserCreatedDto;
import org.esoalahy.stock.core.user.dto.UserDto;
import org.esoalahy.stock.core.user.dto.UserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserDto> list(@RequestParam(required = false) Role role,
                              @RequestParam(required = false) Boolean actif,
                              @RequestParam(required = false) String q) {
        return userService.search(role, actif, q);
    }

    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public List<RoleDto> roles() {
        return Arrays.stream(Role.values()).map(RoleDto::from).toList();
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserCreatedDto create(@Valid @RequestBody UserRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserRequest request,
                          @CurrentUser Long currentUserId) {
        return userService.update(id, request, currentUserId);
    }

    @PostMapping("/{id}/reset-password")
    public UserCreatedDto resetPassword(@PathVariable Long id) {
        return userService.resetPassword(id);
    }

    @PostMapping("/{id}/reset-2fa")
    public UserDto resetTwoFactor(@PathVariable Long id) {
        return userService.resetTwoFactor(id);
    }
}
