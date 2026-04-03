# Step 17 AI 협업 개발 로그

## 개요
- 이 문서는 Step 17의 구현 과정과 이후 설계 판단을 기록하기 위한 개발 로그다.
- 포함 범위는 Redis 기반 recommendation cache 추가, final recommendation result cache 적용, recommendation cache key/TTL 테스트다.
- AI는 설계 문서를 기준으로 코드 생성과 구조 정리를 보조하는 수단으로만 사용했고, 실제 추천 판단 로직은 여전히 resolver, weather client, rule engine, fallback component 조합으로 유지했다.

---

## Step 17: Redis recommendation cache

### 사용한 프롬프트
<details>
<summary>Step 17 프롬프트</summary>

```text
Re-read ALL documents in /docs from scratch.

Important:
- Ignore previous assumptions
- Treat the latest documents as the single source of truth
- Follow the documented architecture and boundaries strictly
- Preserve the hybrid design: deterministic logic in code, AI only where allowed

Step 17: Redis recommendation cache

Implement only the recommendation cache for this project.

Goal:
Cache final recommendation results so repeated recommendation requests can return quickly
without re-running the full recommendation flow every time.

Scope:
- Add Redis-backed cache only for final recommendation results
- Apply cache to the recommendation flow only
- Keep current business flow unchanged except for cache lookup/store
- Cache only the final recommendation result appropriate for the current MVP flow

Constraints:
- Do NOT implement location cache
- Do NOT implement weather cache
- Do NOT change rule engine logic
- Do NOT expand AI responsibility
- Do NOT redesign current package structure unnecessarily
- Do NOT introduce unrelated infra/components
- Do NOT cache intermediate data broadly in this step
- Do NOT add invalidation/event-driven complexity unless strictly needed by current docs
```
</details>

### 수행 결과
- `application/recommendation/RecommendationCache`를 추가해 recommendation cache를 application 포트로 분리했다.
- `infrastructure/recommendation/RedisRecommendationCache`, `RecommendationCacheProperties`를 추가해 Redis 기반 recommendation cache adapter와 TTL 설정을 구성했다.
- `RecommendationService`에 recommendation cache read/write를 추가해 `recommend()` 진입 직후 cache를 조회하고, miss 시 기존 전체 recommendation 흐름을 실행한 뒤 최종 `RecommendationResult`를 저장하도록 구현했다.
- `RecommendationAssemblyConfig`에서 recommendation cache bean을 조립하고 `RecommendationService` 생성자에 주입하도록 변경했다.
- `application.yaml`에 recommendation cache TTL 30분 설정을 추가했다.
- `RecommendationServiceTest`에 recommendation cache hit/miss 테스트를 추가해 cache hit 시 전체 흐름을 다시 실행하지 않는지, miss 후 결과를 cache에 저장하는지 검증했다.
- `RedisRecommendationCacheTest`를 추가해 recommendation cache key와 TTL이 문서 기준으로 동작하는지 검증했다.

### 발생한 문제 / 애매한 부분
- 문서에는 recommendation cache `key: event + time + location`, `TTL: 30min`만 있고 invalidation이나 event 변경 감지 정책은 없었다.
- final result를 cache하려면 `OutfitDecision`만 저장할지, `RecommendationResult` 전체를 저장할지 선택이 필요했다.
- recommendation cache를 별도 decorator로 둘지, 현재 `RecommendationService` 상단에 직접 적용할지 선택이 필요했다.

### 개선 및 결정
- cache 대상은 현재 MVP recommendation flow의 최종 출력으로 보고 `RecommendationResult` 전체를 저장하도록 선택했다. 이는 weather fallback 여부와 중간 weather snapshot 포함 결과까지 그대로 재사용하기 위함이다.
- cache key는 문서의 `event + time + location`에 맞추되, 실제 구현에서는 `eventId + 30분 버킷 시각 + startAt + endAt + rawLocation`을 함께 사용해 같은 일정 요청을 더 안정적으로 식별하도록 구성했다.
- cache read/write는 `RecommendationService.recommend()` 상단과 하단에 직접 배치했다. recommendation cache가 “최종 흐름 결과”만 대상으로 하므로, 별도 하위 component보다 orchestration 진입점에 두는 편이 범위상 더 단순했다.
- cache miss 시에는 기존 `location resolve -> weather 조회 3건 -> rule engine or weather fallback -> summary` 흐름을 그대로 유지했다.
- TTL은 문서 기준대로 30분을 사용했다.
- location cache, weather cache 구조 변경, cache invalidation, event-driven 갱신, 부분 결과 cache는 이번 단계 범위에서 제외했다.

---

## 전체 회고

### 잘 된 점
- recommendation cache를 최종 recommendation flow에만 적용해 하위 domain 규칙이나 외부 adapter 책임을 추가로 섞지 않았다.
- `RecommendationResult` 전체를 cache해 정상 경로와 weather fallback 경로 모두 같은 출력 구조로 재사용할 수 있게 했다.
- cache hit 시 resolver, weather client, rule engine을 다시 호출하지 않는다는 점을 service 테스트에서 직접 확인했다.

### 어려웠던 점
- recommendation cache는 최종 결과를 저장하므로 key가 너무 단순하면 잘못된 재사용 위험이 있고, 너무 복잡하면 의미가 흐려질 수 있어 균형이 필요했다.
- cache 대상을 `OutfitDecision`만으로 줄이면 current flow가 노출하는 중간 결과와 fallback 의미가 빠질 수 있어, 현재 wrapper 구조를 그대로 저장하는 판단이 필요했다.
- invalidation을 넣고 싶어도 문서에 정책이 없기 때문에, 이번 단계에서는 의도적으로 배제해야 했다.

### 개선 방향
- 이후 문서가 event 갱신 시점이나 recommendation 재계산 정책을 더 명시하면 cache key 단순화 또는 invalidation 전략을 따로 정리할 수 있다.
- 현재 단계에서는 최종 결과 재사용만 담당하도록 유지하고, cache 정책 고도화는 이후 범위에서 다루는 것이 적절하다.
