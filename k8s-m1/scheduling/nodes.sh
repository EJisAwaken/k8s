#!/usr/bin/env bash
# Labels et taint des nœuds : ce ne sont pas des objets déclaratifs, ce script les rend reproductibles.
set -euo pipefail

# workload=app : nœuds autorisés pour l'application (cible de la nodeAffinity).
kubectl label node k8s-worker01 k8s-worker02 workload=app --overwrite

# storage=db : nœud qui porte le disque de la base (cible du nodeSelector de Postgres).
kubectl label node k8s-worker01 storage=db --overwrite

# worker02 est réservé à la production : seuls les Pods qui tolèrent ce taint y sont planifiés.
# NoSchedule n'expulse pas les Pods déjà présents (NoExecute le ferait).
kubectl taint node k8s-worker02 dedicated=prod:NoSchedule --overwrite

kubectl get nodes -L workload,storage
kubectl describe node k8s-worker02 | grep -A1 Taints
