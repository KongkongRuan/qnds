# QDNS 采集状态字段说明

## 1. 说明

本文档说明 `QDNS` 完成 SNMP 采集后写入 Redis 的设备状态对象结构，以及各字段的含义、单位和理解方式。

适用对象：

- Redis 中缓存的设备最新状态
- `/api/device/status`
- `/api/device/status/all`
- `QDNS` 采集结果页面

本版文档已根据 `2026-04-16` 的实际 Redis 样例更新，重点补充了：

- `tunnels[].currentRateBps`
- `tunnels[].throughputBytes`
- `ikeSas`
- `ipsecSas`
- `rawOidData` 与顶层字段的对应关系

说明约定：

- “顶层字段”指 Redis 状态对象第一层的字段。
- “表格字段”指 `interfaces`、`tunnels`、`ikeSas`、`ipsecSas` 这类列表字段。
- “原始值”指 `rawOidData` 中保存的字符串结果。
- “标准化值”指 `QDNS` 转换后写入顶层字段或表格字段的值。

## 2. 整体结构

典型状态对象可以分为三部分：

1. 顶层基础状态字段
2. 表格类明细字段
3. 原始 OID 数据

其中：

- 顶层字段用于接口返回、Redis 存储、页面展示和业务判断。
- `interfaces`、`tunnels`、`ikeSas`、`ipsecSas` 用于表达 SNMP Walk 得到的表格型数据。
- `rawOidData` 保留设备返回的原始字符串，主要用于核对和排障。

需要特别注意：

- `rawOidData` 主要保存标量 OID 的原始值，不等同于把所有表格数据都平铺进去。
- 像 `tunnels[].currentRateBps`、`tunnels[].throughputBytes` 这样的隧道表字段，最终体现在 `tunnels` 列表中，而不是 `rawOidData` 顶层键中。
- `online=true` 且采集成功时，`errorMessage` 往往为空或不出现在 JSON 中。

## 3. 顶层字段说明

| 字段 | 示例 | 类型 | 说明 |
| --- | --- | --- | --- |
| `deviceId` | `8c861bddac67461381ef360246a551ee` | `String` | `QDNS` 内部用于标识设备的主键。通常对应 Redis 中该设备状态对象的唯一标识，不等于模拟器内部原始 `device_id`。 |
| `deviceIp` | `192.168.1.152` | `String` | 设备管理 IP，也是 `QDNS` 发起 SNMP 采集时使用的地址。 |
| `snmpPort` | `161` | `int` | SNMP 端口。默认通常为 `161`。 |
| `online` | `true` | `boolean` | 本次采集是否判定设备在线。`true` 表示本次 SNMP 采集成功并生成了可用状态对象。 |
| `errorMessage` | 空或未返回 | `String` | 采集失败时的错误信息。在线且采集成功时通常为空，序列化时也可能不返回。 |
| `sysDescr` | `Quantum VPN Gateway QV-2000 V3.2.7-build20260413` | `String` | 设备系统描述，来源于标准 System MIB 的 `sysDescr`。通常包含产品名称、型号和版本。 |
| `sysObjectID` | `1.3.6.1.4.1.99999.100.1` | `String` | 设备厂商对象标识，用于区分厂商和私有 MIB 分支。 |
| `sysUpTime` | `0` | `long` | 设备运行时间的标准化数值。当前如果原始值是 `0:00:07.31` 这种显示格式，转换失败后可能回落为 `0`，排障时要结合 `rawOidData.sysUpTime` 一起看。 |
| `sysContact` | `admin@qasky.com` | `String` | 设备联系人信息。 |
| `sysName` | `QV-GW-0002` | `String` | 设备系统名称，可视为主机名。 |
| `sysLocation` | `Site-0002` | `String` | 设备部署位置。 |
| `cpuUsage` | `17` | `int` | CPU 使用率。当前实现映射自 `ssCpuUser`，表示用户态占用百分比，不等于严格意义上的总 CPU 使用率。 |
| `cpuIdle` | `82` | `int` | CPU 空闲百分比，对应 `ssCpuIdle`。 |
| `memTotalKb` | `16777216` | `long` | 物理内存总量，单位 `KB`。 |
| `memAvailKb` | `9264462` | `long` | 可用物理内存，单位 `KB`。 |
| `memUsagePercent` | `44` | `int` | 内存使用百分比，通常按 `(总内存 - 可用内存) / 总内存` 计算后取整。 |
| `diskPercent` | `23` | `int` | 磁盘使用率百分比。 |
| `vpnDeviceType` | `1` | `int` | 厂商私有 MIB 中定义的设备类型编码。精确含义以设备 MIB 或业务约定为准。 |
| `vpnFirmwareVersion` | `V3.2.7-build20260413` | `String` | 固件版本。 |
| `vpnSerialNumber` | `QASKY-QV-2000-0002` | `String` | 设备序列号。 |
| `vpnDeviceStatus` | `1` | `int` | 设备总体运行状态编码。 |
| `vpnMacAddress` | `4A:34:F7:BD:80:02` | `String` | 设备 MAC 地址。 |
| `vpnDeviceModel` | `QV-2000` | `String` | 设备型号。 |
| `vpnVendor` | `QASKY` | `String` | 设备厂商。 |
| `tunnelCount` | `4` | `int` | 隧道数量。通常表示当前可见的 VPN 隧道条目数。正常情况下应接近 `tunnels.length`。 |
| `tunnels` | 见第 5 节 | `List<TunnelStatus>` | 隧道明细列表。每个元素包含隧道状态、累计流量、当前速率和时间窗吞吐量。 |
| `ifNumber` | `3` | `int` | 网络接口数量，来源于标准 MIB `ifNumber`。 |
| `interfaces` | 见第 4 节 | `List<InterfaceStatus>` | 网络接口明细列表，由 `ifTable` 和 `ifXTable` 拼装而成。 |
| `wirelessStatus` | `1` | `int` | 无线模块状态编码。当前样例中为 `1`，表示无线相关数据有效；精确枚举值以设备 MIB 为准。 |
| `wirelessInOctets` | `725872698515` | `long` | 无线接口入方向累计字节数。 |
| `wirelessOutOctets` | `735425943304` | `long` | 无线接口出方向累计字节数。 |
| `cryptoCardStatus` | `1` | `int` | 密码卡状态编码。当前样例中为 `1`，说明密码卡状态正常或有效。最终含义以设备 MIB 为准。 |
| `cryptoCardCallCount` | `295190` | `long` | 密码卡累计调用次数。 |
| `cryptoCardAlgorithms` | `SM1,SM2,SM3,SM4` | `String` | 密码卡支持或启用的算法列表。 |
| `cryptoCardModel` | `SJJ1012-A` | `String` | 密码卡型号。 |
| `cryptoCardErrorCount` | `0` | `long` | 密码卡错误次数累计值。 |
| `cryptoCardCompliance` | `1` | `int` | 密码卡合规状态编码。 |
| `ikeCount` | `4` | `int` | IKE SA 数量。通常应接近 `ikeSas.length`。 |
| `ikeSas` | 见第 6 节 | `List<IkeSaStatus>` | IKE SA 明细列表。 |
| `ipsecSas` | 见第 7 节 | `List<IpsecSaStatus>` | IPsec SA 明细列表。当前对象没有单独的 `ipsecCount` 顶层字段。 |
| `firewallRuleCount` | `0` | `int` | 防火墙规则总数。 |
| `firewallAclCount` | `0` | `int` | ACL 条目数。 |
| `firewallSnatCount` | `0` | `int` | SNAT 条目数。 |
| `firewallDnatCount` | `0` | `int` | DNAT 条目数。 |
| `firewallWhitelistCount` | `0` | `int` | 白名单条目数。 |
| `routeIpv4Count` | `0` | `int` | IPv4 路由条目数。 |
| `routeIpv6Count` | `0` | `int` | IPv6 路由条目数。 |
| `routeTotalCount` | `0` | `int` | 路由总条目数。 |
| `rawOidData` | 见第 8 节 | `Map<String,String>` | 标量 OID 的原始采集结果，键为逻辑名，值为原始字符串。 |
| `collectTime` | `2026-04-16 15:20:17` | `String/Date` | 本次状态写入 Redis 的采集时间。对外序列化格式为 `yyyy-MM-dd HH:mm:ss`。 |

## 4. interfaces 字段说明

`interfaces` 是接口状态列表，每个元素对应一块网络接口，通常由 `ifTable` 和 `ifXTable` 数据组合而成。

### 4.1 单个接口对象字段

| 字段 | 示例 | 类型 | 说明 |
| --- | --- | --- | --- |
| `index` | `1` | `int` | 接口索引，对应 MIB 中的 `ifIndex`。 |
| `name` | `eth0` | `String` | 接口名称。通常优先使用 `ifName`，如果设备没有该字段，也可能回退为 `ifDescr`。 |
| `ifType` | `6` | `int` | 接口类型编码。`6` 常表示以太网接口，`243` 这类值通常表示厂商定义或特殊接口类型。 |
| `speed` | `1000000000` | `long` | 32 位接口速率字段，单位通常为 `bit/s`。 |
| `adminStatus` | `1` | `int` | 管理状态编码。常见约定中 `1` 表示 up。 |
| `operStatus` | `1` | `int` | 运行状态编码。常见约定中 `1` 表示 up。 |
| `inOctets` | `1981692687` | `long` | 32 位入方向累计字节数，大流量场景下可能回绕。 |
| `outOctets` | `2041832979` | `long` | 32 位出方向累计字节数，大流量场景下可能回绕。 |
| `hcInOctets` | `575424867717` | `long` | 64 位入方向累计字节数，更适合高吞吐场景。 |
| `hcOutOctets` | `1012509973492` | `long` | 64 位出方向累计字节数。 |
| `highSpeed` | `1000` | `long` | 高速接口速率字段，单位通常为 `Mbps`。 |

### 4.2 样例中接口的理解

| 接口 | 说明 |
| --- | --- |
| `eth0` | 千兆以太接口，管理与运行状态都为正常。 |
| `eth1` | 第二块千兆以太接口，管理与运行状态都为正常。 |
| `4g0` | 特殊类型接口，通常可理解为 4G/蜂窝链路接口；其累计流量与顶层无线流量字段具有关联性。 |

## 5. tunnels 字段说明

`tunnels` 是隧道状态列表。当前样例中共有 `4` 条隧道，且每条都已经包含累计流量、当前速率和吞吐量两个动态指标。

### 5.1 单条隧道对象字段

| 字段 | 示例 | 类型 | 说明 |
| --- | --- | --- | --- |
| `index` | `1` | `int` | 隧道索引。 |
| `name` | `tunnel-0002-01` | `String` | 隧道名称。 |
| `status` | `1` | `int` | 隧道状态编码。当前模拟器和页面中 `1` 常表示运行中，精确枚举以设备 MIB 为准。 |
| `inOctets` | `7782993` | `long` | 隧道入方向累计字节数。 |
| `outOctets` | `6015033` | `long` | 隧道出方向累计字节数。 |
| `localAddr` | `192.168.1.152` | `String` | 隧道本端地址。 |
| `remoteAddr` | `172.16.186.109` | `String` | 隧道对端地址。 |
| `ikeRuleName` | `tunnel-0002-01` | `String` | 与该隧道关联的 IKE 规则名称。 |
| `encryptAlgo` | `SM4-CBC` | `String` | 隧道加密算法。 |
| `keySource` | `1` | `int` | 密钥来源编码。当前页面通常把 `1` 展示为“协商”，但最终仍应以设备定义为准。 |
| `currentRateBps` | `18300565` | `long` | 隧道当前总速率，单位 `bps`。表示当前采样点下入方向和出方向合计的瞬时速率。 |
| `throughputBytes` | `13798026` | `long` | 隧道最近固定时间窗内的吞吐量，单位 `bytes`。当前实现口径为“最近 60 秒传输总量”。 |

### 5.2 隧道累计值与动态值的区别

需要区分下面三类指标：

- `inOctets` / `outOctets`
  这是累计字节数，反映设备启动以来或计数器重置以来的总量。
- `currentRateBps`
  这是当前速率，反映当前采样点下隧道瞬时传输速度，单位是 `bps`。
- `throughputBytes`
  这是时间窗吞吐量，反映最近固定时间窗口内实际传输了多少数据，当前窗口为 `60` 秒，单位是 `bytes`。

因此：

- `currentRateBps` 适合看“现在跑得多快”
- `throughputBytes` 适合看“最近一段时间一共跑了多少数据”
- `inOctets` / `outOctets` 适合做长期累计、差值计算和趋势分析

## 6. ikeSas 字段说明

`ikeSas` 是 IKE SA 状态列表。当前样例中 `ikeCount=4`，并且 `ikeSas` 中有 4 条记录。

### 6.1 单条 IKE SA 对象字段

| 字段 | 示例 | 类型 | 说明 |
| --- | --- | --- | --- |
| `index` | `1` | `int` | IKE SA 索引。 |
| `localAddr` | `192.168.1.152` | `String` | 本端地址。 |
| `remoteAddr` | `172.16.186.109` | `String` | 对端地址。 |
| `status` | `1` | `int` | IKE SA 状态编码。当前模拟器和页面中 `1` 常表示已建立。 |
| `version` | `2` | `int` | IKE 版本，当前样例为 IKEv2。 |
| `authMethod` | `1` | `int` | 认证方式编码。 |
| `encryptAlgo` | `SM4-CBC` | `String` | 加密算法。 |
| `hashAlgo` | `SM3` | `String` | 哈希算法。 |
| `dhGroup` | `group14` | `String` | DH 组。 |
| `rekeyRemain` | `3593` | `int` | 距离下次 Rekey 的剩余秒数。 |
| `dpdEnabled` | `1` | `int` | 是否启用 DPD 的编码值。 |

## 7. ipsecSas 字段说明

`ipsecSas` 是 IPsec SA 状态列表。当前样例中共有 4 条记录，分别关联到 `tunnelId=1..4`。

### 7.1 单条 IPsec SA 对象字段

| 字段 | 示例 | 类型 | 说明 |
| --- | --- | --- | --- |
| `index` | `1` | `int` | IPsec SA 索引。 |
| `tunnelId` | `1` | `int` | 关联的隧道索引，可与 `tunnels[].index` 对应。 |
| `protocol` | `1` | `int` | 协议编码。当前模拟器/页面通常将 `1` 解释为 `ESP`，最终以设备定义为准。 |
| `encryptAlgo` | `SM4-CBC` | `String` | 加密算法。 |
| `authAlgo` | `SM3-HMAC` | `String` | 认证算法。 |
| `keySource` | `1` | `int` | 密钥来源编码。 |
| `rekeyRemain` | `593` | `int` | 距离下次 Rekey 的剩余秒数。 |
| `workMode` | `1` | `int` | 工作模式编码。 |
| `establishTime` | `177632380538` | `long` | 建立时间相关数值。当前实现按 `long` 直接保存，具体单位和起算方式需以设备私有 MIB 为准，不建议在未确认前直接按 Unix 时间戳解释。 |
| `inBytes` | `9151575` | `long` | IPsec SA 入方向累计字节数。 |
| `outBytes` | `6973933` | `long` | IPsec SA 出方向累计字节数。 |

## 8. rawOidData 字段说明

`rawOidData` 保存采集器从设备直接取回的原始字符串值，主要用于：

- 排查设备返回值是否异常
- 核对标准化转换前后的差异
- 补充顶层字段未直接表达的原始信息

### 8.1 当前样例中出现的 rawOidData 键

| 分类 | 键 | 说明 |
| --- | --- | --- |
| 系统信息 | `sysDescr`、`sysObjectID`、`sysUpTime`、`sysContact`、`sysName`、`sysLocation` | 对应系统基础信息的原始值。 |
| 接口与资源 | `ifNumber`、`memTotalReal`、`memAvailReal`、`dskPercent`、`ssCpuUser`、`ssCpuIdle` | 对应接口数量、内存、磁盘、CPU 等原始值。 |
| 设备信息 | `vpnDeviceType`、`vpnFirmwareVersion`、`vpnSerialNumber`、`vpnDeviceStatus`、`vpnMacAddress`、`vpnDeviceModel`、`vpnVendor` | 厂商私有 MIB 中的设备信息原始值。 |
| 统计计数 | `tunnelCount`、`wirelessStatus`、`wirelessInOctets`、`wirelessOutOctets`、`cryptoCardStatus`、`cryptoCardCallCount`、`cryptoCardAlgorithms`、`cryptoCardModel`、`cryptoCardErrorCount`、`cryptoCardCompliance`、`ikeCount`、`firewallRuleCount`、`firewallAclCount`、`firewallSnatCount`、`firewallDnatCount`、`firewallWhitelistCount`、`routeIpv4Count`、`routeIpv6Count`、`routeTotalCount` | 对应顶层统计字段的原始字符串值。 |
| 扩展可写项 | `deviceConfig`、`rateLimitRule` | 与配置下发或限流规则相关的扩展 OID 原始值。当前样例中都为空字符串。 |

### 8.2 rawOidData 与顶层字段的关系

可以简单理解为：

- `rawOidData` 保留“设备原话”
- 顶层字段和表格字段保留“QDNS 标准化后的结果”

例如：

- `rawOidData.memTotalReal = "16777216"`
- 顶层 `memTotalKb = 16777216`

再比如：

- `rawOidData.sysUpTime = "0:00:07.31"`
- 顶层 `sysUpTime = 0`

第二种情况说明：原始值存在，但当前转换逻辑没有把该显示格式成功转成数值，因此排障时不能只看顶层字段，还要看 `rawOidData`。

### 8.3 为什么 rawOidData 里看不到隧道当前速率和吞吐量

这是正常的。

原因是：

- `rawOidData` 当前主要保存标量 OID 的原始结果
- `currentRateBps`、`throughputBytes` 属于隧道表中的列
- 它们最终保存在 `tunnels[]` 每个元素里，而不是 `rawOidData` 顶层键中

因此看到：

- `tunnels[0].currentRateBps = 18300565`
- `tunnels[0].throughputBytes = 13798026`

但在 `rawOidData` 中没有同名键，这是符合当前实现的。

## 9. 计数、速率和时间字段的理解建议

### 9.1 累计计数字段

下面这些字段通常是累计值，不是“当前瞬时值”：

- `interfaces[].inOctets`
- `interfaces[].outOctets`
- `interfaces[].hcInOctets`
- `interfaces[].hcOutOctets`
- `tunnels[].inOctets`
- `tunnels[].outOctets`
- `wirelessInOctets`
- `wirelessOutOctets`
- `cryptoCardCallCount`
- `cryptoCardErrorCount`
- `ipsecSas[].inBytes`
- `ipsecSas[].outBytes`

这些字段更适合：

- 做两次采集之间的差值计算
- 估算带宽、吞吐量和增长量
- 做趋势统计和图表展示

### 9.2 动态速率字段

下面这些字段更偏向“当前状态”：

- `tunnels[].currentRateBps`

它表示当前采样点的瞬时速率，单位是 `bps`，更适合做实时监控和状态页展示。

### 9.3 时间窗吞吐量字段

下面这些字段更偏向“最近一段时间总量”：

- `tunnels[].throughputBytes`

当前实现中，它表示最近 `60` 秒内该隧道总共传输了多少字节，单位是 `bytes`。

### 9.4 时间相关字段

需要区分以下字段：

- `collectTime`
  表示 `QDNS` 把本次状态写入 Redis 的时间。
- `sysUpTime`
  表示设备运行时间的标准化结果，但当前对某些显示格式转换还不完善。
- `ipsecSas[].establishTime`
  当前以 `long` 保存的设备原始建立时间相关值，具体单位要以设备 MIB 为准。

## 10. 状态编码字段的理解建议

以下字段目前都以整数编码形式保存：

- `vpnDeviceType`
- `vpnDeviceStatus`
- `wirelessStatus`
- `cryptoCardStatus`
- `cryptoCardCompliance`
- `interfaces[].ifType`
- `interfaces[].adminStatus`
- `interfaces[].operStatus`
- `tunnels[].status`
- `tunnels[].keySource`
- `ikeSas[].status`
- `ikeSas[].authMethod`
- `ikeSas[].dpdEnabled`
- `ipsecSas[].protocol`
- `ipsecSas[].keySource`
- `ipsecSas[].workMode`

这些编码的精确业务含义，最终应以设备私有 MIB 或设备侧约定为准。当前如果没有独立枚举表，建议页面和文档按以下原则处理：

- 原样保留数字值
- 对确定无误的常见值补充中文说明
- 不在没有 MIB 依据时强行解释全部编码

## 11. 常见排障建议

### 11.1 顶层值为 0，但设备看起来是有数据的

优先检查：

- `rawOidData` 中是否有对应原始值
- 该字段是否因为格式转换失败被置为默认值

典型例子：

- `sysUpTime`

### 11.2 计数类字段很大是否正常

正常。因为这些通常是设备启动以来的累计值，不是瞬时值。

### 11.3 数量字段与列表长度不一致

例如：

- `ifNumber` 与 `interfaces.length`
- `tunnelCount` 与 `tunnels.length`
- `ikeCount` 与 `ikeSas.length`

如果不一致，可能原因包括：

- 某些表项在 Walk 时未完整返回
- 某些字段在不同采集模式下刷新频率不同
- 某些设备本身声明数量与实际返回条目有偏差

此时建议同时结合：

- Redis 当前值
- 采集日志
- 原始 SNMP Walk 结果

一起判断。

## 12. 样例对象总结

你给出的这份 Redis 设备状态样例可以概括为：

- 设备在线，系统信息完整
- CPU、内存、磁盘、无线、密码卡指标都已采到
- 接口表已采到，共 3 个接口
- 隧道表已采到，共 4 条隧道，且每条都有 `currentRateBps` 和 `throughputBytes`
- IKE SA 已采到 4 条
- IPsec SA 已采到 4 条
- 防火墙和路由统计当前都为 0
- `rawOidData` 完整度较高，但它不直接承载隧道表中的动态字段
- `sysUpTime` 顶层值与原始值仍有格式差异，属于后续可以继续优化的标准化点
