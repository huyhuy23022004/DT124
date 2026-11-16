// DT124 safe indicator rules (YARA-style subset).
// Scanned entirely in RAM against headers, bodies, URLs and attachment metadata.
// No payload is executed and nothing is written to disk.

rule eval_obfuscation
{
    strings:
        $a = "eval(" nocase
        $b = /document\s*\.\s*write/
        $c = "unescape(" nocase
        $d = /String\.fromCharCode/
        $e = /atob\s*\(/
    condition:
        any of them
}

rule base64_blob
{
    strings:
        $blob = /[A-Za-z0-9+\/]{120,}={0,2}/
    condition:
        any of them
}

rule hidden_login_form
{
    strings:
        $pw = /<input[^>]*type\s*=\s*["']?password/
        $hidden = /display\s*:\s*none|visibility\s*:\s*hidden/
    condition:
        all of them
}

rule onload_js
{
    strings:
        $onload = /on(load|error|mouseover)\s*=/
        $script = /<script/i
    condition:
        any of them
}

rule credential_harvest
{
    strings:
        $a = /verify your account/i
        $b = /confirm your (account|identity|information)/i
        $c = /update your (payment|billing|account)/i
        $d = /unusual sign.?in/i
        $e = /account (has been )?(suspended|locked|limited)/i
    condition:
        any of them
}

rule urgent_language
{
    strings:
        $a = /within 24 hours/i
        $b = /immediate action/i
        $c = /final (warning|notice)/i
        $d = /your account will be (closed|suspended|terminated)/i
    condition:
        any of them
}

rule remote_template
{
    strings:
        $iframe = /<iframe/i
        $embed = /<embed/i
        $object = /<object/i
        $datauri = /src\s*=\s*["']?data:/
    condition:
        any of them
}

rule crypto_wallet
{
    strings:
        $btc = /\b(bc1|[13])[a-zA-HJ-NP-Z0-9]{25,39}\b/
        $eth = /0x[a-fA-F0-9]{40}/
        $kw = /\b(bitcoin|usdt|wallet address|seed phrase)\b/i
    condition:
        any of them
}
