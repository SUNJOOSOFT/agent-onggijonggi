-- doc 생명주기 추적 + 부서 스코프 + 문서 버전 이력(doc_ver) 분리.
-- 근거: 04·DATA 스키마 설계 리뷰 반영분.
-- doc 테이블은 아직 어떤 애플리케이션 코드도 쓰지 않아(Doc 엔티티 이번에 신규 생성) 항상 비어
-- 있다 — 백필 없이 안전하게 ALTER한다.
--
-- dept = department(부서, Keycloak Groups 클레임에서 옴), pnd = pending(대기 중).

alter table doc
    add column dept                text        not null,
    add column acc_tag_ver         int         not null default 1,
    add column status              varchar(16) not null default 'PENDING'
        check (status in ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
    add column status_at           timestamptz not null default now(),
    add column pnd_idx_run_id      uuid,
    add column cur_idx_run_id      uuid,
    add column last_err            text,
    add column att_cnt             int         not null default 0,
    add column emb_fgpt            text,
    add column deleted_at          timestamptz,
    add column deleted_by          text,
    add column cur_ver_id          uuid,
    add column next_ver_seq        int         not null default 1;

-- doc_key 유니크 범위를 전역 → (dept, doc_key)로 좁힌다 — 부서 간 충돌을 구조적으로
-- 막는다. 소프트 삭제 행은 스코프 밖 — 삭제된 키는 재사용 가능해야 한다.
alter table doc drop constraint doc_doc_key_key;
create unique index ux_doc_dept_doc_key on doc (dept, doc_key) where deleted_at is null;

-- uploaded_by는 doc_ver로 이동한다 — 버전마다 올린 사람이 다를 수 있고, SET NULL은 계정 삭제
-- 시 정보를 파괴하므로 값 복사(FK 아님)로 바뀐다.
alter table doc drop constraint doc_uploaded_by_fkey;
alter table doc drop column uploaded_by;

-- doc_ver: 문서 버전(업로드) 이력. 색인 상태("지금 검색되는 것"의 속성)는 doc에 남고, 이 테이블은
-- 순수하게 "업로드된 파일의 이력"만 담는다.
create table doc_ver (
    id          uuid          primary key,
    doc_id      uuid          not null references doc (id),
    ver_seq     int           not null,
    file_name   varchar(1024) not null,
    org_path    varchar(1024) not null,
    uploaded_by text          not null,
    uploaded_at timestamptz   not null default now()
);

create index idx_doc_ver_doc on doc_ver (doc_id, uploaded_at);

-- doc.cur_ver_id → doc_ver.id는 순환 참조라 doc_ver 생성 후에 FK를 붙인다.
alter table doc
    add constraint doc_cur_ver_id_fkey foreign key (cur_ver_id) references doc_ver (id);
