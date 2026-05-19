## 관련 이슈
<!-- 예: Closes #123 / Refs #456 -->


## 변경 유형
<!-- 해당되는 항목 모두 체크 -->
- [ ] 🐞 Bug fix
- [ ] ✨ Feature
- [ ] 🛠 Refactor / Chore
- [ ] 🗄 DB 마이그레이션 (Flyway V_*.sql 추가)
- [ ] 📝 Docs

## 요약
<!-- 무엇을 / 왜 바꿨는지 2~3줄로 -->


## 영향 받는 모듈
- [ ] userApi
- [ ] orderApi
- [ ] gateway
- [ ] infra (docker-compose, CI, etc.)

## 동작 확인
<!-- 어떻게 검증했는지 (단위테스트 / 통합테스트 / docker compose / 수동 호출 등) -->


## 체크리스트
- [ ] 단위 / 통합 테스트 통과 (`./gradlew test`)
- [ ] DB 스키마 변경이 있다면 Flyway 마이그레이션 (`V_*.sql`) 추가
- [ ] 두 모듈에 공유되는 DTO/Authority 등 변경 시 양쪽 모두 반영
- [ ] 운영 yaml(`application.yml`)에 추가된 설정이 있다면 `application-test.yml` 및 `docker-compose.test.yml`에도 반영
- [ ] 외부 API 추가/변경 시 Swagger 문서 갱신

## 스크린샷 / 로그 (선택)
<!-- API 응답, UI 변경 등 첨부 -->
