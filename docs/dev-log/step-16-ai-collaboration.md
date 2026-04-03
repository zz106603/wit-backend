# Step 16 AI 협업 개발 로그

## 개요
- 이 문서는 Step 16의 구현 과정과 이후 설계 판단을 기록하기 위한 개발 로그다.
- 포함 범위는 Redis 기반 weather cache 추가, weather retrieval 흐름에 대한 cache 적용, weather cache key/TTL 테스트다.
- AI는 설계 문서를 기준으로 코드 생성과 구조 정리를 보조하는 수단으로만 사용했고, 실제 weather 판단과 recommendation 판단은 여전히 코드와 규칙 엔진 책임으로 유지했다.

---

## Step 16: Redis weather cache

### 사용한 프롬프트
<details>
<summary>Step 16 프롬프트</summary>

```text
Re-read ALL documents in /docs from scratch.

Important:
- Ignore previous assumptions
- Treat the latest documents as the single source of truth
- Follow the documented architecture and boundaries strictly
- Preserve the hybrid design: deterministic logic in code, AI only where allowed

Step 16: Redis weather cache

Implement only the weather cache for this project.

Goal:
Cache weather lookup results so repeated weather requests do not repeatedly call the external weather source.
This cache must fit the current weather client / snapshot conversion flow.

Scope:
- Add Redis-backed cache only for weather lookup results
- Apply cache to the weather retrieval flow only
- Keep current business flow unchanged except for cache lookup/store
- Support the documented TTL policy for current weather vs forecast if applicable

Constraints:
- Do NOT implement location cache
- Do NOT implement recommendation cache
- Do NOT change rule engine logic
- Do NOT expand AI responsibility
- Do NOT redesign current package structure unnecessarily
- Do NOT introduce unrelated infra/components
- Do NOT cache everything broadly; only weather lookup result(s)
```
</details>

### 수행 결과
- `application/weather/WeatherCache`를 추가해 weather cache를 application 포트로 분리했다.
- `application/weather/CachingWeatherClient`를 추가해 기존 `WeatherClient` 앞단에서 current/forecast weather cache hit/miss를 처리하는 decorator를 구현했다.
- `infrastructure/weather/RedisWeatherCache`, `WeatherCacheProperties`를 추가해 Redis 기반 weather cache adapter와 TTL 설정을 구성했다.
- `WeatherClientConfig`에서 `HttpWeatherClient`를 직접 주입하지 않고, 이를 감싼 `CachingWeatherClient`를 `@Primary WeatherClient`로 노출하도록 조립했다.
- `application.yaml`에 weather cache TTL 1시간 설정을 추가했다.
- `CachingWeatherClientTest`를 추가해 current weather cache hit, forecast cache miss 후 저장 동작을 검증했다.
- `RedisWeatherCacheTest`를 추가해 weather cache key와 TTL이 문서 기준으로 동작하는지 검증했다.

### 발생한 문제 / 애매한 부분
- 문서에는 weather cache `key: lat/lon + time`, `TTL: 1h`만 있고 current weather와 forecast를 다른 TTL로 나누라는 정책은 없었다.
- current weather는 매 요청 시각이 계속 변하므로 cache key를 그대로 now로 쓰면 hit가 거의 나지 않을 수 있었다.
- `HttpWeatherClient` 내부에 cache를 넣으면 외부 호출/변환 책임과 cache 책임이 섞일 수 있었다.

### 개선 및 결정
- cache는 `HttpWeatherClient` 바깥의 `CachingWeatherClient`에서 처리해 기존 `external call -> WeatherSnapshotMapper` 흐름을 그대로 유지했다.
- current weather는 cache key용 시각을 hour 단위로 잘라 사용하고, forecast는 요청 `targetTime`을 그대로 사용하도록 분리했다.
- cache key는 문서 기준대로 좌표와 시각을 중심으로 구성하고, `current` / `forecast` 타입만 prefix로 분리했다.
- TTL은 문서에 명시된 weather cache 1시간을 current/forecast 모두에 동일하게 적용했다.
- cache miss 시에는 기존 `HttpWeatherClient.fetch... -> WeatherSnapshotMapper.toSnapshot(...)` 흐름을 그대로 타고, 그 결과만 cache에 저장하도록 유지했다.
- location cache, recommendation cache, weather fallback 정책 변경, provider retry 전략은 이번 단계 범위에서 제외했다.

---

## 전체 회고

### 잘 된 점
- weather cache를 `WeatherClient` decorator로 적용해 recommendation service나 rule engine을 수정하지 않고 조회 비용만 줄이는 구조를 만들었다.
- `HttpWeatherClient`의 책임을 fetch + transform에 그대로 두고, Redis와 cache key/TTL 책임은 별도 adapter로 분리했다.
- current weather와 forecast를 같은 포트 안에서 다루되, cache key 구성만 다르게 처리해 최소 구현으로 맞췄다.

### 어려웠던 점
- current weather는 시각이 계속 변하므로 cache hit를 확보하면서도 문서 범위를 넘지 않는 key 설계가 필요했다.
- forecast key를 임의로 더 강하게 정규화하면 의미가 바뀔 수 있어, 요청 시각 자체를 유지하는 쪽으로 제한해야 했다.
- current/forecast TTL 분리 요구를 과도하게 넣지 않고 문서의 단일 TTL 정책을 따르는 선을 유지해야 했다.

### 개선 방향
- 이후 문서가 추가되면 current와 forecast의 TTL을 더 세분화할 수 있지만, 현재 단계에서는 1시간 단일 TTL이 적절하다.
- weather cache hit율 최적화는 provider 스펙과 실제 요청 패턴이 더 명확해질 때 추가로 검토하는 것이 맞다.
