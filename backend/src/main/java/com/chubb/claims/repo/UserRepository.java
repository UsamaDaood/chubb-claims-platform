package com.chubb.claims.repo;

import com.chubb.claims.domain.AppUser;
import com.chubb.claims.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRepository extends JpaRepository<AppUser, String> {
    List<AppUser> findByRole(Role role);
}
