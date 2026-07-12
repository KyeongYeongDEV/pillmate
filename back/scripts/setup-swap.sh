#!/usr/bin/env bash
# Oracle A1 12GB — 배포 피크(~10-11g/12g) OOM-kill 방지용 swap. 서버 1회 실행.
# 사용: sudo ./setup-swap.sh [크기]   (기본 8G)
set -euo pipefail

SIZE="${1:-8G}"

if [ -f /swapfile ]; then
    echo "swapfile 이미 존재 — skip"
    free -h
    exit 0
fi

echo "[1/5] ${SIZE} swapfile 생성"
sudo fallocate -l "$SIZE" /swapfile

echo "[2/5] 권한 설정 (600)"
sudo chmod 600 /swapfile

echo "[3/5] swap 영역 초기화 + 활성화"
sudo mkswap /swapfile
sudo swapon /swapfile

echo "[4/5] /etc/fstab 등록 (재부팅 후 유지)"
if ! grep -q '^/swapfile ' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo "[5/5] swappiness=10 (물리램 우선, swap 은 스파이크 완충만)"
sudo sysctl vm.swappiness=10
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-swappiness.conf

echo "완료:"
free -h
