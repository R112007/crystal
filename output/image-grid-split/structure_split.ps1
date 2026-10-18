$ErrorActionPreference = 'Stop'

Add-Type -AssemblyName System.Drawing.Common

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$sourcePath = Join-Path $root 'output\image-grid-split\source\lutian.png'
$partsDir = Join-Path $PSScriptRoot 'structure-parts'
$croppedDir = Join-Path $PSScriptRoot 'structure-parts-cropped'
$previewPath = Join-Path $PSScriptRoot 'lutian-structure-preview.png'

New-Item -ItemType Directory -Force -Path $partsDir | Out-Null
New-Item -ItemType Directory -Force -Path $croppedDir | Out-Null

$seeds = @(
  @{X = 200; Y = 180; Name = 'outer-wing-left'},
  @{X = 270; Y = 330; Name = 'inner-wing-left'},
  @{X = 400; Y = 260; Name = 'head'},
  @{X = 530; Y = 330; Name = 'inner-wing-right'},
  @{X = 600; Y = 180; Name = 'outer-wing-right'},
  @{X = 150; Y = 520; Name = 'side-wing-left'},
  @{X = 400; Y = 520; Name = 'torso'},
  @{X = 650; Y = 520; Name = 'side-wing-right'},
  @{X = 300; Y = 650; Name = 'hip-left'},
  @{X = 500; Y = 650; Name = 'hip-right'},
  @{X = 180; Y = 760; Name = 'leg-left'},
  @{X = 620; Y = 760; Name = 'leg-right'}
)

$source = [System.Drawing.Bitmap]::new($sourcePath)
try {
  $width = $source.Width
  $height = $source.Height
  $rect = [System.Drawing.Rectangle]::new(0, 0, $width, $height)
  $data = $source.LockBits(
    $rect,
    [System.Drawing.Imaging.ImageLockMode]::ReadOnly,
    [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)

  try {
    $pixels = [int[]]::new($width * $height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $pixels, 0, $pixels.Length)
  } finally {
    $source.UnlockBits($data)
  }

  $owner = [int[]]::new($pixels.Length)
  $sumX = [double[]]::new($seeds.Count)
  $sumY = [double[]]::new($seeds.Count)
  $counts = [long[]]::new($seeds.Count)
  $minX = [int[]]::new($seeds.Count)
  $minY = [int[]]::new($seeds.Count)
  $maxX = [int[]]::new($seeds.Count)
  $maxY = [int[]]::new($seeds.Count)

  for ($i = 0; $i -lt $seeds.Count; $i++) {
    $minX[$i] = $width
    $minY[$i] = $height
    $maxX[$i] = -1
    $maxY[$i] = -1
  }

  for ($y = 0; $y -lt $height; $y++) {
    for ($x = 0; $x -lt $width; $x++) {
      $index = $y * $width + $x
      $argb = $pixels[$index]
      $alpha = ($argb -shr 24) -band 255
      if ($alpha -le 8) {
        continue
      }

      $best = 0
      $bestDistance = [double]::MaxValue
      for ($i = 0; $i -lt $seeds.Count; $i++) {
        $dx = $x - $seeds[$i].X
        $dy = $y - $seeds[$i].Y
        $distance = $dx * $dx + $dy * $dy
        if ($distance -lt $bestDistance) {
          $bestDistance = $distance
          $best = $i
        }
      }

      $owner[$index] = $best + 1
      $sumX[$best] += $x
      $sumY[$best] += $y
      $counts[$best]++
      if ($x -lt $minX[$best]) { $minX[$best] = $x }
      if ($y -lt $minY[$best]) { $minY[$best] = $y }
      if ($x -gt $maxX[$best]) { $maxX[$best] = $x }
      if ($y -gt $maxY[$best]) { $maxY[$best] = $y }
    }
  }

  $partImages = [System.Drawing.Bitmap[]]::new($seeds.Count)
  for ($part = 0; $part -lt $seeds.Count; $part++) {
    $bitmap = [System.Drawing.Bitmap]::new(
      $width,
      $height,
      [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $partRect = [System.Drawing.Rectangle]::new(0, 0, $width, $height)
    $partData = $bitmap.LockBits(
      $partRect,
      [System.Drawing.Imaging.ImageLockMode]::WriteOnly,
      [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
      $partPixels = [int[]]::new($pixels.Length)
      $ownerId = $part + 1
      for ($i = 0; $i -lt $pixels.Length; $i++) {
        if ($owner[$i] -eq $ownerId) {
          $partPixels[$i] = $pixels[$i]
        }
      }
      [System.Runtime.InteropServices.Marshal]::Copy($partPixels, 0, $partData.Scan0, $partPixels.Length)
    } finally {
      $bitmap.UnlockBits($partData)
    }

    $path = Join-Path $partsDir ('lutian-structure-{0:D2}.png' -f ($part + 1))
    $bitmap.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $partImages[$part] = $bitmap

    if ($counts[$part] -gt 0) {
      $cropRect = [System.Drawing.Rectangle]::new(
        $minX[$part],
        $minY[$part],
        $maxX[$part] - $minX[$part] + 1,
        $maxY[$part] - $minY[$part] + 1)
      $cropped = $bitmap.Clone($cropRect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
      try {
        $croppedPath = Join-Path $croppedDir ('lutian-part-{0:D2}.png' -f ($part + 1))
        $cropped.Save($croppedPath, [System.Drawing.Imaging.ImageFormat]::Png)
      } finally {
        $cropped.Dispose()
      }
    }

    $centerX = if ($counts[$part] -gt 0) { $sumX[$part] / $counts[$part] } else { 0 }
    $centerY = if ($counts[$part] -gt 0) { $sumY[$part] / $counts[$part] } else { 0 }
    [PSCustomObject]@{
      Index = $part + 1
      Name = $seeds[$part].Name
      Pixels = $counts[$part]
      CenterX = [Math]::Round($centerX, 2)
      CenterY = [Math]::Round($centerY, 2)
      MinX = $minX[$part]
      MinY = $minY[$part]
      MaxX = $maxX[$part]
      MaxY = $maxY[$part]
      Path = $path
    }
  }

  try {
    $preview = [System.Drawing.Bitmap]::new(1000, 760, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
      $graphics = [System.Drawing.Graphics]::FromImage($preview)
      try {
        $graphics.Clear([System.Drawing.Color]::FromArgb(255, 26, 29, 34))
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $font = [System.Drawing.Font]::new('Arial', 12, [System.Drawing.FontStyle]::Bold)
        $brush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::White)
        try {
          $cellWidth = 250
          $cellHeight = 190
          for ($part = 0; $part -lt $partImages.Count; $part++) {
            $column = $part % 4
            $row = [Math]::Floor($part / 4)
            $cropWidth = $maxX[$part] - $minX[$part] + 1
            $cropHeight = $maxY[$part] - $minY[$part] + 1
            if ($cropWidth -le 0 -or $cropHeight -le 0) {
              continue
            }

            $scale = [Math]::Min(205.0 / $cropWidth, 145.0 / $cropHeight)
            $drawWidth = [Math]::Max(1, [int]($cropWidth * $scale))
            $drawHeight = [Math]::Max(1, [int]($cropHeight * $scale))
            $destX = $column * $cellWidth + [int](($cellWidth - $drawWidth) / 2)
            $destY = $row * $cellHeight + 28 + [int](($cellHeight - 28 - $drawHeight) / 2)
            $sourceRect = [System.Drawing.Rectangle]::new(
              $minX[$part],
              $minY[$part],
              $cropWidth,
              $cropHeight)
            $destRect = [System.Drawing.Rectangle]::new($destX, $destY, $drawWidth, $drawHeight)
            $graphics.DrawImage($partImages[$part], $destRect, $sourceRect, [System.Drawing.GraphicsUnit]::Pixel)
            $graphics.DrawString(
              ('{0:D2} {1}' -f ($part + 1), $seeds[$part].Name),
              $font,
              $brush,
              $column * $cellWidth + 8,
              $row * $cellHeight + 6)
          }
        } finally {
          $font.Dispose()
          $brush.Dispose()
        }
      } finally {
        $graphics.Dispose()
      }
      $preview.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
      $preview.Dispose()
    }
  } finally {
    foreach ($bitmap in $partImages) {
      if ($null -ne $bitmap) {
        $bitmap.Dispose()
      }
    }
  }
} finally {
  $source.Dispose()
}
