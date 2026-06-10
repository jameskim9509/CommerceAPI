# ADR 007: 이메일 발송 수단 MailGun → Gmail SMTP(App Password) 전환

- 상태: 수용 (Accepted)
- 작성일: 2026-06-09
- 관련 코드: [GmailClient.java](../userApi/src/main/java/com/zerobase/userApi/service/GmailClient.java), [application.yml](../userApi/src/main/resources/application.yml), [CICD.yml](../.github/workflows/CICD.yml), [k8s/overlays/](../k8s/overlays/), [.vscode/launch.json](../.vscode/launch.json)
- 관련 이슈: #58

## 컨텍스트

회원가입(구매자·판매자) 시 이메일 검증 코드를 발송한다. 기존 구현은 **MailGun** REST API 를 **OpenFeign** 클라이언트(`MailgunClient` + `MailgunConfig`)로 호출했다.

- 자격증명: `mailgun.apiKey` / `mailgun.domain` (환경변수 `MAILGUN_APIKEY` / `MAILGUN_DOMAIN`)
- MailGun 은 **검증된 도메인**과 **유료 플랜**(샌드박스는 수신자 화이트리스트 제약)이 전제라, 개인·데모 환경에서 실제 발송이 번거롭다.
- 테스트는 `MailgunClient` 를 `@MockBean` 으로 대체했지만, `MailgunConfig` 의 `@Value("${mailgun.apiKey}")` 때문에 **단위 테스트에서도** `-Dmailgun.apiKey` / `-Dmailgun.domain` JVM 프로퍼티를 강제로 넘겨야 했다 (`build.gradle` test 블록 · CICD).

## 결정

이메일 발송을 **Gmail SMTP + 앱 비밀번호(App Password)** 기반 `JavaMailSender` 로 전환한다.

1. `spring-boot-starter-mail` 도입, MailGun/Feign 경로(`MailgunClient` · `MailgunConfig` · `@EnableFeignClients` · `spring-cloud-starter-openfeign`)를 제거한다. (userApi 의 유일한 Feign 사용처였다.)
2. `GmailClient`(`@Component`) 가 `SimpleMailMessage` 로 발송하며, 발신자(From)는 인증 계정(`spring.mail.username`)과 일치시킨다 (Gmail SMTP 제약).
3. SMTP 설정은 `application.yml`(base) 에 둔다 — host `smtp.gmail.com`, port `587`, STARTTLS. **모든 프로파일(default·test·prod)이 공유**한다.
4. 자격증명은 코드/저장소에 두지 않고 환경변수 `MAIL_USERNAME` / `MAIL_PASSWORD` 로만 주입한다. placeholder 는 빈 기본값(`${MAIL_USERNAME:}`)으로 두어, 자격증명 미설정 환경에서도 **컨텍스트 부팅은 실패하지 않고 실제 발송 시점에만** 실패하도록 한다.

### 자격증명 주입 경로

| 환경 | 주입 방법 |
|---|---|
| 로컬 (VS Code · docker compose) | 루트 `.env`(gitignore) — `launch.json` 의 `envFile` 과 docker compose 가 공유 (compose 는 미설정 시 더미 기본값) |
| 단위/통합 테스트 | 미주입 — `GmailClient` 를 `@MockBean` 으로 대체 (실제 SMTP 연결 없음) |
| Kubernetes | `commerce-secret` (test 는 더미, prod 는 `REPLACE_ME` → External Secrets 권장) |
| EC2 (레거시 CI 배포) | `docker run -e MAIL_USERNAME -e MAIL_PASSWORD` (GitHub Secrets) |

## App Password 의 로컬 관리 (노출 방지)

App Password 는 메일 발송 권한을 가지므로 **절대 커밋하지 않는다.** 로컬은 **루트 `.env` 단일 파일**로 관리하며, docker compose 와 VS Code 자바 실행이 같은 파일을 공유한다.

- `.env`(루트) — 실제 값. `.gitignore` 의 `.env` 규칙으로 커밋 차단. docker compose(루트 실행 시 자동 인식) 와 `.vscode/launch.json` 의 `envFile` 이 함께 읽는다.
- `.env.example`(루트) — 추적되는 템플릿(값 없음). 복사해 `.env` 로 채워 쓴다.
- `.vscode/launch.json` — 추적되는 실행 설정(비밀 없음, `envFile: ${workspaceFolder}/.env` 참조만).

## 결과

**장점**
- 개인 Gmail 계정만으로 즉시 실제 발송 — 외부 유료 도메인/플랜 불필요.
- userApi 에서 Feign 의존 제거 → 의존성·기동 표면 축소.
- 단위 테스트에서 mailgun JVM 프로퍼티 강제 주입 제거 (`./gradlew test` 가 메일 설정과 독립).

**트레이드오프 / 주의**
- Gmail 무료 계정은 일일 발송량 한도(약 500통)와 발신 평판 한계가 있어 **대량 운영용은 아니다** — 데모/개발에 적합. 운영 대량 발송이 필요하면 Amazon SES 등으로 재전환한다(별도 ADR).
- App Password 는 2단계 인증이 활성화된 계정에서만 발급 가능하다.
- ADR-006 의 다이어그램·Secret 표에 'Mailgun' 으로 표기된 부분은 본 ADR 이 대체한다 (ADR 은 시점 기록이므로 원문은 보존).

## 대안

- **MailGun 유지**: 도메인 검증·유료 플랜 부담이 개인 데모에 과하다.
- **Amazon SES**: 운영 확장성은 우수하나 도메인 검증·IAM 설정이 필요 — 현 단계(데모) 대비 과투자.
- **SMTP 자격증명을 `application-*.yml` 에 평문 기입**: 노출 위험으로 기각하고 환경변수 외부화를 채택했다.
