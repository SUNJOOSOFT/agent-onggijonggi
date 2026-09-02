-- 04·DATA — app_user 계정 상태(Tombstone).
--
-- 이름은 scripts/glossary/data-glossary.md 축약 사전을 따른다.
--
-- 지금까지 app_user에는 계정 비활성을 표현할 방법이 없었다. 계정이 없어져도 과거 메시지의
-- 작성자를 잃지 않으려면 행을 지우지 않고 상태로 표시해야 하는데, 그 자리가 없었다.
--
-- 이 컬럼은 인가 판정에 쓰지 않는다. 계정이 살아 있는지는 Keycloak이 정본이고, 여기 두는
-- 상태는 참조 무결성과 Tombstone 표시용이다. keycloak_subj는 Tombstone 뒤에도 unique 값을
-- 유지한다 — 같은 외부 주체가 다시 로그인했을 때 새 사용자로 조용히 만들어지지 않고 명시적
-- 재활성화 절차를 거치게 하려는 것이다. 재활성화 절차와 계정 비활성화 도메인 연산(소유
-- 대화 정리·참여 회수 순서)은 여기서 만들지 않는다 — 스레드·참여자 테이블이 이미 있으니
-- 그 도메인 연산은 이후 별도로 다룬다.
--
-- 기존 데이터 전제: app_user에 이미 행이 있을 수 있는 운영 스키마다. status는 DEFAULT
-- 'ACTIVE'로 두어 기존 행이 전부 활성으로 채워지게 한다. 값 채우기와 제약 추가를 한 번에
-- 해도 안전한 이유는 상수 DEFAULT라서다 — PostgreSQL이 테이블 전체를 다시 쓰지 않는다.
alter table app_user
    add column status      varchar(16) not null default 'ACTIVE'
        check (status in ('ACTIVE', 'INACTIVE')),
    add column inactive_at timestamptz;

-- INACTIVE일 때만 비활성 시각이 있다. thr의 상태·시각 CHECK와 같은 방향 — 되돌릴 여지를
-- 남기지 않고 "그 상태면 그 시각이 있다"만 강제한다.
alter table app_user
    add constraint app_user_inactive_at_matches_status
        check ((status = 'INACTIVE') = (inactive_at is not null));
