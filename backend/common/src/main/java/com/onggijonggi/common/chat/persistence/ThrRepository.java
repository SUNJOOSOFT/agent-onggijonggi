package com.onggijonggi.common.chat.persistence;

import com.onggijonggi.common.chat.domain.Thr;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Class Name : ThrRepository.java
 * Description : thr JPA 레포지토리.
 */
public interface ThrRepository extends JpaRepository<Thr, UUID> {
}
