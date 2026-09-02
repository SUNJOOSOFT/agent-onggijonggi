-- doc.uploaded_by를 사용자 삭제와 독립시킨다.
--
-- 지금은 FK에 ON DELETE가 없어, 문서를 올린 사용자를 삭제하려 하면 제약이 막는다. uploaded_by는 "누가
-- 올렸나"의 기록일 뿐 하드 의존이 아니므로(감사 로그가 req_id를 값으로 두는 것과 같은 취지), 사용자가
-- 삭제되면 null로 두고 문서는 남긴다.
--
-- v0에는 사용자 삭제 경로가 없다. 미래의 삭제가 FK로 막히지 않도록 지금 열어 둔다.
alter table doc drop constraint doc_uploaded_by_fkey;
alter table doc
    add constraint doc_uploaded_by_fkey
        foreign key (uploaded_by) references app_user (id) on delete set null;
