-- 04·DATA — 협업 채팅 Thread(thr).
--
-- 이름은 scripts/glossary/data-glossary.md 축약 사전을 따른다.
--
-- 왜 chat_sess를 확장하지 않고 별도 테이블인가.
-- chat_sess는 "사용자 한 명이 소유한 1:1 대화"를 전제로 user_id를 not null FK로 두고 사용자가
-- 지워지면 대화도 함께 지운다(on delete cascade). 협업 Thread는 소유자가 없고 참가자가 여럿이며,
-- 마지막 참가자가 나가도 대화가 남아야 한다. 한 테이블에서 두 전제를 함께 만족시키려면 지금
-- not null인 user_id를 nullable로 풀고 CHECK로 종류를 갈라야 하는데, 그 순간 이미 동작 중인
-- 1:1 저장 경로의 보장이 느슨해진다. 대신 id를 uuid로 두어 나중에 chat_sess.id를 값 그대로
-- 보존하며 옮길 수 있게 한다.
--
-- 기존 데이터 전제: 신규 테이블이라 backfill이 없다. chat_sess·chat_msg는 이 마이그레이션에서
-- 건드리지 않는다. chat_sess → thr 복사는 별도 마이그레이션이고, 그때까지 두 테이블이 함께
-- 존재한다. 새 읽기 경로 검증과 애플리케이션 전환이 끝나기 전에는 chat_sess를 지우지 않는다.
--
-- 이번 단계에 넣지 않은 것: Tenant·Workspace 참조(및 그와 짝인 (tenant, id) 유니크)는 해당
-- 테이블이 들어오는 단계의 것이다. 그때 add column → 기존 행 backfill → not null → 복합 FK
-- 순서로 붙인다. 요약 버전 카운터도 요약 테이블과 같은 단계라 지금 두지 않는다.
create table thr (
    id              uuid         not null,
    kind            varchar(16)  not null check (kind in ('DIRECT', 'COLLAB')),
    status          varchar(16)  not null default 'ACTIVE' check (status in ('ACTIVE', 'LOCKED', 'ARCHIVED')),
    drc_own_user_id uuid         references app_user (id),
    created_user_id uuid         not null references app_user (id),
    title           varchar(255) not null,
    next_seq        bigint       not null default 0,
    created_at      timestamptz  not null default now(),
    updated_at      timestamptz  not null default now(),
    locked_at       timestamptz,
    archived_at     timestamptz,
    primary key (id),
    -- DIRECT만 소유자를 갖는다. COLLAB의 소유는 이 컬럼이 아니라 OWNER 역할 참가자로 표현한다.
    constraint thr_direct_has_owner check ((kind = 'DIRECT') = (drc_own_user_id is not null)),
    -- 상태와 시각을 맞추되 시각은 "그 상태에 도달한 시점"이라 되돌릴 때도 지우지 않는다.
    -- LOCKED를 거쳐 ARCHIVED가 되면 locked_at이 그대로 남아야 언제 잠겼는지를 잃지 않는다.
    constraint thr_active_has_no_end_time check (status <> 'ACTIVE' or (locked_at is null and archived_at is null)),
    constraint thr_locked_has_locked_at check (status <> 'LOCKED' or (locked_at is not null and archived_at is null)),
    constraint thr_archived_has_archived_at check (status <> 'ARCHIVED' or archived_at is not null)
);

-- next_seq는 Message 순서의 정본이다. created_at은 같은 트랜잭션에서 저장된 사람/AI 메시지가
-- 동률이 되고 SQL이 동률 순서를 보장하지 않으므로 쓰지 않는다.
-- n개를 예약할 때는 아래 한 문장으로 원자적으로 채번하고, 반환값이 증가 후 값이므로 할당 범위는
-- returned - n + 1 부터 returned 까지다.
--
--   update thr set next_seq = next_seq + :n where id = :thread_id returning next_seq;
--
-- 채번을 쓰는 쪽(03·CORE)이 아직 없어 Message 테이블은 이 마이그레이션에 포함하지 않는다.
