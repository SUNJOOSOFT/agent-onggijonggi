package com.onggijonggi.api.chat;

import com.onggijonggi.common.chat.domain.Thr;
import java.util.List;
import java.util.UUID;

/**
 * Class Name : CollabThreadSummary.java
 * Description : GET /api/collab/threads 응답 항목. 필드 구성은 01·CLIENT의
 *               CollabThreadSummary(frontend/lib/api/collab.ts)와 같아야 한다.
 *
 *               participants는 방을 제목만으로 가려내기 어려워 화면이 함께 보여주는 표시 이름인데,
 *               app_user에 이름 컬럼이 없어 지금은 늘 비어 나간다. 채우는 시점·방법은 이슈 #22를
 *               확인한다.
 */
public record CollabThreadSummary(
		UUID id,
		String title,
		List<String> participants
) {

	static CollabThreadSummary from(Thr thr) {
		return new CollabThreadSummary(thr.getId(), thr.getTitle(), List.of());
	}

}
