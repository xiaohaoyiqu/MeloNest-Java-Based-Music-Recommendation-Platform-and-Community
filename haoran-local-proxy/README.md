# 浩然音乐本地媒体代理

该服务只监听 `127.0.0.1`，让网页在用户明确授权后扫描、读取元数据和播放本机音频或视频文件。文件不会由此服务上传。

## 启动与授权

需要 Node.js 18 或更新版本。在服务目录执行：

```powershell
npm start
```

未设置媒体根目录时，服务启动后没有可访问目录。用户必须在页面中使用“选择文件夹扫描”，由 Windows 原生选择器授予一个目录；该授权仅在本次服务运行期间有效，服务重启后需要重新选择。

需要固定允许目录时，在启动前设置 `LOCAL_PROXY_ALLOWED_ROOTS`。多个目录用分号分隔，不能填写 `D:\`、`E:\` 这类盘符根目录：

```powershell
$env:LOCAL_PROXY_ALLOWED_ROOTS='D:\MyMusic;E:\MyMV'
npm start
```

路径会先检查是否位于允许目录内，再解析真实路径并再次检查，因此不能通过 `..` 或符号链接跳出授权目录。

`/health` 只返回运行状态，不分发访问凭据。允许来源的页面需通过 `POST /session` 建立仅存于当前内存的代理会话；普通 API 使用请求头授权，媒体元素使用绑定单个已授权文件、默认 30 分钟有效的短时票据。长期会话令牌不会出现在媒体 URL、浏览器历史或健康探测结果中。

## 可修改项

仅在启动前设置环境变量；数值超出下表范围时服务会拒绝启动。除非确实需要兼容额外的本机前端地址，不要放宽允许目录或来源列表。

| 环境变量 | 默认值 | 可修改范围与用途 |
| --- | --- | --- |
| `LOCAL_PROXY_ALLOWED_ROOTS` | 无 | 固定允许的媒体目录；使用分号分隔，不能是盘符根目录。 |
| `LOCAL_PROXY_PORT` | `7777` | `1`–`65535`；前端默认连接 `7777`，改端口时需同步修改前端 `src/api/localProxy.ts`。 |
| `LOCAL_PROXY_ALLOWED_ORIGINS` | 本机 `3000` 端口及兼容地址 | 可访问代理的网页来源，使用分号分隔。只保留实际使用的可信来源。 |
| `LOCAL_PROXY_MAX_ACTIVE_REQUESTS` | `16` | `1`–`64`；同时处理的请求数。 |
| `LOCAL_PROXY_MAX_CONNECTIONS` | `32` | `1`–`128`；同时连接数。 |
| `LOCAL_PROXY_MAX_SCAN_FILES` | `2000` | `1`–`5000`；一次目录扫描最多返回的媒体文件数。 |
| `LOCAL_PROXY_MAX_SCAN_DEPTH` | `20` | `0`–`64`；递归扫描的最大层级。 |
| `LOCAL_PROXY_SCAN_TIMEOUT_MS` | `10000` | `1000`–`30000`；目录扫描超时时间（毫秒）。 |
| `LOCAL_PROXY_MAX_CHECK_PATHS` | `200` | `1`–`1000`；单次文件存在性检查的路径数。 |
| `LOCAL_PROXY_METADATA_TIMEOUT_MS` | `4000` | `500`–`10000`；单个 `ffprobe` 元数据读取超时（毫秒）。 |
| `LOCAL_PROXY_MAX_METADATA_PROCESSES` | `2` | `1`–`4`；同时运行的 `ffprobe` 数量。 |
| `LOCAL_PROXY_FFPROBE_PATH` | `ffprobe` | `ffprobe` 可执行文件路径；仅在系统 PATH 中找不到它时设置。 |
| `LOCAL_PROXY_MEDIA_TICKET_TTL_MS` | `1800000` | `60000`–`7200000`；单文件播放票据有效期（毫秒）。 |
| `LOCAL_PROXY_MAX_MEDIA_TICKETS` | `256` | `16`–`1000`；内存中保留的播放票据上限。 |

媒体格式限定为：`mp3`、`flac`、`wav`、`m4a`、`aac`、`ogg`、`wma`、`ape`，以及 `mp4`、`mov`、`avi`、`mkv`、`flv`、`wmv`。盘符根目录始终拒绝，未知来源也会被 CORS 拒绝。
