-- chat_message 순서 보장을 위한 단조 증가 컬럼.
-- created_at만으로는 한 턴의 user/assistant가 같은 트랜잭션·같은 시각에 저장돼 동률이 나고
-- SQL이 동률 순서를 보장하지 않는다.
--
-- 채번(원자적 UPDATE + RETURNING) 로직은 03·CORE(PersistingChatStreamService) 영역이라 이번
-- 마이그레이션엔 포함하지 않는다 — 그래서 chat_msg.seq는 NOT NULL로 잠그지 않는다. 지금 잠그면
-- 현재 채번 로직 없이 동작 중인 채팅 저장(INSERT)이 그 자리에서 깨진다. 채번 로직이 붙은 뒤
-- 별도 마이그레이션으로 NOT NULL을 잠글 것.
alter table chat_sess add column next_seq int not null default 0;
alter table chat_msg add column seq int;
