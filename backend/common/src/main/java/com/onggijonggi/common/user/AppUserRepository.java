package com.onggijonggi.common.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class Name : AppUserRepository.java
 * Description : app_user JPA 레포지토리. keycloak_subj 기준 조회만 추가한다(JIT 프로비저닝, 03·CORE).
 */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

	Optional<AppUser> findByKeycloakSubj(String keycloakSubj);

}
