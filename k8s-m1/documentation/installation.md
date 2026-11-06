# Installation du cluster

## 1. Machines

| VM | IP | vCPU | RAM | Disque | OS |
|---|---|---|---|---|---|
| k8s-master | 192.168.10.10 | 2 | 4 Go | 40 Go | Ubuntu 26.04 LTS |
| k8s-worker01 | 192.168.10.11 | 2 | 4 Go | 40 Go | Ubuntu 26.04 LTS |
| k8s-worker02 | 192.168.10.12 | 2 | 4 Go | 40 Go | Ubuntu 26.04 LTS |

**Réseau.** Le LAN du cluster est 192.168.10.0/24, sur l'interface `enp0s8`. Le kubelet doit annoncer l'IP de ce LAN, et non celle de l'interface NAT :

```bash
echo "KUBELET_EXTRA_ARGS=--node-ip=192.168.10.10" | sudo tee /etc/default/kubelet   # IP propre à chaque nœud
```

**Noms d'hôtes**, sur chaque VM :

```bash
sudo hostnamectl set-hostname k8s-master      # k8s-worker01, k8s-worker02 sur les autres
cat <<EOF | sudo tee -a /etc/hosts
192.168.10.10 k8s-master
192.168.10.11 k8s-worker01
192.168.10.12 k8s-worker02
EOF
ping -c2 k8s-worker01 && ping -c2 k8s-worker02
```

La synchronisation horaire (`timedatectl`, NTP actif) est indispensable : les certificats et les jetons du cluster dépendent de l'heure.

## 2. Prérequis Kubernetes : `installation-k8s.sh`

Le script [`installation-k8s.sh`](installation-k8s.sh) se lance sur **les trois nœuds** avec `sudo ./installation-k8s.sh`. Il fait les six étapes suivantes.

1. **Désactive le swap**, maintenant et au redémarrage (dans `/etc/fstab`). Par défaut, le kubelet refuse de démarrer avec du swap : la gestion mémoire de Kubernetes (requests, limits, éviction) suppose que la RAM est la seule mémoire.
2. **Charge les modules `overlay` et `br_netfilter`.** `overlay` sert aux couches d'images. `br_netfilter` permet à iptables de voir le trafic des bridges, dont dépendent les Services et les NetworkPolicy.
3. **Règle les paramètres sysctl** :
   - `net.bridge.bridge-nf-call-iptables=1` ;
   - `net.bridge.bridge-nf-call-ip6tables=1` ;
   - `net.ipv4.ip_forward=1`, pour que le nœud route le trafic des Pods.
4. **Installe containerd 2.x** depuis le dépôt Docker, en version 2 au minimum, exigée par Kubernetes v1.37. La configuration est régénérée avec `SystemdCgroup = true` : le kubelet et le runtime doivent utiliser le même gestionnaire de cgroups (systemd), sinon les Pods sont instables.
5. **Installe kubelet, kubeadm et kubectl v1.37** depuis `pkgs.k8s.io`. Les versions sont bloquées avec `apt-mark hold`, pour éviter une mise à jour non maîtrisée.
6. **Vérifie l'installation** : versions, accès CRI à containerd, présence de `--node-ip`.

## 3. Initialisation du Control Plane (master)

L'historique des commandes n'a pas été conservé. La commande ci-dessous est l'équivalent reconstruit à partir de la configuration réelle du cluster : `kubectl -n kube-system get cm kubeadm-config`.

```bash
sudo kubeadm init \
  --apiserver-advertise-address=192.168.10.10 \
  --pod-network-cidr=10.244.0.0/16 \
  --service-cidr=10.96.0.0/12

mkdir -p $HOME/.kube
sudo cp /etc/kubernetes/admin.conf $HOME/.kube/config
sudo chown $(id -u):$(id -g) $HOME/.kube/config
```

## 4. Réseau des Pods : Calico

Calico est installé avec l'opérateur Tigera. Le fichier [`calico-custom-resources.yaml`](calico-custom-resources.yaml) fixe trois paramètres :

- le pool d'IP `10.244.0.0/16`, identique à `--pod-network-cidr` ;
- l'encapsulation `VXLANCrossSubnet` ;
- `interface: enp0s8`, pour que Calico utilise le LAN du cluster.

```bash
kubectl create -f https://raw.githubusercontent.com/projectcalico/calico/<version>/manifests/tigera-operator.yaml
kubectl create -f calico-custom-resources.yaml
kubectl get pods -n calico-system -w
```

Sans CNI, les nœuds restent `NotReady` : aucun Pod ne peut recevoir d'IP ni joindre les autres nœuds.

## 5. Ajout des Workers

Sur le master, générer la commande de jonction :

```bash
kubeadm token create --print-join-command
```

Puis la lancer sur chaque Worker :

```bash
sudo kubeadm join 192.168.10.10:6443 --token <TOKEN> --discovery-token-ca-cert-hash sha256:<HASH>
```

Enfin, sur le master :

```bash
kubectl label node k8s-worker01 k8s-worker02 node-role.kubernetes.io/worker=
kubectl get nodes -o wide        # les 3 nœuds doivent être Ready
```

## 6. Briques de plateforme

Une fois le cluster prêt, il faut installer :

- l'Ingress Controller NGINX ;
- Metrics Server ;
- la stack Prometheus/Grafana ;
- les labels et le taint des nœuds.

Ces étapes sont détaillées dans le [README](../README.md#déploiement-complet-cluster-déjà-installé).

## Rôle des composants

| Composant | Rôle |
|---|---|
| kube-apiserver | Point d'entrée unique du cluster. Il authentifie et valide toutes les requêtes, et il est le seul à lire et écrire dans etcd. |
| etcd | Base clé-valeur qui contient tout l'état du cluster. C'est l'élément à sauvegarder en priorité. |
| kube-scheduler | Choisit le nœud de chaque nouveau Pod : filtrage, puis score. |
| kube-controller-manager | Boucles de réconciliation qui ramènent l'état réel vers l'état voulu. |
| kubelet | Agent de chaque nœud. Il fait tourner les Pods attribués au nœud via le CRI, exécute les probes et remonte l'état. |
| containerd | Runtime de conteneurs : téléchargement des images, création des conteneurs (runc). |
| kube-proxy | Traduit les Services en règles iptables ou IPVS. |
| Calico | CNI : une IP par Pod, routage entre nœuds, application des NetworkPolicy. |
| kubeadm / kubectl | kubeadm installe et maintient le cluster ; kubectl est le client de l'API. |
