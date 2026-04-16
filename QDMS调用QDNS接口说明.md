# QDMS 调用 QDNS 接口说明

## 1. 说明

本文档整理了当前 `QDMS` 平台对接 `QDNS` 节点时应调用的北向接口。

当前链路为：

`QDMS -> QDNS -> VPN-Sim`

其中：

- `QDMS` 不直接调用模拟设备。
- 设备运维统一通过 `QDNS /api/unified/*` 进入。
- 证书编排场景额外提供 `QDNS /api/device-certificates/*` 专用接口，用于证书状态同步、自建 CA 签发并下发、第三方证书安装等流程型操作。
- `10005/10006/10007/10008` 已改为由 `QDNS` 通过 HTTP 调用 `VPN-Sim` 的设备接口。
- `QDMS` 传给 `QDNS` 的 `deviceId` 表示 `QDMS` 平台设备 ID，不要求与 `VPN-Sim` 中的 `device_id` 一致。
- `QDNS` 收到运维请求后，会先根据 `deviceId` 在节点缓存 / Redis 设备列表中找到对应的 `deviceIp`、`devicePort`，再按设备地址调用 `VPN-Sim`。

## 2. 基础信息

- QDNS 默认端口：`18023`
- QDMS 调用参数约定：
  - `GET` 接口统一使用固定路径 + 查询参数，不使用 REST 风格路径变量传参。
  - `POST` 接口统一使用固定路径 + JSON 请求体传参，不把业务参数放到 URI 路径中。
- 接口返回风格分两类：
  - `ApiResponse` 风格：`{ "code": 200, "message": "success", "data": ... }`
  - 普通 Map 风格：`{ "code": 200, "message": "...", "data": ... }`

建议 `QDMS` 统一按 `code == 200` 判断 HTTP 业务成功，再结合 `data.status` 或 `task.status` 判断运维任务结果。

## 3. 节点信息接口

### 3.1 获取节点信息

- 方法：`GET`
- 路径：`/api/node/info`
- 说明：用于获取节点 IP、端口、节点标识、JVM 运行信息，可用于节点连通性检测。

返回示例：

```json
{
  "hostname": "qdns-node-01",
  "ip": "192.168.1.37",
  "appName": "qdns",
  "port": 18023,
  "nodeKey": "192.168.1.37:18023",
  "javaVersion": "1.8.0_451",
  "osName": "Windows 11",
  "uptime": "53210ms"
}
```

## 4. 设备同步接口

### 4.1 向节点同步单台设备

- 方法：`POST`
- 路径：`/api/device/sync`
- 说明：`QDMS` 将设备下发给指定 `QDNS` 节点，由节点建立本地设备缓存并纳入采集队列。

请求体字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID；`QDNS` 以此作为北向设备标识 |
| `deviceIp` | 是 | 设备 IP |
| `devicePort` | 否 | 设备 SNMP 端口，默认 `161`；`QDNS` 会结合 `deviceIp`、`devicePort` 定位下游设备 |
| `deviceName` | 否 | 设备名称 |
| `manufacturer` | 否 | 厂商，默认 `QASKY` |
| `deviceType` | 否 | 设备类型，默认 `QVPN` |
| `deviceModel` | 否 | 设备型号 |
| `snmpVersion` | 否 | `SNMPv2c` 或 `SNMPv3`，默认 `SNMPv3` |
| `snmpV3Username` | 否 | SNMPv3 用户名 |
| `snmpV3AuthPassword` | 否 | SNMPv3 认证密码 |
| `snmpV3PrivPassword` | 否 | SNMPv3 加密密码 |
| `snmpV3AuthProtocol` | 否 | SNMPv3 认证协议 |
| `snmpV3PrivProtocol` | 否 | SNMPv3 加密协议 |
| `sshUsername` | 否 | 运维账号，当前 HTTP 运维链路不依赖该字段 |
| `sshPassword` | 否 | 运维密码，当前 HTTP 运维链路不依赖该字段 |
| `sshPort` | 否 | SSH 端口，默认 `22` |

请求示例：

```json
{
  "deviceId": "qdms-device-0001",
  "deviceIp": "192.168.1.151",
  "devicePort": 161,
  "deviceName": "QDMS-VPN-0001",
  "manufacturer": "QASKY",
  "deviceType": "QVPN",
  "deviceModel": "QV-2000",
  "snmpVersion": "SNMPv3",
  "snmpV3Username": "qasky",
  "snmpV3AuthPassword": "qasky1234",
  "snmpV3PrivPassword": "QaSky20191818",
  "snmpV3AuthProtocol": "SM3",
  "snmpV3PrivProtocol": "SM4"
}
```

返回示例：

```json
{
  "code": 200,
  "message": "设备同步成功",
  "trapTargetSet": true,
  "data": {
    "id": "qdms-device-0001",
    "name": "QDMS-VPN-0001",
    "deviceIp": "192.168.1.151",
    "devicePort": "161",
    "protocol": "SNMPv3"
  }
}
```

## 5. 设备查询与采集接口

### 5.1 获取节点设备列表

- 方法：`GET`
- 路径：`/api/device/list`
- 说明：查询节点当前持有的设备列表。

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `page` | 否 | 页码，从 `0` 开始 |
| `size` | 否 | 每页大小；不传或小于等于 `0` 时返回全部 |

### 5.2 获取单台设备最新状态

- 方法：`GET`
- 路径：`/api/device/status`
- 说明：返回节点最近一次采集到的状态。

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `id` | 是 | `QDMS` 平台设备 ID |

### 5.3 获取全部设备最新状态

- 方法：`GET`
- 路径：`/api/device/status/all`
- 说明：返回节点缓存中的所有设备状态。

### 5.4 触发单台设备采集

- 方法：`POST`
- 路径：`/api/collector/collect`
- 说明：手动触发单台设备采集，常用于运维完成后刷新版本/状态。

请求示例：

```json
{
  "id": "qdms-device-0001"
}
```

### 5.5 触发全量采集

- 方法：`POST`
- 路径：`/api/collector/collectAll`
- 说明：手动触发节点对全部设备做一次采集。

## 6. 日志拉取接口

### 6.1 拉取单台设备日志

- 方法：`POST`
- 路径：`/api/device/logs/fetch`

请求示例：

```json
{
  "deviceId": "0001",
  "count": 50,
  "since": null
}
```

### 6.2 批量拉取指定设备日志

- 方法：`POST`
- 路径：`/api/device/logs/batch`

请求示例：

```json
{
  "deviceIds": ["0001", "0002"],
  "count": 50
}
```

### 6.3 拉取全部设备日志

- 方法：`POST`
- 路径：`/api/device/logs/fetch-all`

请求示例：

```json
{
  "count": 50
}
```

## 7. 统一运维接口

### 7.1 提交统一运维命令

- 方法：`POST`
- 路径：`/api/unified/command`
- 返回风格：`ApiResponse`

通用请求体：

```json
{
  "requestId": "optional-request-id",
  "deviceId": "qdms-device-0001",
  "code": 10007,
  "operation": "upgrade",
  "payload": {
    "version": "V4.0.0-build20260415"
  },
  "operator": "qdms"
}
```

通用返回体：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "requestId": "optional-request-id",
    "taskId": "bdf7b7f0d5f64c5a9d73b51d5574b401",
    "deviceId": "qdms-device-0001",
    "code": 10007,
    "operation": "upgrade",
    "mode": "ASYNC",
    "status": "ACCEPTED",
    "statusMessage": "任务已受理",
    "downstream": null
  }
}
```

### 7.2 查询统一运维异步任务

- 方法：`GET`
- 路径：`/api/unified/task`
- 返回风格：`ApiResponse`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `taskId` | 是 | 统一运维异步任务 ID |

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "taskId": "bdf7b7f0d5f64c5a9d73b51d5574b401",
    "requestId": "optional-request-id",
    "deviceId": "qdms-device-0001",
    "code": 10007,
    "operation": "upgrade",
    "mode": "ASYNC",
    "status": "SUCCESS",
    "statusMessage": "升级完成，当前版本: V4.0.0-build20260415",
    "downstream": {
      "action": "upgrade",
      "targetVersion": "V4.0.0-build20260415",
      "firmwareVersion": "V4.0.0-build20260415"
    },
    "createdAt": "2026-04-15 17:30:00",
    "startedAt": "2026-04-15 17:30:00",
    "finishedAt": "2026-04-15 17:31:03"
  }
}
```

## 8. 统一运维能力编码表

当前 `QDNS` 已注册的统一运维能力如下。

### 8.1 SSL VPN 配置类

| code | operation | mode | 说明 | 必填字段 |
| --- | --- | --- | --- | --- |
| `10000` | `set_base_nego` | `SYNC` | 基础协商配置 | `interface_name`, `local_port`, `local_port_nat`, `ike_ver`, `auth_type` |
| `10000` | `set_anonymous_nego` | `SYNC` | 匿名协商配置 | `status`, `ph1_algs`, `ph2_algs`, `ph1_ttl_range`, `ph2_ttl_range`, `encap_protocol`, `encap_mode` |
| `10000` | `add_policy` | `SYNC` | 新增策略 | `name`, `status`, `tu_name`, `src_addr`, `dst_addr`, `action`, `protocol` |
| `10000` | `update_policy` | `SYNC` | 修改策略 | `name`, `id`, `status`, `tu_name`, `src_addr`, `dst_addr`, `action`, `protocol` |
| `10000` | `add_tunnel` | `SYNC` | 新增隧道 | `name`, `local_addr_type`, `local_addr`, `remote_addr_type`, `remote_addr`, `ph1_algs`, `ph2_algs`, `encap_mode`, `ph1_ttl`, `ph2_ttl`, `encap_protocol`, `dpd_state`, `dpd_interval` |
| `10000` | `update_tunnel` | `SYNC` | 修改隧道 | `name`, `id`, `local_addr_type`, `local_addr`, `remote_addr_type`, `remote_addr`, `ph1_algs`, `ph2_algs`, `encap_mode`, `ph1_ttl`, `ph2_ttl`, `encap_protocol`, `dpd_state`, `dpd_interval` |
| `10001` | `add` | `SYNC` | 新增 ACL | `name`, `action`, `protocol`, `src_addr`, `dst_addr`, `time_limit_state`, `begin_time`, `end_time` |
| `10001` | `delete` | `SYNC` | 删除 ACL | `id` |
| `10002` | `add` | `SYNC` | 新增路由 | `dst_addr`, `mask`, `next_ip`, `interface_name`, `weight`, `distance` |
| `10002` | `delete` | `SYNC` | 删除路由 | `dst_addr`, `mask`, `next_ip`, `interface_name` |
| `10003` | `add` | `SYNC` | 新增白名单 | `name`, `type`, `addr`, `state` |
| `10003` | `delete` | `SYNC` | 删除白名单 | `id` |
| `10003` | `update_state` | `SYNC` | 修改白名单状态 | `id`, `state` |
| `10004` | `add_snat` | `SYNC` | 新增 SNAT | `name`, `src_addr`, `dst_addr` |
| `10004` | `delete_snat` | `SYNC` | 删除 SNAT | `id` |
| `10004` | `add_dnat` | `SYNC` | 新增 DNAT | `name`, `protocol`, `local_addr`, `local_port`, `external_addr`, `external_port` |
| `10004` | `delete_dnat` | `SYNC` | 删除 DNAT | `id` |

### 8.2 设备运维类

| code | operation | mode | 说明 | 必填字段 |
| --- | --- | --- | --- | --- |
| `10005` | `backup` | `SYNC` | 导出设备备份 zip | 无 |
| `10006` | `restore` | `SYNC` | 恢复备份 zip | `zipBase64` |
| `10007` | `upgrade` | `ASYNC` | 升级固件并轮询版本刷新 | `version` |
| `10008` | `reboot` | `ASYNC` | 重启设备并轮询恢复在线 | 无 |

### 8.3 证书类

| code | operation | mode | 说明 | 必填字段 |
| --- | --- | --- | --- | --- |
| `10009` | `upload_ca_chain` | `SYNC` | 下发 CA 证书链 | 无 |
| `10009` | `upload_root_cert` | `SYNC` | 兼容别名，下发 CA 证书链 | 无 |
| `10010` | `upload_cert` | `SYNC` | 下发设备证书 | 无 |
| `10011` | `query_cert_status` | `SYNC` | 查询证书状态 | 无 |
| `10012` | `read_device_cert` | `SYNC` | 读取设备证书 | 无 |
| `10013` | `read_ca_chain` | `SYNC` | 读取 CA 证书链 | 无 |
| `10014` | `read_device_csr` | `SYNC` | 读取设备 CSR | 无 |

说明：

- `10009` ~ `10014` 为证书原子能力，适合 `QDMS` 自行编排读 CSR、下发 CA 链、下发设备证书等步骤。
- 如平台需要“自建 CA 签发并下发”或“第三方证书安装”这类流程型能力，建议直接调用下文 `8.4` 的证书编排接口，而不是由平台自行拆分多个原子接口。

### 8.4 证书编排接口

说明：

- 路径前缀：`/api/device-certificates`
- 返回风格：`ApiResponse`
- 适用场景：证书状态同步、证书读取、CSR 生成 / 回读、自建 CA 签发并自动下发、第三方证书安装
- `QDMS` 调用这些接口时，`GET` 参数统一通过查询参数传递，`POST` 参数统一通过请求体传递，不使用路径变量形式传参。

接口清单：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/device-certificates/status` | 查询设备证书状态 |
| `POST` | `/api/device-certificates/sync` | 同步设备当前证书信息，聚合状态 / 设备证书 / CA 链 / CSR |
| `GET` | `/api/device-certificates/device-cert` | 读取设备证书 |
| `GET` | `/api/device-certificates/ca-chain` | 读取 CA 证书链 |
| `GET` | `/api/device-certificates/csr` | 读取或生成设备 CSR |
| `POST` | `/api/device-certificates/issue/self-ca` | 调用自建 CA 完成签发，并自动下发 CA 证书链和设备证书 |
| `POST` | `/api/device-certificates/install/third-party` | 下发第三方设备证书和 CA 证书链，并回读校验 |

`GET /api/device-certificates/status` 查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |

`POST /api/device-certificates/sync` 请求体字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |

`GET /api/device-certificates/device-cert` 查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |

`GET /api/device-certificates/ca-chain` 查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |

`GET /api/device-certificates/csr` 查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |
| `algorithm` | 否 | 证书算法，支持 `RSA` / `SM2` |
| `forceRegenerate` | 否 | 是否强制重新生成 CSR |
| `commonName` | 否 | 生成 CSR 时使用的证书主题 `CN` |

`POST /api/device-certificates/issue/self-ca` 请求体字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |
| `algorithm` | 否 | 证书算法，支持 `RSA` / `SM2`；不传时按设备当前 CSR 返回结果判断 |
| `forceRegenerate` | 否 | 是否强制重新生成 CSR |
| `commonName` | 否 | 生成 CSR 时使用的证书主题 `CN` |
| `validDays` | 否 | 自建 CA 签发有效期天数 |

`POST /api/device-certificates/install/third-party` 请求体字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `deviceId` | 是 | `QDMS` 平台设备 ID |
| `algorithm` | 否 | 证书算法，支持 `RSA` / `SM2` |
| `forceRegenerate` | 否 | 是否强制重新生成 CSR |
| `commonName` | 否 | 生成 CSR 时使用的证书主题 `CN` |
| `deviceCertPemBase64` | 是 | 设备证书 PEM 文本的 Base64；也兼容直接传 PEM 文本 |
| `caChainPemBase64List` | 是 | CA 证书链 PEM 文本 Base64 列表；也兼容直接传 PEM 文本列表 |

## 9. 运维与证书接口示例

### 9.1 导出备份

请求：

```json
{
  "deviceId": "qdms-device-0001",
  "code": 10005,
  "operation": "backup",
  "payload": {}
}
```

成功返回中的 `downstream` 示例：

```json
{
  "action": "backup",
  "filename": "192.168.1.151_backup_20260415173000.zip",
  "contentType": "application/zip",
  "zipBase64": "UEsDBBQAAAAI...",
  "size": 2048
}
```

### 9.2 恢复备份

请求：

```json
{
  "deviceId": "qdms-device-0001",
  "code": 10006,
  "operation": "restore",
  "payload": {
    "zipBase64": "UEsDBBQAAAAI...",
    "filename": "192.168.1.151_backup_20260415173000.zip"
  }
}
```

### 9.3 升级固件

请求：

```json
{
  "deviceId": "qdms-device-0001",
  "code": 10007,
  "operation": "upgrade",
  "payload": {
    "version": "V4.0.0-build20260415"
  }
}
```

说明：

- `upgrade` 为异步任务。
- `QDNS` 会在后台持续轮询 `VPN-Sim`，直到设备版本变成目标版本后才把任务状态写为 `SUCCESS`。

### 9.4 重启设备

请求：

```json
{
  "deviceId": "qdms-device-0001",
  "code": 10008,
  "operation": "reboot",
  "payload": {}
}
```

说明：

- `reboot` 为异步任务。
- `QDNS` 会在后台轮询设备状态，恢复在线后任务才结束。

### 9.5 查询或生成设备 CSR

路径：

```http
GET /api/device-certificates/csr
```

查询参数示例：

```json
{
  "deviceId": "qdms-device-0001",
  "algorithm": "SM2",
  "forceRegenerate": false,
  "commonName": "qdms-device-0001"
}
```

返回示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "deviceId": "qdms-device-0001",
    "csr": {
      "code": 10014,
      "operation": "read_device_csr",
      "mode": "SYNC",
      "status": "SUCCESS",
      "statusMessage": "CSR 读取成功",
      "downstream": {
        "algorithm": "SM2",
        "csrPemBase64": "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURSBSRVFVRVNULS0tLS0K..."
      }
    }
  }
}
```

### 9.6 自建 CA 签发并下发

请求：

```json
{
  "deviceId": "qdms-device-0001",
  "algorithm": "SM2",
  "forceRegenerate": false,
  "commonName": "qdms-device-0001",
  "validDays": 365
}
```

调用方式：

```http
POST /api/device-certificates/issue/self-ca
Content-Type: application/json
```

返回中的关键字段说明：

- `flow=self_ca`：表示本次走自建 CA 编排流程。
- `csr`：设备 CSR 读取 / 生成结果。
- `caIssue`：调用自建 CA 服务签发得到的证书结果。
- `caChain`：从自建 CA 服务获取的 CA 证书链。
- `install`：向设备下发 CA 链和设备证书的执行结果。
- `verification`：安装完成后的状态回读与证书校验结果。

### 9.7 安装第三方证书

请求：

```json
{
  "deviceId": "qdms-device-0001",
  "algorithm": "RSA",
  "forceRegenerate": false,
  "commonName": "qdms-device-0001",
  "deviceCertPemBase64": "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCg...",
  "caChainPemBase64List": [
    "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0tCg..."
  ]
}
```

调用方式：

```http
POST /api/device-certificates/install/third-party
Content-Type: application/json
```

说明：

- 该接口会先读取 / 生成设备 CSR，再下发第三方 CA 链和设备证书。
- 接口返回中会给出安装结果以及回读校验信息，便于 `QDMS` 直接展示证书安装是否成功。

## 10. 对接建议

- 平台首次下发设备到节点时，先调用 `/api/device/sync`。
- 平台只需要维护自己的 `deviceId` 与设备地址信息；`QDNS` 不要求 `deviceId` 与模拟器 `device_id` 相同。
- 常规设备运维统一走 `/api/unified/command`。
- 证书原子能力如读取 CSR、下发 CA 链、下发设备证书，可走 `/api/unified/command` 的 `10009` ~ `10014`。
- 如需“自建 CA 签发并下发”或“第三方证书安装”这类流程型证书能力，建议直接调用 `/api/device-certificates/*`。
- 对于 `ASYNC` 操作，必须继续调用 `/api/unified/task` 并通过查询参数传 `taskId` 轮询状态，不要只看受理返回。
- 备份接口返回的是 `zipBase64`，平台若需要下载文件，可自行转为 zip 文件流。
- 升级或恢复完成后，如平台需要立刻刷新设备状态，可再调用一次 `/api/collector/collect`，并在请求体中传 `id` 或 `deviceId`。
