## 致LJJ：A 的基础与简单表达式部分完成报告 — 2026-11-15

A 负责的基础与简单表达式部分已全部完成，`make test` 结果 9/10 通过，唯一失败项属 B 的 5.6 节。

### 一、已补齐的空实现（P3 声明与作用域迁移）

1. `visitStructDef`：按 `fieldDef+` 声明顺序构建成员表，重名报告 `DUPLICATE_MEMBER`，仅在全部成员合法时调用 `defineStruct`，成员经 `StructType.defineMember` 逐个加入。
2. `visitGlobalVarDef`：解析声明类型并 `define`，重名报告 `REDEFINED_SYMBOL`。
3. `visitFunctionDef`：访问函数体前注册函数符号以支持递归；以 `FunctionSymbol` 为函数作用域；形参经 `parameterList().parameter().variableDecl()` 处理，用 `FunctionSymbol.addParameter` 同时进入作用域与参数列表；函数体最外层不新建作用域，直接遍历 `ctx.block().statement()`。
4. `visitBlock`：进入 `enterScope`，退出在 `finally` 中 `exitScope`。
5. `visitLocalVarDef`：先定义变量再访问初始化表达式（`int x = x` 合法），C 的初始化检查原样保留在同一方法内。

### 二、已修正的既有逻辑

1. 裸标识符无法解析时调用 `reporter.undeclaredIdentifier(token)`，不再静默返回 `null`。
2. `isUnaryOpCtx` 改用 `ctx.expression().size() == 1`，补一元 `+`；`unaryOpToken` 同步支持 `+`。
3. `isBinaryOpCtx` 补 `MOD` 与 `POW`。
4. `isParenthesized` 增加 `ctx.Identifier() == null`，避免把 `f(x)` 误判为括号表达式。
5. `isSameType` 数组比较同时比较长度。

### 三、额外修复（A 职责范围内）

- **shadowing 误报**：局部变量与形参判重由递归 `resolve` 改为只查当前层的 `resolveLocal`，修复 `006`。
- **形参未进参数列表**：形参注册改用 `FunctionSymbol.addParameter`，修复 `002`（输出恢复 `Function add(int a, int b): int`）。
- `Scope.java` 新增 `resolveLocal(String)`，仅查当前作用域。

### 四、验收结果

- `make build` 编译通过。
- `make test`：`001–009` 全部通过（9/10）。
- `010_bad_function_argument` 仍失败，输出 `OK` 而非 `Error BAD_ARGUMENT_TYPE at Line 0: p`。

### 五、010 失败原因与归属

`visitExpression` 缺少函数调用分支 `Identifier LPAREN argumentList? RPAREN`。`f(p)` 既不满足 `isPlainIdentifier`（要求 `LPAREN == null`），也不满足 `isParenthesized`（要求 `Identifier() == null`），最终 `return null` 静默通过，未检查实参类型。**属 B 的 5.6 节**，需补 `NOT_FUNCTION`、`BAD_ARGUMENT_COUNT`、`BAD_ARGUMENT_TYPE`（数量不匹配仍检查各实参、数组不退化、`BAD_ARGUMENT_TYPE` 定位实参起始 Token），A 不处理。

### 六、遗留风险

- `reporter.duplicateMember` / `redefinedSymbol` / `undeclaredIdentifier` 方法名以实际 `ErrorReporter` 为准。
- `StructType.defineMember` 方法名需与 `StructType.java` 一致（如实际为 `addMember`/`putMember` 需同步替换）。

### 七、下一步

- A 部分无遗留待办。
- 请 B 补齐函数调用检查后复跑 `010`；全绿后由 C 执行 `make handin` 并核对压缩包五个 Java 文件与目录结构。