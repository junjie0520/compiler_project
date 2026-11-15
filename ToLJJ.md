## 致LJJ ：A的基础与简单表达式部分待办事项 — 2026-11-14 10：00

B 负责的复合表达式部分已完成并通过编译，以下事项需由 A 完成。

### 一、待补充的空实现（P3 声明与作用域迁移）

1. `visitStructDef`：按声明顺序构建成员表，重名成员报告 `DUPLICATE_MEMBER`，仅在全部成员合法时调用 `defineStruct`。
2. `visitGlobalVarDef`：解析声明类型并调用 `define`，重名报告 `REDEFINED_SYMBOL`。
3. `visitFunctionDef`：在访问函数体前注册函数符号以支持递归调用；以 `FunctionSymbol` 作为函数作用域；依次处理形参；函数体最外层不新建作用域，直接遍历 statement。
4. `visitBlock`：进入时调用 `enterScope`，退出时在 `finally` 中调用 `exitScope`。
5. `visitLocalVarDef`：先定义变量，再访问初始化表达式（保证 `int x = x` 合法）。初始化类型检查由 C 负责，将在同一方法内追加。

### 二、需修正的既有逻辑

1. 裸标识符无法解析时，应调用 `reporter.undeclaredIdentifier(token)`；当前实现直接返回 `null`，未报告错误。
2. `isUnaryOpCtx` 目前仅依据 `MINUS` 判断，会将 `a - b` 误判为一元表达式。建议改用 `ctx.expression().size() == 1` 判断，并补充一元 `+` 的处理。
3. `isBinaryOpCtx` 缺少 `MOD` 与 `POW`。
4. `isParenthesized` 会将 `f(x)` 误判为括号表达式，建议增加 `ctx.Identifier() == null` 条件。
5. `isSameType` 在比较数组时需同时比较长度，`int[3]` 与 `int[4]` 不应视为相同类型。
 
### 三、验收预期

A 完成上述事项后，`003 / 004 / 005 / 006 / 008 / 009` 应全部通过；`007` 还需等待 A 的 undeclared 报告与 C 的 return 语句访问共同完成。

## C 部分进度补充 — 2026-11-15

- 7.1 局部变量初始化的类型不匹配检查（定位 `=`）和数组初始化诊断（定位变量名）已加入 `visitLocalVarDef`。变量定义及作用域注册仍由 A 负责，需与初始化检查合并在同一方法中。
- 用例 `007` 的 return 检查已实现，但需等待 A 完成未声明标识符报告及函数/作用域访问逻辑后再做集成验证。

