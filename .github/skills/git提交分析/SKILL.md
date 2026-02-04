---
name: git提交分析
description: 用于根据提供的 git commit logs 生成项目分析文档，支持数据库变更分析和业务功能分析。
---

# Git 提交分析技能

此技能用于分析一组 git 提交，并在 `docs/project_analysis/<branch_name>` 目录下生成分析文档。

## 1. 准备工作

当用户提供一个或多个 commit hash 时，首先执行以下脚本以生成 diff 文件：

执行 Node.js 脚本：
```bash
node .github/skills/git提交分析/scripts/git_diff_generator.js <commit_id_1> <commit_id_2> ...
```
*   该脚本会自动获取当前分支名称，并在 `docs/project_analysis/<branch_name>/` 下创建文件夹。
*   脚本会为每个 commit 生成 `diff_x.txt` 文件。

## 2. 分析步骤

脚本执行完成后，请按照以下步骤进行分析：

1.  **读取生成的 diff 文件**：
    *   使用 `list_dir` 查看生成的目录内容。
    *   使用 `read_file` 读取所有的 `diff_x.txt` 文件。

2.  **生成文档**：
    在生成的目录下（`docs/project_analysis/<branch_name>/`）创建以下 Markdown 文档：

    *   **数据库关系文档 (`database_relationships.md`)**：
        *   **触发条件**：如果 diff 文件中包含 `.sql` 文件的变更（如 `CREATE TABLE`, `ALTER TABLE` 等）。
        *   **内容要求**：
            *   分析 SQL 变更，提取表结构。
            *   使用 Mermaid 生成 ER 关系图 (`erDiagram`)。
            *   解释表的作用及字段含义。
            *   如果有分库分表设计，需特别说明。

    *   **业务功能文档 (`business_features.md`)**：
        *   **触发条件**：总是生成。
        *   **内容要求**：
            *   需要详细分析，便于用户理解本次提交
            *   根据 Java/XML/配置文件的变更，分析本次提交实现的业务功能。
            *   整理功能列表和业务流程。
            *   重点分析特色功能（如设计模式应用、并发处理、架构亮点等）。
            *   如果涉及 DDD 领域模型变化，请详细说明聚合根、实体、值对象的变化。

## 3. 输出示例

完成分析后，请告知用户文档已生成，并提供文档链接。

例如：
> 已为您生成此次提交的分析文档：
> - [数据库关系文档](docs/project_analysis/xxx/database_relationships.md)
> - [业务功能文档](docs/project_analysis/xxx/business_features.md)
