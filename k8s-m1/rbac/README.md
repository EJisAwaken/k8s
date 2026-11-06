# RBAC — vérification

Simuler le compte developer avec l'impersonation (`--as`) :

```bash
SA=system:serviceaccount:prod:developer
kubectl auth can-i get pods             -n prod --as=$SA   # yes
kubectl auth can-i list services        -n prod --as=$SA   # yes
kubectl auth can-i delete pods          -n prod --as=$SA   # no
kubectl auth can-i create deployments   -n prod --as=$SA   # no
kubectl auth can-i get secrets          -n prod --as=$SA   # no
kubectl auth can-i get pods             -n dev  --as=$SA   # no (Role limité à prod)
kubectl auth can-i --list               -n prod --as=$SA
```

Utiliser réellement le compte avec un jeton à durée limitée.

Piège : `kubectl --token=...` seul ne suffit PAS. Le kubeconfig admin contient un certificat
client, et l'API server authentifie d'abord par certificat : le jeton est ignoré et on reste admin.
Il faut une connexion qui ne porte que le jeton (`--kubeconfig=/dev/null`) :

```bash
SRV=$(kubectl config view --minify -o jsonpath='{.clusters[0].cluster.server}')
kubectl config view --raw --minify -o jsonpath='{.clusters[0].cluster.certificate-authority-data}' | base64 -d > /tmp/ca.crt
TOKEN=$(kubectl -n prod create token developer --duration=1h)
K="kubectl --kubeconfig=/dev/null --server=$SRV --certificate-authority=/tmp/ca.crt --token=$TOKEN -n prod"

$K auth whoami                                   # system:serviceaccount:prod:developer
$K get pods                                      # OK
$K delete pod database-0 --dry-run=server        # Forbidden
$K get secrets                                   # Forbidden
$K create deployment x --image=nginx --dry-run=server   # Forbidden
```
