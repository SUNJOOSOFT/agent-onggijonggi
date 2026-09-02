-- 04·DATA
--
-- 이름은 scripts/glossary/data-glossary.md 축약 사전을 따른다.
--
-- chat_sess / chat_msg: 사용자 소유. 사용자가 삭제하면 메시지도 함께 사라진다.
-- adt_log: 대화 이력과 완전히 별도. 세션·사용자 FK가 없다.

create table chat_sess (
    id         uuid         primary key,
    user_id    uuid         not null references app_user (id) on delete cascade,
    title      varchar(255) not null,
    created_at timestamptz  not null default now(),
    updated_at timestamptz  not null default now()
);

create table chat_msg (
    id         uuid        primary key,
    sess_id    uuid        not null references chat_sess (id) on delete cascade,
    -- 값은 클라이언트 계약(03·CORE ChatMessage.role)과 동일한 소문자 3종을 그대로 쓴다.
    -- 변환 계층을 두지 않아야 계약과 저장값이 어긋나지 않는다.
    role       varchar(16) not null check (role in ('user', 'assistant', 'system')),
    content    text        not null,
    -- assistant 응답 시점의 근거 스냅샷. "그 응답 시점에 무엇을 봤나"의 박제다.
    src_json   jsonb,
    created_at timestamptz not null default now()
);

create index idx_chat_msg_sess_created on chat_msg (sess_id, created_at);

-- 감사 로그. FK가 없는 것이 핵심이다.
-- 사용자·세션 삭제와 무관하게 유지되어야 하므로 req_id는 값 복사다.
create table adt_log (
    id         uuid         primary key,
    trc_id     varchar(64)  not null,
    req_id     varchar(255) not null,
    qst        text,
    ans        text,
    src_json   jsonb,
    created_at timestamptz  not null default now()
);

create index idx_adt_log_trc on adt_log (trc_id);
create index idx_adt_log_created on adt_log (created_at);
