-- 04·DATA — 협업 채팅 Message(msg).
--
-- 이름은 scripts/glossary/data-glossary.md 축약 사전을 따른다.
--
-- 사람 메시지와 AI 응답을 한 테이블에 담는다. 작성 주체(HUMAN/AGENT/SYSTEM)에 따라 참조 가능한
-- 컬럼을 CHECK로 가른다.
--
-- HUMAN은 thr_mbr(참여 기록)을 참조한다. app_user를 직접 참조하지 않는 이유는, 같은 사람이
-- 나갔다 재초대돼 새 참여 행이 생겨도 그 메시지가 "그때 그 참여"를 가리키게 하기 위해서다 —
-- thr_mbr이 이력을 새 행으로 남기는 것과 같은 이유다.
--
-- AGENT·SYSTEM은 지금 아무 컬럼도 참조하지 않는다. 페르소나(어느 Agent가 답했는지)를 구분하는
-- 이슈가 아직 없고, 멘션 순서·실행 이력(msg_mnt·agn_run)도 여러 Agent 병렬 실행 제품 정책이
-- 나올 때 만든다. 필요해지면 agn_key 같은 nullable 값 컬럼을 후행 추가하고, 그 전 행은
-- 1차 채팅 이관의 agent_key='legacy' 패턴처럼 백필한다.
--
-- 순서는 created_at이 아니라 스레드별 단조 증가 seq가 정본이다. 한 턴의 사람 메시지와 AI
-- 응답이 같은 트랜잭션·같은 시각에 저장되면 created_at이 동률이 되고 SQL은 동률 순서를
-- 보장하지 않는다. 채번은 thr.next_seq를 UPDATE ... RETURNING으로 원자적으로 올려 받는다 —
-- 그 서비스 로직은 #18 몫이라 이 마이그레이션엔 없다.
--
-- reply_msg_id·thr_mbr_id는 복합 FK로 같은 Thread인지 강제한다. 참조 대상에 unique(thr_id,id)가
-- 있어야 해서 thr_mbr에 그 유니크를 먼저 추가한다.
alter table thr_mbr
    add constraint thr_mbr_thr_id_id_key unique (thr_id, id);

-- 이번 단계에 넣지 않은 것: tnn_id(Tenant가 들어오는 단계), agn_run_id와 그 unique(여러 Agent
-- 병렬 실행 정책이 나올 때)는 후행 추가한다. thr·thr_mbr과 같은 이유다.
create table msg (
    id           uuid        not null,
    thr_id       uuid        not null references thr (id) on delete cascade,
    seq          bigint      not null,
    rpl_msg_id   uuid,
    ath_kind     varchar(16) not null
        check (ath_kind in ('HUMAN', 'AGENT', 'SYSTEM')),
    thr_mbr_id   uuid,
    status       varchar(16) not null default 'PENDING'
        check (status in ('PENDING', 'COMPLETE', 'DENIED', 'FAILED', 'CANCELLED')),
    content      text        not null default '',
    pyl_json     jsonb,
    src_json     jsonb,
    created_at   timestamptz not null default now(),
    completed_at timestamptz,
    primary key (id),
    -- 자기 자신을 같은 Thread로만 답글로 참조하게 하는 대상. reply가 없으면(null) MATCH SIMPLE이라
    -- thr_id만으로는 FK를 안 본다.
    constraint msg_thr_id_id_key unique (thr_id, id),
    constraint msg_rpl_msg_id_fkey
        foreign key (thr_id, rpl_msg_id) references msg (thr_id, id),
    constraint msg_thr_mbr_id_fkey
        foreign key (thr_id, thr_mbr_id) references thr_mbr (thr_id, id),
    -- HUMAN만 참여 기록을 가리킨다. AGENT·SYSTEM은 위 헤더 주석대로 지금 아무것도 안 가리킨다.
    constraint msg_human_has_participant
        check ((ath_kind = 'HUMAN') = (thr_mbr_id is not null)),
    -- AGENT만 PENDING으로 시작할 수 있다. HUMAN·SYSTEM은 쓰이는 순간 이미 완료된 메시지다.
    constraint msg_pending_only_for_agent
        check (ath_kind = 'AGENT' or status <> 'PENDING'),
    -- thr·app_user와 같은 방향 — 되돌릴 여지를 남기지 않고 "그 상태면 그 시각이 있다"만 강제한다.
    constraint msg_completed_at_matches_status
        check ((status = 'PENDING') = (completed_at is null))
);

create unique index ux_msg_thr_seq on msg (thr_id, seq);

-- 완료된 메시지는 불변이고 개별 삭제도 거부한다. Thread가 지워질 때의 cascade만 통과시킨다.
-- pg_trigger_depth()가 1이면 최상위 문장(DELETE FROM msg ... 직접 실행)이고, 2 이상이면
-- 다른 트리거(예: thr의 ON DELETE CASCADE) 안에서 파생된 것이다 — PostgreSQL에서 "cascade는
-- 허용하고 개별 삭제는 막는다"의 표준 관용구다.
create or replace function msg_block_terminal_mutation() returns trigger
    language plpgsql as $$
begin
    if TG_OP = 'UPDATE' and OLD.status <> 'PENDING' then
        raise exception '완료된 메시지는 수정할 수 없습니다: id=%', OLD.id
            using errcode = 'check_violation';
    end if;
    if TG_OP = 'DELETE' and pg_trigger_depth() <= 1 then
        raise exception '메시지는 개별 삭제할 수 없습니다: id=%', OLD.id
            using errcode = 'check_violation';
    end if;
    return coalesce(NEW, OLD);
end;
$$;

create trigger msg_block_terminal_update
    before update on msg
    for each row execute function msg_block_terminal_mutation();

create trigger msg_block_individual_delete
    before delete on msg
    for each row execute function msg_block_terminal_mutation();
