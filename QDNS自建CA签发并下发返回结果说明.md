# QDNS 自建 CA 签发并下发返回结果说明

## 1. 说明

本文档用于说明 `QDNS` 自建 CA 签发并下发接口：

```http
POST /api/device-certificates/issue/self-ca
```

返回结果中的各层字段分别表示什么、应该如何理解，以及结合本次样例可以读出哪些业务结论。

需要先说明一点：

- 你提供的这一大段 JSON，是接口外层 `ApiResponse.data` 里的内容，不是完整 HTTP 返回。
- 完整返回通常还会包一层：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "...": "这里才是你给出的这份 JSON"
  }
}
```

## 2. 整体结构

这份返回结果本质上是一个“流程编排结果”，不是单一步骤的原始回包。  
它把一次“自建 CA 签发并下发”拆成了 5 个阶段：

1. 读取或生成设备 CSR
2. 调用 CA 服务签发设备证书
3. 调用 CA 服务获取 CA 证书链
4. 把 CA 链和设备证书下发到设备
5. 回读设备状态并做最终校验

所以顶层结构可以理解为：

```json
{
  "deviceId": "...",
  "flow": "self_ca",
  "csr": { "...设备 CSR 阶段结果..." },
  "caIssue": { "...CA 签发结果..." },
  "caChain": { "...CA 证书链结果..." },
  "install": { "...下发安装结果..." },
  "verification": { "...安装后校验结果..." }
}
```

## 3. 顶层字段说明

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `deviceId` | `8c861bddac67461381ef360246a551ee` | `String` | QDNS / QDMS 侧使用的设备唯一标识。整个流程都是围绕这台设备执行的。 |
| `flow` | `self_ca` | `String` | 固定表示本次走“自建 CA 签发并下发”流程。若是第三方证书安装，通常会是 `third_party`。 |
| `csr` | 对象 | `Object` | 第一步“读取或生成设备 CSR”的结果。 |
| `caIssue` | 对象 | `Object` | 第二步“调用自建 CA 服务签发设备证书”的结果。 |
| `caChain` | 对象 | `Object` | 第三步“获取自建 CA 证书链”的结果。 |
| `install` | 对象 | `Object` | 第四步“将 CA 链和设备证书安装到设备”的结果。 |
| `verification` | 对象 | `Object` | 第五步“安装后的回读校验结果”。 |

## 4. 通用执行结果包装字段

`csr`、`install.uploadCaChain`、`install.uploadCert`、`verification.queryCertStatus`、`verification.readDeviceCert` 这几块，结构都类似，都是 QDNS 统一命令框架封装后的结果。

通用结构如下：

```json
{
  "requestId": "...",
  "code": 10014,
  "operation": "read_device_csr",
  "mode": "SYNC",
  "status": "SUCCESS",
  "statusMessage": "success",
  "downstream": {
    "...设备侧原始结果..."
  }
}
```

各字段含义如下：

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `requestId` | `a45ebb84d81e416ea78f8aca94f15f1f` | `String` | 本次 QDNS 调用该步骤时生成的请求 ID，用于日志追踪和排障。 |
| `code` | `10014` | `int` | QDNS 统一命令码。不同操作对应不同 code。 |
| `operation` | `read_device_csr` | `String` | 操作名，和 `code` 一一对应。 |
| `mode` | `SYNC` | `String` | 执行模式。这里是同步执行。 |
| `status` | `SUCCESS` | `String` | QDNS 对该步骤执行结果的判定。`SUCCESS` 表示步骤成功，失败时通常为 `FAILED`。 |
| `statusMessage` | `success` | `String` | 该步骤的简要结果描述。 |
| `downstream` | 对象 | `Object` | 设备侧或下游代理返回的原始业务结果。真正的设备状态、证书信息大多都在这里面。 |

### 4.1 本流程中出现的操作码

| `code` | `operation` | 含义 |
| --- | --- | --- |
| `10014` | `read_device_csr` | 读取设备 CSR |
| `10009` | `upload_ca_chain` | 下发 CA 证书链 |
| `10010` | `upload_cert` | 下发设备证书 |
| `10011` | `query_cert_status` | 查询设备证书状态 |
| `10012` | `read_device_cert` | 读取设备证书 |

## 5. `csr` 字段说明

`csr` 表示“读取或生成设备 CSR”这一步的结果。

### 5.1 `csr` 包装层字段

| 字段 | 示例值 | 含义 |
| --- | --- | --- |
| `code` | `10014` | 表示本步是“读取设备 CSR”操作。 |
| `operation` | `read_device_csr` | 操作名。 |
| `status` | `SUCCESS` | 表示设备 CSR 已成功读取。 |
| `downstream` | 对象 | 设备侧详细结果。 |

### 5.2 `csr.downstream` 字段说明

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `command` | `ipc:rsp:ipsec_agent2web_agent:read_device_csr` | `String` | 下游 IPC 实际返回的命令字，表示这是设备代理对 `read_device_csr` 的响应。 |
| `src_channel` | `ovpn:channel:ipsec_agent` | `String` | 下游返回源通道。 |
| `dst_channel` | `ovpn:channel:web_agent` | `String` | 下游返回目标通道。 |
| `session_id` | `1269158730` | `int` | 下游会话 ID，用于链路追踪。 |
| `result` | `0` | `int` | 设备侧结果码。样例中 `0` 表示成功。 |
| `message` | `success` | `String` | 设备侧结果说明。 |
| `source` | `self_ca` | `String` | 本次证书来源。这里表示是自建 CA 流程。 |
| `algorithm` | `SM2` | `String` | 设备 CSR 使用的算法。样例中是国密 `SM2`。 |
| `hasPrivateKey` | `true` | `boolean` | 设备端是否已经存在与该 CSR 配套的私钥。 |
| `hasCsr` | `true` | `boolean` | 当前设备端是否已有 CSR。 |
| `csrSubject` | `CN=0002` | `String` | CSR 主题字段。样例中 CSR 申请的主体是 `CN=0002`。 |
| `deviceCertInstalled` | `true` | `boolean` | 读取 CSR 时，设备上是否已经安装设备证书。注意这是“当前状态快照”，不代表本次流程才刚安装。 |
| `caChainInstalled` | `true` | `boolean` | 读取 CSR 时，设备上是否已经安装 CA 证书链。同样是“当前状态快照”。 |
| `deviceCertSerial` | `19D94F35E52` | `String` | 读取 CSR 当时设备上现有设备证书的序列号。 |
| `deviceCertFingerprint` | `8F:D9:4C:61:30:4F:1A:32:91:17:A5:4B:C1:F7:68:47:30:E8:15:18:2E:54:92:5C:89:0E:C0:FD:84:66:7E:63` | `String` | 读取 CSR 当时设备上现有设备证书的 SHA-256 指纹。 |
| `deviceCertSubject` | `CN=0002` | `String` | 当时设备上现有设备证书的主题。 |
| `deviceCertIssuer` | `C=CN,O=Qasky,CN=Qasky CA Root` | `String` | 当时设备上现有设备证书的签发者。 |
| `deviceCertNotBefore` | `Apr 16 08:25:44 2026 GMT` | `String` | 当时设备上现有设备证书的生效时间。 |
| `deviceCertNotAfter` | `Apr 13 08:25:44 2036 GMT` | `String` | 当时设备上现有设备证书的失效时间。 |
| `caChainCount` | `1` | `int` | 当时设备上已安装的 CA 链证书数量。 |
| `updatedAt` | `1776327944` | `long` | 下游状态更新时间，Unix 秒级时间戳。样例值对应 `2026-04-16 08:25:44 UTC`。 |
| `csrPem` | `-----BEGIN CERTIFICATE REQUEST-----...` | `String` | CSR 的 PEM 明文，可直接查看或落盘。 |
| `csrPemBase64` | `LS0tLS1CRUdJTi...` | `String` | 对 `csrPem` 整段文本再做一次 Base64 编码后的内容，便于 JSON 传输。 |

### 5.3 对 `csr` 的理解重点

- 这一步的核心产物是 `csrPem` / `csrPemBase64`，后续 CA 签发就是基于这个 CSR 进行。
- `deviceCertInstalled`、`caChainInstalled`、`deviceCertSerial` 等字段只是“当时设备已有证书状态”的快照，不是本次安装动作的结果。
- 从样例来看，这台设备在发起自建 CA 流程前，其实已经有一张证书和一条 CA 链。

## 6. `caIssue` 字段说明

`caIssue` 表示 QDNS 调用自建 CA 服务，根据 CSR 签发设备证书后的结果。

### 6.1 `caIssue` 字段说明

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `caType` | `SM2` | `String` | CA 签发时采用的算法类型。这里与 CSR 的 `algorithm` 对应。 |
| `validDays` | `365` | `int` | QDNS 传给 CA 服务的“期望有效期参数”或 CA 服务回显值。 |
| `deviceCertPemBase64` | `LS0tLS1CRUdJTi...` | `String` | CA 服务签发出的设备证书 PEM 文本，再经过 Base64 编码后的结果。 |
| `raw` | 对象 | `Object` | CA 服务原始返回。 |

### 6.2 `caIssue.raw` 字段说明

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `code` | `0` | `int` | CA 服务返回码。样例中 `0` 表示调用成功。 |
| `message` | `根据CSR签发证书成功` | `String` | CA 服务返回说明。 |
| `data.cert` | `LS0tLS1CRUdJTi...` | `String` | CA 服务真正返回的设备证书内容，格式为 PEM 文本的 Base64。 |

### 6.3 对 `caIssue` 的理解重点

- 这一步只表示“CA 已经签发出一张证书”，还不代表设备已经安装成功。
- 真正安装到设备上的动作，要看后面的 `install.uploadCert`。
- 样例里 `validDays = 365`，但最终设备回读出来的 `deviceCertNotAfter = Apr 13 08:27:47 2036 GMT`，明显超过 365 天。  
  这说明 `validDays` 更适合理解为“请求签发参数 / 服务回显值”，真正设备上生效的证书有效期，应以后续回读证书的 `NotBefore / NotAfter` 或证书解析结果为准。

## 7. `caChain` 字段说明

`caChain` 表示 QDNS 从自建 CA 服务获取到的 CA 证书链。

### 7.1 `caChain` 字段说明

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `caType` | `SM2` | `String` | CA 链使用的算法类型。 |
| `caChainPemBase64List` | 长度为 `1` 的数组 | `List<String>` | CA 证书链列表。每个元素都是一张 PEM 证书文本的 Base64。样例中只有 1 张根证书。 |
| `raw` | 对象 | `Object` | CA 服务原始返回。 |

### 7.2 `caChain.raw` 字段说明

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `code` | `0` | `int` | CA 服务返回码。`0` 表示成功。 |
| `message` | `获取CA证书成功` | `String` | 获取 CA 证书链成功。 |
| `data.cert` | `LS0tLS1CRUdJTi...` | `String` | CA 服务返回的 CA 证书 PEM 文本 Base64。 |

### 7.3 对 `caChain` 的理解重点

- 虽然字段名叫 `caChainPemBase64List`，但样例里只有 1 张证书，说明当前链条只有根证书。
- 如果后续引入中间 CA，这个列表就可能包含多张证书，顺序通常按证书链顺序组织。

## 8. `install` 字段说明

`install` 表示“把证书真正下发到设备”这一步的结果，分成两部分：

1. `uploadCaChain`：先安装 CA 链
2. `uploadCert`：再安装设备证书

### 8.1 `install.uploadCaChain`

表示将 `caChain.caChainPemBase64List` 下发到设备后的结果。

#### `install.uploadCaChain.downstream` 关键字段

| 字段 | 示例值 | 含义 |
| --- | --- | --- |
| `command` | `ipc:rsp:ipsec_agent2web_agent:upload_ca_chain` | 表示这是“下发 CA 链”的设备响应。 |
| `result` | `0` | 设备侧执行成功。 |
| `source` | `self_ca` | 本次下发来源为自建 CA。 |
| `caChainInstalled` | `true` | 当前设备上 CA 链已处于已安装状态。 |
| `caChainCount` | `1` | 当前设备上 CA 链数量为 1。 |
| `caChain` | 数组 | 设备回读出的 CA 链详细信息。 |
| `caChainPemBase64List` | 数组 | 设备侧保存的 CA 链证书 Base64 列表。 |

#### `install.uploadCaChain.downstream.caChain[]` 单个元素说明

| 字段 | 示例值 | 含义 |
| --- | --- | --- |
| `serial` | `19D7A405132` | CA 证书序列号。 |
| `fingerprint` | `14:DF:48:08:31:C6:54:BE:...` | CA 证书 SHA-256 指纹。 |
| `subject` | `C=CN,O=Qasky,CN=Qasky CA Root` | CA 证书主题。 |
| `issuer` | `C=CN,O=Qasky,CN=Qasky CA Root` | CA 证书签发者。根证书自签，因此与 `subject` 相同。 |
| `notBefore` | `Apr 11 01:55:28 2026 GMT` | CA 证书生效时间。 |
| `notAfter` | `Apr 08 01:55:28 2036 GMT` | CA 证书失效时间。 |
| `certPem` | `-----BEGIN CERTIFICATE-----...` | CA 证书 PEM 明文。 |
| `certPemBase64` | `LS0tLS1CRUdJTi...` | CA 证书 PEM 文本的 Base64。 |

### 8.2 `install.uploadCert`

表示将 `caIssue.deviceCertPemBase64` 下发到设备后的结果。

#### `install.uploadCert.downstream` 关键字段

| 字段 | 示例值 | 含义 |
| --- | --- | --- |
| `command` | `ipc:rsp:ipsec_agent2web_agent:upload_cert` | 表示这是“下发设备证书”的设备响应。 |
| `result` | `0` | 设备侧执行成功。 |
| `deviceCertInstalled` | `true` | 设备证书已安装。 |
| `deviceCertSerial` | `19D94F35E53` | 当前安装后设备证书序列号。 |
| `deviceCertFingerprint` | `55:D2:B3:02:9E:5B:01:4A:...` | 当前安装后设备证书指纹。 |
| `deviceCertSubject` | `CN=0002` | 当前安装后设备证书主题。 |
| `deviceCertIssuer` | `C=CN,O=Qasky,CN=Qasky CA Root` | 当前安装后设备证书签发者。 |
| `deviceCertNotBefore` | `Apr 16 08:27:47 2026 GMT` | 当前安装后设备证书生效时间。 |
| `deviceCertNotAfter` | `Apr 13 08:27:47 2036 GMT` | 当前安装后设备证书失效时间。 |
| `deviceCertPem` | `-----BEGIN CERTIFICATE-----...` | 设备当前证书 PEM 明文。 |
| `deviceCertPemBase64` | `LS0tLS1CRUdJTi...` | 设备当前证书 PEM 文本的 Base64。 |

### 8.3 对 `install` 的理解重点

- `uploadCaChain` 成功，只能说明“CA 链已经安装成功”，不代表设备证书也成功。
- `uploadCert` 成功，才表示设备证书也完成安装。
- 如果 `uploadCaChain` 成功但 `uploadCert` 失败，QDNS 会把这次流程视为“部分成功”，也就是 CA 链已装上，但设备证书未装上。

## 9. `verification` 字段说明

`verification` 是安装完成之后的最终回读确认。

它的目的不是再次执行安装，而是验证设备当前状态是否和预期一致。

### 9.1 `verification.queryCertStatus`

这是对设备当前证书状态的查询结果，对应操作：

| 字段 | 值 |
| --- | --- |
| `code` | `10011` |
| `operation` | `query_cert_status` |

其 `downstream` 里的关键字段有：

| 字段 | 示例值 | 含义 |
| --- | --- | --- |
| `deviceCertInstalled` | `true` | 当前设备证书已安装。 |
| `caChainInstalled` | `true` | 当前 CA 链已安装。 |
| `deviceCertSerial` | `19D94F35E53` | 当前设备证书序列号。 |
| `deviceCertFingerprint` | `55:D2:B3:02:9E:5B:01:4A:...` | 当前设备证书指纹。 |
| `caChainCount` | `1` | 当前 CA 链数量。 |
| `updatedAt` | `1776328067` | 设备状态更新时间，Unix 秒级时间戳。样例值对应 `2026-04-16 08:27:47 UTC`。 |

### 9.2 `verification.readDeviceCert`

这是对当前设备证书内容的回读结果，对应操作：

| 字段 | 值 |
| --- | --- |
| `code` | `10012` |
| `operation` | `read_device_cert` |

它与 `uploadCert` 类似，但意义不同：

- `uploadCert` 是“安装动作的执行结果”
- `readDeviceCert` 是“安装完成后重新读回来确认设备现在到底装的是哪张证书”

### 9.3 `verification.caStatusCheck`

这是 QDNS 再调用 CA 服务，对刚签发的设备证书做状态检查后的结果。

| 字段 | 示例值 | 类型 | 含义 |
| --- | --- | --- | --- |
| `result` | `0` | `int` | CA 服务返回的证书状态结果码。样例里可理解为状态正常。 |
| `raw.code` | `0` | `int` | 调用 CA 状态查询接口成功。 |
| `raw.message` | `查询指定证书的状态成功` | `String` | CA 服务返回说明。 |
| `raw.data.result` | `0` | `int` | CA 服务原始状态码。 |
| `success` | `true` | `boolean` | QDNS 对这次 CA 状态校验的最终判定，`true` 表示调用成功且拿到了结果。 |

### 9.4 对 `verification` 的理解重点

- `verification` 才是最适合前端做“最终是否下发成功”判断的区域。
- 一般可以同时看下面三个条件：
  - `verification.queryCertStatus.status == SUCCESS`
  - `verification.readDeviceCert.status == SUCCESS`
  - `verification.caStatusCheck.success == true`

## 10. 样例能读出的业务结论

结合这份具体样例，可以直接得出以下结论：

| 结论 | 依据 |
| --- | --- |
| 本次走的是自建 CA 流程 | `flow = self_ca` |
| 本次签发算法是国密 `SM2` | `csr.downstream.algorithm = SM2`，`caIssue.caType = SM2`，`caChain.caType = SM2` |
| 设备 CSR 的主题是 `CN=0002` | `csr.downstream.csrSubject = CN=0002` |
| 设备在本次流程开始前，其实就已经装有旧证书和 CA 链 | `csr.downstream.deviceCertInstalled = true`，`csr.downstream.caChainInstalled = true` |
| 本次下发后，设备证书发生了更新 | `csr.downstream.deviceCertSerial = 19D94F35E52`，而 `install.uploadCert.downstream.deviceCertSerial = 19D94F35E53` |
| 下发后的设备证书签发者是 `Qasky CA Root` | `deviceCertIssuer = C=CN,O=Qasky,CN=Qasky CA Root` |
| 设备当前 CA 链只有 1 张证书 | `caChainCount = 1`，`caChainPemBase64List` 长度为 1 |
| 安装后设备证书和 CA 链都处于已安装状态 | `verification.queryCertStatus.downstream.deviceCertInstalled = true` 且 `caChainInstalled = true` |
| CA 侧状态检查也通过 | `verification.caStatusCheck.success = true` 且 `result = 0` |

## 11. 字段理解时最容易混淆的点

### 11.1 `status` 和 `downstream.result` 不是一回事

| 字段 | 含义 |
| --- | --- |
| `status` | QDNS 对步骤执行是否成功的判定，例如 `SUCCESS` / `FAILED` |
| `downstream.result` | 设备侧或代理侧的结果码，样例中 `0` 表示成功 |

通常两个都成功，才算该步骤真正成功。

### 11.2 `deviceCertInstalled` / `caChainInstalled` 是状态，不一定是“刚刚装上”

这些字段表示的是“该步骤执行完成时，设备当前是否已有证书 / CA 链”。

所以：

- 在 `csr` 阶段出现 `true`，只说明设备之前可能就已经装过。
- 真正安装动作是否成功，优先看 `install.uploadCaChain` 和 `install.uploadCert`。

### 11.3 `xxxPem` 和 `xxxPemBase64` 的区别

| 字段类型 | 含义 |
| --- | --- |
| `xxxPem` | 证书或 CSR 的 PEM 明文，可以直接打开查看 |
| `xxxPemBase64` | 把整个 PEM 文本再次做 Base64 编码后的字符串，更适合 JSON 传输 |

### 11.4 `validDays` 不一定等于最终证书真实有效期

这份样例里：

- `caIssue.validDays = 365`
- 但设备证书回读有效期是 `2026-04-16 08:27:47 GMT` 到 `2036-04-13 08:27:47 GMT`

因此系统展示时，建议：

- “签发请求参数”展示 `validDays`
- “证书真实有效期”展示 `deviceCertNotBefore` / `deviceCertNotAfter`

## 12. 前端或接口消费建议

如果前端需要快速判断这次“自建 CA 签发并下发”是否成功，建议按下面顺序看：

1. 顶层是否成功返回 `code = 200`
2. `csr.status == SUCCESS`
3. `install.uploadCaChain.status == SUCCESS`
4. `install.uploadCert.status == SUCCESS`
5. `verification.queryCertStatus.status == SUCCESS`
6. `verification.readDeviceCert.status == SUCCESS`
7. `verification.caStatusCheck.success == true`

如果前端需要展示“安装后的最终证书是谁”，优先显示这些字段：

| 展示目标 | 建议字段 |
| --- | --- |
| 当前设备证书序列号 | `verification.readDeviceCert.downstream.deviceCertSerial` |
| 当前设备证书指纹 | `verification.readDeviceCert.downstream.deviceCertFingerprint` |
| 当前设备证书主题 | `verification.readDeviceCert.downstream.deviceCertSubject` |
| 当前设备证书签发者 | `verification.readDeviceCert.downstream.deviceCertIssuer` |
| 当前设备证书有效期 | `verification.readDeviceCert.downstream.deviceCertNotBefore` / `deviceCertNotAfter` |
| 当前 CA 链数量 | `verification.queryCertStatus.downstream.caChainCount` |

## 13. 一句话总结

这份返回结果不是单个接口回包，而是一份“完整流程报告”：

- `csr` 说明设备拿到了什么 CSR
- `caIssue` 说明 CA 签发出了什么设备证书
- `caChain` 说明 CA 提供了什么证书链
- `install` 说明证书链和设备证书有没有真正装到设备上
- `verification` 说明装完以后设备当前最终是什么状态
