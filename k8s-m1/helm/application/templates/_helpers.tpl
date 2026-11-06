{{/* Labels communs posés sur les métadonnées de tous les objets (jamais dans les selectors, immuables). */}}
{{- define "application.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: stock
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end -}}

{{/* SecurityContext du Pod : jamais root (TP 10.5). */}}
{{- define "application.podSecurityContext" -}}
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  runAsGroup: 1000
  seccompProfile:
    type: RuntimeDefault
{{- end -}}

{{/* SecurityContext du conteneur. */}}
{{- define "application.containerSecurityContext" -}}
securityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities:
    drop: ["ALL"]
{{- end -}}

{{- define "application.tolerations" -}}
{{- with .Values.scheduling.tolerations }}
tolerations:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- end -}}

{{- define "application.nodeAffinity" -}}
nodeAffinity:
  requiredDuringSchedulingIgnoredDuringExecution:
    nodeSelectorTerms:
      - matchExpressions:
          - key: {{ .Values.scheduling.nodeAffinity.key }}
            operator: In
            values: [{{ .Values.scheduling.nodeAffinity.value | quote }}]
{{- end -}}

{{- define "application.keelAnnotations" -}}
{{- if .Values.keel.enabled }}
keel.sh/policy: force
keel.sh/match-tag: "true"
keel.sh/trigger: poll
keel.sh/pollSchedule: {{ .Values.keel.pollSchedule | quote }}
{{- end }}
{{- end -}}

{{/*
Valeur d'une clé du Secret : valeur fournie > valeur actuelle dans le cluster (lookup) > aléatoire.
Usage : include "application.secretValue" (list $ "API_TOKEN" .Values.secret.apiToken 64)
*/}}
{{- define "application.secretValue" -}}
{{- $root := index . 0 -}}{{- $key := index . 1 -}}{{- $given := index . 2 -}}{{- $len := index . 3 -}}
{{- if $given -}}
{{- $given -}}
{{- else -}}
{{- $existing := lookup "v1" "Secret" $root.Release.Namespace "app-secret" -}}
{{- if and $existing (hasKey $existing.data $key) -}}
{{- index $existing.data $key | b64dec -}}
{{- else -}}
{{- randAlphaNum $len -}}
{{- end -}}
{{- end -}}
{{- end -}}
