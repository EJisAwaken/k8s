# Rapport d'incidents

Incidents réellement rencontrés pendant la construction de la plateforme. Format demandé : symptôme, hypothèses, diagnostic, preuve, correction, vérification, cause racine.

## Incident n°1 – 3 403 Pods tigera-operator en échec

- **Symptôme** : kubectl get pods -A affichait des milliers de Pods tigera-operator en ContainerStatusUnknown/Evicted.
- **Hypothèses** : crash de l'opérateur Calico, manque de mémoire, disque saturé.
- **Diagnostic** : kubectl get pods -n tigera-operator --no-headers | awk '{print $3}' | sort | uniq -c, puis kubectl get pod <pod> -o jsonpath='{.status.reason} {.status.message} {.spec.nodeName}'
- **Preuve** : « Evicted | The node had condition: [DiskPressure] | k8s-worker02 ».
- **Cause racine** : le disque de k8s-worker02 a dépassé le seuil d'éviction du kubelet. Le kubelet évince alors les Pods ; le ReplicaSet en recréait aussitôt un, qui était évincé à son tour, en boucle.
- **Correction** : la place disque est revenue ; suppression des Pods morts (kubectl delete pods -n tigera-operator --field-selector=status.phase=Failed).
- **Vérification** : un seul Pod tigera-operator Running, aucun nœud en DiskPressure.
- **Prévention** : la base et ses volumes ont été placés sur worker01, et l'espace disque est surveillé dans Grafana (node-exporter).

## Incident n°2 – Données perdues après le redémarrage des VMs

- **Symptôme** : après un redémarrage des VMs, l'application repartait avec une base vide.
- **Diagnostic** : kubectl get deploy database-deployment -o yaml : aucun volume monté sur /var/lib/postgresql/data.
- **Cause racine** : la base tournait en Deployment sans PVC. Les données étaient dans la couche inscriptible du conteneur, qui disparaît avec lui.
- **Correction** : migration vers un StatefulSet avec volumeClaimTemplates (PV de 5Gi) et restauration des données par pg_dump/psql ; ajout d'une sauvegarde quotidienne (CronJob).
- **Vérification** : suppression de database-0, qui est recréé avec les mêmes données (section 8.5).

## Incident n°3 – Grafana en redémarrage permanent à l'installation

- **Symptôme** : helm install échoue (« monitoring-grafana not ready ») ; le Pod reste 2/3 et redémarre en boucle.
- **Hypothèses** : image introuvable, configuration invalide, mémoire insuffisante, probes trop strictes.
- **Diagnostic** : kubectl describe pod (Events) et kubectl logs -c grafana.
- **Preuve** : « Liveness probe failed: connection refused » puis « Container grafana failed liveness probe, will be restarted ». Les logs montraient que Grafana était encore en train d'initialiser ses modules.
- **Cause racine** : au premier démarrage, Grafana 13 met plusieurs minutes à exécuter ses migrations. La livenessProbe par défaut (60 s + 10 x 10 s) le tuait avant la fin, et il recommençait de zéro.
- **Correction** : livenessProbe avec initialDelaySeconds: 300 et limite mémoire de 512Mi (values Helm), puis helm upgrade.
- **Vérification** : Pod 3/3 Running, 0 redémarrage, dashboard accessible.

## Incident n°4 – Les deux backends sur le même nœud après un rollout

- **Symptôme** : après une mise à jour du Deployment backend, les 2 replicas étaient sur k8s-worker02, malgré une topologySpreadConstraint stricte.
- **Diagnostic** : kubectl -n prod get pods -l app=backend -o wide pendant le rollout.
- **Cause racine** : la contrainte compte tous les Pods app=backend, y compris l'ancien Pod encore présent sur worker01 pendant le rollout. Les deux nouveaux Pods sont donc placés sur worker02, puis l'ancien est supprimé.
- **Correction** : matchLabelKeys: [pod-template-hash], pour ne compter que les Pods de la même révision.
- **Vérification** : nouveau rollout, un backend par Worker.

## Incident n°5 – HPA : Deployment ramené à 1 replica

- **Symptôme** : en retirant replicas: du manifest pour laisser la main à l'HPA, le Deployment est passé à 1 replica pendant quelques secondes.
- **Cause racine** : kubectl apply supprime un champ retiré du fichier depuis le dernier apply : replicas reprend sa valeur par défaut (1) jusqu'à ce que l'HPA corrige.
- **Leçon** : créer l'HPA avant de retirer replicas, ou retirer le champ avec kubectl apply --server-side.

## Incident n°6 – Première tentative de sauvegarde en échec

- **Symptôme** : le Job de sauvegarde a réussi, mais avec 1 redémarrage, et un fichier .dump.tmp vide est resté dans /backups.
- **Diagnostic** : kubectl get pod -l job-name=... (RESTARTS 1), puis kubectl get pod -o jsonpath='{.status.containerStatuses[0].lastState}' : exitCode 1 au bout de 5 s. Les logs de la tentative ratée n'étaient plus disponibles.
- **Hypothèse retenue** : au démarrage d'un nouveau Pod, Calico n'a pas encore autorisé son IP vers la base (NetworkPolicy). La connexion de pg_dump échoue alors, et restartPolicy: OnFailure relance le conteneur, qui réussit.
- **Correction** : le script attend la base (pg_isready, jusqu'à 60 s), supprime son .tmp en cas d'échec (trap) et nettoie les anciens .tmp.
- **Vérification** : nouvelle sauvegarde sans redémarrage, .tmp supprimé (capture 9.5).

## Incident n°7 – Helm 4 : upgrade refusé pour conflit de propriété

- **Symptôme** : helm install, puis helm upgrade, échouent : « conflict occurred while applying object prod/app-config: Apply failed with 1 conflict: conflict with kubectl-client-side-apply: .data.LOG_LEVEL ».
- **Cause racine** : Helm 4 applique en server-side apply (SSA). Le serveur mémorise, champ par champ, quel gestionnaire l'a posé (metadata.managedFields). Les objets avaient été créés par kubectl apply : leurs champs appartenaient à kubectl-client-side-apply, et le serveur refuse qu'un autre gestionnaire en change la valeur.
- **Fausse piste** : effacer les managedFields. Le serveur attribue alors les champs à un propriétaire fictif « before-first-apply », et le conflit revient.
- **Correction** : utiliser l'application côté client pour cette release (helm upgrade --server-side=false, comportement de Helm 3 : fusion ancien manifeste / nouveau manifeste / état réel). La méthode est mémorisée dans la release (helm get metadata : APPLY_METHOD: client-side apply) : les upgrades et rollbacks suivants n'ont plus besoin de l'option.
- **Vérification** : upgrade (révision 8) et rollback (révision 9) réussis.
- **Leçon** : pour une nouvelle reprise d'objets, enchaîner directement helm/adopter.sh puis helm install --server-side=false (procédure documentée dans le script). Pendant tous ces échecs, l'application n'a jamais été coupée : un upgrade raté laisse les objets en place, et helm rollback ramène un état connu.

## Incident n°8 – Quelques erreurs 503 à chaque redéploiement

- **Symptôme** : une boucle de requêtes (1 par seconde) pendant un upgrade puis un rollback : 442 réponses 200 sur l'application, mais 2 réponses 503 sur l'API.
- **Cause racine** : quand un ancien Pod backend reçoit SIGTERM, Spring commence à s'arrêter immédiatement, alors que la suppression de ses endpoints se propage de façon asynchrone (EndpointSlice, kube-proxy, contrôleur Ingress). Pendant ce court instant, l'Ingress envoie encore des requêtes à un Pod qui n'accepte plus de connexions.
- **Correction** : lifecycle.preStop qui attend 10 s (sleep 10) sur le backend et le frontend : le Pod continue de servir pendant que le trafic est détourné, puis s'arrête proprement.
- **Vérification** : mesure renouvelée à 2 requêtes par seconde pendant un upgrade complet (les deux backends remplacés) et le début du rollback : 600 réponses 200 sur 600, aucune erreur.

## Incident n°9 – Échec du build de la première release

- **Symptôme** : release v2.1.0, les deux jobs build-push échouent en quelques secondes ; le job deploy est ignoré.
- **Diagnostic** : gh run view <id> --log-failed.
- **Preuve** : « invalid reference format: repository name (EJisAwaken/stock-frontend) must be lowercase ».
- **Cause racine** : les noms de dépôt Docker doivent être en minuscules. docker/metadata-action convertit le nom de l'image, mais les références du cache buildx étaient construites directement avec github.repository_owner (EJisAwaken).
- **Correction** : une étape calcule une seule fois le nom en minuscules (${GITHUB_REPOSITORY_OWNER,,}), réutilisé pour les tags et le cache. La release v2.1.0, qui n'avait produit aucun artefact, a été recréée sur le commit corrigé.
- **Vérification** : pipeline complet au vert (tests, build-push x2, deploy) ; production sur ghcr.io/ejisawaken/stock-*:v2.1.0.

## Incident n°10 – Images du registry accessibles sans authentification

- **Symptôme** : le cluster télécharge les images, mais un test d'accès anonyme au manifeste répond HTTP 200.
- **Cause racine** : ghcr.io donne aux nouveaux paquets la visibilité du dépôt GitHub, qui est public. La visibilité d'un paquet ne se change que dans l'interface GitHub, pas par l'API.
- **Statut** : accepté pour l'instant (dépôt et images publics). Correction prévue : passer les paquets en Private, puis vérifier qu'un accès anonyme répond 401 et que le cluster tire bien les images grâce à ghcr-pull.
- **Leçon** : un téléchargement réussi ne prouve pas que l'authentification fonctionne. Il faut aussi tester le cas négatif (sans jeton).

## Incident n°11 (simulé) – Règle d'Ingress erronée

Voir 8.3 : 503 ; la règle vise frontend:8888 alors que le Service n'expose que le port 80 ; le journal du contrôleur montre l'upstream prod-frontend-8888 ; correction par le manifest versionné.

## Méthode de diagnostic utilisée

- ImagePullBackOff : kubectl describe pod (Events : nom ou tag de l'image, authentification au registry, imagePullSecrets).
- CrashLoopBackOff : kubectl logs <pod> --previous, kubectl describe pod (exit code, OOMKilled, probes).
- Pod Pending : kubectl describe pod. Les Events donnent la raison nœud par nœud : requests insuffisantes, taint non toléré, affinité, PVC non lié.
- Service inaccessible : kubectl get endpointslices (le selector correspond-il aux labels des Pods ?), readiness des Pods, NetworkPolicy, règles de l'Ingress.
- Pod Running mais trafic impossible : readiness (0/1), port du Service et targetPort, DNS (nslookup depuis un Pod), NetworkPolicy (test-flux.sh).
- HPA inactif : kubectl get apiservice v1beta1.metrics.k8s.io, kubectl top pods, requests.cpu présent, kubectl describe hpa (Conditions).
