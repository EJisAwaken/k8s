#!/usr/bin/env bash
# Fait "adopter" par Helm les objets déjà créés avec kubectl apply (migration sans coupure).
#
# Helm refuse d'écraser un objet qui ne porte pas ses métadonnées de propriété ; on les ajoute,
# puis "helm install" reprend les objets existants au lieu de les recréer.
#
# Usage :
#   ./adopter.sh [release] [namespace]
#   helm install application ./application -n prod --server-side=false
#
# Pourquoi --server-side=false : Helm 4 applique par défaut en server-side apply. Les champs posés
# par "kubectl apply" appartiennent au gestionnaire kubectl-client-side-apply, et toute
# modification par Helm est refusée (conflit). En client-side apply (comportement de Helm 3),
# Helm fusionne ancien manifeste / nouveau manifeste / état réel sans tenir compte de ces
# propriétaires. La méthode est mémorisée dans la release (helm get metadata : APPLY_METHOD) :
# les "helm upgrade" et "helm rollback" suivants n'ont plus besoin de l'option.
set -euo pipefail
REL=${1:-application}; NS=${2:-prod}
OBJETS="
configmap/app-config secret/app-secret
service/database service/database-headless service/backend-service service/frontend
statefulset/database deployment/backend deployment/frontend
hpa/backend hpa/frontend ingress/stock cronjob/db-backup
networkpolicy/default-deny-ingress networkpolicy/allow-to-frontend
networkpolicy/allow-to-backend networkpolicy/allow-backend-to-database
serviceaccount/developer role/pod-service-reader rolebinding/developer-pod-service-reader
"
for o in $OBJETS; do
  kubectl -n "$NS" label "$o" app.kubernetes.io/managed-by=Helm --overwrite >/dev/null
  kubectl -n "$NS" annotate "$o" meta.helm.sh/release-name="$REL" meta.helm.sh/release-namespace="$NS" --overwrite >/dev/null
  echo "adopté : $o"
done
# L'ancien Job db-init (créé par kubectl) est remplacé par le hook Helm du même nom.
kubectl -n "$NS" delete job db-init --ignore-not-found
