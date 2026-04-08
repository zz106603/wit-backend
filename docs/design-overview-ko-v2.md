## 0. 사용 지침 (중요)

이 설계는 아래 원칙을 반드시 유지해야 한다.

```
- 설계를 변경하지 말 것
- AI 역할을 확대하지 말 것
- 규칙 엔진을 제거하지 말 것
- MVP 범위를 확장하지 말 것
```

이 문서를 기반으로 작업할 때는

**“수정”이 아니라 “보완”만 허용된다**

---

# 1. 프로젝트 개요

## 한 줄 정의

> 일정, 위치, 시간대별 날씨를 기반으로
>
>
> 우산 여부와 기능적 옷차림을 자동으로 판단하는 AI 기반 외출 준비 시스템
>

---

# 2. 핵심 설계 철학 (매우 중요)

## 2.1 하이브리드 의사결정 구조

```
정형 데이터 → 코드 (규칙 엔진)
비정형 데이터 → AI
```

---

## 2.2 AI 역할 제한

AI는 아래 2가지 역할만 수행한다:

```
1. location 자연어 해석
2. 최종 요약 문장 생성
```

---

## 2.3 AI 금지 영역

```
- 날씨 판단 X
- 우산 판단 X
- 옷차림 결정 X
- 온도 계산 X
```

이건 전부 **규칙 엔진 책임**

---

# 3. MVP 범위 (확정)

## 포함

- iOS 앱 (SwiftUI)
- Google Calendar 연동
- 현재 위치 기반
- 일정 3개 조회
- 날씨 비교 (현재 / 시작 / 종료)
- 우산 판단
- 옷차림 추천
- AI 요약

---

## 제외

```
- 교통/경로 추천
- 지하철 혼잡도
- Apple Health
- 패션/코디 추천
- 소셜 기능
- 푸시 알림
```

---

# 4. 핵심 기능 흐름

```
CalendarEvent
   ↓
ResolvedLocation (rule → Google Places → AI fallback)
   ↓
WeatherSnapshot (3개)
   ↓
Rule Engine (핵심)
   ↓
OutfitDecision
   ↓
AI Summary
```

---

# 5. 도메인 모델

## 5.1 CalendarEvent

```
- eventId
- title
- startAt
- endAt
- rawLocation
  - Google Calendar location 우선
  - location이 비어 있으면 장소성 있는 title을 보조 후보로 사용
```

---

## 5.2 ResolvedLocation

```
- rawLocation
- normalizedQuery
- displayLocation
- lat
- lng
- confidence
- status (RESOLVED / APPROXIMATED / FAILED)
- resolvedBy (RULE / GOOGLE_PLACES / AI)
```

---

## 5.3 WeatherSnapshot

```
- regionName
- targetTime
- temperature
- feelsLike
- precipitationProbability
- weatherType
```

---

## 5.4 OutfitDecision

```
- needUmbrella
- recommendedOutfitLevel
- recommendedOutfitText
- umbrellaReason
- outfitReason
- temperatureGap
- weatherChangeSummary
- aiSummary
```

---

# 6. 규칙 엔진 (핵심 로직)

## 6.1 우산 판단

```
조건:
- 종료 시점 강수 확률 ≥ 50%
- OR weatherType == RAIN
```

---

## 6.2 옷차림 기준 (종료 시점 체감온도)

| 온도 | 결과 |
| --- | --- |
| ≥ 23 | 반팔 |
| 20~22 | 반팔 + 얇은 겉옷 |
| 17~19 | 긴팔 |
| 13~16 | 긴팔 + 가벼운 겉옷 |
| ≤ 12 | 두꺼운 겉옷 |

---

## 6.3 보정 규칙

다음 중 하나면 +1 단계:

```
- 현재 대비 -4도 이상
- 시작→종료 -3도 이상
- (저온 + 비)
```

---

## 6.4 location 실패

```
- 현재 위치 기준 판단
- 안내 문구 표시
- 추천은 계속 제공
```

---

# 7. AI 설계

## 7.1 Location 해석

구조:

```
rule → Google Places → AI fallback
```

AI는 Google Places로도 충분히 해석되지 않는 경우의 fallback으로만 사용한다.

### 입력

```
{ "rawLocation":"강남 회식" }
```

### 출력

```
{
  "normalizedQuery":"강남",
  "regionName":"서울특별시 강남구",
  "confidence":0.82
}
```

---

## 7.2 Summary 생성

### 입력

- 날씨 정보
- 우산 여부
- 옷차림 결과

### 출력

- 1~2문장
- 짧고 실용적
- 과장 금지

---

# 8. API 구조

## 주요 API

```
GET  /api/integrations/google/login-url
POST /api/integrations/google/callback
GET  /api/recommendations/home
GET  /api/recommendations/events/{eventId}
```

---

# 9. 저장 구조

## MySQL

```
- user
- google_integration
```

---

## Redis

```
- location cache
- weather cache
- recommendation cache
```

---

## 저장하지 않음

```
- 전체 일정
- 전체 날씨 응답
- AI 전체 로그
```

---

# 10. 캐시 전략

## location

```
TTL:
- RESOLVED: 30일
- APPROXIMATED: 7일
- FAILED: 1일
```

---

## weather

```
TTL:
- 현재: 10분
- 예보: 30분
```

---

## recommendation

```
TTL:
- 10~30분
```

---

# 11. 시스템 아키텍처

```
iOS (SwiftUI)
   ↓
Spring Boot
   ↓
 ├─ Google Calendar API
 ├─ Google Places API
 ├─ Open-Meteo
 ├─ AI API
   ↓
MySQL + Redis
```

---

# 12. 테스트 시나리오 (핵심)

반드시 포함:

- 종료 시점 비
- 온도 하락
- location 실패
- 날씨 API 실패
- 일정 없음

---

# 13. 기술 스택

```
Backend: Spring Boot
DB: MySQL
Cache: Redis
Mobile: SwiftUI
```

---

# 14. 절대 변경 금지 규칙

```
1. AI가 판단 로직을 수행하면 안 된다
2. 규칙 엔진을 제거하면 안 된다
3. location 해석은 rule → Google Places → AI fallback 구조 유지
4. 옷차림 추천은 기능적 기준 유지
```
---

# 최종 핵심 요약

```
- AI는 일부만 사용한다
- 핵심 판단은 코드가 한다
- 캐시로 비용을 줄인다
- 실제 생활 문제를 해결한다
```
