# N8 local demo HTTP smoke (stub IdP). Disposable DB only. No secrets in this file.
$ErrorActionPreference = "Stop"
$base = "http://127.0.0.1:8080"
$out = Join-Path $PSScriptRoot "http-smoke-result.json"
$steps = New-Object System.Collections.Generic.List[object]

function Stub-Token([string]$sub, [string]$email, [string]$name) {
  $json = '{"sub":"' + $sub + '","email":"' + $email + '","preferred_username":"' + $email + '","name":"' + $name + '","acr":"mfa","amr":"otp"}'
  $b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json)).TrimEnd("=").Replace("+", "-").Replace("/", "_")
  return "stub.$b64"
}

function Invoke-Api([string]$method, [string]$path, $body = $null, $headers = @{}) {
  $uri = "$base$path"
  $hdr = @{ Accept = "application/json" }
  foreach ($k in $headers.Keys) { $hdr[$k] = $headers[$k] }
  $params = @{ Uri = $uri; Method = $method; Headers = $hdr }
  if ($null -ne $body) {
    $params.ContentType = "application/json; charset=utf-8"
    $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Compress -Depth 8))
  }
  try {
    $resp = Invoke-WebRequest @params
    $parsed = $null
    if ($resp.Content) {
      try { $parsed = $resp.Content | ConvertFrom-Json } catch { $parsed = $resp.Content }
    }
    return @{ ok = $true; status = [int]$resp.StatusCode; body = $parsed; raw = $resp.Content }
  } catch {
    $ex = $_.Exception
    $status = 0
    $raw = $ex.Message
    if ($ex.Response) {
      $status = [int]$ex.Response.StatusCode
      $reader = New-Object System.IO.StreamReader($ex.Response.GetResponseStream())
      $raw = $reader.ReadToEnd()
    }
    $parsed = $null
    try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $raw }
    return @{ ok = $false; status = $status; body = $parsed; raw = $raw }
  }
}

function Record([string]$name, $result, [string]$note = "") {
  $steps.Add([pscustomobject]@{
      step = $name
      ok = [bool]$result.ok
      status = $result.status
      note = $note
      summary = if ($result.body -is [string]) { $result.body.Substring(0, [Math]::Min(200, $result.body.Length)) } else { $result.body }
    }) | Out-Null
  Write-Output ("{0} status={1} ok={2} {3}" -f $name, $result.status, $result.ok, $note)
}

$linToken = Stub-Token "demo-seed:p2:lin" "lin.xiao@demo.invalid" "Lin"
$zhouToken = Stub-Token "demo-seed:p2:zhou" "zhou.ran@demo.invalid" "Zhou"
$zhouPerson = "01900000-0000-7000-8000-000000000204"
$platformHeaders = @{
  "X-Nexus-Demo-Actor-Id" = "01900000-0000-7000-8000-0000000000ac"
  "X-Nexus-Demo-Reason" = "n8-smoke"
}

$login = Invoke-Api POST "/api/v1/auth/login" @{ access_token = $linToken }
Record "login-lin" $login
if (-not $login.ok) { $steps | ConvertTo-Json -Depth 8 | Set-Content -Path $out -Encoding utf8; exit 1 }
$linCtx = [string]$login.body.context_id
$tenantId = [string]$login.body.tenant_id
$linAuth = @{ Authorization = ("Bearer " + $linToken); "X-Nexus-Context-Id" = $linCtx }

$catalog = Invoke-Api GET "/api/v1/p4/catalog" $null $linAuth
Record "catalog" $catalog
$sku = "demo.simple-approval"
$versionId = $null
foreach ($item in @($catalog.body)) {
  foreach ($entry in @($item.skus)) {
    $code = $entry.code
    if (-not $code -and $entry.sku) { $code = $entry.sku.code }
    if ($code -eq $sku) { $versionId = [string]$item.version.id; break }
  }
}
$subBody = @{ sku_code = $sku }
if ($versionId) { $subBody.version_id = $versionId }
$sub = Invoke-Api POST ("/api/v1/p4/tenants/" + $tenantId + "/subscriptions") $subBody $linAuth
if ((-not $sub.ok) -and $sub.status -eq 409) {
  $sub = @{ ok = $true; status = 409; body = $sub.body; raw = $sub.raw }
}
Record "subscribe" $sub ("version_id=" + $versionId)

$installs = Invoke-Api GET "/api/v1/p4/installations" $null $linAuth
Record "installations" $installs
$installationId = $null
foreach ($row in @($installs.body)) {
  if ($row.id) { $installationId = [string]$row.id; break }
}
if (-not $installationId) {
  Record "start" @{ ok = $false; status = 0; body = "no installation" } "blocked"
  $steps | ConvertTo-Json -Depth 8 | Set-Content -Path $out -Encoding utf8
  exit 2
}

$create = Invoke-Api POST ("/api/v1/p3/product-applications/installations/" + $installationId) @{
  values = @{ title = "N8 smoke title"; approver = $zhouPerson }
} $linAuth
Record "start" $create
$appId = $null
$businessKey = $null
if ($create.body.id) { $appId = [string]$create.body.id }
if ($create.body.business_key) { $businessKey = [string]$create.body.business_key }
elseif ($appId) { $businessKey = "product-application:" + $appId }

$loginZ = Invoke-Api POST "/api/v1/auth/login" @{ access_token = $zhouToken }
Record "login-zhou" $loginZ
$zhouCtx = [string]$loginZ.body.context_id
$zhouAuth = @{ Authorization = ("Bearer " + $zhouToken); "X-Nexus-Context-Id" = $zhouCtx }
$todos = Invoke-Api GET "/api/v1/p3/todos" $null $zhouAuth
Record "zhou-inbox" $todos
$todoId = $null
foreach ($t in @($todos.body)) {
  $bk = $t.business_key
  if (-not $bk) { $bk = $t.businessKey }
  if ($businessKey -and ($bk -eq $businessKey -or ("$bk" -like ("*" + $appId + "*")))) { $todoId = [string]$t.id; break }
  if (-not $todoId -and $t.id) { $todoId = [string]$t.id }
}
# list may wrap as { items: [...] }
if (-not $todoId -and $todos.body.items) {
  foreach ($t in @($todos.body.items)) {
    $bk = $t.business_key
    if (-not $bk) { $bk = $t.businessKey }
    if ($businessKey -and ($bk -eq $businessKey -or ("$bk" -like ("*" + $appId + "*")))) { $todoId = [string]$t.id; break }
    if (-not $todoId -and $t.id) { $todoId = [string]$t.id }
  }
}
$approve = @{ ok = $false; status = 0; body = "no todo" }
if ($todoId) {
  $path = "/api/v1/process/tasks/" + $todoId + "/approve"
  $approve = Invoke-Api POST $path @{ comment = "N8 approve ok" } $zhouAuth
}
Record "approve" $approve ("todo=" + $todoId + " business_key=" + $businessKey)

$mine = Invoke-Api GET "/api/v1/p3/product-applications" $null $linAuth
Record "my-requests" $mine

$templates = Invoke-Api GET "/api/v1/p1/platform/form-templates" $null $platformHeaders
Record "platform-templates" $templates
$defs = Invoke-Api GET "/api/v1/p4/product-definitions" $null $platformHeaders
Record "product-definitions" $defs
$procs = Invoke-Api GET "/api/v1/process/definitions" $null $linAuth
Record "process-definitions" $procs

$result = [pscustomobject]@{
  at = (Get-Date).ToString("o")
  base = $base
  tenant_id = $tenantId
  installation_id = $installationId
  application_id = $appId
  business_key = $businessKey
  todo_id = $todoId
  steps = $steps
}
$result | ConvertTo-Json -Depth 10 | Set-Content -Path $out -Encoding utf8
Write-Output ("WROTE " + $out)
$failed = @($steps | Where-Object { -not $_.ok })
if ($failed.Count) { Write-Output ("FAILED_STEPS=" + $failed.Count); exit 3 }
Write-Output "ALL_HTTP_STEPS_OK"
