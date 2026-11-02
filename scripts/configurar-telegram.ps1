# Ejecutar manualmente después de configurar Heroku y desplegar el backend.
# No escribe secretos en archivos ni los muestra en la salida.
$ErrorActionPreference = 'Stop'
foreach ($nombre in @('TELEGRAM_BOT_TOKEN', 'TELEGRAM_WEBHOOK_SECRET', 'TELEGRAM_WEBHOOK_URL')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($nombre))) {
        throw "Falta la variable de entorno $nombre."
    }
}
if ($env:TELEGRAM_WEBHOOK_SECRET -notmatch '^[A-Za-z0-9_-]{16,256}$') {
    throw 'TELEGRAM_WEBHOOK_SECRET debe tener entre 16 y 256 caracteres: letras, números, guion o guion bajo.'
}
$webhookUri = [Uri]$env:TELEGRAM_WEBHOOK_URL
if ($webhookUri.Scheme -ne 'https' -or $webhookUri.AbsolutePath -ne '/api/telegram/webhook' -or $webhookUri.Query) {
    throw 'TELEGRAM_WEBHOOK_URL debe ser una URL HTTPS pública terminada en /api/telegram/webhook, sin parámetros.'
}
try {
    $botUri = 'https://api.telegram.org/bot' + $env:TELEGRAM_BOT_TOKEN
    $bot = Invoke-RestMethod -Uri ($botUri + '/getMe') -Method Get
    if (-not $bot.ok) { throw 'bot no disponible' }
    if ($env:TELEGRAM_BOT_USERNAME -and $env:TELEGRAM_BOT_USERNAME -cne $bot.result.username) {
        throw 'usuario del bot no coincide'
    }
    $body = @{
        url = $env:TELEGRAM_WEBHOOK_URL
        secret_token = $env:TELEGRAM_WEBHOOK_SECRET
        allowed_updates = @('message', 'callback_query')
        max_connections = 1
        drop_pending_updates = $false
    } | ConvertTo-Json -Depth 5
    $resultado = Invoke-RestMethod -Uri ($botUri + '/setWebhook') -Method Post -ContentType 'application/json' -Body $body
    if (-not $resultado.ok) { throw 'registro de webhook rechazado' }
    $verificacion = Invoke-RestMethod -Uri ($botUri + '/getWebhookInfo') -Method Get
    if (-not $verificacion.ok -or $verificacion.result.url -cne $env:TELEGRAM_WEBHOOK_URL) {
        throw 'webhook no coincide'
    }
    Write-Output ('Webhook registrado para @' + $bot.result.username + '. Verifica que TELEGRAM_BOT_USERNAME tenga ese mismo valor en el backend.')
} catch {
    # Las excepciones HTTP originales pueden incluir la URL con el token del bot.
    throw 'No se pudo configurar Telegram. Revisa el token, el usuario del bot, la URL y la conectividad. No compartas las credenciales.'
}
