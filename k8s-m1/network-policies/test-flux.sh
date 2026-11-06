#!/usr/bin/env bash
# Matrice de tests des flux réseau du namespace prod.
# À lancer avant puis après "kubectl apply -f network-policies/".
NS=prod
t() { # $1 libellé, $2 namespace du Pod source, $3 Pod source, $4 hôte, $5 port, $6 attendu
  if kubectl -n "$2" exec "$3" -- nc -z -w 3 "$4" "$5" >/dev/null 2>&1; then r=OUVERT; else r=BLOQUE; fi
  [[ $r == "$6" ]] && ok="ok" || ok="!! inattendu"
  printf '%-34s %-7s (attendu %-7s) %s\n' "$1" "$r" "$6" "$ok"
}
FE=$(kubectl -n $NS get pod -l app=frontend -o name | head -1)
BE=$(kubectl -n $NS get pod -l app=backend  -o name | head -1)
kubectl -n default run netpol-test --image=busybox:1.37 --restart=Never -- sleep 300 >/dev/null 2>&1
kubectl -n default wait --for=condition=Ready pod/netpol-test --timeout=60s >/dev/null

t "frontend -> backend:8080"          $NS "$FE" backend-service 8080 OUVERT
t "backend  -> database:5432"         $NS "$BE" database        5432 OUVERT
t "frontend -> database:5432"         $NS "$FE" database        5432 BLOQUE
t "autre namespace -> backend:8080"   default netpol-test backend-service.prod 8080 BLOQUE
t "autre namespace -> database:5432"  default netpol-test database.prod        5432 BLOQUE
t "autre namespace -> frontend:80"    default netpol-test frontend.prod        80   BLOQUE

kubectl -n default delete pod netpol-test --now >/dev/null 2>&1
