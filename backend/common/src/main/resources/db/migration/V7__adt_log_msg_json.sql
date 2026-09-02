-- adt_log를 "마지막 질문/답변"에서 "그 턴 시점까지의 전체 대화 스냅샷"으로 재설계.
-- 근거: 02·EDGE 감사 로그 설계("전체 채팅 이력 저장"), 04·DATA adt_log 절.
--
-- adt_log를 쓰는 애플리케이션 코드가 아직 없어(AuditingChatStreamService 미착수) 항상 비어
-- 있다 — 백필 없이 안전하게 컬럼을 교체한다.
alter table adt_log drop column qst;
alter table adt_log drop column ans;
alter table adt_log add column msg_json jsonb not null;
