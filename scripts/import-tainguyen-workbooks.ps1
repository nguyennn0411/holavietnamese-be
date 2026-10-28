param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "contentmanager",
    [string]$Password = ""
)

$ErrorActionPreference = "Stop"
Write-Host "=========================================================="
Write-Host " HolaVietnamese - Import 15 Chuong Khoa Hoc tu c:\tainguyen "
Write-Host "=========================================================="

$tainguyenDir = "C:\tainguyen"
if (-not (Test-Path $tainguyenDir)) {
    $tainguyenDir = Join-Path $PSScriptRoot "..\src\main\resources\learning\workbooks"
}

$files = @(
    "HolaVietnamese_Chuong1_DaSua_LessonType.xlsx",
    "HolaVietnamese_Chapter2_QuocGia_NgonNgu.xlsx",
    "HolaVietnamese_Chapter3_SoDem_VaCachDem.xlsx",
    "HolaVietnamese_Chapter04_Telling_the_Time.xlsx",
    "HolaVietnamese_Chapter05_Days_and_Dates.xlsx",
    "HolaVietnamese_Chapter06_Family.xlsx",
    "HolaVietnamese_Chapter07_Accommodation.xlsx",
    "HolaVietnamese_Chapter08_Weather.xlsx",
    "HolaVietnamese_Chapter09_School_and_Study.xlsx",
    "HolaVietnamese_Chapter10_Occupations.xlsx",
    "HolaVietnamese_Chapter11_Colors_and_Descriptions.xlsx",
    "HolaVietnamese_Chapter12_Food_and_Drinks.xlsx",
    "HolaVietnamese_Chapter13_Shopping.xlsx",
    "HolaVietnamese_Chapter14_Traffic_and_Transport.xlsx",
    "HolaVietnamese_Chapter15_News_and_the_Media.xlsx"
)

# Check if backend is reachable
try {
    $test = Invoke-WebRequest -Uri "$BaseUrl/api/courses" -Method Get -TimeoutSec 3 -ErrorAction Stop
    $backendOnline = $true
} catch {
    $backendOnline = $false
}

if ($backendOnline) {
    Write-Host "[+] Ket noi backend thanh cong tai $BaseUrl"
    if (-not $Password) {
        $Password = Read-Host "Nhap mat khau cho tai khoan '$Username'"
    }

    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
    $loginRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/token" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $loginRes.token
    if (-not $token) { throw "Dang nhap that bai, khong co token." }
    Write-Host "[+] Dang nhap thanh cong voi vai tro Content Manager / Admin"

    $headers = @{ "Authorization" = "Bearer $token" }

    foreach ($file in $files) {
        $fullPath = Join-Path $tainguyenDir $file
        if (-not (Test-Path $fullPath)) {
            Write-Warning "Khong tim thay file: $fullPath"
            continue
        }
        Write-Host "[-] Dang import $file..."
        $form = @{
            file = Get-Item $fullPath
        }
        try {
            $res = Invoke-RestMethod -Uri "$BaseUrl/api/admin/courses/workbook/import" -Method Post -Headers $headers -Form $form
            Write-Host "    [OK] Import thanh cong: Course ID $($res.courseId)"
            # Publish course
            try {
                Invoke-RestMethod -Uri "$BaseUrl/api/admin/courses/$($res.courseId)/status" -Method Patch -Headers $headers -Body (@{ status = "PUBLISHED" } | ConvertTo-Json) -ContentType "application/json"
                Write-Host "    [OK] Da Publish khoa hoc ID $($res.courseId)"
            } catch {
                Write-Host "    [INFO] Khoa hoc o trang thai $($res.status)"
            }
        } catch {
            Write-Warning "    Loi khi import $file : $($_.Exception.Message)"
        }
    }
} else {
    Write-Host "[i] Backend chua khoi chay o cong 8080. Cac khoa hoc da duoc tich hop vao bo Seed tu dong:"
    Write-Host "    - Classpath: src/main/resources/learning/workbooks/"
    Write-Host "    - Component: BasicVietnameseWorkbookSeed / BasicVietnameseWorkbookSeedRunner"
    Write-Host "    - Khi backend khoi dong, tat ca 15 khoa hoc se tu dong duoc import va xuat ban ngay lap tuc."
}

Write-Host "=========================================================="
Write-Host " Hoan tat kiem tra & import 15 chuong khoa hoc!"
Write-Host "=========================================================="
