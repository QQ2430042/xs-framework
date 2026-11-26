---
name: resolve-readme-conflict
overview: 解决 README.md 中已提交的合并冲突标记：删除 HEAD 侧的简短标题和所有冲突标记，保留 origin/main 的详细中文文档。
todos:
  - id: clean-readme-conflict
    content: 清理 README.md 冲突标记并保留完整文档
    status: completed
  - id: verify-readme-diff
    content: 校验 README.md 差异范围与冲突标记清除
    status: completed
    dependencies:
      - clean-readme-conflict
---

## 用户需求

处理 `README.md` 中已经提交的 Git 合并冲突：删除冲突起始标记、`HEAD` 侧简短描述、分隔标记和结束标记，保留 `origin/main` 提供的详细中文文档内容。

## 产品概述

项目 README 当前把两版内容连同冲突标记一起展示，影响文档可读性；处理后应恢复为一份完整、可直接阅读的项目说明。

## 核心功能

- 保留第一行标题 `# xs-framework`
- 删除 `&lt;&lt;&lt;&lt;&lt;&lt;&lt; HEAD`、`xs-framework 1.0`、`=======`、`&gt;&gt;&gt;&gt;&gt;&gt;&gt; 39b3e3a8a4d99f50c1dfdcac5711edd48a11189f`
- 原样保留技术基线、模块一览、快速开始、远程适配器、架构与 SDD、已知边界、License 等正文
- 不提交、不推送，不改动其他文件

## 技术栈

- 文件类型：Markdown 文档
- 修改方式：直接编辑 `d:/work/workbuddywork/xs-framework/README.md`
- 验证方式：`git diff -- README.md`、冲突标记全文搜索、`git diff --check`
- 不涉及 Java/Maven 编译与运行测试

## 实施方案

基于已确认的行号，只做最小范围文本清理：删除第 2–4 行与第 81 行，保留第 1 行标题和第 5–80 行正文及原有空行。该方案不重排格式、不改写文案，能把变更控制在单个文件且可精确验证。

## 实施注意事项

- 当前冲突标记已随合并提交进入仓库，解决后只形成工作区未提交修改。
- 不执行 `git add`、`git commit`、`git push`，除非用户后续明确要求。
- 不触碰 `pom.xml`、Java 源码、脚本、`docs/` 目录或 GitHub 配置。
- 预期无性能影响；验证重点是正文未被误删、冲突标记完全清除。

## 目录结构

```text
d:/work/workbuddywork/xs-framework/
└── README.md
    # [MODIFY] 删除 4 行冲突标记与 HEAD 侧文本；保留完整中文文档和原有 Markdown 结构
```