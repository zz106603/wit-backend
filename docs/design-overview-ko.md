## 1. 프로젝트 개요

### 한 줄 설명

> Google Calendar 일정과 현재 위치, 목적지의 날씨 변화를 기반으로
>
>
> 우산 여부와 기능적 옷차림을 추천하는 AI 기반 외출 판단 서비스
>

---

## 2. 문제 정의

기존 날씨 앱과 캘린더는 분리되어 있어

사용자는 다음을 직접 판단해야 한다.

- 약속 장소의 날씨
- 현재 위치와의 온도 차이
- 시간대별 날씨 변화
- 우산 필요 여부
- 어떤 옷을 입어야 하는지

즉,

> **외출 준비에 필요한 판단을 사용자가 직접 해야 한다**
>

---

## 3. 해결 방법

이 프로젝트는 다음을 자동화한다.

- 캘린더 일정 조회
- 일정 location 해석 (rule → Google Places → AI fallback)
- 현재 위치 + 목적지 날씨 비교
- 시간대별 날씨 변화 분석
- 규칙 기반 판단
- AI 기반 최종 요약

---

## 4. 핵심 기능

### 1. 일정 기반 추천

- Google Calendar 연동
- 가장 가까운 일정 3개 조회

---

### 2. 위치 해석 (Google Places + AI fallback)

- "강남 회식" → "서울특별시 강남구"
- rule → Google Places → AI fallback 구조

---

### 3. 날씨 분석

- 현재 위치
- 일정 시작 시점
- 일정 종료 시점

---

### 4. 규칙 엔진 기반 판단

- 우산 필요 여부
- 옷차림 추천
- 날씨 변화 리스크

---

### 5. AI 요약

- 최종 결과를 1~2문장으로 요약
- 짧고 실용적인 스타일

---

## 5. 시스템 아키텍처

```
iOS App (SwiftUI)
        ↓
Spring Boot Backend
        ↓
 ┌───────────────┐
 │ External APIs │
 ├───────────────┤
 │ Google Calendar │
 │ Google Places   │
 │ Open-Meteo      │
 │ AI API          │
 └───────────────┘
        ↓
 ┌───────────────┐
 │ Storage       │
 ├───────────────┤
 │ MySQL (영속)   │
 │ Redis (캐시)   │
 └───────────────┘
```

---

## 6. 핵심 설계 포인트

### 6.1 AI를 “시스템 일부”로 설계

이 프로젝트는 AI를 단순 호출이 아닌

**역할이 명확한 컴포넌트**로 설계했다.

### AI 역할

1. Location 해석
- Google Places 실패/낮은 신뢰도 시 비정형 문자열 → 구조화된 지역 데이터
1. 최종 요약 생성
- 규칙 엔진 결과 → 자연어 설명

---

### 6.2 하이브리드 구조

| 영역 | 처리 방식 |
| --- | --- |
| 날씨, 시간 계산 | 코드 (Deterministic) |
| 위치 해석 | Rule + Google Places + AI fallback |
| 최종 설명 | AI |

핵심:

> **정형 데이터는 코드, 비정형 데이터는 AI**
>

---

### 6.3 캐시 전략 (Redis)

| 대상 | 목적 |
| --- | --- |
| 위치 해석 결과 | AI 비용 절감 |
| 날씨 데이터 | API 호출 감소 |
| 추천 결과 | 응답 속도 개선 |

---

## 7. 도메인 설계

### 주요 도메인

- CalendarEvent
- ResolvedLocation
- WeatherSnapshot
- OutfitDecision

---

### 데이터 흐름

```
CalendarEvent
   ↓
ResolvedLocation (rule → Google Places → AI fallback)
   ↓
WeatherSnapshot (3개)
   ↓
Rule Engine
   ↓
OutfitDecision
   ↓
AI Summary
```

---

## 8. 규칙 엔진 설계

### 우산 판단

- 종료 시점 강수 확률 ≥ 50%
- 또는 비 상태

---

### 옷차림 기준 (종료 시점 체감온도 기준)

| 온도 | 추천 |
| --- | --- |
| ≥ 23 | 반팔 |
| 20~22 | 반팔 + 얇은 겉옷 |
| 17~19 | 긴팔 |
| 13~16 | 긴팔 + 가벼운 겉옷 |
| ≤ 12 | 두꺼운 겉옷 |

---

### 보정 규칙

다음 중 하나면 옷차림 +1 단계:

- 현재 대비 -4도 이상
- 시작→종료 -3도 이상
- 저온 + 비

---

## 9. API 설계

### 주요 API

- Google OAuth 연동
- 홈 추천 조회
- 상세 추천 조회

---

### 홈 API 예시

```
{
  "title":"저녁 회식",
  "location":"서울특별시 강남구",
  "needUmbrella":true,
  "recommendedOutfitText":"반팔 + 얇은 겉옷",
  "summary":"종료 시간대 비 가능성이 있어 우산이 필요합니다."
}
```

---

## 10. 저장 구조

### MySQL

- 사용자 정보
- Google 연동 정보

---

### Redis

- 위치 해석 캐시
- 날씨 캐시
- 추천 결과 캐시

---

## 11. 테스트 전략

### 핵심 시나리오

- 종료 시점 비 발생
- 현재보다 목적지 온도 낮음
- 시간대별 온도 하락
- location 해석 실패

---

### 예외 처리

- location 없음
- 날씨 API 실패
- AI 실패
- Google 토큰 만료

---

## 12. 기술 스택

### Backend

- Java / Spring Boot
- MySQL
- Redis

### Mobile

- SwiftUI

### External

- Google Calendar API
- Google Places API
- Open-Meteo
- AI API

---

## 13. 프로젝트 특징 (핵심 어필 포인트)

### 1. AI를 시스템 일부로 설계

- 단순 호출이 아닌 역할 분리

### 2. 하이브리드 의사결정 구조

- 규칙 + AI 결합

### 3. 캐시 기반 비용 최적화

- Redis 활용

### 4. 실제 사용자 문제 해결

- 외출 준비 자동화

---

## 14. 향후 확장 (Phase 2)

- 출발 시간 추천
- 대중교통 혼잡도
- Apple Health 연동
- 개인화 옷차림 추천

---
