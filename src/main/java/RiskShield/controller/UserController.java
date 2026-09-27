package RiskShield.controller;

import RiskShield.dto.CreateUserRequest;
import RiskShield.dto.UserResponse;
import RiskShield.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse user = userService.registerUser(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + user.id()))
                .body(user);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUser(id));
    }
}