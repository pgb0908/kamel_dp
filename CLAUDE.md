# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Project: Integration Solution (Kaoto UI Control Plane + Custom Camel Data Plane)

## Overview
Apache Camel 기반의 Integration 솔루션입니다.
- **Control Plane**: Kaoto UI 온라인(red.ht/kaoto)으로 YAML 라우트를 시각적으로 관리합니다. Docker 설치 불필요.
- **Data Plane**: 직접 구축한 Camel Quarkus Runtime으로 Kaoto가 생성한 라우트를 실행합니다.

## Commands (Build & Run)
```bash
# Build
./mvnw clean package

# Run Data Plane (Quarkus dev mode with hot reload)
./mvnw quarkus:dev

# Run a single test
./mvnw test -Dtest=TestClassName
```

## Kaoto UI Workflow
1. 브라우저에서 Kaoto UI(red.ht/kaoto) 접속
2. YAML 라우트를 시각적으로 편집
3. 편집 완료 후 YAML 텍스트를 수동으로 복사
4. 로컬 프로젝트 `routes/` 디렉토리에 `*.camel.yaml` 파일로 저장
5. Quarkus dev mode가 변경 감지 후 자동 리로드

## Architecture

### Two-Plane Design
- **Kaoto UI (온라인)**: Visual YAML route editor at red.ht/kaoto. 편집 후 YAML을 수동으로 로컬 `routes/`에 저장.
- **Quarkus Runtime**: Loads and executes all `*.camel.yaml` files from the `routes/` directory at startup and on hot reload.

### Route Directory
라우트 파일은 프로젝트 루트의 `routes/` 디렉토리에 저장합니다 (Maven 비표준 경로).

Quarkus가 런타임에 파일시스템에서 직접 참조하도록 `application.properties`에 다음 설정이 필요합니다:
```
camel.main.routes-include-pattern=file:routes/*.camel.yaml
```
`file:` 프로토콜을 사용하여 런타임에 파일시스템을 직접 참조합니다.

### Route Definition — YAML Only
모든 Camel 라우트는 반드시 **YAML DSL** (`*.camel.yaml`)로 작성합니다. Java DSL(`RouteBuilder` 상속)은 라우트 정의에 사용하지 않습니다. 이는 Kaoto UI와의 양방향 호환성을 위한 핵심 제약입니다.

### Business Logic — Java Beans/Processors
복잡한 변환, DB 처리 등의 로직은 `src/main/java/com/mycompany/integration/`에 Java Bean 또는 Processor로 구현하고, YAML 라우트에서 `ref` 이름으로 호출합니다.

```yaml
# YAML에서 Bean 호출 예시
- to:
    uri: "bean:orderValidator"
```

## Key Conventions
- **Route files**: `kebab-case` (e.g., `order-process-route.camel.yaml`) in `routes/` (프로젝트 루트)
- **Java Beans**: `camelCase` (e.g., `orderValidator`) in `src/main/java/com/mycompany/integration/`
- **Error handling**: `onException` 블록을 YAML 라우트 상단 또는 공통 YAML 파일로 분리하여 정의
- **Logging**: YAML 내에서는 Camel `log` 컴포넌트, Java 코드에서는 Slf4j 사용

## Tech Stack
- Java 17+
- Apache Camel 4.x (YAML DSL)
- Quarkus (Camel Quarkus extension)
- Kaoto UI (온라인, red.ht/kaoto) — 설치 불필요
- Maven
