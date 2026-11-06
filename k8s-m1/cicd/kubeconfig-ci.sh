#!/usr/bin/env bash
# Génère ~/.kube/ci-deployer.config : kubeconfig du runner, qui ne porte QUE le jeton
# du compte ci-deployer (aucun certificat admin). À lancer sur k8s-master.
set -euo pipefail
OUT=${1:-$HOME/.kube/ci-deployer.config}
SRV=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
CA=$(kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}')
for i in $(seq 1 10); do
  TOKEN=$(kubectl -n prod get secret ci-deployer-token -o jsonpath='{.data.token}' | base64 -d)
  [[ -n "$TOKEN" ]] && break; sleep 1
done
umask 077
cat > "$OUT" <<KCFG
apiVersion: v1
kind: Config
clusters:
  - name: k8s-m1
    cluster:
      server: $SRV
      certificate-authority-data: $CA
users:
  - name: ci-deployer
    user:
      token: $TOKEN
contexts:
  - name: ci-deployer@k8s-m1
    context: { cluster: k8s-m1, user: ci-deployer, namespace: prod }
current-context: ci-deployer@k8s-m1
KCFG
echo "kubeconfig écrit : $OUT"
KUBECONFIG="$OUT" kubectl auth whoami | grep Username
