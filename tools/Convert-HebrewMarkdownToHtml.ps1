param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Escape-Html {
    param([string]$Text)
    if ($null -eq $Text) {
        return ""
    }
    return [System.Net.WebUtility]::HtmlEncode($Text)
}

function Apply-InlineMarkdown {
    param([string]$Text)

    $escaped = Escape-Html $Text
    $escaped = [System.Text.RegularExpressions.Regex]::Replace(
        $escaped,
        '`([^`]+)`',
        '<span class="inline-code">$1</span>'
    )
    return $escaped
}

function Close-Lists {
    param([ref]$Html, [ref]$InUl, [ref]$InOl)
    if ($InUl.Value) {
        $null = $Html.Value.AppendLine('</ul>')
        $InUl.Value = $false
    }
    if ($InOl.Value) {
        $null = $Html.Value.AppendLine('</ol>')
        $InOl.Value = $false
    }
}

function Flush-Table {
    param([ref]$Html, [ref]$TableBuffer)

    if ($TableBuffer.Value.Count -eq 0) {
        return
    }

    $rows = @()
    foreach ($rawRow in $TableBuffer.Value) {
        $trimmed = $rawRow.Trim()
        if (-not $trimmed.StartsWith('|')) {
            continue
        }

        $cells = $trimmed.Trim('|').Split('|') | ForEach-Object { $_.Trim() }
        if ($cells.Count -eq 0) {
            continue
        }

        $isSeparator = $true
        foreach ($cell in $cells) {
            if ($cell -notmatch '^:?-{3,}:?$') {
                $isSeparator = $false
                break
            }
        }

        if (-not $isSeparator) {
            $rows += ,$cells
        }
    }

    if ($rows.Count -gt 0) {
        $null = $Html.Value.AppendLine('<table class="md-table">')
        $null = $Html.Value.AppendLine('<thead><tr>')
        foreach ($headerCell in $rows[0]) {
            $null = $Html.Value.AppendLine('<th>' + (Apply-InlineMarkdown $headerCell) + '</th>')
        }
        $null = $Html.Value.AppendLine('</tr></thead>')

        if ($rows.Count -gt 1) {
            $null = $Html.Value.AppendLine('<tbody>')
            for ($i = 1; $i -lt $rows.Count; $i++) {
                $null = $Html.Value.AppendLine('<tr>')
                foreach ($bodyCell in $rows[$i]) {
                    $null = $Html.Value.AppendLine('<td>' + (Apply-InlineMarkdown $bodyCell) + '</td>')
                }
                $null = $Html.Value.AppendLine('</tr>')
            }
            $null = $Html.Value.AppendLine('</tbody>')
        }

        $null = $Html.Value.AppendLine('</table>')
    }

    $TableBuffer.Value.Clear()
}

$stream = [System.IO.File]::Open((Resolve-Path $InputPath).Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
$reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
try {
    $content = $reader.ReadToEnd()
}
finally {
    $reader.Dispose()
    $stream.Dispose()
}

$lines = $content -split "`r?`n"
$html = New-Object System.Text.StringBuilder

$null = $html.AppendLine('<!DOCTYPE html>')
$null = $html.AppendLine('<html lang="he" dir="rtl">')
$null = $html.AppendLine('<head>')
$null = $html.AppendLine('<meta charset="utf-8">')
$null = $html.AppendLine('<title>תיק פרויקט SMARTDIARY</title>')
$null = $html.AppendLine('<style>')
$null = $html.AppendLine('@page { size: A4; margin: 2cm 1.7cm 2cm 1.7cm; }')
$null = $html.AppendLine('body { font-family: David, Arial, sans-serif; direction: rtl; text-align: right; margin: 0; color: #1f1f1f; background: #f5f0e8; line-height: 1.75; }')
$null = $html.AppendLine('.page { max-width: 900px; margin: 0 auto; padding: 1.3cm 1.2cm; background: #fffdf9; box-sizing: border-box; }')
$null = $html.AppendLine('h1, h2, h3, h4 { font-weight: 700; color: #273737; }')
$null = $html.AppendLine('h1 { text-align: center; font-size: 28pt; margin: 0 0 10px; letter-spacing: 0.4px; }')
$null = $html.AppendLine('h2 { font-size: 20pt; margin: 28px 0 10px; padding-bottom: 4px; border-bottom: 2px solid #d8cdbb; page-break-before: auto; }')
$null = $html.AppendLine('h2.numbered { page-break-before: always; }')
$null = $html.AppendLine('h3 { font-size: 15.5pt; margin: 22px 0 8px; color: #5c4527; }')
$null = $html.AppendLine('h4 { font-size: 13.5pt; margin: 16px 0 6px; color: #34504e; }')
$null = $html.AppendLine('p { margin: 0 0 11px; }')
$null = $html.AppendLine('ul, ol { margin: 0 0 12px; padding-right: 22px; }')
$null = $html.AppendLine('li { margin-bottom: 4px; }')
$null = $html.AppendLine('.page-break { page-break-after: always; height: 0; }')
$null = $html.AppendLine('.cover-meta { text-align: center; font-size: 13pt; color: #474747; margin-bottom: 9px; }')
$null = $html.AppendLine('.inline-code { font-family: Consolas, "Courier New", monospace; background: #f2eee8; padding: 1px 5px; border-radius: 4px; direction: ltr; unicode-bidi: embed; }')
$null = $html.AppendLine('.code-block { margin: 18px 0; padding: 14px 16px; border: 1px solid #ddd1bf; border-radius: 14px; background: #f7f1e6; }')
$null = $html.AppendLine('.code-block pre { margin: 0; font-family: Consolas, "Courier New", monospace; font-size: 11pt; white-space: pre-wrap; }')
$null = $html.AppendLine('.code-block.generic pre { direction: ltr; text-align: left; }')
$null = $html.AppendLine('.code-block.pseudo-he { border-right: 6px solid #36524f; background: #f3efe7; }')
$null = $html.AppendLine('.code-block.pseudo-he pre { direction: rtl; text-align: right; font-family: David, Arial, sans-serif; font-size: 13pt; }')
$null = $html.AppendLine('figure.diagram { margin: 18px auto 24px; text-align: center; page-break-inside: avoid; }')
$null = $html.AppendLine('figure.diagram img { display: block; width: 15.4cm; max-width: 100%; height: auto; margin: 0 auto; border: 1px solid #e0d6c6; border-radius: 16px; background: #fff; padding: 8px; box-sizing: border-box; }')
$null = $html.AppendLine('figure.diagram.cover img { width: 15.6cm; border: none; padding: 0; background: transparent; }')
$null = $html.AppendLine('figure.diagram.tall img { width: 13.9cm; }')
$null = $html.AppendLine('figure.diagram.wide img { width: 15.8cm; }')
$null = $html.AppendLine('figure.diagram.standard img { width: 15.2cm; }')
$null = $html.AppendLine('figure.diagram figcaption { margin-top: 8px; font-size: 11pt; color: #5f5f5f; }')
$null = $html.AppendLine('table.md-table { width: 100%; border-collapse: collapse; margin: 16px 0 20px; font-size: 11.6pt; page-break-inside: avoid; }')
$null = $html.AppendLine('table.md-table th, table.md-table td { border: 1px solid #d8ccb9; padding: 8px 10px; vertical-align: top; }')
$null = $html.AppendLine('table.md-table th { background: #efe4d4; color: #3b2e20; }')
$null = $html.AppendLine('table.md-table tr:nth-child(even) td { background: #fcfaf6; }')
$null = $html.AppendLine('.spacer { height: 10px; }')
$null = $html.AppendLine('</style>')
$null = $html.AppendLine('</head>')
$null = $html.AppendLine('<body>')
$null = $html.AppendLine('<div class="page">')

$inCodeBlock = $false
$codeBlockKind = 'generic'
$inUl = $false
$inOl = $false
$tableBuffer = New-Object System.Collections.Generic.List[string]

foreach ($line in $lines) {
    $trimmed = $line.Trim()

    if ($trimmed.StartsWith('```')) {
        Flush-Table ([ref]$html) ([ref]$tableBuffer)
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)

        if (-not $inCodeBlock) {
            $label = $trimmed.Substring(3).Trim().ToLowerInvariant()
            if ([string]::IsNullOrWhiteSpace($label)) {
                $label = 'generic'
            }
            $codeBlockKind = $label
            $cssClass = if ($label -eq 'pseudo-he') { 'pseudo-he' } else { 'generic' }
            $null = $html.AppendLine('<div class="code-block ' + $cssClass + '"><pre>')
            $inCodeBlock = $true
        }
        else {
            $null = $html.AppendLine('</pre></div>')
            $inCodeBlock = $false
            $codeBlockKind = 'generic'
        }
        continue
    }

    if ($inCodeBlock) {
        $null = $html.AppendLine((Escape-Html $line))
        continue
    }

    if ($trimmed.StartsWith('|')) {
        $tableBuffer.Add($line)
        continue
    }

    Flush-Table ([ref]$html) ([ref]$tableBuffer)

    if ($trimmed -eq '[[PAGEBREAK]]') {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $null = $html.AppendLine('<div class="page-break"></div>')
        continue
    }

    if ($trimmed -eq '---') {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $null = $html.AppendLine('<div class="spacer"></div>')
        continue
    }

    if ($trimmed -eq '') {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        continue
    }

    if ($trimmed -match '^!\[(.*)\]\((.+)\)$') {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $alt = $matches[1]
        $path = $matches[2]
        if ($alt -match 'שער' -or $path -match 'cover') {
            $figureClass = 'diagram cover'
        }
        elseif ($path -match 'scheduling_flow') {
            $figureClass = 'diagram tall'
        }
        elseif ($path -match 'soft_time_windows') {
            $figureClass = 'diagram wide'
        }
        else {
            $figureClass = 'diagram standard'
        }
        $null = $html.AppendLine('<figure class="' + $figureClass + '">')
        $null = $html.AppendLine('<img src="' + (Escape-Html $path) + '" alt="' + (Escape-Html $alt) + '">')
        $null = $html.AppendLine('<figcaption>' + (Apply-InlineMarkdown $alt) + '</figcaption>')
        $null = $html.AppendLine('</figure>')
        continue
    }

    if ($trimmed.StartsWith('#### ')) {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $null = $html.AppendLine('<h4>' + (Apply-InlineMarkdown $trimmed.Substring(5)) + '</h4>')
        continue
    }

    if ($trimmed.StartsWith('### ')) {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $null = $html.AppendLine('<h3>' + (Apply-InlineMarkdown $trimmed.Substring(4)) + '</h3>')
        continue
    }

    if ($trimmed.StartsWith('## ')) {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $headingText = $trimmed.Substring(3)
        $classes = if ($headingText -match '^\d+\.') { ' class="numbered"' } else { '' }
        $null = $html.AppendLine('<h2' + $classes + '>' + (Apply-InlineMarkdown $headingText) + '</h2>')
        continue
    }

    if ($trimmed.StartsWith('# ')) {
        Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
        $null = $html.AppendLine('<h1>' + (Apply-InlineMarkdown $trimmed.Substring(2)) + '</h1>')
        continue
    }

    if ($trimmed -match '^\-\s+') {
        if (-not $inUl) {
            Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
            $null = $html.AppendLine('<ul>')
            $inUl = $true
        }
        $null = $html.AppendLine('<li>' + (Apply-InlineMarkdown ($trimmed -replace '^\-\s+', '')) + '</li>')
        continue
    }

    if ($trimmed -match '^(\d+)\.\s+') {
        if (-not $inOl) {
            Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
            $null = $html.AppendLine('<ol>')
            $inOl = $true
        }
        $null = $html.AppendLine('<li>' + (Apply-InlineMarkdown ($trimmed -replace '^\d+\.\s+', '')) + '</li>')
        continue
    }

    Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)
    $paragraphClass = ''
    $null = $html.AppendLine('<p' + $paragraphClass + '>' + (Apply-InlineMarkdown $line) + '</p>')
}

Flush-Table ([ref]$html) ([ref]$tableBuffer)
Close-Lists ([ref]$html) ([ref]$inUl) ([ref]$inOl)

if ($inCodeBlock) {
    $null = $html.AppendLine('</pre></div>')
}

$null = $html.AppendLine('</div>')
$null = $html.AppendLine('</body>')
$null = $html.AppendLine('</html>')

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($OutputPath), $html.ToString(), $utf8NoBom)
