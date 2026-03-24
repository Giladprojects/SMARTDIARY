param(
    [Parameter(Mandatory = $true)]
    [string]$InputPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$word = $null
$doc = $null

try {
    $word = New-Object -ComObject Word.Application
    $word.Visible = $false
    $word.DisplayAlerts = 0
    $doc = $word.Documents.Open((Resolve-Path $InputPath).Path)

    foreach ($section in $doc.Sections) {
        $header = $section.Headers.Item(1)
        $header.Range.Text = "SMARTDIARY"
        $header.Range.ParagraphFormat.Alignment = 1

        $footer = $section.Footers.Item(1)
        $footer.Range.ParagraphFormat.Alignment = 1
        if ($footer.PageNumbers.Count -eq 0) {
            $null = $footer.PageNumbers.Add()
        }
    }

    $doc.SaveAs([ref]([System.IO.Path]::GetFullPath($OutputPath)), [ref]16)
    $doc.Close()
    $word.Quit()
}
finally {
    if ($doc -ne $null) {
        try { $doc.Close() } catch {}
    }
    if ($word -ne $null) {
        try { $word.Quit() } catch {}
    }
}
