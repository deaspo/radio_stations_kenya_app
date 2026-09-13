<#
    Answers the cleartext question with data instead of a guess.

    network_security_config.xml forbids plain http, matching the platform
    default. Whether that actually breaks any station is an empirical question
    nobody has measured. This script measures it: it scrapes the same station
    list the app does, asks the same stream API, and reports which stream URLs
    come back as http.

    Run from the repository root:
        powershell -ExecutionPolicy Bypass -File .\tools\check-stream-schemes.ps1

    Output: a per-station table, then a ready-to-paste <domain-config> block for
    the hosts that actually need it. Adding only those hosts keeps cleartext off
    for everything else, including the station API itself.
#>

param(
    [string] $StationsUrl = "https://radio.or.ke/",
    [string] $StreamApi   = "https://api.instant.audio/data/streams/81/",
    [int]    $TimeoutSec  = 20
)

$ErrorActionPreference = "Stop"
$ProgressPreference    = "SilentlyContinue"

Write-Host "Fetching station list from $StationsUrl ..." -ForegroundColor Cyan
$page = Invoke-WebRequest -Uri $StationsUrl -TimeoutSec $TimeoutSec -UseBasicParsing

# Same shape the app's scraper looks for: <li class="item-..."> ... <a href="...#id" title="Name">
$stations = @()
foreach ($li in [regex]::Matches($page.Content, '(?s)<li[^>]*class="item-[^"]*"[^>]*>(.*?)</li>')) {
    $block = $li.Groups[1].Value
    $anchor = [regex]::Match($block, '(?s)<a\b([^>]*)>')
    if (-not $anchor.Success) { continue }
    $attrs = $anchor.Groups[1].Value

    $href  = [regex]::Match($attrs, 'href="([^"]*)"')
    $title = [regex]::Match($attrs, 'title="([^"]*)"')
    if (-not $href.Success) { continue }

    $id = ($href.Groups[1].Value -split '#')[-1]
    if ([string]::IsNullOrWhiteSpace($id)) { continue }

    $stations += [pscustomobject]@{
        Id   = $id
        Name = if ($title.Success) { $title.Groups[1].Value } else { $id }
    }
}
$stations = $stations | Sort-Object Id -Unique

if ($stations.Count -eq 0) {
    Write-Host ""
    Write-Host "No stations matched. The site's markup has probably changed -" -ForegroundColor Red
    Write-Host "which would mean the app's scraper is broken too. Check it." -ForegroundColor Red
    exit 1
}
Write-Host "Found $($stations.Count) stations." -ForegroundColor Cyan
Write-Host ""

$results     = @()
$failed      = @()
$cleartext   = New-Object System.Collections.Generic.HashSet[string]

foreach ($station in $stations) {
    try {
        $response = Invoke-RestMethod -Uri "$StreamApi$($station.Id)" -TimeoutSec $TimeoutSec
    } catch {
        $failed += [pscustomobject]@{ Id = $station.Id; Name = $station.Name; Error = $_.Exception.Message }
        continue
    }

    if (-not $response.success -or -not $response.result.streams) {
        $failed += [pscustomobject]@{ Id = $station.Id; Name = $station.Name; Error = "no streams in response" }
        continue
    }

    foreach ($stream in $response.result.streams) {
        if ([string]::IsNullOrWhiteSpace($stream.url)) { continue }

        $uri = $null
        if (-not [System.Uri]::TryCreate($stream.url, [System.UriKind]::Absolute, [ref]$uri)) { continue }

        if ($uri.Scheme -eq "http") { [void]$cleartext.Add($uri.Host) }

        $results += [pscustomobject]@{
            Station = $station.Name
            Type    = $stream.mediaType
            Scheme  = $uri.Scheme
            Host    = $uri.Host
        }
    }
}

$results | Sort-Object Scheme, Station | Format-Table -AutoSize

Write-Host ""
Write-Host "--- Summary -------------------------------------------------" -ForegroundColor Cyan
Write-Host ("stations queried : {0}" -f $stations.Count)
Write-Host ("streams found    : {0}" -f $results.Count)
Write-Host ("https            : {0}" -f ($results | Where-Object Scheme -eq "https").Count)
Write-Host ("http (cleartext) : {0}" -f ($results | Where-Object Scheme -eq "http").Count)
Write-Host ("lookups failed   : {0}" -f $failed.Count)

if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "Stations whose stream lookup failed (these are broken in the app too):" -ForegroundColor Yellow
    $failed | Format-Table -AutoSize
}

Write-Host ""
if ($cleartext.Count -eq 0) {
    Write-Host "No station uses cleartext. Leave network_security_config.xml as it is -" -ForegroundColor Green
    Write-Host "there is nothing to allow, and the open item is closed." -ForegroundColor Green
    exit 0
}

Write-Host "Paste this into app/src/main/res/xml/network_security_config.xml," -ForegroundColor Yellow
Write-Host "replacing the commented-out domain-config block:" -ForegroundColor Yellow
Write-Host ""
Write-Host '    <domain-config cleartextTrafficPermitted="true">'
foreach ($streamHost in ($cleartext | Sort-Object)) {
    Write-Host ('        <domain includeSubdomains="true">{0}</domain>' -f $streamHost)
}
Write-Host '    </domain-config>'
Write-Host ""
Write-Host "Re-run after any change to the station list; hosts move." -ForegroundColor DarkGray
