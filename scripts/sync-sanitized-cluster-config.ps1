[CmdletBinding()]
                     
param(
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\ee\current'),
    [string]$SshConfig = (Join-Path $PSScriptRoot 'ssh\config')
)

$ErrorActionPreference = 'Stop'
$ssh = Join-Path $env:WINDIR 'System32\OpenSSH\ssh.exe'
if (-not (Test-Path -LiteralPath $ssh -PathType Leaf)) {
    throw "OpenSSH client not found: $ssh"
}
if (-not (Test-Path -LiteralPath $SshConfig -PathType Leaf)) {
    throw "SSH config not found: $SshConfig"
}

$files = @(
    @{ Source = '/usr/local/soft/hadoop-3.3.6/etc/hadoop/core-site.xml'; Target = 'hadoop/core-site.xml' },
    @{ Source = '/usr/local/soft/hadoop-3.3.6/etc/hadoop/hdfs-site.xml'; Target = 'hadoop/hdfs-site.xml' },
    @{ Source = '/usr/local/soft/hadoop-3.3.6/etc/hadoop/mapred-site.xml'; Target = 'hadoop/mapred-site.xml' },
    @{ Source = '/usr/local/soft/hadoop-3.3.6/etc/hadoop/yarn-site.xml'; Target = 'hadoop/yarn-site.xml' },
    @{ Source = '/usr/local/soft/hadoop-3.3.6/etc/hadoop/workers'; Target = 'hadoop/workers' },
    @{ Source = '/usr/local/soft/hadoop-3.3.6/etc/hadoop/hadoop-env.sh'; Target = 'hadoop/hadoop-env.sh' },
    @{ Source = '/usr/local/soft/hive-3.1.2/conf/hive-site.xml'; Target = 'hive/hive-site.xml' },
    @{ Source = '/usr/local/soft/hive-3.1.2/conf/hive-env.sh'; Target = 'hive/hive-env.sh' },
    @{ Source = '/usr/local/soft/hbase-2.5.12/conf/hbase-site.xml'; Target = 'hbase/hbase-site.xml' },
    @{ Source = '/usr/local/soft/hbase-2.5.12/conf/hbase-env.sh'; Target = 'hbase/hbase-env.sh' },
    @{ Source = '/usr/local/soft/hbase-2.5.12/conf/regionservers'; Target = 'hbase/regionservers' },
    @{ Source = '/usr/local/soft/spark-2.4.8/conf/spark-defaults.conf'; Target = 'spark/spark-defaults.conf' },
    @{ Source = '/usr/local/soft/spark-2.4.8/conf/spark-env.sh'; Target = 'spark/spark-env.sh' },
    @{ Source = '/usr/local/soft/spark-2.4.8/conf/workers'; Target = 'spark/workers' },
    @{ Source = '/usr/local/soft/kafka-2.4.1/config/server.properties'; Target = 'kafka/server.properties' },
    @{ Source = '/usr/local/soft/kafka-2.4.1/config/log4j.properties'; Target = 'kafka/log4j.properties' },
    @{ Source = '/usr/local/soft/zookeeper-3.7.2/conf/zoo.cfg'; Target = 'zookeeper/zoo.cfg' },
    @{ Source = '/usr/local/soft/redis5.0.14/redis.conf'; Target = 'redis/redis.conf' },
    @{ Source = '/usr/local/soft/redis5.0.14/sentinel.conf'; Target = 'redis/sentinel.conf' },
    @{ Source = '/usr/local/soft/nginx-1.28.0/conf/nginx.conf'; Target = 'nginx/nginx.conf' },
    @{ Source = '/sdb1/haoranmusicData/elasticsearch-7.17.24/config/elasticsearch.yml'; Target = 'elasticsearch/elasticsearch.yml' },
    @{ Source = '/sdb1/haoranmusicData/elasticsearch-7.17.24/config/jvm.options'; Target = 'elasticsearch/jvm.options' },
    @{ Source = '/etc/my.cnf'; Target = 'mysql/my.cnf' }
)

function Protect-ConfigurationText {
    param([Parameter(Mandatory)][string]$Text)

    $protected = [regex]::Replace(
        $Text,
        '(?is)(<name>\s*[^<]*(?:password|passwd|secret|token|credential|access[._-]?key|secret[._-]?key)[^<]*</name>\s*<value>).*?(</value>)',
        '$1${REDACTED_SECRET}$2'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^(\s*(?:requirepass|masterauth)\s+).+$',
        '$1${REDIS_PASSWORD}'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^(\s*sentinel\s+auth-pass\s+\S+\s+).+$',
        '$1${REDIS_PASSWORD}'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^(\s*(?:[^#\r\n]*?(?:password|passwd|secret|credential|auth[._-]?token|bearer[._-]?token|session[._-]?token|api[._-]?key|access[._-]?key|secret[._-]?key|private[._-]?key))\s*[:=]\s*).+$',
        '$1${REDACTED_SECRET}'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^(\s*(?:export\s+)?[A-Z0-9_]*(?:PASSWORD|PASSWD|SECRET|AUTH_TOKEN|BEARER_TOKEN|SESSION_TOKEN|API_KEY|ACCESS_KEY|PRIVATE_KEY)[A-Z0-9_]*\s*=\s*).+$',
        '$1"${REDACTED_SECRET}"'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^(\s*proxy_set_header\s+(?:Authorization|Proxy-Authorization)\s+).+;$',
        '$1"${REDACTED_SECRET}";'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^([^\r\n]*@author\s+)liuhao\b',
        '${1}xiaohaoyiqu'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?im)^[^\r\n]*github\.com[^\r\n]*(?:\r?\n|$)',
        ''
    )
    return $protected
}

$root = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $root) {
    $resolved = (Resolve-Path -LiteralPath $root).Path
    $workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
    if (-not $resolved.StartsWith($workspace, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to replace configuration directory outside workspace: $resolved"
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
[IO.Directory]::CreateDirectory($root) | Out-Null

$manifest = [System.Collections.Generic.List[object]]::new()
foreach ($node in 'node1', 'node2', 'node3') {
    foreach ($file in $files) {
        $remote = "if [ -f '$($file.Source)' ]; then base64 -w0 '$($file.Source)'; else exit 44; fi"
        $encoded = (& $ssh -F $SshConfig $node $remote 2>$null) -join ''
        if ($LASTEXITCODE -eq 44) {
            continue
        }
        if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($encoded)) {
            throw "Unable to read ${node}:$($file.Source)"
        }

        $raw = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($encoded))
        $safe = Protect-ConfigurationText -Text $raw
        $destination = Join-Path (Join-Path $root $node) $file.Target
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
        [IO.File]::WriteAllText($destination, $safe, [Text.UTF8Encoding]::new($false))
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant()
        $manifest.Add([pscustomobject]@{
            node = $node
            source = $file.Source
            target = $file.Target.Replace('\', '/')
            sha256 = $hash
        })
    }
}

$manifestPath = Join-Path $root 'manifest.json'
$manifestJson = $manifest | ConvertTo-Json -Depth 4
[IO.File]::WriteAllText($manifestPath, $manifestJson + "`n", [Text.UTF8Encoding]::new($false))
$readme = @(
    '# Current sanitized cluster configuration',
    '',
    'Generated in memory from node1, node2 and node3 by scripts/sync-sanitized-cluster-config.ps1.',
    '',
    '- manifest.json records source paths, package targets and SHA-256 values.',
    '- Passwords, tokens, authorization headers and private-key locations are replaced by placeholders.',
    '- This snapshot is for review and upgrade planning. Do not install it directly.',
    '- Put reviewed configuration with protected values in ee/deploy before using the distribution script.'
) -join "`n"
[IO.File]::WriteAllText((Join-Path $root 'README.md'), $readme + "`n", [Text.UTF8Encoding]::new($false))
Write-Output "Synchronized $($manifest.Count) sanitized configuration files to $root"
