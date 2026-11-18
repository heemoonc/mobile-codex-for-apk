# Android library simulation / Android 라이브러리 모의 검사

태블릿의 Linux ARM64 환경에서 Room·DataStore·로컬 HTTP 동작을 검증하는 별도 테스트 프로젝트입니다. 실제 Android OS 부팅이나 사용자 앱 전체 호환성 검사가 아닙니다. 임시 DB·파일과 루프백 HTTP 서버만 사용하며 테스트가 만든 자원만 정리합니다.

```sh
sh tools/android-library-simulation/run.sh 29,35
# 모든 지원 API
sh tools/android-library-simulation/run.sh 29,30,31,32,33,34,35
```

기본 도구 경로는 `/opt/android/jdk`, `/opt/android/sdk`, `/opt/android/bin/aapt2`입니다. 다른 환경에서는 `JAVA_HOME`, `ANDROID_HOME`, `AAPT2`를 지정하십시오. 첫 실행에는 Gradle·라이브러리·Android 모의 런타임 다운로드가 필요합니다. SDK 약관 수락은 사용자가 직접 진행합니다.

| 범위 | 검사 |
|---|---|
| Room 2.7.2 + KSP | CRUD, Flow, 트랜잭션 롤백, 재열기, v1→v2 마이그레이션, 누락 마이그레이션 거부·원본 보존 |
| DataStore 1.1.7 | 쓰기·읽기, 재열기, 동시 원자 갱신, 자료형 보존 |
| OkHttp/MockWebServer 4.12.0 | 로컬 HTTP 성공 응답·본문·경로, 503 실패 응답 |

Robolectric 4.18과 JDK 17을 사용합니다. API 29~35를 선택할 수 있으며 각 API의 실행 결과를 직접 확인해야 합니다. 이 검사 결과는 개발 도우미의 기본 모의 검사 행렬에 자동으로 가져오지 않습니다.

Run the commands above in the tablet Linux environment. This separate test project checks Room persistence/migrations, DataStore consistency and local HTTP success/failure responses using Robolectric 4.18. It does not boot Android or validate a whole application. Results are not automatically imported into the assistant baseline matrix. Only test-owned temporary data is cleaned up. Initial execution requires downloads; set JAVA_HOME, ANDROID_HOME and AAPT2 when using non-default tool paths.

Kotlin의 JVM 의존성 치환과 충돌하는 `kotlin-stdlib-common` 메타데이터 모듈만 버전 잠금에서 제외합니다. 해당 모듈의 파일 해시 검증은 유지합니다. / Only Kotlin common metadata is excluded from version locking to accommodate JVM substitution; artifact checksum verification remains enabled.
