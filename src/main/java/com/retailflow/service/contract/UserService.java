package com.retailflow.service.contract;

import com.retailflow.dto.UserCreateRequest;
import com.retailflow.entity.AppUser;

public interface UserService {
  AppUser create(UserCreateRequest request);

  AppUser authenticate(String username, String password);
}
