package com.onggijonggi.common.user;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Class Name : UserIdentityService.java
 * Description : Keycloak JWT subject를 app_user 행으로 지연(JIT) 프로비저닝한다. 회원가입 시점이 아니라
 *               첫 채팅 요청 시점에 조회하고 없으면 그 자리에서 생성한다 — Keycloak을 별도로 연동할 필요
 *               없이 매 요청마다 재확인하므로 실패해도 다음 요청에서 자연히 복구된다. JPA(블로킹)를
 *               WebFlux 요청 스레드에서 직접 부르지 않도록 boundedElastic으로 오프로딩한다.
 */
@Service
public class UserIdentityService {

	private final AppUserRepository appUserRepository;

	public UserIdentityService(AppUserRepository appUserRepository) {
		this.appUserRepository = appUserRepository;
	}

	/**
	* resolveOrProvision: keycloakSubj로 app_user를 조회하고, 없으면 새로 생성해 저장한다.
	* @param keycloakSubj JWT sub 클레임
	* @return app_user.id
	*/
	public Mono<UUID> resolveOrProvision(String keycloakSubj) {
		return Mono.fromCallable(() -> resolveOrProvisionBlocking(keycloakSubj))
				.subscribeOn(Schedulers.boundedElastic());
	}

	private UUID resolveOrProvisionBlocking(String keycloakSubj) {
		return appUserRepository.findByKeycloakSubj(keycloakSubj)
				.map(AppUser::getId)
				.orElseGet(() -> createOrFetchExisting(keycloakSubj));
	}

	/**
	* createOrFetchExisting: 새로 생성을 시도하되, 같은 사용자의 동시 요청이 먼저 만들었다면(keycloak_subj
	* unique 제약 위반) 예외를 잡고 그 행을 재조회해 쓴다 — 같은 사람이 브라우저 탭 두 개로 거의 동시에
	* 첫 요청을 보내는 등 실제로 발생할 수 있는 경합이다.
	* @param keycloakSubj JWT sub 클레임
	* @return 새로 만들었거나, 경합에서 진 경우 이긴 쪽이 만든 행의 id
	*/
	private UUID createOrFetchExisting(String keycloakSubj) {
		try {
			return appUserRepository.save(new AppUser(keycloakSubj)).getId();
		} catch (DataIntegrityViolationException raced) {
			return appUserRepository.findByKeycloakSubj(keycloakSubj)
					.map(AppUser::getId)
					.orElseThrow(() -> raced);
		}
	}

}
