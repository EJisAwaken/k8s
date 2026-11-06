# CI/CD : test → build → push → deploy

Pipeline : [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).

```
push sur main ───► test-frontend (lint, types, build) + test-backend (mvn verify + PostgreSQL)

release vX.Y.Z ──► tests ──► build-push ──────────────────────► deploy (runner sur k8s-master)
                              ghcr.io/ejisawaken/stock-frontend:vX.Y.Z   helm upgrade application
                              ghcr.io/ejisawaken/stock-backend:vX.Y.Z      --set *.image.tag=vX.Y.Z
                              (registry privé, GITHUB_TOKEN)                --rollback-on-failure
                                                                           helm test application
```

| Étape | Où | Détail |
|---|---|---|
| test | runners GitHub | Lint, typage et build Next.js ; `mvn verify` du backend avec un vrai PostgreSQL |
| build | runners GitHub | Images Docker (`target: production`), cache de couches dans le registry |
| push | ghcr.io (privé) | Tag = tag de la release (`v2.1.0`) : version traçable et immuable, plus de `:latest` |
| deploy | runner auto-hébergé `k8s-master` | `helm upgrade` avec le tag, retour arrière automatique si les Pods ne deviennent pas prêts, puis `helm test` |

## Pourquoi un runner auto-hébergé

Le cluster est sur un LAN privé (192.168.10.0/24) : les runners de GitHub ne peuvent pas le joindre. Le runner installé sur k8s-master fonctionne dans l'autre sens. Il ouvre lui-même une connexion **sortante** vers GitHub et récupère les jobs qui portent le label `k8s-deploy`. Aucun port n'est ouvert vers le cluster.

**Remplacement de Keel.** Keel redéployait les images `:latest` en interrogeant le registry. Il a été retiré, pour trois raisons :

- **Pas de version** : avec `:latest`, impossible de savoir ce qui tourne.
- **Historique Helm faux** : Keel modifiait les Deployments derrière Helm, donc un `helm rollback` ne ramenait pas l'ancienne image.
- **Pas de trace** : le déploiement n'apparaissait pas dans le pipeline.

## Sécurité

- **Compte Kubernetes dédié** : `ci-deployer` ([`ci-deployer-rbac.yml`](ci-deployer-rbac.yml)) n'a de droits que dans le namespace `prod`, et seulement sur les ressources du chart. Il n'a aucun droit sur les nœuds, `kube-system` ou les autres namespaces. Son kubeconfig (`~/.kube/ci-deployer.config`, généré par [`kubeconfig-ci.sh`](kubeconfig-ci.sh)) ne contient que son jeton, pas le certificat admin.
- **Dépôt public et runner auto-hébergé** : une pull request venant d'un fork pourrait exécuter du code sur le runner. Trois protections :
  - le workflow ne réagit pas aux `pull_request` ;
  - les workflows des contributeurs extérieurs exigent une approbation (`fork-pr-contributor-approval = all_external_contributors`) ;
  - le runner n'accepte que les jobs portant le label `k8s-deploy`.
- **Registry privé** : le cluster tire les images avec le Secret `ghcr-pull` (type `docker-registry`). Il contient un jeton personnel GitHub limité à `read:packages`, et il est créé hors du dépôt.
- **Accès au registry depuis la CI** : la CI pousse avec le `GITHUB_TOKEN` éphémère du workflow (`permissions: packages: write`). Aucun mot de passe de registry n'est stocké.

## Mise en place (une seule fois, sur k8s-master)

```bash
# 1. Compte de déploiement et kubeconfig du runner
kubectl apply -f k8s-m1/cicd/ci-deployer-rbac.yml
./k8s-m1/cicd/kubeconfig-ci.sh

# 2. Accès du cluster au registry privé (jeton GitHub "classic", scope read:packages uniquement)
read -rs GHCR_TOKEN
kubectl -n prod create secret docker-registry ghcr-pull \
  --docker-server=ghcr.io --docker-username=EJisAwaken --docker-password="$GHCR_TOKEN"
unset GHCR_TOKEN

# 3. Runner GitHub Actions (jeton d'enregistrement : Settings > Actions > Runners > New runner)
mkdir ~/actions-runner && cd ~/actions-runner
curl -fsSL -o runner.tgz https://github.com/actions/runner/releases/download/v2.338.2/actions-runner-linux-x64-2.338.2.tar.gz
tar xzf runner.tgz && rm runner.tgz
./config.sh --url https://github.com/EJisAwaken/k8s --token <JETON> --name k8s-master --labels k8s-deploy --unattended
sudo ./svc.sh install josefa && sudo ./svc.sh start     # service systemd : redémarre avec la VM
```

## Livrer une version

```bash
gh release create v2.1.0 --title "v2.1.0" --notes "..."    # ou depuis l'interface GitHub
gh run watch                                               # suivre le pipeline
helm history application -n prod                           # la révision porte le tag déployé
```

**Retour arrière manuel** : `helm rollback application <révision> -n prod`. L'ancienne image versionnée est toujours dans ghcr.io.
