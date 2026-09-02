-- 04·DATA — 협업 채팅 Thread 참가자(thr_mbr).
--
-- 이름은 scripts/glossary/data-glossary.md 축약 사전을 따른다.
--
-- PK를 (thr_id, user_id)가 아니라 대리키로 둔다. 그 조합을 PK로 두면 한 사람이 한 Thread에
-- 행을 하나만 가져서, 참가자를 뺄 때 행을 지워야 하고 그러면 누가 언제 왜 나갔는지가 사라진다.
-- 재초대는 과거 행을 되살리지 않고 새 행을 만드는 방식으로 가므로, 대리키가 있어야 같은
-- 사람이 같은 Thread에 여러 행(과거 참가 이력)을 가질 수 있다. 중복 참가는 활성 행에 대한
-- 부분 유니크로 막는다.
--
-- 사용자 참조는 app_user(id) 단일 FK다. Tenant 회원 테이블을 거치지 않는다 — 소속과 역할의
-- 정본은 Keycloak이고 DB는 그 미러를 두지 않는다. 그래서 다른 Tenant 사용자가 이 Thread에
-- 들어오는 것을 이 FK 하나로는 막지 못한다. thr의 사람 참조 FK와 같은 처지이고, 같은 이유로
-- 감수한다 — 참가자 추가는 도메인 함수를 통해서만 하고, 그 함수가 사용자 확인을 한다.
--
-- 이번 단계에 넣지 않은 것: tnn_id와 그에 따른 복합 FK·(tnn_id,thr_id,id) 형태의 unique는
-- Tenant가 들어오는 단계의 것이다(thr과 같은 이유). 그때까지 활성 참가자 유니크는 thr_id와
-- user_id만으로 잡는다. Workspace VIEW 확인에 따른 참가자 회수도 마찬가지로 다음 단계다 —
-- 지금 참가자는 명시 초대·명시 제외로만 관리한다.
create table thr_mbr (
    id                 uuid        not null,
    thr_id             uuid        not null references thr (id) on delete cascade,
    user_id            uuid        not null references app_user (id),
    role               varchar(16) not null check (role in ('OWNER', 'MEMBER')),
    status             varchar(16) not null default 'ACTIVE' check (status in ('ACTIVE', 'LEFT', 'REVOKED')),
    created_by_user_id uuid        not null references app_user (id),
    created_at         timestamptz not null default now(),
    ended_at           timestamptz,
    end_rsn            varchar(64),
    primary key (id),
    -- thr의 상태·시각 CHECK와 같은 방향 — 되돌릴 여지를 남기지 않고 "그 상태면 그 정보가 있다"만
    -- 강제한다. end_rsn을 ended_at과 함께 묶는 이유는 "언제 끝났는지는 있는데 왜인지는 없는" 행이
    -- 생기지 않게 하기 위해서다.
    constraint thr_mbr_active_has_no_end_info
        check (status <> 'ACTIVE' or (ended_at is null and end_rsn is null)),
    constraint thr_mbr_ended_has_end_info
        check (status = 'ACTIVE' or (ended_at is not null and end_rsn is not null))
);

-- 활성 참가자 중복 방지. 재초대로 생긴 과거 LEFT/REVOKED 행은 이 인덱스 밖이라 그대로 남는다.
create unique index ux_thr_mbr_active_participant
    on thr_mbr (thr_id, user_id)
    where status = 'ACTIVE';
