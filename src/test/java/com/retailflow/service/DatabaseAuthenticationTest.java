package com.retailflow.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.retailflow.repository.UserRepository;
import com.retailflow.service.implement.UserServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DatabaseAuthenticationTest {
  @Test
  void missingAdminCannotBeCreatedThroughLogin() {
    UserRepository repository = mock(UserRepository.class);
    when(repository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.empty());
    UserServiceImpl users = new UserServiceImpl(repository);
    assertThatThrownBy(() -> users.authenticate("admin", "admin1234"))
        .isInstanceOf(ResponseStatusException.class);
    verify(repository, never()).save(any());
  }
}
