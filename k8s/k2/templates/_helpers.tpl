{{- define "pulse.name" -}}
pulse-server
{{- end }}

{{- define "pulse.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/name: {{ include "pulse.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: pulse
{{- end }}

{{- define "pulse.selectorLabels" -}}
app: pulse-server
{{- end }}
