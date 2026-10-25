package com.retailflow.controller;

import com.retailflow.dto.*;
import com.retailflow.entity.AppUser;
import com.retailflow.repository.UserRepository;
import com.retailflow.service.contract.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserRepository repo;
  private final UserService service;

  public UserController(UserRepository repo, UserService service) {
    this.repo = repo;
    this.service = service;
  }

  private void admin(HttpServletRequest request) {
    AppUser current = (AppUser) request.getAttribute("currentUser");
    if (current == null || !"ADMIN".equals(current.role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator role required");
  }

  @GetMapping
  public List<UserResponse> list(HttpServletRequest request) {
    admin(request);
    return repo.findAll().stream().map(UserResponse::from).toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(
      @Valid @RequestBody UserCreateRequest input, HttpServletRequest request) {
    admin(request);
    return UserResponse.from(service.create(input));
  }

  @GetMapping("/me")
  public UserResponse me(HttpServletRequest request) {
    AppUser current = (AppUser) request.getAttribute("currentUser");
    if (current == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    return UserResponse.from(current);
  }
}
