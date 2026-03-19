# Particle Navigation

Minecraft 1.21.11 Fabric 클라이언트 모드 — 3D A* 경로탐색 + 파티클 네비게이션

## 기능

- 목표 좌표까지 3D A* 알고리즘으로 최적 경로 계산
- 초록색 파티클로 확정 경로, 노란색 파티클로 추정 경로 표시
- 빨간색 비컨 기둥으로 목표 지점 표시
- 액션바에 방향 화살표(↑↗→↘↓↙←↖) + 거리 실시간 표시
- 새 청크 로드 시 자동 경로 재계산
- 경로 이탈 시 자동 재탐색
- 점프(+1), 낙하(-3), 사다리/덩굴/수중 이동 지원

## 명령어

```
/nav set <x> <y> <z>   목표 좌표 설정 및 네비게이션 시작
/nav stop              네비게이션 종료
/nav info              현재 목표 정보 표시
```

## 설치

### 필수 요구사항

- Minecraft 1.21.11
- Fabric Loader 0.18+
- Fabric API

### 설치 방법

1. [Fabric Installer](https://fabricmc.net/use/installer/)로 Fabric Loader 설치
2. `mods/` 폴더에 넣기:
   - [Fabric API](https://modrinth.com/mod/fabric-api) jar
   - `particle-navigation-1.0.0.jar` (Releases에서 다운로드)
3. 런처에서 Fabric 프로필로 실행

## 빌드

```bash
./gradlew build
```

결과물: `build/libs/particle-navigation-1.0.0.jar`

## 청크 로딩 문제 해결 방식

로드되지 않은 청크의 블록 정보는 알 수 없으므로, 계층적 점진 탐색 방식을 사용합니다:

1. 로드된 청크 범위 내에서 정밀 3D A* 경로 탐색 (초록색)
2. 미로드 구간은 직선 추정 경로로 표시 (노란색)
3. 플레이어 이동으로 새 청크가 로드되면 자동 재계산
4. A*는 틱당 3ms 시간 분할 실행으로 렉 방지

## 라이선스

MIT
