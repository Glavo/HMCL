# HMCL 公告系统设计

HMCL 从静态 JSON 文件获取公告，在主页或弹窗中展示，并在本地保存缓存、展示记录和关闭记录。

## 公告格式

公告地址为 `https://hmcl.glavo.site/announcements/v1.json`。
文件包含全部有效公告，格式为 JSON 数组；空数组表示没有公告，从列表中移除公告即表示撤回。
字段结构见 [JSON Schema](schemas/announcements/1.0.0.json)。

```json
[
  {
    "id": "019976a8-04af-7442-9ffd-ebdaf9bbf68e",
    "type": "board",
    "category": "service",
    "priority": 100,
    "severity": "warning",
    "title": {
      "default": "Service disruption",
      "en": "Service disruption"
    },
    "content": "**Account sign-in may be temporarily unavailable.**\n\nPlease try again later.",
    "url": "https://example.com/status",
    "expiresAt": "2026-09-13T00:00:00Z"
  }
]
```

| 字段            | 必填 | 含义                                                               |
|-----------------|------|--------------------------------------------------------------------|
| `id`            | 是   | 公告唯一标识，使用小写标准文本形式的 UUID v7。                     |
| `type`          | 是   | `board` 在主页展示，`popup` 通过弹窗展示。                         |
| `category`      | 否   | 公告分类，默认为 `general`，用于用户筛选。                         |
| `title`         | 是   | 本地化纯文本标题。                                                 |
| `content`       | 是   | 本地化 Markdown 正文。                                             |
| `priority`      | 否   | 32 位整数，默认为 `0`，值越大越靠前。                              |
| `severity`      | 否   | `info`、`warning` 或 `critical`，默认为 `info`，用于区分展示样式。 |
| `requiresShown` | 否   | 依赖的公告 ID；只有曾向用户展示过该公告，当前公告才可展示。        |
| `url`           | 否   | 本地化详情链接，点击后在浏览器打开。                               |
| `expiresAt`     | 否   | UTC 失效时间，如 `2026-09-13T00:00:00Z`；省略表示不自动过期。      |

可选字段未设置时省略。同一 ID 的内容更新不重置关闭状态，需要重新提醒时使用新 ID。

## 分类与筛选

每条公告使用一个 `category` 标识其内容分类，与展示方式 `type` 和重要程度 `severity` 独立。

| 分类 | 含义 |
| --- | --- |
| `general` | 一般公告，也是未指定分类时的默认值。 |
| `promotion` | 宣传公告。 |
| `service` | 服务器状态公告，例如宕机、维护和恢复。 |
| `security` | 安全公告。 |

分类使用非空字符串标识，可以增加新分类。
分类开关保存在 HMCL 目录下的 `state/launcher-state.json` 中，使用 `announcementCategoryStates` 对象记录，例如：

```json
{
  "announcementCategoryStates": {
    "promotion": false,
    "security": true
  }
}
```

对象的键为分类标识，值为布尔值：`true` 表示开启，`false` 表示关闭。
没有记录的分类使用客户端定义的该分类默认值；当前分类默认开启。删除分类对应的记录即恢复默认值。
关闭分类后，该分类的公告不在主页展示，也不弹窗；筛选对所有 `severity` 生效。
筛选不会新增 `shown` 或 `closed` 记录，也不删除已有记录；分类重新开启后，按正常展示条件重新判断。
筛选只在客户端生效，不改变下载和缓存的公告列表。

## 本地化与正文

`title`、`content` 和 `url` 接受字符串或语言标签到字符串的对象。
字符串用于所有语言；对象使用 `en`、`zh-Hans`、`zh-Hant` 等语言标签，并提供 `default` 作为回退。
所有文本值均非空，语言选择沿用 HMCL 现有的本地化规则。

正文采用 CommonMark 0.31.2，不启用扩展。原始 HTML 显示为文本，图片仅显示替代文本。
正文链接和详情链接使用绝对 HTTPS URL。

## 展示与关闭

主页公告和弹窗分别按 `priority` 降序排列，相同时按 ID 字典序降序排列。
弹窗在主界面就绪后依次展示，避免打断正在进行的操作。
已关闭、已过期或被分类筛选器屏蔽的公告不再展示。

每条公告实际展示后，将其 ID 保存到本地 `shown` 集合，仅下载到缓存不算展示。
设置了 `requiresShown` 的公告，只有在其依赖的公告 ID 已存在于 `shown` 中时才可展示。
公告之间保持独立，各自排序、关闭和过期；依赖关系适用于 `board` 和 `popup`，不要求相邻展示。
依赖的公告可以已经关闭、过期或撤回，也不必存在于当前公告列表中。

例如，公告 A 提示服务器宕机；服务恢复后发布公告 B，并将 B 的 `requiresShown` 设为 A 的 ID。
这样 B 只向曾展示过 A 的用户展示，没有见过宕机公告的用户不会收到恢复公告。

用户关闭公告后，将其 ID 保存到本地 `closed` 集合。
展示记录和关闭记录均不随公告撤回或过期而删除。

## 获取与缓存

HMCL 启动时先读取本地缓存，再异步检查更新；运行期间也定期刷新，默认间隔为 10 分钟。
请求失败时保留缓存并延后重试，不阻塞启动器。缓存中的公告仍按 `expiresAt` 失效。

使用 HTTP 条件请求减少重复传输：

- 将服务端返回的 `Last-Modified` 解析为 Unix 毫秒时间戳保存，下次请求时格式化为 GMT 时区的 HTTP 日期，通过
  `If-Modified-Since` 发送。
- 本地没有有效缓存，或服务端未提供 `Last-Modified` 时，直接获取完整公告列表。

返回 `200` 且内容有效时，替换本地公告列表及对应的修改时间；响应未提供 `Last-Modified` 时清除旧值。
返回 `304` 时继续使用缓存。请求失败或返回内容无效时，不覆盖原有公告和修改时间。
本地请求时间只用于控制刷新频率，不能作为 `If-Modified-Since` 的值。

缓存保存在 `./.hmcl/announcements.json`，包含公告列表、展示及关闭记录、最后请求时间和服务端修改时间，例如：

```json
{
  "lastAttemptTime": 1788940800000,
  "lastModified": 1788940800000,
  "shown": [
    "019976a8-04af-7442-9ffd-ebdaf9bbf68e"
  ],
  "closed": [
    "019976a8-04af-7442-9ffd-ebdaf9bbf68e"
  ],
  "announcements": []
}
```

`lastAttemptTime` 和 `lastModified` 均使用 Unix 毫秒时间戳，分别表示本地请求时间和服务端提供的文件修改时间；服务端未提供
`Last-Modified` 时省略 `lastModified`。
`announcements` 保存包含 Markdown 原文的公告列表。

## 参考

- [公告系统需求与草案](https://github.com/HMCL-dev/HMCL/issues/4544)
- [RFC 9110：条件请求](https://www.rfc-editor.org/rfc/rfc9110.html#section-13)
- [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/)
