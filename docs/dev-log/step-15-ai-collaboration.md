# Step 15 AI 협업 개발 로그

## 개요
- 이 문서는 Step 15의 구현 과정과 이후 설계 판단을 기록하기 위한 개발 로그다.
- 포함 범위는 Redis 기반 location cache 추가, location resolver 흐름에 대한 cache 적용, cache decorator 테스트다.
- AI는 설계 문서를 기준으로 코드 생성과 구조 정리를 보조하는 수단으로만 사용했고, 실제 location 판단 구조는 기존 rule + AI fallback 흐름을 그대로 유지했다.

---

## Step 15: Redis location cache

### 사용한 프롬프트
<details>
<summary>Step 15 프롬프트</summary>

```text
Re-read ALL documents in /docs from scratch.

Important:
- Ignore previous assumptions
- Treat the latest documents as the single source of truth
- Follow the documented architecture and boundaries strictly
- Preserve the hybrid design: deterministic logic in code, AI only where allowed

Step 15: Redis location cache

Implement only the location cache for this project.

Goal:
Cache location resolution results so repeated location interpretation does not require repeated resolution work.
This cache must fit the current rule + AI fallback structure.

Scope:
- Add Redis-backed cache only for location resolution results
- Apply cache to the location resolver flow only
- Keep current business flow unchanged except for cache lookup/store
- Support different TTL behavior according to the documented location resolution status if applicable

Constraints:
- Do NOT implement weather cache
- Do NOT implement recommendation cache
- Do NOT change rule engine logic
- Do NOT expand AI responsibility
- Do NOT redesign current package structure unnecessarily
- Do NOT introduce unrelated infra/components
- Do NOT cache everything broadly; only the location resolution result
```
</details>

### 수행 결과
- `application/location/LocationResolutionCache`를 추가해 location cache를 application 포트로 분리했다.
- `application/location/CachingLocationResolver`를 추가해 기존 `LocationResolver` 앞단에서 cache hit/miss를 처리하는 decorator를 구현했다.
- `infrastructure/location/RedisLocationResolutionCache`, `LocationCacheProperties`를 추가해 Redis 기반 location cache adapter와 TTL 설정을 구성했다.
- `RecommendationAssemblyConfig`에서 기존 `DefaultLocationResolver`를 직접 주입하지 않고, 이를 감싼 `CachingLocationResolver`를 `LocationResolver` bean으로 노출하도록 조립했다.
- `application.yaml`에 location cache TTL 24시간 설정을 추가했다.
- `CachingLocationResolverTest`를 추가해 cache hit, cache miss, failed 결과 cache 저장 동작을 검증했다.

### 발생한 문제 / 애매한 부분
- 문서에는 location cache `TTL: 24h`만 있고 `RESOLVED / APPROXIMATED / FAILED` 상태별 TTL 차등 정책은 명시되어 있지 않았다.
- cache key를 `location_name`로 볼지, 실제 raw location 입력 문자열 기준으로 볼지 선택이 필요했다.
- 기존 `DefaultLocationResolver` 안에 cache 로직을 넣으면 rule + AI fallback 흐름과 cache 관심사가 섞일 수 있었다.

### 개선 및 결정
- cache는 resolver 내부 로직을 수정하지 않고 바깥에서 감싸는 decorator로 적용해 기존 `rule -> AI fallback` 구조를 그대로 보존했다.
- cache port는 application에 두고 Redis 구현은 infrastructure에 두어 Redis 의존성이 business flow로 새지 않도록 유지했다.
- cache key는 raw location 입력을 단순 정규화한 문자열 기준으로 구성했다. 이는 “같은 입력 재요청”에 대한 cache 목적에 맞춘 최소 설계다.
- TTL은 문서 기준대로 모든 location resolution 결과에 대해 24시간으로 통일했다. 상태별 TTL 차등은 문서에 없으므로 이번 단계에서 추가하지 않았다.
- `FAILED` 결과도 cache에 저장하도록 두어 같은 입력에 대해 반복적으로 rule/AI fallback을 다시 수행하지 않도록 했다.
- weather cache, recommendation cache, invalidation 정책, cache 장애 복구 정책은 이번 단계 범위에서 제외했다.

---

## 전체 회고

### 잘 된 점
- location cache를 `LocationResolver` 앞단에만 적용해 recommendation, weather, rule engine 쪽 책임을 건드리지 않았다.
- 기존 resolver의 `RESOLVED / APPROXIMATED / FAILED` 반환 모델을 유지한 채 cache를 덧씌워 구조 변경을 최소화했다.
- cache hit 시 delegate를 다시 호출하지 않고, miss 시 기존 resolver 결과를 그대로 저장하는 흐름을 단위 테스트로 확인했다.

### 어려웠던 점
- 문서상 location cache 정책은 단순하지만, 실제 구현에서는 key 기준과 TTL 차등 여부를 과도하게 확장하지 않도록 범위를 제한해야 했다.
- cache를 `DefaultLocationResolver` 내부에 넣을지, 별도 decorator로 둘지 결정할 때 기존 hybrid 설계의 책임 경계를 유지하는 쪽을 우선해야 했다.

### 개선 방향
- 이후 문서가 추가되면 location key 정규화 수준이나 failed 결과 TTL 정책을 별도로 조정할 수 있다.
- 현재 단계에서는 Redis adapter와 application port 분리만으로 충분하며, cache invalidation이나 운영 최적화는 이후 범위에서 다루는 것이 적절하다.
