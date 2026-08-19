/********************************************************
 파일명 : models.ts
 설 명 : 모델 선택 관련 공용 로직. 목록 자체는 여기에 두지 않는다 — 어떤 모델을 서빙하는지 아는 곳은
 게이트웨이(infra/config/litellm_config.yaml) 하나뿐이라, 화면은 BFF의 GET /api/models로 받아 쓴다.
 *********************************************************/

/**
 * 쿠키에 남아 있는 마지막 선택을 현재 목록과 대조해 확정한다. 설정에서 빠진 모델이 쿠키에 남아 있으면
 * 목록의 첫 모델로 되돌린다. 목록이 비어 있으면(게이트웨이 응답 실패) 빈 문자열이라 선택기가 비고,
 * 전송 시 BFF의 @NotBlank 검증에 걸린다 — 잘못된 모델로 조용히 보내는 것보다 낫다.
 */
export function resolveSelectedModelId(
  availableModels: string[],
  modelIdFromCookie?: string,
): string {
  if (modelIdFromCookie && availableModels.includes(modelIdFromCookie)) {
    return modelIdFromCookie;
  }
  return availableModels[0] ?? '';
}
