#!/usr/bin/env bash
# install-k8s-prereqs.sh — Prépare un nœud Ubuntu pour kubeadm (Kubernetes v1.37)
# - swap désactivé, modules noyau, sysctl
# - containerd 2.x (dépôt Docker, paquet containerd.io) + SystemdCgroup
# - kubelet, kubeadm, kubectl v1.37 (dépôt pkgs.k8s.io), versions bloquées
# Usage : sudo ./install-k8s-prereqs.sh
set -euo pipefail

K8S_VERSION="v1.37"
export DEBIAN_FRONTEND=noninteractive

log()  { echo -e "\n\033[1;34m==> $*\033[0m"; }
ok()   { echo -e "\033[1;32m OK \033[0m $*"; }
warn() { echo -e "\033[1;33m !! \033[0m $*"; }
die()  { echo -e "\033[1;31m ERREUR \033[0m $*"; exit 1; }

[[ $EUID -eq 0 ]] || die "lance ce script avec sudo."
grep -qi ubuntu /etc/os-release || die "ce script est prévu pour Ubuntu."

# ---------------------------------------------------------------- 1. Swap
log "1/6 Désactivation du swap"
swapoff -a
sed -ri '/^[^#].*\sswap\s/ s/^/#/' /etc/fstab
[[ -z "$(swapon --show)" ]] && ok "swap désactivé" || die "le swap est encore actif"

# ---------------------------------------------------------------- 2. Modules noyau
log "2/6 Modules noyau overlay et br_netfilter"
cat > /etc/modules-load.d/k8s.conf <<EOF
overlay
br_netfilter
EOF
modprobe overlay
modprobe br_netfilter
ok "modules chargés"

# ---------------------------------------------------------------- 3. Sysctl
log "3/6 Paramètres réseau du noyau"
cat > /etc/sysctl.d/k8s.conf <<EOF
net.bridge.bridge-nf-call-iptables  = 1
net.bridge.bridge-nf-call-ip6tables = 1
net.ipv4.ip_forward                 = 1
EOF
sysctl --system >/dev/null
ok "ip_forward=$(sysctl -n net.ipv4.ip_forward)"

# ---------------------------------------------------------------- 4. containerd 2.x
log "4/6 Installation de containerd 2.x (dépôt Docker)"

# Supprimer les anciennes déclarations de dépôts (évite l'erreur "Signed-By en conflit")
clean_sources() {
  local pattern="$1" keep="$2" f
  for f in $(grep -rl "$pattern" /etc/apt/sources.list /etc/apt/sources.list.d/ 2>/dev/null || true); do
    [[ "$f" == "$keep" ]] && continue
    if [[ "$f" == /etc/apt/sources.list ]]; then
      sed -i "/${pattern//./\\.}/ s/^/# /" "$f"
      warn "ligne $pattern commentée dans $f"
    else
      rm -f "$f"
      warn "ancienne source supprimée : $f"
    fi
  done
}
clean_sources "download.docker.com" "/etc/apt/sources.list.d/docker.list"
clean_sources "pkgs.k8s.io" "/etc/apt/sources.list.d/kubernetes.list"
rm -f /etc/apt/keyrings/docker.gpg

apt-get update -q
apt-get install -y -q apt-transport-https ca-certificates curl gpg

# Retirer les paquets Ubuntu qui entrent en conflit (containerd 1.x)
for p in containerd runc docker.io; do
  if dpkg -s "$p" &>/dev/null; then
    warn "suppression du paquet Ubuntu '$p'"
    apt-get remove -y -q "$p"
  fi
done

install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
CODENAME=$(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${CODENAME} stable" \
  > /etc/apt/sources.list.d/docker.list

apt-get update -q
apt-get install -y -q containerd.io

CTR_VER=$(containerd --version | awk '{print $3}' | sed 's/^v//')
CTR_MAJOR=${CTR_VER%%.*}
[[ "$CTR_MAJOR" -ge 2 ]] || die "containerd $CTR_VER installé, Kubernetes $K8S_VERSION exige containerd 2.x"
ok "containerd $CTR_VER"

# Config propre (la config livrée par containerd.io désactive le plugin CRI)
mkdir -p /etc/containerd
[[ -f /etc/containerd/config.toml ]] && cp /etc/containerd/config.toml /etc/containerd/config.toml.bak
containerd config default > /etc/containerd/config.toml
sed -i 's/SystemdCgroup = false/SystemdCgroup = true/' /etc/containerd/config.toml

systemctl daemon-reload
systemctl enable --now containerd
systemctl restart containerd

if grep -q 'SystemdCgroup = true' /etc/containerd/config.toml; then
  ok "SystemdCgroup = true"
else
  warn "ligne SystemdCgroup introuvable dans /etc/containerd/config.toml : vérifie la section runc.options"
fi

# ---------------------------------------------------------------- 5. kubelet / kubeadm / kubectl
log "5/6 Installation de kubelet, kubeadm, kubectl ${K8S_VERSION}"
curl -fsSL "https://pkgs.k8s.io/core:/stable:/${K8S_VERSION}/deb/Release.key" \
  | gpg --dearmor --yes -o /etc/apt/keyrings/kubernetes-apt-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/kubernetes-apt-keyring.gpg] https://pkgs.k8s.io/core:/stable:/${K8S_VERSION}/deb/ /" \
  > /etc/apt/sources.list.d/kubernetes.list

apt-mark unhold kubelet kubeadm kubectl >/dev/null 2>&1 || true
apt-get update -q
apt-get install -y -q kubelet kubeadm kubectl
apt-mark hold kubelet kubeadm kubectl >/dev/null
systemctl enable kubelet >/dev/null 2>&1
ok "paquets installés et bloqués (apt-mark hold)"

# ---------------------------------------------------------------- 6. Vérification
log "6/6 Vérification"
echo "kubeadm    : $(kubeadm version -o short)"
echo "kubelet    : $(kubelet --version | awk '{print $2}')"
echo "kubectl    : $(kubectl version --client 2>/dev/null | awk '/Client/{print $3}')"
echo "containerd : $CTR_VER ($(systemctl is-active containerd))"
echo "modules    : $(lsmod | grep -cE '^(overlay|br_netfilter) ') / 2"

if crictl --runtime-endpoint unix:///run/containerd/containerd.sock info >/dev/null 2>&1; then
  ok "kubelet pourra parler à containerd (CRI OK)"
else
  warn "crictl ne joint pas containerd : vérifie 'systemctl status containerd'"
fi

if grep -q -- '--node-ip' /etc/default/kubelet 2>/dev/null; then
  ok "$(cat /etc/default/kubelet)"
else
  warn "/etc/default/kubelet sans --node-ip : lance setup-ip.sh avant kubeadm init/join"
fi

echo -e "\nNœud prêt. Le kubelet redémarre en boucle jusqu'à 'kubeadm init' ou 'kubeadm join' : c'est normal."
